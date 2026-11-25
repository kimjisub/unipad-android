package com.kimjisub.launchpad.api

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okhttp3.tls.decodeCertificatePem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.AlgorithmParameters
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.X509ExtendedTrustManager

/**
 * OkHttp's own blocklist must refuse a server whose chain runs through the compromised eMudhra
 * intermediate, on devices whose system certificate store still trusts the eMudhra root.
 *
 * The client here checks the chain with [ChainSignatureTrustManager] rather than the JDK's trust
 * manager: JDK 21.0.13.1 and later refuse this intermediate themselves (`lib/security/blocked.certs`),
 * which would end the handshake before OkHttp's check runs and leave it untested.
 */
class CompromisedIntermediateCertificateTest {

	@Test
	fun forgedServerIsRefusedBeforeAnyRequestEvenWhenItsRootIsTrusted() {
		serve(forgedCertificate(), compromisedIntermediate) { url, requests ->
			val refusal = assertThrows(SSLPeerUnverifiedException::class.java) {
				fetch(clientTrusting(root), url)
			}
			assertTrue(refusal.message.orEmpty().contains("blocklisted by OkHttp"))
			assertEquals("No HTTP request should reach the forged server", 0, requests.get())
		}
	}

	/** Proves the refusal above comes from OkHttp's blocklist, not from the client's chain check. */
	@Test
	fun sameChainCheckAcceptsAnIntactChainAndTheRequestArrives() {
		val ownRoot = HeldCertificate.Builder().certificateAuthority(1).build()
		val ownIntermediate = HeldCertificate.Builder().certificateAuthority(0).signedBy(ownRoot).build()
		val server = HeldCertificate.Builder()
			.signedBy(ownIntermediate)
			.addSubjectAlternativeName("127.0.0.1")
			.build()

		serve(server, ownIntermediate.certificate) { url, requests ->
			assertEquals("fake pack list", fetch(clientTrusting(ownRoot.certificate), url))
			assertEquals(1, requests.get())
		}
	}

	/** Proves the client's chain check verifies signatures instead of accepting any chain. */
	@Test
	fun sameChainCheckRefusesTheForgedChainWithoutItsRoot() {
		val unrelatedRoot = HeldCertificate.Builder().certificateAuthority(1).build()

		serve(forgedCertificate(), compromisedIntermediate) { url, requests ->
			val refusal = assertThrows(SSLHandshakeException::class.java) {
				fetch(clientTrusting(unrelatedRoot.certificate), url)
			}
			assertTrue(refusal.toString(), generateSequence<Throwable>(refusal) { it.cause }.any {
				it.message.orEmpty().contains("No trusted root")
			})
			assertEquals(0, requests.get())
		}
	}

	private fun forgedCertificate(): HeldCertificate {
		// This intermediate's publicly known private scalar is 4. Reconstructing it lets a
		// loopback server present a chain that is valid up to the eMudhra root.
		val parameters = AlgorithmParameters.getInstance("EC").apply {
			init(ECGenParameterSpec("secp384r1"))
		}.getParameterSpec(ECParameterSpec::class.java)
		val privateKey = KeyFactory.getInstance("EC")
			.generatePrivate(ECPrivateKeySpec(BigInteger.valueOf(4), parameters))
		val issuer = HeldCertificate(KeyPair(compromisedIntermediate.publicKey, privateKey), compromisedIntermediate)
		return HeldCertificate.Builder()
			.signedBy(issuer)
			.addSubjectAlternativeName("127.0.0.1")
			.build()
	}

	private fun serve(
		certificate: HeldCertificate,
		intermediate: X509Certificate,
		block: (url: String, requests: AtomicInteger) -> Unit,
	) {
		val serverCertificates = HandshakeCertificates.Builder()
			.heldCertificate(certificate, intermediate)
			.build()
		val requests = AtomicInteger()
		val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
		server.httpsConfigurator = HttpsConfigurator(serverCertificates.sslContext())
		server.createContext("/packs") { exchange ->
			requests.incrementAndGet()
			val body = "fake pack list".toByteArray()
			exchange.sendResponseHeaders(200, body.size.toLong())
			exchange.responseBody.use { it.write(body) }
		}
		server.start()
		try {
			block("https://127.0.0.1:${server.address.port}/packs", requests)
		} finally {
			server.stop(0)
		}
	}

