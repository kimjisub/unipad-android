package com.kimjisub.launchpad.networks;

import com.kimjisub.launchpad.networks.dto.MakeUrlDTO;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface UniPadApiService {

	// ============================================================================================= /makeUrl

	@GET("/makeUrl")
	Call<List<MakeUrlDTO>> makeUrl_list();

	@POST("/makeUrl")
	Call<MakeUrlDTO> makeUrl_make(@Body MakeUrlDTO item);

	@GET("/makeUrl/{code}")
	Call<MakeUrlDTO> makeUrl_get(@Path("code") String code);

	@GET("/makeUrl/{code}/addCount")
	Call<ResponseBody> makeUrl_addCount(@Path("code") String code);
}
