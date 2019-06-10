package com.kimjisub.launchpad.api.file;

import com.kimjisub.launchpad.api.BaseApiService;
import com.kimjisub.launchpad.api.ProgressResponseBody;
import com.kimjisub.launchpad.manager.Log;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

public class FileApi extends BaseApiService {

	static final String APIURL = "https://api.unipad.kr";
	static FileService fileService;

	static ProgressResponseBody.OnAttachmentDownloadListener progressListener;

	public static void setProgressListener(ProgressResponseBody.OnAttachmentDownloadListener progressListener) {
		FileApi.progressListener = progressListener;
	}

	public static FileService getService() {
		if (fileService == null) {
			HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(Log::network)
					.setLevel(HttpLoggingInterceptor.Level.BODY);
			OkHttpClient client = getUnsafeOkHttpClient()
					.addInterceptor(interceptor)
					//.cookieJar(new MyCookieJar())
					.addInterceptor(chain -> {
						if(progressListener == null) return chain.proceed(chain.request());

						Response originalResponse = chain.proceed(chain.request());
						return originalResponse.newBuilder()
								.body(new ProgressResponseBody(originalResponse.body(), progressListener))
								.build();
					})
					.build();

			Retrofit retrofit = new Retrofit.Builder()
					.baseUrl(APIURL)
					.addConverterFactory(GsonConverterFactory.create())
					.client(client)
					.build();
			fileService = retrofit.create(FileService.class);
		}

		return fileService;
	}



	public interface FileService {

		// ============================================================================================= /unishare

		@GET("/unishare/{code}/download")
		Call<ProgressResponseBody> unishare_download(@Path("code") String code);
	}
}
