package com.kimjisub.launchpad.manager;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.TransactionDetails;

import static com.kimjisub.launchpad.manager.Constant.*;

public class BillingManager {
	Activity activity;
	BillingProcessor billingProcessor;

	boolean refreshDone = false;
	boolean isRemoveAds = false;
	boolean isProTools = false;

	// ============================================================================================= Constructor

	public BillingManager(Activity activity) {
		this.activity = activity;

		billingProcessor = new BillingProcessor(activity, BILLING.DEVELOPERPAYLOAD, new BillingProcessor.IBillingHandler() {
			@Override
			public void onProductPurchased(@NonNull String productId, @Nullable TransactionDetails details) {
				refresh();
				_onProductPurchased(productId, details);
			}

			@Override
			public void onPurchaseHistoryRestored() {
				_onPurchaseHistoryRestored();
			}

			@Override
			public void onBillingError(int errorCode, @Nullable Throwable error) {
				_onBillingError(errorCode, error);
			}

			@Override
			public void onBillingInitialized() {
				refresh();
				_onBillingInitialized();
			}
		});
		billingProcessor.initialize();
	}

	public BillingManager(Activity activity, BillingEventListener billingEventListener) {
		this(activity);
		this.billingEventListener = billingEventListener;
	}


	// ============================================================================================= Getter

	public boolean isPurchaseRemoveAds() {
		return isRemoveAds;
	}

	public boolean isPurchaseProTools() {
		return isProTools;
	}

	public boolean isShowAds() {
		return isPurchaseRemoveAds();
	}

	public boolean isUnlockProTools() {
		return isPurchaseProTools();
	}

	// ============================================================================================= Cycle

	public void refresh() {
		if (billingProcessor.isInitialized()) {
			billingProcessor.loadOwnedPurchasesFromGoogle();

			isRemoveAds = billingProcessor.isSubscribed(BILLING.REMOVE_ADS);
			isProTools = billingProcessor.isSubscribed(BILLING.PRO_TOOLS);
			refreshDone = true;
			_onRefresh();
		}
	}

	public void release() {
		if (billingProcessor.isInitialized())
			billingProcessor.release();
	}

	public boolean isAvailable() {
		return BillingProcessor.isIabServiceAvailable(activity);
	}

	public boolean isOneTimePurchaseSupported() {
		return billingProcessor.isOneTimePurchaseSupported();
	}

	public boolean isSubscriptionUpdateSupported() {
		return billingProcessor.isSubscriptionUpdateSupported();
	}

	public void restorePurchases() {
		billingProcessor.loadOwnedPurchasesFromGoogle();
	}

	// =============================================================================================

	public void subscribe(String productId) {
		billingProcessor.subscribe(activity, productId);
	}

	public void purchase(String productId) {
		billingProcessor.purchase(activity, productId);
	}

	// =============================================================================================

	public void subscribe_removeAds() {
		subscribe(BILLING.REMOVE_ADS);
	}

	public void subscribe_proTools() {
		subscribe(BILLING.PRO_TOOLS);
	}

	// ============================================================================================= BillingEventListener

	BillingEventListener billingEventListener;

	public interface BillingEventListener {
		void onProductPurchased(@NonNull String productId, @Nullable TransactionDetails details);

		void onPurchaseHistoryRestored();

		void onBillingError(int errorCode, @Nullable Throwable error);

		void onBillingInitialized();

		void onRefresh();
	}

	void _onProductPurchased(@NonNull String productId, @Nullable TransactionDetails details) {
		if (billingEventListener != null)
			billingEventListener.onProductPurchased(productId, details);
	}

	void _onPurchaseHistoryRestored() {
		if (billingEventListener != null)
			billingEventListener.onPurchaseHistoryRestored();
	}

	void _onBillingError(int errorCode, @Nullable Throwable error) {
		if (billingEventListener != null)
			billingEventListener.onBillingError(errorCode, error);
	}

	void _onBillingInitialized() {
		if (billingEventListener != null)
			billingEventListener.onBillingInitialized();
	}

	void _onRefresh() {
		if (billingEventListener != null)
			billingEventListener.onRefresh();
	}

}
