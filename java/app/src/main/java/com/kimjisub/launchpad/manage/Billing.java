package com.kimjisub.launchpad.manage;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import static com.kimjisub.launchpad.manage.Constant.DEVELOPERPAYLOAD;

public class Billing {
	
	public static boolean isPremium;
	
	Activity activity;
	
	IInAppBillingService mService;
	ServiceConnection mServiceConn = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			mService = null;
			Billing.this.onServiceDisconnected();
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mService = IInAppBillingService.Stub.asInterface(service);
			getAll();
			Billing.this.onServiceConnected();
		}
	};
	
	
	public Billing(Activity c) {
		this.activity = c;
	}
	
	public Billing start() {
		try {
			Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
			serviceIntent.setPackage("com.android.vending");
			activity.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
		} catch (Exception e) {
		}
		
		return this;
	}
	
	public Billing onDestroy() {
		if (mService != null)
			activity.unbindService(mServiceConn);
		
		return this;
	}
	
	void getAll() {
		getPremium();
	}
	
	boolean getPremium() {
		
		try {
			Bundle ownedItems = mService.getPurchases(3, activity.getPackageName(), "subs", null);
			int response = ownedItems.getInt("RESPONSE_CODE");
			if (response == 0) {
				ArrayList<String> ownedSkus = ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
				ArrayList<String> purchaseDataList = ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
				ArrayList<String> signatureList = ownedItems.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
				String continuationToken = ownedItems.getString("INAPP_CONTINUATION_TOKEN");
				
				for (int i = 0; i < purchaseDataList.size(); ++i) {
					String purchaseData = purchaseDataList.get(i);
					String signature = signatureList.get(i);
					String sku = ownedSkus.get(i);
					
					try {
						JSONObject jo = new JSONObject(purchaseData);
						return isPremium = jo.getBoolean("autoRenewing");
					} catch (JSONException e) {
						e.printStackTrace();
						return false;
					}
				}
				return false;
			}
		} catch (RemoteException | NullPointerException e) {
			e.printStackTrace();
			return false;
		}
		return false;
	}
	
	public void buyPremium() {
		try {
			Bundle bundle = mService.getBuyIntent(3, activity.getPackageName(), "premium", "subs", DEVELOPERPAYLOAD);
			PendingIntent pendingIntent = bundle.getParcelable("BUY_INTENT");
			
			//if (bundle.getInt("RESPONSE_CODE") == BILLING_RESPONSE_RESULT_OK)
			activity.startIntentSenderForResult(pendingIntent.getIntentSender(), 1001, new Intent(), 0, 0, 0);
			
		} catch (Exception e) {
			Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
			e.printStackTrace();
		}
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 1001) {
			int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
			String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
			String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");
			
			if (resultCode == activity.RESULT_OK) {
				try {
					JSONObject jo = new JSONObject(purchaseData);
					isPremium = jo.getBoolean("autoRenewing");
					Billing.this.onPurchaseDone(jo);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private OnEventListener onEventListener = null;
	
	public interface OnEventListener {
		
		void onServiceDisconnected(Billing v);
		
		void onServiceConnected(Billing v);
		
		void onPurchaseDone(Billing v, JSONObject jo);
	}
	
	public Billing setOnEventListener(OnEventListener listener) {
		this.onEventListener = listener;
		return this;
	}
	
	void onServiceDisconnected() {
		if (onEventListener != null) onEventListener.onServiceDisconnected(this);
	}
	
	void onServiceConnected() {
		if (onEventListener != null) onEventListener.onServiceConnected(this);
	}
	
	void onPurchaseDone(JSONObject jo) {
		if (onEventListener != null) onEventListener.onPurchaseDone(this, jo);
	}
}