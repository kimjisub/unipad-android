package com.kimjisub.launchpad;

/**
 * Created by kimjisub on 2017. 3. 24..
 */

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class DNotice {
	
	public String title;
	public String content;
	
	public DNotice() {
	}
	
	public DNotice(String title, String content) {
		this.title = title;
		this.content = content;
	}
	
	@Exclude
	public Map<String, Object> toMap() {
		HashMap<String, Object> result = new HashMap<>();
		result.put("title", title);
		result.put("content", content);
		
		return result;
	}
	
}