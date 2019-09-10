package com.kimjisub.launchpad.db.ent;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.kimjisub.launchpad.db.converter.DateConverter;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.annotation.Nonnull;

@Entity
@TypeConverters(DateConverter.class)
public class UnipackENT {
	@PrimaryKey
	@NonNull
	public String path;
	public int padTouch;
	public boolean bookmark;
	public boolean pin;
	public Date created_at;

	public UnipackENT(String path, int padTouch, boolean bookmark, boolean pin, Date created_at) {
		this.path = path;
		this.padTouch = padTouch;
		this.bookmark = bookmark;
		this.pin = pin;
		this.created_at = created_at;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public int getPadTouch() {
		return padTouch;
	}

	public void setPadTouch(int padTouch) {
		this.padTouch = padTouch;
	}

	public boolean isBookmark() {
		return bookmark;
	}

	public void setBookmark(boolean bookmark) {
		this.bookmark = bookmark;
	}

	public boolean isPin() {
		return pin;
	}

	public void setPin(boolean pin) {
		this.pin = pin;
	}

	public Date getCreated_at() {
		return created_at;
	}

	public void setCreated_at(Date created_at) {
		this.created_at = created_at;
	}

	@Override
	public String toString() {
		return "UnipackENT{" +
				"path='" + path + '\'' +
				", padTouch=" + padTouch +
				", bookmark=" + bookmark +
				", pin=" + pin +
				", created_at=" + created_at +
				'}';
	}
}