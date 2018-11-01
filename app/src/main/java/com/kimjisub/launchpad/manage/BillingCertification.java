package com.kimjisub.launchpad.manage;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.TransactionDetails;

import static com.kimjisub.launchpad.manage.Constant.BILLING.DEVELOPERPAYLOAD;
import static com.kimjisub.launchpad.manage.Constant.BILLING.REMOVE_ADS;
import static com.kimjisub.launchpad.manage.Constant.BILLING.PRO_TOOLS;

public class BillingCertification {
	
	Activity activity;
	BillingProcessor billingProcessor;
	
	public BillingCertification(Activity activity) {
		this.activity = activity;
		
		initialize();
	}
	
	public BillingCertification(Activity activity, BillingEventListener billingEventListener) {
		this.activity = activity;
		this.billingEventListener = billingEventListener;
		
		initialize();
	}
	
	// =========================================================================================
	
	static boolean isRemoveAds = false;
	static boolean isProTools = false;
	
	public static boolean isPurchaseRemoveAds() {
		return isRemoveAds;
	}
	
	public static boolean isPurchaseProTools() {
		return isProTools;
	}
	
	public static boolean isShowAds() {
		return !isPurchaseRemoveAds();
	}
	
	public static boolean isUnlockProTools() {
		return isPurchaseProTools();
	}
	
	// =========================================================================================
	
	BillingEventListener billingEventListener;
	
	public interface BillingEventListener {
		void onProductPurchased(@NonNull String productId, @Nullable TransactionDetails details);
		
		void onPurchaseHistoryRestored();
		
		void onBillingError(int errorCode, @Nullable Throwable error);
		
		void onBillingInitialized();
		
		void onRefresh();
	}
	
	// =========================================================================================
	
	public void initialize() {
		billingProcessor = new BillingProcessor(activity, DEVELOPERPAYLOAD, new BillingProcessor.IBillingHandler() {
			@Override
			public void onProductPurchased(@NonNull String productId, @Nullable TransactionDetails details) {
				refresh();
				if (billingEventListener != null)
					billingEventListener.onProductPurchased(productId, details);
			}
			
			@Override
			public void onPurchaseHistoryRestored() {
				if (billingEventListener != null)
					billingEventListener.onPurchaseHistoryRestored();
			}
			
			@Override
			public void onBillingError(int errorCode, @Nullable Throwable error) {
				if (billingEventListener != null)
					billingEventListener.onBillingError(errorCode, error);
			}
			
			@Override
			public void onBillingInitialized() {
				refresh();
				if (billingEventListener != null)
					billingEventListener.onBillingInitialized();
			}
		});
		billingProcessor.initialize();
	}
	
	public void refresh() {
		if (billingProcessor != null && billingProcessor.isInitialized()) {
			billingProcessor.loadOwnedPurchasesFromGoogle();
			
			isRemoveAds = billingProcessor.isSubscribed(REMOVE_ADS);
			isProTools = billingProcessor.isSubscribed(PRO_TOOLS);
			billingEventListener.onRefresh();
		}
	}
	
	public void release() {
		if (billingProcessor != null && billingProcessor.isInitialized())
			billingProcessor.release();
	}
	
	
	// =========================================================================================
	
	public boolean isAvailable() {
		return BillingProcessor.isIabServiceAvailable(activity);
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
	
	public void purchase_removeAds() {
		billingProcessor.subscribe(activity, REMOVE_ADS);
	}
	
	public void purchase_proTools() {
		billingProcessor.subscribe(activity, PRO_TOOLS);
	}
	
}
