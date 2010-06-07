package com.maass.android.imgur_uploader;

import java.io.File;
import java.util.ArrayList;
import java.util.ListIterator;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;
import android.util.Log;

public class HistoryDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "imgur.db";
    private static final int DATABASE_VERSION = 11;
    private static final String IMGUR_TABLE_NAME = "imgur_history";

    private Context context;

    // id is necessary so cursor adapter can iterate over table
    private static final String IMGUR_TABLE_CREATE = "CREATE TABLE "
        + IMGUR_TABLE_NAME + " (" + "hash" + " TEXT, " + "key" + " TEXT, "
        + "value" + " TEXT);";
    private static final String IMGUR_TABLE_CREATE_INDEX = "CREATE INDEX "
        + IMGUR_TABLE_NAME + "_idx ON " + IMGUR_TABLE_NAME + " (hash);";

    HistoryDatabase(final Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Log.i(this.getClass().getName(), "in cstr");

        this.context = context;
    }

    @Override
    public void onOpen(final SQLiteDatabase db) {
        final SharedPreferences prefs = PreferenceManager
            .getDefaultSharedPreferences(context);

        //delete if image is too old
        if (prefs.getBoolean("clean_old_images", false)) {
            final String maxAgeStr = prefs.getString("clean_old_images_date",
                "-1");
            Long maxAge = Long.parseLong(maxAgeStr);
            //put in miliseconds
            maxAge = maxAge * 86400000;
            if (maxAge > 0) {

                //stores hashes of images to remove
                final ArrayList<String> hashToDelete = new ArrayList<String>();

                final Cursor result = db.query("imgur_history", new String[] {
                    "hash", "value" }, "key = 'upload_time'", null, null, null,
                    null);

                if (result.getCount() > 0) {

                    result.moveToFirst();
                    do {
                        final Long savedTime = Long.parseLong(result
                            .getString(result.getColumnIndex("value")));
                        if (System.currentTimeMillis() - savedTime > maxAge) {
                            hashToDelete.add(result.getString(result
                                .getColumnIndex("hash")));
                        }
                    } while (result.moveToNext());

                    deleteImages(hashToDelete, db);
                }

                result.close();
            }
        }

        //delete if too many images
        if (prefs.getBoolean("clean_max_images", false)) {
            final String maxImagesStr = prefs.getString(
                "clean_max_images_number", "-1");
            final int maxImages = Integer.parseInt(maxImagesStr);
            if (maxImages > 0) {

                //stores hashes of images to remove
                final ArrayList<String> hashToDelete = new ArrayList<String>();

                final Cursor result = db.rawQuery(
                    "SELECT ltb.hash as _id, ltb.hash as hash, "
                        + "ltb.value as local_thumbnail "
                        + "FROM imgur_history as ltb, imgur_history as tu "
                        + "WHERE tu.hash = ltb.hash "
                        + "AND ltb.key = 'local_thumbnail' "
                        + "AND tu.key='upload_time' "
                        + "ORDER BY tu.value DESC", null);
                if (result.getCount() > maxImages) {
                    result.moveToPosition(maxImages);
                    do {
                        hashToDelete.add(result.getString(result
                            .getColumnIndex("hash")));
                    } while (result.moveToNext());

                    deleteImages(hashToDelete, db);
                }

                result.close();
            }
        }

        super.onOpen(db);
    }

    private void deleteImages(final ArrayList<String> imageHashToDelete,
        final SQLiteDatabase db) {
        //delete images
        final ListIterator<String> litr = imageHashToDelete.listIterator();
        while (litr.hasNext()) {
            final String hash = litr.next();
            final Cursor thumbnail = db.query("imgur_history",
                new String[] { "value" }, "hash = '" + hash
                    + "' AND key = 'local_thumbnail'", null, null, null, null);
            thumbnail.moveToFirst();
            final String thumbnailFile = thumbnail.getString(thumbnail
                .getColumnIndex("value"));
            try {
                final File f = new File(thumbnailFile);
                f.delete();
            } catch (final NullPointerException e) {
            }
            db.delete("imgur_history", "hash = '" + hash + "'", null);
            Log.i("imgur - delete", hash);
        }
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
