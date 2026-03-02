package com.kimjisub.launchpad.api.file

import com.kimjisub.launchpad.api.BaseApiService
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

object FileApi {
	private const val API_BASE_URL = "https://api.unipad.io"

	val service: FileService by lazy {
		BaseApiService.createRetrofitService(API_BASE_URL, FileService::class.java)
	}

	interface FileService {
		@GET
		@Streaming
		fun download(@Url url: String): Call<ResponseBody>
	}
}