	private fun clientTrusting(anchor: X509Certificate): OkHttpClient {
		val trustManager = ChainSignatureTrustManager(listOf(anchor))
		val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
		return OkHttpClient.Builder()
			.sslSocketFactory(sslContext.socketFactory, trustManager)
			.build()
	}

	private fun fetch(client: OkHttpClient, url: String): String =
		client.newCall(Request.Builder().url(url).build()).execute().use { response ->
			assertEquals(200, response.code)
			response.body.string()
		}

	// Public certificate fixtures from OkHttp's CompromisedIntermediateTest:
	// https://github.com/square/okhttp/blob/fb34d4da/okhttp/src/commonTest/kotlin/okhttp3/CompromisedIntermediateTest.kt
	// emSign Root TLS CA - G3, valid through February 2049.
	private val root = """
		-----BEGIN CERTIFICATE-----
		MIICKTCCAa+gAwIBAgIQDnYGcvFDRZ/I/gqwvAXjlDAKBggqhkjOPQQDAzBWMQsw
		CQYDVQQGEwJJTjElMCMGA1UECgwcZU11ZGhyYSBUZWNobm9sb2dpZXMgTGltaXRl
		ZDEgMB4GA1UEAwwXZW1TaWduIFJvb3QgVExTIENBIC0gRzMwHhcNMjQwMjA4MDgx
		OTMyWhcNNDkwMjA3MDgxOTMyWjBWMQswCQYDVQQGEwJJTjElMCMGA1UECgwcZU11
		ZGhyYSBUZWNobm9sb2dpZXMgTGltaXRlZDEgMB4GA1UEAwwXZW1TaWduIFJvb3Qg
		VExTIENBIC0gRzMwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAARfD9w/q1cWxL6D9bMG
		H6CQ4u+gAV6GN9s+PwQx9Bl4hC+0iU8QOk8mWO/RwXQy10DCSxT8vSIJClHKp+Ao
		4qJKdYeV0SwZ7LMQziyLfxIUOY6Grja/sQFwLKYf8EdDXAyjQjBAMB0GA1UdDgQW
		BBThOqshfPHjTVSu1HK3/YtZQiCQVDAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0TAQH/
		BAUwAwEB/zAKBggqhkjOPQQDAwNoADBlAjBhCFTkly7Yl7+s4hiw+k6ZJTXT2AiI
		rvmsmfqPr5E7ecwXb6kXA5LTFd6rjwTuV3sCMQC9w6ZSbVq1mveMO6dKFziOzClT
		KCT2RFT6yXtq6nzgRICgBmiXbJAXsmZ8yAwjyWc=
		-----END CERTIFICATE-----
	""".trimIndent().decodeCertificatePem()

