package com.kimjisub.launchpad.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SyncView {
	private Map<String, ArrayList> onAttrChangeListeners = new HashMap<>();
	
	public interface OnAttrChangeListener {
		void onAttrChange(boolean b);
	}
	
	public void setOnAttrChange(String attr, OnAttrChangeListener listener) {
		addOnAttrChange(attr, listener);
	}
	
	public void addOnAttrChange(String attr, OnAttrChangeListener listener) {
		if (onAttrChangeListeners.containsKey(attr)) {
			ArrayList listeners = onAttrChangeListeners.get(attr);
			listeners.add(listener);
		} else {
			ArrayList listeners = new ArrayList();
			listeners.add(listener);
			onAttrChangeListeners.put(attr, listeners);
		}
	}
	
	public void attrChange(String attr, boolean b) {
		if (onAttrChangeListeners.containsKey(attr)) {
			for (OnAttrChangeListener listener : ((ArrayList<OnAttrChangeListener>) onAttrChangeListeners.get(attr)))
				listener.onAttrChange(b);
		}
	}
}
