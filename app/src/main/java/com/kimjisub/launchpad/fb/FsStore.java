package com.kimjisub.launchpad.fb;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class FsStore {
	
	public long chainCount;
	public String code;
	public String description;
	public long difficulty;
	public long downloadCount;
	public boolean isAutoPlay;
	public boolean isLED;
	public boolean isNew;
	public boolean isProLight;
	public boolean isWormhole;
	public long playTime;
	public String producerName;
	public long rank;
	public String title;
	public Date uploadAt;
	public String url;
	public String website;
	
	public FsStore() {
	}
	
	public FsStore(long chainCount, String code, String description, long difficulty, long downloadCount, boolean isAutoPlay, boolean isLED, boolean isNew, boolean isProLight, boolean isWormhole, long playTime, String producerName, long rank, String title, Date uploadAt, String url, String website) {
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
		this.website = website;
	}
	
	public FsStore(QueryDocumentSnapshot document) {
		chainCount = document.getLong("chainCount");
		code = document.getString("code");
		description = document.getString("description");
		difficulty = document.getLong("difficulty");
		downloadCount = document.getLong("downloadCount");
		isAutoPlay = document.getBoolean("isAutoPlay");
		isLED = document.getBoolean("isLED");
		isNew = document.getBoolean("isNew");
		isProLight = document.getBoolean("isProLight");
		isWormhole = document.getBoolean("isWormhole");
		playTime = document.getLong("playTime");
		producerName = document.getString("producerName");
		rank = document.getLong("rank");
		title = document.getString("title");
		uploadAt = document.getDate("uploadAt");
		url = document.getString("url");
		website = document.getString("website");
	}
	
	public FsStore(Map map) {
		chainCount = (long)map.get( "chainCount");
		code = (String)map.get( "code");
		description = (String)map.get( "description");
		difficulty = (long)map.get( "difficulty");
		downloadCount = (long)map.get( "downloadCount");
		isAutoPlay = (boolean)map.get( "isAutoPlay");
		isLED = (boolean)map.get( "isLED");
		isNew = (boolean)map.get( "isNew");
		isProLight = (boolean)map.get( "isProLight");
		isWormhole = (boolean)map.get( "isWormhole");
		playTime = (long)map.get( "playTime");
		producerName = (String)map.get( "producerName");
		rank = (long)map.get( "rank");
		title = (String)map.get( "title");
		uploadAt = (Date)map.get( "uploadAt");
		url = (String)map.get( "url");
		website = (String)map.get( "website");
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
		result.put("website", website);
		
		return result;
	}
}
