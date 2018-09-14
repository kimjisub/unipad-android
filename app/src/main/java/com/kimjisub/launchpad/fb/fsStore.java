package com.kimjisub.launchpad.fb;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class fsStore {
	
	public String key = null;
	
	long chainCount;
	String code;
	String description;
	long difficulty;
	long downloadCount;
	boolean isAutoPlay;
	boolean isLED;
	boolean isNew;
	boolean isProLight;
	boolean isWormhole;
	long playTime;
	String producerName;
	long rank;
	String title;
	Date uploadAt;
	String url;
	String websiteURL;
	
	public fsStore() {
	}
	
	public fsStore(long chainCount, String code, String description, long difficulty, long downloadCount, boolean isAutoPlay, boolean isLED, boolean isNew, boolean isProLight, boolean isWormhole, long playTime, String producerName, long rank, String title, Date uploadAt, String url, String websiteURL) {
		this.chainCount = chainCount;
		this.code = code;
		this.description = description;
		this.difficulty = difficulty;
		this.downloadCount = downloadCount;
		this.isAutoPlay = isAutoPlay;
		this.isLED = isLED;
		this.isNew = isNew;
		this.isProLight = isProLight;
		this.isWormhole = isWormhole;
		this.playTime = playTime;
		this.producerName = producerName;
		this.rank = rank;
		this.title = title;
		this.uploadAt = uploadAt;
		this.url = url;
		this.websiteURL = websiteURL;
	}
	
	@Exclude
	public Map<String, Object> toMap() {
		HashMap<String, Object> result = new HashMap<>();
		
		result.put("chainCount", chainCount);
		result.put("code", code);
		result.put("description", description);
		result.put("difficulty", difficulty);
		result.put("downloadCount", downloadCount);
		result.put("isAutoPlay", isAutoPlay);
		result.put("isLED", isLED);
		result.put("isNew", isNew);
		result.put("isProLight", isProLight);
		result.put("isWormhole", isWormhole);
		result.put("playTime", playTime);
		result.put("producerName", producerName);
		result.put("rank", rank);
		result.put("title", title);
		result.put("uploadAt", uploadAt);
		result.put("url", url);
		result.put("websiteURL", websiteURL);
		
		return result;
	}
}
