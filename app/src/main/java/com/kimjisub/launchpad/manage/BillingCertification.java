package com.kimjisub.launchpad.manage;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.TransactionDetails;

import static com.kimjisub.launchpad.manage.Constant.BILLING.*;

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
	
	static boolean isPremium = false;
	static boolean isPro = false;
	
	public static boolean isPremium() {
		return isPremium;
	}
	
	public static boolean isPro() {
		return isPro;
	}
	
	public static boolean isShowAds() {
		return !isPremium();
	}
	
	public static boolean isUnlockProTools() {
		return isPro();
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
				refresh();
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
		isPremium = billingProcessor.isSubscribed(PREMIUM);
		isPro = billingProcessor.isSubscribed(PRO);
		
		Toast.makeText(activity, "premium : " + isPremium + "\npro : " + isPro, Toast.LENGTH_SHORT).show();
		
		if (billingEventListener != null)
			billingEventListener.onRefresh();
	}
	
	public void release() {
		if (billingProcessor != null && billingProcessor.isInitialized())
			billingProcessor.release();
	}
	
	public void loadOwnedPurchasesFromGoogle() {
		if (billingProcessor != null && billingProcessor.isInitialized())
			billingProcessor.loadOwnedPurchasesFromGoogle();
		refresh();
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
	
	public void purchasePremium() {
		billingProcessor.subscribe(activity, PREMIUM);
	}
	
	public void purchasePro() {
		billingProcessor.subscribe(activity, PRO);
	}
	
}
