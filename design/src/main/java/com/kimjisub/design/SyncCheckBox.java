package com.kimjisub.design;

import android.content.res.ColorStateList;
import android.os.Build;
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
		checkBox.setOnLongClickListener(view -> {
			longClick();
			return false;
		});
		
		checkBoxes.add(checkBox);
	}
	
	// ========================================================================================= checkedChange
	
	OnCheckedChange onCheckedChange;
	
	public interface OnCheckedChange {
		void onCheckedChange(boolean b);
	}
	
	public void setOnCheckedChange(OnCheckedChange listener) {
		onCheckedChange = listener;
	}
	
	public void onCheckedChange(boolean bool){
		if(onCheckedChange !=null)
			onCheckedChange.onCheckedChange(bool);
	}
	
	public void changeChecked(boolean bool) {
		for (CheckBox checkBox : checkBoxes) {
			if (checkBox.isChecked() != bool) {
				checkBox.setOnCheckedChangeListener(null);
				checkBox.setChecked(bool);
				checkBox.setOnCheckedChangeListener((compoundButton, b) -> changeChecked(b));
			}
		}
		
		onCheckedChange(bool);
	}
	
	// ========================================================================================= longClick
	
	OnLongClick onLongClick;
	
	public interface OnLongClick {
		void onLongClick();
	}
	
	public void setOnLongClick(OnLongClick listener) {
		onLongClick = listener;
	}
	
	public void onLongClick(){
		if(onLongClick != null)
			onLongClick.onLongClick();
	}
	
	public void longClick() {
		onLongClick();
	}
	
	
	// =========================================================================================
	
	/*public void setTextColor(int color) {
		for (CheckBox checkBox : checkBoxes)
			checkBox.setTextColor(color);
	}
	
	public void setButtonTintList(ColorStateList colorStateList) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			for (CheckBox checkBox : checkBoxes)
				checkBox.setButtonTintList(colorStateList);
		}
	}*/
	
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
