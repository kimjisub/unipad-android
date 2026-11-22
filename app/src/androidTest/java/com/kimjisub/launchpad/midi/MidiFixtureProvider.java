package com.kimjisub.launchpad.midi;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public class MidiFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        MidiFixtureService instance = MidiFixtureService.INSTANCES.get(arg);
        if (instance != null) return instance.command(method, extras != null ? extras : Bundle.EMPTY);
        Bundle snapshot = MidiFixtureService.SNAPSHOTS.get(arg);
        if (method.equals("read") && snapshot != null) return snapshot;
        Bundle absent = new Bundle();
        absent.putBoolean("absent", true);
        return absent;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
