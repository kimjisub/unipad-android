package com.kimjisub.launchpad.fb;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class fbStore {

	public int index = 0;

	public String code;
	public String title;
	public String producerName;
	public boolean isAutoPlay;
	public boolean isLED;
	public int downloadCount;
	public String URL;

	public fbStore() {
	}

	public fbStore(String code, String title, String producerName, boolean isAutoPlay, boolean isLED, int downloadCount, String URL) {
		this.code = code;
		this.title = title;
		this.producerName = producerName;
		this.isAutoPlay = isAutoPlay;
		this.isLED = isLED;
		this.downloadCount = downloadCount;
		this.URL = URL;
	}

	@Exclude
	public Map<String, Object> toMap() {
		HashMap<String, Object> result = new HashMap<>();
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
