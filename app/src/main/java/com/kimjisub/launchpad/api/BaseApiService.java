package com.kimjisub.launchpad.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.security.cert.CertificateException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

public class BaseApiService {
	protected static OkHttpClient.Builder getUnsafeOkHttpClient() {
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

	protected static Gson getGson() {
		final GsonBuilder builder = new GsonBuilder();

		builder.registerTypeAdapter(Date.class, new JsonDeserializer<Date>() {

			final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

			@Override
			public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				try {
					return df.parse(json.getAsString());
				} catch (final java.text.ParseException e) {
					e.printStackTrace();
					return null;
				}
			}
		});

		/*builder.registerTypeAdapter(DateTime.class, new JsonDeserializer<DateTime>() {

			final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			@Override
			public DateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				try {
					return new DateTime(df.parse(json.getAsString()));
				} catch (final java.text.ParseException e) {
					e.printStackTrace();
					return null;
				}
			}
		});*/

		return builder.create();
	}
}
