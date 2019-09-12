package com.kimjisub.launchpad.network.fb;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class StoreVO {
	public String code;
	public String title;
	public String producerName;
	public boolean isAutoPlay;
	public boolean isLED;
	public int downloadCount;
	public String URL;

	@Exclude
	public Map<String, Object> toMap() {
		Map<String, Object> result = new HashMap<>();
		result.put("code", code);
		result.put("title", title);
		result.put("producerName", producerName);
		result.put("isAutoPlay", isAutoPlay);
		result.put("isLED", isLED);
		result.put("downloadCount", downloadCount);
		result.put("URL", URL);

		return result;
	}
}
