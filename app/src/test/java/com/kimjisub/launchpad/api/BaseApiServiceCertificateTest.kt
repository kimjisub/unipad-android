package com.kimjisub.launchpad.api

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.http.GET
import java.net.InetSocketAddress
import javax.net.ssl.SSLHandshakeException

/**
 * The app's HTTP client must check the server's certificate in every build. Unit tests run the
 * debug variant, so this also covers the builds testers and developers install.
 */
class BaseApiServiceCertificateTest {
	private interface PingService {
		@GET("ping")
		fun ping(): Call<ResponseBody>
	}

	private val untrusted = HeldCertificate.Builder()
		.commonName("unipad test server")
		.addSubjectAlternativeName("127.0.0.1")
		.build()
	private lateinit var server: HttpsServer
	private lateinit var baseUrl: String

	@Before
	fun setUp() {
		val serverCertificates = HandshakeCertificates.Builder().heldCertificate(untrusted).build()
		server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
		server.httpsConfigurator = HttpsConfigurator(serverCertificates.sslContext())
		server.createContext("/ping") { exchange ->
			val body = "pong".toByteArray()
			exchange.sendResponseHeaders(200, body.size.toLong())
			exchange.responseBody.use { it.write(body) }
		}
		server.start()
		baseUrl = "https://127.0.0.1:${server.address.port}/"
	}

	@After
	fun tearDown() {
		if (::server.isInitialized) server.stop(0)
	}

	@Test
	fun serverWithAnUntrustedCertificateIsRefused() {
		val service = BaseApiService.createRetrofitService(baseUrl, PingService::class.java)

		assertThrows(SSLHandshakeException::class.java) { service.ping().execute() }
	}

	/** Proves the refusal above comes from the certificate check, not from a broken test server. */
	@Test
	fun sameServerAnswersAClientThatTrustsItsCertificate() {
		val clientCertificates = HandshakeCertificates.Builder()
			.addTrustedCertificate(untrusted.certificate)
			.build()
		val client = OkHttpClient.Builder()
			.sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
			.build()

		client.newCall(Request.Builder().url(baseUrl + "ping").build()).execute().use { response ->
			assertEquals(200, response.code)
			assertEquals("pong", response.body.string())
		}
	}
}
