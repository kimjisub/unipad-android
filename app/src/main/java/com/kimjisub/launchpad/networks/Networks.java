package com.kimjisub.launchpad.networks;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Networks {

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

	public static class FirebaseManager {
		FirebaseDatabase database;
		DatabaseReference myRef;

		private ChildEventListener childEventListener = null;
		private ValueEventListener valueEventListener = null;

		public FirebaseManager(String key) {
			database = FirebaseDatabase.getInstance();
			myRef = database.getReference(key);
		}

		public FirebaseManager setEventListener(ChildEventListener childEventListener) {
			this.childEventListener = childEventListener;
			return this;
		}

		public FirebaseManager setEventListener(ValueEventListener valueEventListener) {
			this.valueEventListener = valueEventListener;
			return this;
		}

		public FirebaseManager attachEventListener(boolean bool) {
			if (childEventListener != null)
				if (bool)
					myRef.addChildEventListener(childEventListener);
				else
					myRef.removeEventListener(childEventListener);

			if (valueEventListener != null)
				if (bool)
					myRef.addValueEventListener(valueEventListener);
				else
					myRef.removeEventListener(valueEventListener);
			return this;
		}
	}
}
