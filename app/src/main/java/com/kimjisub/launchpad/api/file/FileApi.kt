package com.kimjisub.launchpad.api.file

import com.kimjisub.launchpad.api.BaseApiService
import org.koin.core.component.KoinComponent
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

object FileApi : KoinComponent {
	private const val API_BASE_URL = "https://api.unipad.io"

	private val productionService: FileService by lazy {
		BaseApiService.createRetrofitService(API_BASE_URL, FileService::class.java)
	}

	// Scoped instrumentation services leave the normal shared client unchanged.
	val service: FileService
		get() = getKoin().getOrNull<FileService>() ?: productionService

	interface FileService {
		@GET
		@Streaming
		fun download(@Url url: String): Call<ResponseBody>
	}
}
