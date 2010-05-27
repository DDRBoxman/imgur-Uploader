package com.maass.android.imgur_uploader;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class HistoryDatabase extends SQLiteOpenHelper {

	private static final String DATABASE_NAME = "imgur.db";
    private static final int DATABASE_VERSION = 8;
    private static final String IMGUR_TABLE_NAME = "imgur_history";
    
    private static final String IMGUR_TABLE_CREATE =
                "CREATE TABLE " + IMGUR_TABLE_NAME + " (" +
                "_id" + " INTEGER PRIMARY KEY, " + //need this so cursor adapter can iterate over table
                "hash" + " TEXT, " +
                "key" + " TEXT, " +
                "value" + " TEXT);";

    HistoryDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(IMGUR_TABLE_CREATE);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w("Imgur", "Upgrading database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS imgur_history");
        onCreate(db);
    }
}
