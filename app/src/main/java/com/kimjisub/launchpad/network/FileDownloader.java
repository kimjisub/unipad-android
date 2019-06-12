package com.kimjisub.launchpad.network;

import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;

public class FileDownloader extends AsyncTask<String, String, String> {
	String url;
	File downloadFile;
	FileDownloadCallback fileDownloadCallback;

	int fileSize = 0;
	long downloadSize = 0;


	public interface FileDownloadCallback {
		void onDownloadThreadStart();

		void onGetFileSize(long fileSize);

		void onDownloadStart();

		void onDownloading(long downloadedSize);

		void onDownloadEnd();

		void onException(Exception e);
	}

	public FileDownloader() {
	}

	public FileDownloader(String url, File downloadFile, FileDownloadCallback fileDownloadCallback) {
		init(url, downloadFile, fileDownloadCallback);
	}

	public void init(String url, File downloadFile, FileDownloadCallback fileDownloadCallback) {
		this.url = url;
		this.downloadFile = downloadFile;
		this.fileDownloadCallback = fileDownloadCallback;
	}


	@Override
	protected void onPreExecute() {
		super.onPreExecute();
	}

	@Override
	protected String doInBackground(String[] params) {
		fileDownloadCallback.onDownloadThreadStart();

		try {
			URL downloadUrl = new URL(url);
			HttpsURLConnection.setFollowRedirects(true);
			HttpsURLConnection connection = (HttpsURLConnection) downloadUrl.openConnection();
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			connection.setInstanceFollowRedirects(true);

			fileSize = connection.getContentLength();
			fileDownloadCallback.onGetFileSize(fileSize);

			InputStream input = new BufferedInputStream(downloadUrl.openStream());
			OutputStream output = new FileOutputStream(downloadFile);

			byte data[] = new byte[1024];
			int count;
			int progress = 0;
			fileDownloadCallback.onDownloadStart();
			while ((count = input.read(data)) != -1) {
				downloadSize += count;
				progress++;
				if (progress % 100 == 0)
					fileDownloadCallback.onDownloading(downloadSize);
				output.write(data, 0, count);
			}
			fileDownloadCallback.onDownloading(downloadSize);

			output.flush();
			output.close();
			input.close();

			fileDownloadCallback.onDownloadEnd();
		} catch (Exception e) {
			e.printStackTrace();
			fileDownloadCallback.onException(e);
		}


		return null;
	}

	@Override
	protected void onProgressUpdate(String... progress) {
	}

	@Override
	protected void onPostExecute(String unused) {
	}
}