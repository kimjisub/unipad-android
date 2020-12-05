package com.kimjisub.launchpad.api.unipad

import com.kimjisub.launchpad.api.BaseApiService
import com.kimjisub.launchpad.api.unipad.vo.UnishareVO
import com.kimjisub.manager.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import okhttp3.logging.HttpLoggingInterceptor.Logger
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.Retrofit.Builder
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

class UniPadApi : BaseApiService() {

	companion object {
		private const val URL = "https://api.unipad.io"

		val service: UniPadApiService by lazy {
			val httpLoggingInterceptor = HttpLoggingInterceptor(object : Logger {
				override fun log(message: String) {
					Log.network(message)
				}
			})
			httpLoggingInterceptor.level = Level.BODY
			val client: OkHttpClient = unsafeOkHttpClient
				.addInterceptor(httpLoggingInterceptor)
				.build()
			val retrofit: Retrofit = Builder()
				.baseUrl(URL)
				.addConverterFactory(GsonConverterFactory.create(gson))
				.client(client)
				.build()
			retrofit.create(UniPadApiService::class.java)
		}

		interface UniPadApiService {

			// unishare /////////////////////////////////////////////////////////////////////////////////////////

			@GET("/unishare")
			fun getUnishares(): Call<List<UnishareVO?>?>?

			@POST("/unishare")
			fun createUnishare(@Body item: UnishareVO?): Call<UnishareVO?>?

			@GET("/unishare/{code}")
			fun getUnishare(@Path("code") code: String?): Call<UnishareVO?>?
		}
	}
}