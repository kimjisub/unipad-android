package com.kimjisub.launchpad.api.file;

import com.kimjisub.launchpad.api.BaseApiService;
import com.kimjisub.launchpad.manager.Log;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Streaming;

public class FileApi extends BaseApiService {

	static final String APIURL = "https://api.unipad.kr";
	static FileService fileService;

	public static FileService getService() {
		if (fileService == null) {
			HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(Log::network)
					.setLevel(HttpLoggingInterceptor.Level.BODY);
			OkHttpClient client = getUnsafeOkHttpClient()
					.addInterceptor(interceptor)
					//.cookieJar(new MyCookieJar())
					.build();

			Retrofit retrofit = new Retrofit.Builder()
					.baseUrl(APIURL)
					.addConverterFactory(GsonConverterFactory.create(getGson()))
					.client(client)
					.build();
			fileService = retrofit.create(FileService.class);
		}

		return fileService;
	}



	public interface FileService {

		// ============================================================================================= /unishare

		@GET("/unishare/{code}/download")
		@Streaming
		Call<ResponseBody> unishare_download(@Path("code") String code);
	}
}