	// EM OV TLS CA - G3C, valid through April 2040.
	private val compromisedIntermediate = """
		-----BEGIN CERTIFICATE-----
		MIIDaDCCAu6gAwIBAgIPKWkmwiejoVJjkrGUgqUEMAoGCCqGSM49BAMDMFYxCzAJ
		BgNVBAYTAklOMSUwIwYDVQQKDBxlTXVkaHJhIFRlY2hub2xvZ2llcyBMaW1pdGVk
		MSAwHgYDVQQDDBdlbVNpZ24gUm9vdCBUTFMgQ0EgLSBHMzAeFw0yNTA0MzAxNTM3
		NDFaFw00MDA0MjgxODMwMDBaMEAxCzAJBgNVBAYTAlVTMRQwEgYDVQQKDAtlTXVk
		aHJhIEluYzEbMBkGA1UEAwwSRU0gT1YgVExTIENBIC0gRzNDMHYwEAYHKoZIzj0C
		AQYFK4EEACIDYgAEE4JRzVKskpjByKrZdzId65fnCb0LTKCspV3IrVHc/J0ViaFZ
		fjpRIOHv1jHGPhg1ysrimGmmLhYx6KKBgatWYW3EXZGKvAnzqw5jz3kqpNztc4e+
		N7ulaVSfHAKycO1no4IBljCCAZIwHwYDVR0jBBgwFoAU4TqrIXzx401UrtRyt/2L
		WUIgkFQwHQYDVR0OBBYEFDTq6gzryQra+dDF4u2beMmHnhtsMB0GA1UdJQQWMBQG
		CCsGAQUFBwMBBggrBgEFBQcDAjBdBgNVHSAEVjBUMDkGCysGAQQBg44hAQABMCow
		KAYIKwYBBQUHAgEWHGh0dHA6Ly9yZXBvc2l0b3J5LmVtc2lnbi5jb20wDQYLKwYB
		BAGDjiEBAm4wCAYGZ4EMAQICMHUGCCsGAQUFBwEBBGkwZzAkBggrBgEFBQcwAYYY
		aHR0cDovL29jc3AtYy5lbXNpZ24uY29tMD8GCCsGAQUFBzAChjNodHRwOi8vcmVw
		b3NpdG9yeS5lbXNpZ24uY29tL2NlcnRzL0VNT1ZUTFNDQUczQy5wN2MwNwYDVR0f
		BDAwLjAsoCqgKIYmaHR0cDovL2NybC5lbXNpZ24uY29tLz9yb290dGxzY2FnMy5j
		cmwwDgYDVR0PAQH/BAQDAgEGMBIGA1UdEwEB/wQIMAYBAf8CAQAwCgYIKoZIzj0E
		AwMDaAAwZQIxAMSZt2xsPeumRdeSMSQ3CbzIZeQ/q7GDCWfeE1Pq+mhgA6gRM44c
		NVF3xJ+wsxJk+wIwFJbaDMokD8Ei6eNOiA8yUMYvfzQyngmrhZQMdPCK1SewrR79
		4rSb9vGqxZzelQth
		-----END CERTIFICATE-----
	""".trimIndent().decodeCertificatePem()
}

/**
 * Trusts a server chain when each certificate is in its validity period, names and is signed by
 * the next one, every issuer is a CA, and the last one is signed by one of [anchors]: the
 * checks a platform trust manager makes, without any platform list of blocked certificates.
 */
private class ChainSignatureTrustManager(private val anchors: List<X509Certificate>) : X509ExtendedTrustManager() {
	override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
		if (chain.isEmpty()) throw CertificateException("Empty server certificate chain")
		chain.forEachIndexed { index, certificate ->
			certificate.checkValidity()
			if (index > 0 && certificate.basicConstraints < 0) {
				throw CertificateException("Not a certificate authority: ${certificate.subjectX500Principal}")
			}
			val issuer = chain.getOrNull(index + 1)
				?: anchors.firstOrNull { it.subjectX500Principal == certificate.issuerX500Principal }
				?: throw CertificateException("No trusted root for ${certificate.issuerX500Principal}")
			if (issuer.subjectX500Principal != certificate.issuerX500Principal) {
				throw CertificateException("Chain out of order at ${certificate.subjectX500Principal}")
			}
			try {
				certificate.verify(issuer.publicKey)
			} catch (e: GeneralSecurityException) {
				throw CertificateException("Bad signature on ${certificate.subjectX500Principal}", e)
			}
		}
	}

	override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
		checkServerTrusted(chain, authType)

	override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
		checkServerTrusted(chain, authType)

	override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String): Unit =
		throw CertificateException("Client certificates are not used")

	override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
		checkClientTrusted(chain, authType)

	override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
		checkClientTrusted(chain, authType)

	override fun getAcceptedIssuers(): Array<X509Certificate> = anchors.toTypedArray()
}
