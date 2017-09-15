package com.kimjisub.design.manage;

import android.content.Context;
import android.util.DisplayMetrics;

/**
 * Created by rlawl on 2017-09-07.
 */

public class UIManager {
	public static int pxToDp(Context context, int pixel) {
		float dp = 0;
		try {
			DisplayMetrics metrics = context.getResources().getDisplayMetrics();
			dp = pixel / (metrics.densityDpi / 160f);
		} catch (Exception e) {
		}
		return (int) dp;
	}

	public static int dpToPx(Context context, float dp) {
		DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		float px = dp * (metrics.densityDpi / 160f);
		return Math.round(px);
	}
}
