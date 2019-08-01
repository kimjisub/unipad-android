package com.kimjisub.launchpad.db.manager;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.kimjisub.launchpad.db.vo.UnipackOpenVO;
import com.kimjisub.manager.Log;

import java.text.SimpleDateFormat;

public class DB_UnipackOpen extends SQLiteOpenHelper {
	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "Database.db";
	private static final String TABLE_NAME = "unipackOpen";

	private static final String KEY_ID = "id";
	private static final String KEY_PATH = "path";
	private static final String KEY_CREATEDAT = "created_at";

	private SQLiteDatabase db;

	public DB_UnipackOpen(Context context) {
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
	public void add(UnipackOpenVO data) {
		ContentValues values = new ContentValues();
		//values.put(KEY_ID, data.id);
		values.put(KEY_PATH, data.path);
		values.put(KEY_CREATEDAT, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(data.created_at));

		db.insert(TABLE_NAME, null, values);
	}

	public int getCountByPath(String path) {
		String sql = "SELECT * FROM " + TABLE_NAME + " WHERE " + KEY_PATH + " = ?";
		Cursor cursor = db.rawQuery(sql, new String[]{path});

		return cursor.getCount();
	}

	public int getAllCount() {
		String sql = "SELECT * FROM " + TABLE_NAME;
		Cursor cursor = db.rawQuery(sql, null);

		return cursor.getCount();
	}

	public void deleteAllRows() {
		db.delete(TABLE_NAME, "1", null);
	}
}