package com.kimjisub.launchpad.networks.api;

import com.kimjisub.launchpad.networks.BaseApiService;
import com.kimjisub.launchpad.networks.api.vo.UnishareVO;
import com.kimjisub.launchpad.manager.Log;

import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public class UniPadApi extends BaseApiService {

	static final String APIURL = "https://api.unipad.kr";
	static UniPadApiService uniPadApiService;

	public static UniPadApiService getService() {
		if (uniPadApiService == null) {
			HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(Log::network)
					.setLevel(HttpLoggingInterceptor.Level.BODY);
			OkHttpClient client = getUnsafeOkHttpClient()
					.addInterceptor(interceptor)
					//.cookieJar(new MyCookieJar())
					.build();

			Retrofit retrofit = new Retrofit.Builder()
					.baseUrl(APIURL)
					.addConverterFactory(GsonConverterFactory.create())
					.client(client)
					.build();
			uniPadApiService = retrofit.create(UniPadApiService.class);
		}

		return uniPadApiService;
	}

	public interface UniPadApiService {

		// ============================================================================================= /makeUrl

		@GET("/makeUrl")
		Call<List<UnishareVO>> makeUrl_list();

		@POST("/makeUrl")
		Call<UnishareVO> makeUrl_make(@Body UnishareVO item);

		@GET("/makeUrl/{code}")
		Call<UnishareVO> makeUrl_get(@Path("code") String code);

		@GET("/makeUrl/{code}/addCount")
		Call<ResponseBody> makeUrl_addCount(@Path("code") String code);
	}
}
