package com.kimjisub.launchpad.api.unipad;

import com.kimjisub.launchpad.api.BaseApiService;
import com.kimjisub.launchpad.api.unipad.vo.UnishareVO;
import com.kimjisub.manager.Log;

import java.util.List;

import okhttp3.OkHttpClient;
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
					.addConverterFactory(GsonConverterFactory.create(getGson()))
					.client(client)
					.build();
			uniPadApiService = retrofit.create(UniPadApiService.class);
		}

		return uniPadApiService;
	}

	public interface UniPadApiService {

		// ============================================================================================= /unishare

		@GET("/unishare")
		Call<List<UnishareVO>> unishare_list();

		@POST("/unishare")
		Call<UnishareVO> unishare_make(@Body UnishareVO item);

		@GET("/unishare/{code}")
		Call<UnishareVO> unishare_get(@Path("code") String code);
	}
}
