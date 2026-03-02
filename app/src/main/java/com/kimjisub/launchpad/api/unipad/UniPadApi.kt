package com.kimjisub.launchpad.api.unipad

import com.kimjisub.launchpad.api.BaseApiService
import com.kimjisub.launchpad.api.unipad.vo.UnishareVO
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

object UniPadApi {
	private const val API_BASE_URL = "https://api.unipad.io"

	val service: UniPadApiService by lazy {
		BaseApiService.createRetrofitService(API_BASE_URL, UniPadApiService::class.java)
	}

	interface UniPadApiService {
		@GET("/unishare/{code}")
		suspend fun getUnishare(@Path("code") code: String): Response<UnishareVO>
	}
}
