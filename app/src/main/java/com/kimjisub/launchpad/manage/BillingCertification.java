package com.kimjisub.launchpad.manage;

public class BillingCertification {
	
	//static boolean isPremium;
	static boolean isPro = true;
	
	/*static public boolean isPremium(){
		return isPremium;
	}*/
	
	static public boolean isPro() {
		return isPro;
	}
	
	// =========================================================================================
	
	static public boolean isShowAds() {
		return !isPro();
	}
	
	static public boolean isUnlockProTools() {
		return isPro();
	}
	
}
