package com.kimjisub.launchpad.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.kimjisub.launchpad.tool.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date

object BaseApiService {

	private val DATE_TIME_FORMATTER: DateTimeFormatter =
		DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

	val gson: Gson by lazy {
		GsonBuilder().registerTypeAdapter(
			Date::class.java,
			object : JsonDeserializer<Date?> {
				@Throws(JsonParseException::class)
				override fun deserialize(
					json: JsonElement,
					typeOfT: Type?,
					context: JsonDeserializationContext?,
				): Date? {
					return try {
						val ldt = LocalDateTime.parse(json.asString, DATE_TIME_FORMATTER)
						Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant())
					} catch (e: DateTimeParseException) {
						Log.err("Date parse failed", e)
						null
					}
				}
			}).create()
	}

	// One client for every service: each Retrofit service used to build its own connection pool,
	// dispatcher, and thread pools. No callTimeout on purpose (a pack download may take minutes);
	// connect/read timeouts stop a stalled socket from holding a download forever.
	private val sharedClient: OkHttpClient by lazy {
		val httpLoggingInterceptor = HttpLoggingInterceptor { message -> Log.network(message) }
		// Never BODY: the same client downloads UniPack ZIPs, and BODY buffers the whole
		// response in memory before logging it (Crashlytics b1f34473 / 0a626898: OOM in
		// okio.Segment during UniPackDownloader).
		httpLoggingInterceptor.level = Level.HEADERS
		OkHttpClient.Builder()
			.connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
			.readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
			.writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
			.addInterceptor(httpLoggingInterceptor)
			.build()
	}

	fun <T> createRetrofitService(baseUrl: String, serviceClass: Class<T>): T {
		val client = sharedClient
		return Retrofit.Builder()
			.baseUrl(baseUrl)
			.addConverterFactory(GsonConverterFactory.create(gson))
			.client(client)
			.build()
			.create(serviceClass)
	}
}
