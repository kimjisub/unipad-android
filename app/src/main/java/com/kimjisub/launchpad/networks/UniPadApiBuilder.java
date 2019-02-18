package com.kimjisub.launchpad.networks;

import com.kimjisub.launchpad.utils.Log;

import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class UniPadApiBuilder {

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
}
