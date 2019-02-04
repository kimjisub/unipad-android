package com.kimjisub.launchpad.manage;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.kimjisub.launchpad.fb.fbStore;
import com.kimjisub.launchpad.manage.network.MakeUrl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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

public class Networks {

	static final String APIURL = "https://api.unipad.kr";
	static UniPadApi uniPadApi;

	public static UniPadApi getUniPadApi() {
		if (uniPadApi == null) {
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
			uniPadApi = retrofit.create(UniPadApi.class);
		}

		return uniPadApi;
	}

	private static OkHttpClient.Builder getUnsafeOkHttpClient() {
		try {
			// Create a trust manager that does not validate certificate chains
			final TrustManager[] trustAllCerts = new TrustManager[]{
					new X509TrustManager() {
						@Override
						public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
						}

						@Override
						public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
						}

						@Override
						public java.security.cert.X509Certificate[] getAcceptedIssuers() {
							return new java.security.cert.X509Certificate[]{};
						}
					}
			};

			final SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

			OkHttpClient.Builder builder = new OkHttpClient.Builder()
					.sslSocketFactory(sslSocketFactory)
					.hostnameVerifier((hostname, session) -> true);
			return builder;
		} catch (Exception e) {
			throw new RuntimeException(e);
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

	public interface UniPadApi {

		// ============================================================================================= /makeUrl

		@GET("/makeUrl")
		Call<List<MakeUrl>> makeUrl_list();

		@POST("/makeUrl")
		Call<MakeUrl> makeUrl_make(@Body MakeUrl item);

		@GET("/makeUrl/{code}")
		Call<MakeUrl> makeUrl_get(@Path("code") String code);

		@GET("/makeUrl/{code}/addCount")
		Call<ResponseBody> makeUrl_addCount(@Path("code") String code);

	}

	public static class CheckVersion {
		FirebaseDatabase database;
		DatabaseReference myRef;

		private onChangeListener dataListener = null;

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


		public interface onChangeListener {
			void onChange(String version);
		}
	}

	public static class GetStoreList {
		FirebaseDatabase database;
		DatabaseReference myRef;

		private onDataListener dataListener = null;

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


		public interface onDataListener {
			void onAdd(fbStore data);

			void onChange(fbStore data);
		}

	}

	public static class GetStoreCount {
		FirebaseDatabase database;
		DatabaseReference myRef;

		private onChangeListener dataListener = null;

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


		public interface onChangeListener {
			void onChange(long count);
		}
	}
}
