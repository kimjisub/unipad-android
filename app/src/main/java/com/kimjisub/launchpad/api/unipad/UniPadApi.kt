package com.kimjisub.launchpad.api.unipad

import com.kimjisub.launchpad.api.BaseApiService
import org.koin.core.component.KoinComponent
import com.kimjisub.launchpad.api.unipad.vo.UnishareVO
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

object UniPadApi : KoinComponent {
	private const val API_BASE_URL = "https://api.unipad.io"

	private val productionService: UniPadApiService by lazy {
		BaseApiService.createRetrofitService(API_BASE_URL, UniPadApiService::class.java)
	}

	// Scoped instrumentation services leave the normal shared client unchanged.
	val service: UniPadApiService
		get() = getKoin().getOrNull<UniPadApiService>() ?: productionService

	interface UniPadApiService {
		@GET("/unishare/{code}")
		suspend fun getUnishare(@Path("code") code: String): Response<UnishareVO>
	}
}
