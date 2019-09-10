package com.kimjisub.launchpad.controller;

import com.kimjisub.launchpad.activity.LaunchpadActivity;
import com.kimjisub.launchpad.activity.MainActivity;

public abstract class BaseController {

	 void attach(){

	 }

	 void detach(){
		 LaunchpadActivity.removeDriverListener(MainActivity.this);
	 }
}
