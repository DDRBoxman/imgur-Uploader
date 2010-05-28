package com.maass.android.imgur_uploader;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class HistoryDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "imgur.db";
    private static final int DATABASE_VERSION = 11;
    private static final String IMGUR_TABLE_NAME = "imgur_history";

    // id is necessary so cursor adapter can iterate over table
    private static final String IMGUR_TABLE_CREATE = "CREATE TABLE "
        + IMGUR_TABLE_NAME + " (" + "hash" + " TEXT, " + "key" + " TEXT, "
        + "value" + " TEXT);";
    private static final String IMGUR_TABLE_CREATE_INDEX = "CREATE INDEX "
        + IMGUR_TABLE_NAME + "_idx ON " + IMGUR_TABLE_NAME + " (hash);";

    HistoryDatabase(final Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Log.i(this.getClass().getName(), "in cstr");
    }

    @Override
    public void onCreate(final SQLiteDatabase db) {
        Log.i(this.getClass().getName(), "in onCreate(SQLiteDatabase)");
        db.execSQL(IMGUR_TABLE_CREATE);
        db.execSQL(IMGUR_TABLE_CREATE_INDEX);
    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion,
        final int newVersion) {
        Log.i(this.getClass().getName(),
            "in onUpgrade(SQLiteDatabase, int, int)");
        Log.w(this.getClass().getName(), "Upgrading database from version "
            + oldVersion + " to " + newVersion
            + ", which will destroy all old data");
        db.execSQL("DROP INDEX IF EXISTS imgur_history_idx");
        db.execSQL("DROP TABLE IF EXISTS imgur_history");
        onCreate(db);
    }
}
