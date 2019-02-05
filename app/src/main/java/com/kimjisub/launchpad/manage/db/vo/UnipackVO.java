package com.kimjisub.launchpad.manage.db.vo;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UnipackVO {
	public int id;
	public String path;
	public int padTouch;
	public boolean bookmark;
	public boolean pin;
	public Date created_at;

	public UnipackVO(Cursor cursor) throws ParseException {
		this(
				cursor.getInt(0),
				cursor.getString(1),
				cursor.getInt(2),
				cursor.getInt(3) == 1,
				cursor.getInt(4) == 1,
				new Date(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(cursor.getString(5)).getTime())
		);
	}

	public UnipackVO(String path, int padTouch, boolean bookmark, boolean pin, Date created_at) {
		this.path = path;
		this.padTouch = padTouch;
		this.bookmark = bookmark;
		this.pin = pin;
		this.created_at = created_at;
	}

	public UnipackVO(int id, String path, int padTouch, boolean bookmark, boolean pin, Date created_at) {
		this(path, padTouch, bookmark, pin, created_at);
		this.id = id;
	}

	public JSONObject toJSON() {
		JSONObject jsonObject = new JSONObject();

		try {
			jsonObject.put("id", id);
			jsonObject.put("path", path);
			jsonObject.put("padTouch", padTouch);
			jsonObject.put("bookmark", bookmark);
			jsonObject.put("pin", pin);
			jsonObject.put("created_at", created_at);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return jsonObject;
	}

}