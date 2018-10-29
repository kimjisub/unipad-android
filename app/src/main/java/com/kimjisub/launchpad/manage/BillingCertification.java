package com.kimjisub.launchpad.manage;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.TransactionDetails;

import static com.kimjisub.launchpad.manage.Constant.DEVELOPERPAYLOAD;

public class BillingCertification {
	
	public static BillingProcessor billingProcessor;
	
	// =========================================================================================
	
	//static boolean isPremium;
	static boolean isPro = false;
	
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
	
	
	// =========================================================================================
	
	static void checkBilling(Context context) {
		billingProcessor = new BillingProcessor(context, DEVELOPERPAYLOAD, billingHandler);
		billingProcessor.initialize();
	}
	
	static BillingProcessor.IBillingHandler billingHandler = new BillingProcessor.IBillingHandler() {
		@Override
		public void onProductPurchased(@NonNull String productId, @Nullable TransactionDetails details) {
		
		}
		
		@Override
		public void onPurchaseHistoryRestored() {
		
		}
		
		@Override
		public void onBillingError(int errorCode, @Nullable Throwable error) {
		
		}
		
		@Override
		public void onBillingInitialized() {
		
		}
	};
	
}
