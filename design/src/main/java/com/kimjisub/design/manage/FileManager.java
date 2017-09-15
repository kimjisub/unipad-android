package com.kimjisub.design.manage;

/**
 * Created by rlawl on 2017-09-15.
 */

public class FileManager {
	public static String byteToMB(float Byte){
		return String.format("%.2f", Byte / 1024L / 1024L);
	}
}
