package com.kimjisub.launchpad;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import static com.kimjisub.launchpad.manage.Tools.logFirebase;

public class MyFirebaseInstanceIDService extends FirebaseInstanceIdService {
	@Override
	public void onTokenRefresh() {
		String refreshedToken = FirebaseInstanceId.getInstance().getToken();
		logFirebase("Refreshed token: " + refreshedToken);
		sendRegistrationToServer(refreshedToken);
	}
	
	private void sendRegistrationToServer(String token) {
	}
}