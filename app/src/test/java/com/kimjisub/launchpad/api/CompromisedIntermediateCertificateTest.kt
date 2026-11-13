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
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLPeerUnverifiedException

class CompromisedIntermediateCertificateTest {

	@Test
	fun forgedServerIsRefusedBeforeAnyRequestEvenWhenItsRootIsTrusted() {
		// This intermediate's publicly known private scalar is 4. Reconstructing it lets a
		// loopback server present a valid chain that only OkHttp's blocklist should reject.
		val parameters = AlgorithmParameters.getInstance("EC").apply {
			init(ECGenParameterSpec("secp384r1"))
		}.getParameterSpec(ECParameterSpec::class.java)
		val privateKey = KeyFactory.getInstance("EC")
			.generatePrivate(ECPrivateKeySpec(BigInteger.valueOf(4), parameters))
		val issuer = HeldCertificate(KeyPair(compromisedIntermediate.publicKey, privateKey), compromisedIntermediate)
		val forgedCertificate = HeldCertificate.Builder()
			.signedBy(issuer)
			.addSubjectAlternativeName("127.0.0.1")
			.build()
		val serverCertificates = HandshakeCertificates.Builder()
			.heldCertificate(forgedCertificate, compromisedIntermediate)
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
			val clientCertificates = HandshakeCertificates.Builder()
				.addTrustedCertificate(root)
				.build()
			val client = OkHttpClient.Builder()
				.sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
				.build()
			val request = Request.Builder().url("https://127.0.0.1:${server.address.port}/packs").build()

			val refusal = assertThrows(SSLPeerUnverifiedException::class.java) {
				client.newCall(request).execute().use { response ->
					throw AssertionError("Forged server accepted: code=${response.code}, body=${response.body.string()}, requests=${requests.get()}")
				}
			}
			assertTrue(refusal.message.orEmpty().contains("blocklisted by OkHttp"))
			assertEquals("No HTTP request should reach the forged server", 0, requests.get())
		} finally {
			server.stop(0)
		}
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
