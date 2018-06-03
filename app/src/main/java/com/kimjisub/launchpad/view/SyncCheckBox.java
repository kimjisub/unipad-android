package com.kimjisub.launchpad.view;

import android.widget.CheckBox;

import java.util.ArrayList;

public class SyncCheckBox {
	
	
	// =========================================================================================
	
	ArrayList<CheckBox> checkBoxes = new ArrayList();
	
	public SyncCheckBox(CheckBox... cbs) {
		for (CheckBox cb : cbs)
			addCheckBox(cb);
	}
	
	public void addCheckBox(CheckBox checkBox) {
		checkBox.setOnCheckedChangeListener((compoundButton, b) -> changeChecked(b));
		addOnCheckedChange(b -> checkBox.setChecked(b));
		
		checkBoxes.add(checkBox);
	}
	
	// ========================================================================================= checkedChange
	
	OnCheckedChange onCheckedChange;
	
	public interface OnCheckedChange {
		void onCheckedChange(boolean b);
	}
	
	public void
	
	public void setOnCheckedChange(OnCheckedChange listener) {
		onCheckedChange = listener;
	}
	
	public void changeChecked(boolean b) {
		for(CheckBox checkBox : checkBoxes)
			checkBox.setChecked(b);
	}
	
	
	// =========================================================================================
	
	public void setVisibility(int visibility) {
		for (CheckBox checkBox : checkBoxes)
			checkBox.setVisibility(visibility);
	}
	
	public void setChecked(boolean b) {
		for (CheckBox checkBox : checkBoxes)
			checkBox.setChecked(b);
	}
	
	public boolean isChecked() {
		for (CheckBox checkBox : checkBoxes)
			return checkBox.isChecked();
		return false;
	}
}
