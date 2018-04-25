package com.kimjisub.launchpad.manage;

import android.os.AsyncTask;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.kimjisub.launchpad.fb.fbNotice;
import com.kimjisub.launchpad.fb.fbStore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Networks {
	public static class CheckVersion extends AsyncTask<String, String, Boolean> {
		String packageName;
		String version = null;

		public CheckVersion(String packageName) {
			this.packageName = packageName;
		}

		private onEndListener listener = null;

		public interface onEndListener {
			void onEnd(String verson);
		}

		public CheckVersion setOnEndListener(onEndListener listener) {
			this.listener = listener;
			return this;
		}

		void onEnd(String version) {
			if (listener != null) listener.onEnd(version);
		}

		public void run() {
			this.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}

		@Override
		protected Boolean doInBackground(String... params) {

			String mData = "";

			try {
				URL mUrl = new URL("https://play.google.com/store/apps/details?id=" + packageName);
				HttpURLConnection mConnection = (HttpURLConnection) mUrl
					.openConnection();

				if (mConnection == null)
					return null;

				mConnection.setConnectTimeout(5000);
				mConnection.setUseCaches(false);
				mConnection.setDoOutput(true);

				if (mConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
					BufferedReader mReader = new BufferedReader(
						new InputStreamReader(mConnection.getInputStream()));

					while (true) {
						String line = mReader.readLine();
						if (line == null)
							break;
						mData += line;
					}

					mReader.close();
				}

				mConnection.disconnect();

			} catch (Exception ex) {
				ex.printStackTrace();
				return null;
			}

			String startToken = "softwareVersion\">";
			String endToken = "<";
			int index = mData.indexOf(startToken);

			if (index == -1)
				version = null;
			else {
				version = mData.substring(index + startToken.length(), index
					+ startToken.length() + 100);
				version = version.substring(0, version.indexOf(endToken)).trim();
			}

			return false;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			onEnd(version);
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
			void onChange(long data);
		}


		public GetStoreCount setOnChangeListener(onChangeListener listener) {
			this.dataListener = listener;
			return this;
		}

		void onChange(long data) {
			if (dataListener != null)
				dataListener.onChange(data);
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
