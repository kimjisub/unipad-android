package com.kimjisub.launchpad.manage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class OptionManager {
	static class BoolManager {
		//================================================================================== vars
		private boolean bool;
		private OnValueChangeListener onValueChangeListener;
		private ArrayList<OnValueChangeListener> onValueChangeListeners = new ArrayList();
		
		
		//================================================================================== constructor
		public BoolManager(boolean bool) {
			this.bool = bool;
		}
		
		public BoolManager(boolean bool, OnValueChangeListener listener) {
			this.bool = bool;
			onValueChangeListener = listener;
		}
		
		//================================================================================== listener
		interface OnValueChangeListener {
			void onValueChange(boolean bool);
		}
		
		void addOnValueChangeListener(OnValueChangeListener listener) {
			onValueChangeListeners.add(listener);
		}
		
		void clearOnValueChangeListener(){
			onValueChangeListeners.clear();
		}
		
		//
		
		void onValueChange(boolean bool) {
			if(onValueChangeListener != null)
				onValueChangeListener.onValueChange(bool);
			for (OnValueChangeListener listener : onValueChangeListeners){
				listener.onValueChange(bool);
			}
		}
		
		//================================================================================== func
		void set(boolean bool) {
			this.bool = bool;
			onValueChange(bool);
		}
		
		boolean get() {
			return bool;
		}
	}
	
	private Map<String, BoolManager> boolManagers = new HashMap<>();
	
	void add(String key, BoolManager.OnValueChangeListener listener) {
		boolManagers.put(key, new BoolManager(false, listener));
	}
	
	void addOnValueChangeListener(String key, BoolManager.OnValueChangeListener listener){
		boolManagers.get(key).addOnValueChangeListener(listener);
	}
	
	void set(String key, boolean bool) {
		boolManagers.get(key).set(bool);
	}
	void toggle(String key) {
		BoolManager boolManager = boolManagers.get(key);
		boolManager.set(!boolManager.get());
	}
	
	boolean get(String key) {
		return boolManagers.get(key).get();
	}
	
}
