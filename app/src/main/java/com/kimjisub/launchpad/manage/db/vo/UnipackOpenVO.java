package com.kimjisub.launchpad.manage.db.vo;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UnipackOpenVO {
	public int id;
	public String path;
	public Date created_at;

	public UnipackOpenVO(Cursor cursor) throws ParseException {
		this(
				cursor.getInt(0),
				cursor.getString(1),
				new Date(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(cursor.getString(2)).getTime())
		);
	}

	public UnipackOpenVO(String path, Date created_at) {
		this.path = path;
		this.created_at = created_at;
	}

	public UnipackOpenVO(int id, String path, Date created_at) {
		this(path, created_at);
		this.id = id;
	}

	public JSONObject toJSON() {
		JSONObject jsonObject = new JSONObject();

		try {
			jsonObject.put("id", id);
			jsonObject.put("path", path);
			jsonObject.put("created_at", created_at);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return jsonObject;
	}
}
