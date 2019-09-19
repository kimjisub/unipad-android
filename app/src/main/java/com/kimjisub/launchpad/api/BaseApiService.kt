package com.kimjisub.launchpad.api


import android.annotation.SuppressLint
import com.google.gson.*
import okhttp3.OkHttpClient
import java.lang.reflect.Type
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.*


open class BaseApiService {

	companion object {

		@SuppressLint("SimpleDateFormat")
		private val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

		val unsafeOkHttpClient: OkHttpClient.Builder
			get() {
				// Create a trust manager that does not validate certificate chains


				val trustAllCerts = arrayOf<TrustManager>(
					object : X509TrustManager {
						override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
						}

						override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
						}

						override fun getAcceptedIssuers(): Array<out X509Certificate>? {
							return arrayOf()
						}
					}
				)
				val sslContext = SSLContext.getInstance("SSL")
				sslContext.init(null, trustAllCerts, SecureRandom())
				val sslSocketFactory = sslContext.socketFactory
				return OkHttpClient.Builder()
					.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
					.hostnameVerifier(HostnameVerifier { _: String?, _: SSLSession? -> true })

			}

		val gson: Gson?
			get() {
				return GsonBuilder().registerTypeAdapter(Date::class.java, object : JsonDeserializer<Date?> {
					@Throws(JsonParseException::class)
					override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): Date? {
						return try {
							df.parse(json.asString)
						} catch (e: ParseException) {
							e.printStackTrace()
							null
						}
					}
				}).create()
			}

	}


}