package com.kimjisub.launchpad;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.kimjisub.launchpad.utils.Log;

public class MyFirebaseInstanceIDService extends FirebaseInstanceIdService {
	@Override
	public void onTokenRefresh() {
		String refreshedToken = FirebaseInstanceId.getInstance().getToken();
		Log.firebase("Refreshed token: " + refreshedToken);
		sendRegistrationToServer(refreshedToken);
	}

	private void sendRegistrationToServer(String token) {
	}
}