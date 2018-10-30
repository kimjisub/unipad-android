package com.kimjisub.launchpad.manage;

import android.app.Activity;
import android.content.Context;

import com.anjlab.android.iab.v3.BillingProcessor;

import static com.kimjisub.launchpad.manage.Constant.DEVELOPERPAYLOAD;

public class BillingCertification {
	
	Activity activity;
	BillingProcessor billingProcessor;
	BillingProcessor.IBillingHandler billingHandler;
	
	public BillingCertification(Activity activity, BillingProcessor.IBillingHandler billingHandler) {
		this.activity = activity;
		this.billingHandler = billingHandler;
		
		initialize();
	}
	
	// =========================================================================================
	
	static String PREMIUM = "premium";
	static String PRO = "pro";
	
	static boolean isUpdated = false;
	static boolean isPremium = false;
	static boolean isPro = false;
	
	public static boolean isIsUpdated() {
		return isUpdated;
	}
	
	static public boolean isPremium() {
		return isPremium;
	}
	
	static public boolean isPro() {
		return isPro;
	}
	
	// =========================================================================================
	
	static public boolean isShowAds() {
		return !isPremium();
	}
	
	static public boolean isUnlockProTools() {
		return isPro();
	}
	
	// =========================================================================================
	
	public void initialize() {
		billingProcessor = new BillingProcessor(activity, DEVELOPERPAYLOAD, billingHandler);
		billingProcessor.initialize();
		
		isPremium = billingProcessor.subscribe(activity, PREMIUM);
		isPro = billingProcessor.subscribe(activity, PRO);
		
		isUpdated = true;
	}
	
	public void release() {
		if (billingProcessor != null && billingProcessor.isInitialized())
			billingProcessor.release();
	}
	
	
	// =========================================================================================
	
	public boolean isAvailable(Context context) {
		return BillingProcessor.isIabServiceAvailable(context);
	}
	
	public boolean isOneTimePurchaseSupported() {
		return billingProcessor.isOneTimePurchaseSupported();
	}
	
	public boolean isSubscriptionUpdateSupported() {
		return billingProcessor.isSubscriptionUpdateSupported();
	}
	
	// =========================================================================================
	
	public void restorePurchases() {
		billingProcessor.loadOwnedPurchasesFromGoogle();
	}
	
	// =========================================================================================
	
	public void purchasePremium() {
		billingProcessor.consumePurchase(PREMIUM);
		billingProcessor.consumePurchase(PRO);
	}
	
}
