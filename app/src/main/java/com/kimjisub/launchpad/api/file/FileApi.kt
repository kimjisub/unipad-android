package com.kimjisub.launchpad.api.file

import com.kimjisub.launchpad.api.BaseApiService
import com.kimjisub.manager.Log
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import okhttp3.logging.HttpLoggingInterceptor.Logger
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.Retrofit.Builder
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Url

class FileApi : BaseApiService() {

	companion object {
		private const val URL = "https://api.unipad.kr"

		val service: FileService by lazy {
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
			retrofit.create(FileService::class.java)
		}

		interface FileService {

			// unishare /////////////////////////////////////////////////////////////////////////////////////////

			@GET("/unishare/{code}/download")
			@Streaming
			fun unishare_download(@Path("code") code: String?): Call<ResponseBody?>?

			@GET
			@Streaming
			fun download(@Url url : String): Call<ResponseBody>
		}

	}
}