package com.kimjisub.launchpad.manage;

import android.bluetooth.BluetoothClass;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.kimjisub.launchpad.fb.fbStore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public class Networks {
	
	static final String APIURL = "http://ec2-18-223-111-184.us-east-2.compute.amazonaws.com";
	static PhotoframeApi photoframeApi;
	
	
	public interface PhotoframeApi {
		
		// ================================================================================= /auth
		
		/*@POST("/auth/login")
		Call<Res> auth_login(@Body LoginUser loginUser);
		
		@POST("/auth/logout")
		Call<Res> auth_logout();
		
		@POST("/auth/signup")
		Call<Res> auth_signup(@Body RegisterUser registerUser);
		
		// ================================================================================= /device
		
		@POST("/device/register")
		Call<Res> device_register(@Body BluetoothClass.Device registerUser);
		
		@PATCH("/device/{id}")
		Call<Res> device_patch(@Path("id") int id, @Body Device registerUser);
		
		@DELETE("/device/{id}")
		Call<Res> device_delete(@Path("id") int id);
		
		@GET("/device/")
		Call<List<Device>> device_list();
		
		@GET("/device/{id}")
		Call<Device> device_getByID(@Path("id") int id);
		
		@GET("/device/{id}/imagepreview?")
		Call<ResponseBody> device_imagepreview(@Path("id") int id, @Query("size") int size);
		
		@PATCH("/device/{id}/image/{image_id}")
		Call<Res> device_setImage(@Path("id") int id, @Path("image_id") int image_id);
		
		// ================================================================================= /image_list
		
		@GET("/image")
		Call<List<Integer>> image_list();
		
		@GET("/image/{id}")
		Call<ResponseBody> image_getByID(@Path("id") int id);
		
		@GET("/image/{id}?")
		Call<ResponseBody> image_getByID(@Path("id") int id, @Query("size") int size);*/
		
	}
	
	public static PhotoframeApi getPhotoframeApi() {
		if (photoframeApi == null) {
			HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(message -> Log.network(message))
				.setLevel(HttpLoggingInterceptor.Level.BODY);
			OkHttpClient client = new OkHttpClient.Builder()
				.addInterceptor(interceptor)
				//.cookieJar(new MyCookieJar())
				.build();
			
			Retrofit retrofit = new Retrofit.Builder()
				.baseUrl(APIURL)
				.addConverterFactory(GsonConverterFactory.create())
				.client(client)
				.build();
			photoframeApi = retrofit.create(PhotoframeApi.class);
		}
		
		return photoframeApi;
	}
	
	public static class CheckVersion {
		FirebaseDatabase database;
		DatabaseReference myRef;
		
		private onChangeListener dataListener = null;
		
		public interface onChangeListener {
			void onChange(String version);
		}
		
		
		public CheckVersion setOnChangeListener(onChangeListener listener) {
			this.dataListener = listener;
			return this;
		}
		
		void onChange(String version) {
			if (dataListener != null)
				dataListener.onChange(version);
		}
		
		
		public void run() {
			database = FirebaseDatabase.getInstance();
			myRef = database.getReference("appVersion");
			
			myRef.addValueEventListener(new ValueEventListener() {
				@Override
				public void onDataChange(DataSnapshot dataSnapshot) {
					onChange(dataSnapshot.getValue(String.class));
				}
				
				@Override
				public void onCancelled(DatabaseError databaseError) {
				
				}
			});
			
		}
	}
	
	public static class GetStoreList {
		FirebaseDatabase database;
		DatabaseReference myRef;
		
		private onDataListener dataListener = null;
		
		public interface onDataListener {
			void onAdd(fbStore data);
			
			void onChange(fbStore data);
		}
		
		
		public GetStoreList setDataListener(onDataListener listener) {
			this.dataListener = listener;
			return this;
		}
		
		void onAdd(fbStore data) {
			if (dataListener != null)
				dataListener.onAdd(data);
		}
		
		void onChange(fbStore data) {
			if (dataListener != null)
				dataListener.onChange(data);
		}
		
		
		public void run() {
			database = FirebaseDatabase.getInstance();
			myRef = database.getReference("store");
			
			myRef.addChildEventListener(new ChildEventListener() {
				@Override
				public void onChildAdded(DataSnapshot dataSnapshot, String s) {
					try {
						fbStore data = dataSnapshot.getValue(fbStore.class);
						onAdd(data);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				@Override
				public void onChildChanged(DataSnapshot dataSnapshot, String s) {
					try {
						fbStore data = dataSnapshot.getValue(fbStore.class);
						onChange(data);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				@Override
				public void onChildRemoved(DataSnapshot dataSnapshot) {
				
				}
				
				@Override
				public void onChildMoved(DataSnapshot dataSnapshot, String s) {
				
				}
				
				@Override
				public void onCancelled(DatabaseError databaseError) {
				
				}
			});
		}
		
	}
	
	public static class GetStoreCount {
		FirebaseDatabase database;
		DatabaseReference myRef;
		
		private onChangeListener dataListener = null;
		
		public interface onChangeListener {
			void onChange(long count);
		}
		
		
		public GetStoreCount setOnChangeListener(onChangeListener listener) {
			this.dataListener = listener;
			return this;
		}
		
		void onChange(long count) {
			if (dataListener != null)
				dataListener.onChange(count);
		}
		
		
		public void run() {
			database = FirebaseDatabase.getInstance();
			myRef = database.getReference("storeCount");
			
			myRef.addValueEventListener(new ValueEventListener() {
				@Override
				public void onDataChange(DataSnapshot dataSnapshot) {
					onChange(dataSnapshot.getValue(Long.class));
				}
				
				@Override
				public void onCancelled(DatabaseError databaseError) {
				
				}
			});
			
		}
	}
	
	public static String sendGet(String str) {
		
		StringBuilder html = new StringBuilder();
		try {
			URL url = new URL(str);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			if (conn != null) {
				conn.setConnectTimeout(10000);
				conn.setUseCaches(false);
				if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
					BufferedReader br = new BufferedReader(
						new InputStreamReader(conn.getInputStream()));
					for (; ; ) {
						String line = br.readLine();
						if (line == null) break;
						html.append(line);
						html.append('\n');
					}
					br.close();
				}
				conn.disconnect();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return html.toString();
	}
}
