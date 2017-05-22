package com.kimjisub.launchpad;

import android.os.AsyncTask;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by rlawl on 2016-02-17.
 * ReCreated by rlawl on 2016-04-23.
 */
public class 통신 {
	static class 업로드된버전 extends AsyncTask<String, String, Boolean> {
		String[] 정보;
		String 결과 = null;
		
		public 업로드된버전(String... 정보) {
			this.정보 = 정보;
		}
		
		private onEndListener listener = null;
		
		interface onEndListener {
			void onEnd(String 결과);
		}
		
		public 업로드된버전 setOnEndListener(onEndListener listener) {
			this.listener = listener;
			return this;
		}
		
		public void onEnd(String 결과) {
			if (listener != null) listener.onEnd(결과);
		}
		
		public void 실행() {
			this.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
		
		@Override
		protected Boolean doInBackground(String... params) {
			
			String mData = "";
			
			try {
				URL mUrl = new URL("https://play.google.com/DStore/apps/details?id=" + 정보[0]);
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
			
			if (index == -1) {
				결과 = null;
				
			} else {
				결과 = mData.substring(index + startToken.length(), index
					+ startToken.length() + 100);
				결과 = 결과.substring(0, 결과.indexOf(endToken)).trim();
			}
			
			return false;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			onEnd(결과);
		}
	}
	
	static class 공지사항 {
		
		FirebaseDatabase database;
		DatabaseReference myRef;
		
		String languageParam = "";
		
		
		public 공지사항(String languageParam) {
			this.languageParam = languageParam;
		}
		
		public void execute() {
			database = FirebaseDatabase.getInstance();
			myRef = database.getReference("notice");
			
			myRef.addChildEventListener(new ChildEventListener() {
				@Override
				public void onChildAdded(DataSnapshot dataSnapshot, String s) {
					try {
						
						화면.log(dataSnapshot.toString());
						DNotice data = dataSnapshot.getValue(DNotice.class);
						
						if (dataSnapshot.getKey().equals(languageParam))
							onEnd(data.title, data.content);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				@Override
				public void onChildChanged(DataSnapshot dataSnapshot, String s) {
					
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
		
		interface onEndListener {
			void onEnd(String 제목, String 글);
		}
		
		private onEndListener 리스너 = null;
		
		public 공지사항 setOnEndListener(onEndListener listener) {
			this.리스너 = listener;
			return this;
		}
		
		public void onEnd(String 제목, String 글) {
			if (리스너 != null)
				리스너.onEnd(제목, 글);
		}
		
	}
	
	static class 목록쓰레드 {
		
		FirebaseDatabase database;
		DatabaseReference myRef;
		
		public 목록쓰레드() {
		}
		
		public void execute() {
			database = FirebaseDatabase.getInstance();
			myRef = database.getReference("store");
			
			myRef.addChildEventListener(new ChildEventListener() {
				@Override
				public void onChildAdded(DataSnapshot dataSnapshot, String s) {
					try {
						DStore data = dataSnapshot.getValue(DStore.class);
						onAdd(data);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				@Override
				public void onChildChanged(DataSnapshot dataSnapshot, String s) {
					try {
						DStore data = dataSnapshot.getValue(DStore.class);
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
		
		interface onAddListener {
			void onAdd(DStore data);
		}
		
		private onAddListener addListener = null;
		
		public 목록쓰레드 setOnAddListener(onAddListener listener) {
			this.addListener = listener;
			return this;
		}
		
		public void onAdd(DStore data) {
			if (addListener != null)
				addListener.onAdd(data);
		}
		
		
		interface onChangeListener {
			void onChange(DStore data);
		}
		
		private onChangeListener changeListener = null;
		
		public 목록쓰레드 setOnChangeListener(onChangeListener listener) {
			this.changeListener = listener;
			return this;
		}
		
		public void onChange(DStore data) {
			if (changeListener != null)
				changeListener.onChange(data);
		}
		
	}
	
	static String sendGet(String addr) {
		
		StringBuilder html = new StringBuilder();
		try {
			URL url = new URL(addr);
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
		} catch (Exception ex) {
			;
		}
		
		return html.toString();
	}
}
