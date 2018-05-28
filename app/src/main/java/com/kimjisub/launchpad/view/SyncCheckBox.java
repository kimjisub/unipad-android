package com.kimjisub.launchpad.view;

import android.widget.CheckBox;

import java.util.ArrayList;

public class SyncCheckBox extends SyncView {
	
	ArrayList<CheckBox> checkBoxes = new ArrayList();
	
	public SyncCheckBox(CheckBox ... cbs) {
		for(CheckBox cb : cbs)
			addCheckBox(cb);
	}
	
	public void addCheckBox(CheckBox checkBox) {
		checkBox.setOnCheckedChangeListener((compoundButton, b) -> attrChange("OnCheckedChange", b));
		addOnAttrChange("OnCheckedChange", b -> checkBox.setChecked(b));
		
		checkBoxes.add(checkBox);
	}
	
	public void setVisibility(int visibility){
		for(CheckBox checkBox : checkBoxes){
			checkBox.setVisibility(visibility);
		}
	}
}
