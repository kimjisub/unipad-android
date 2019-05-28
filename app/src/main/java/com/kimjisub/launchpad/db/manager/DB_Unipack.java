package com.kimjisub.launchpad.db.manager;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.kimjisub.launchpad.manager.Log;
import com.kimjisub.launchpad.db.vo.UnipackVO;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DB_Unipack extends SQLiteOpenHelper {
	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "Database.db";
	private static final String TABLE_NAME = "unipack";

	private static final String KEY_ID = "id";
	private static final String KEY_PATH = "path";
	private static final String KEY_PADTOUCH = "padTouch";
	private static final String KEY_BOOKMARK = "bookmark";
	private static final String KEY_PIN = "pin";
	private static final String KEY_CREATEDAT = "created_at";

	private SQLiteDatabase db;

	public DB_Unipack(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		db = getWritableDatabase();
		onCreate(db);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		String sql =
				"CREATE TABLE IF NOT EXISTS " + TABLE_NAME + "(" +
						KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
						KEY_PATH + " TEXT NOT NULL," +
						KEY_PADTOUCH + " INTEGER NOT NULL," +
						KEY_BOOKMARK + " INTEGER NOT NULL," +
						KEY_PIN + " INTEGER NOT NULL," +
						KEY_CREATEDAT + " DATETIME NOT NULL" +
						");";
		Log.sqlite(sql);
		db.execSQL(sql);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		String sql = "DROP TABLE IF EXISTS " + TABLE_NAME;
		Log.sqlite(sql);
		db.execSQL(sql);

		onCreate(db);
	}

	@SuppressLint("SimpleDateFormat")
	public void add(UnipackVO data) {
		ContentValues values = new ContentValues();
		//values.put(KEY_ID, data.id);
		values.put(KEY_PATH, data.path);
		values.put(KEY_PADTOUCH, data.padTouch);
		values.put(KEY_BOOKMARK, data.bookmark);
		values.put(KEY_PIN, data.pin);
		values.put(KEY_CREATEDAT, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(data.created_at));

		db.insert(TABLE_NAME, null, values);
	}

	@SuppressLint("SimpleDateFormat")
	public void update(String path, UnipackVO data) {
		ContentValues values = new ContentValues();
		//values.put(KEY_ID, data.id);
		values.put(KEY_PATH, data.path);
		values.put(KEY_PADTOUCH, data.padTouch);
		values.put(KEY_BOOKMARK, data.bookmark);
		values.put(KEY_PIN, data.pin);
		values.put(KEY_CREATEDAT, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(data.created_at));

		db.update(TABLE_NAME, values, KEY_PATH + "=?", new String[]{path});
	}

	public UnipackVO getOrCreateByPath(String path) {
		UnipackVO data = getByPath(path);
		if (data == null) {
			data = new UnipackVO(path, 0, false, false, new Date());
			add(data);
		}

		return data;
	}

	public UnipackVO getByPath(String path) {
		String sql = "SELECT * FROM " + TABLE_NAME + " WHERE " + KEY_PATH + " = ?";
		Cursor cursor = db.rawQuery(sql, new String[]{path});

		UnipackVO data = null;
		if (cursor.moveToFirst()) {
			try {
				data = new UnipackVO(cursor);
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}

		return data;
	}

	public List<UnipackVO> getAll() {
		String SELECT_ALL = "SELECT * FROM " + TABLE_NAME;
		Cursor cursor = db.rawQuery(SELECT_ALL, null);

		List<UnipackVO> dataList = new ArrayList<>();
		if (cursor.moveToFirst()) {
			do {
				try {
					dataList.add(new UnipackVO(cursor));
				} catch (ParseException e) {
					e.printStackTrace();
				}
			} while (cursor.moveToNext());
		}

		return dataList;
	}

	public void deleteAllRows() {
		db.delete(TABLE_NAME, "1", null);
	}
}