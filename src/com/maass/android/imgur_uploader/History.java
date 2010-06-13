package com.maass.android.imgur_uploader;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.OnItemClickListener;

/**
 * This is now the main activity, 
 *
 */
public class History extends Activity {

    private SQLiteDatabase histDB = null;
    private Cursor vCursor;
    private GridView historyGrid;
    final int CHOOSE_AN_IMAGE_REQUEST = 2910;

    private OnItemClickListener mMessageClickedHandler = new OnItemClickListener() {
        @Override
        public void onItemClick(
            @SuppressWarnings("unchecked") final AdapterView parent,
            final View v, final int position, final long id) {

            Log.i("", "" + position);

            final Cursor item = (Cursor) historyGrid
                .getItemAtPosition(position);

            //start ImageDetails activity passing info about the image
            final Intent intent = new Intent(getBaseContext(),
                ImageDetails.class);
            final String hash = item.getString(item.getColumnIndex("hash"));
            intent.putExtra("hash", hash);
            intent.putExtra("image_url", dbGetString(hash, "image_url"));
            intent.putExtra("delete_hash", dbGetString(hash, "delete_hash"));
            intent.putExtra("local_thumbnail", dbGetString(hash,
                "local_thumbnail"));
            startActivity(intent);
        }
    };

    /** 
     * This method does a self joining query that locates the thumbnail and 
     * time uploaded so that it can be displayed in the gui sorted newest first
     * 
     * @return a cursor
     */
    private Cursor getCursor() {
        try {
            return histDB.rawQuery("SELECT ltb.hash as _id, ltb.hash as hash, "
                + "ltb.value as local_thumbnail "
                + "FROM imgur_history as ltb, imgur_history as tu "
                + "WHERE tu.hash = ltb.hash "
                + "AND ltb.key = 'local_thumbnail' "
                + "AND tu.key='upload_time' " + "ORDER BY tu.value DESC", null);
        } catch (final Exception e) {
            Log.e(this.getClass().getName(), "Error with database", e);
        }
        return null;
    }

    /**
     * This is used to get columns from the database that were not queried in
     * the main query, this allows for additional properties that get added on
     * later to be gotten without having to break old entries that may be 
     * missing these columns
     * 
     * @param hash hash provided by imgur
     * @param col the name of the column that you want to get
     * @return value or null on error/empty
     */
    private String dbGetString(final String hash, final String col) {
        try {
            final Cursor c = histDB.query("imgur_history",
                new String[] { "value" }, "hash = ? AND key = ?", new String[] {
                    hash, col }, null, null, null);
            final int n = c.getCount();
            if (n > 1) {
                Log.e(this.getClass().getName(), " image " + hash
                    + " has more than one " + col + " ??");
            }
            if (n > 0) {
                c.moveToFirst();
                final String returnString = c.getString(c
                    .getColumnIndex("value"));
                //make sure we close the cursor
                c.close();
                return returnString;
            }
        } catch (final Exception e) {
            Log.e(this.getClass().getName(), " error while getting " + col
                + " from " + hash, e);
        }
        return null;
    }

    @Override
    protected void onActivityResult(final int requestCode,
        final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            // we got an Image now upload it :D
            if (requestCode == CHOOSE_AN_IMAGE_REQUEST) {
                final Uri chosenImageUri = data.getData();
                final Intent intent = new Intent();
                // intent.setData(chosenImageUri);
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, chosenImageUri);
                intent.setClass(this, ImgurUpload.class);
                startService(intent);
            }
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //see if we need to pass a call to upload to the upload service
        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();

        //upload a new image
        if (Intent.ACTION_SEND.equals(intent.getAction()) && (extras != null)
            && extras.containsKey(Intent.EXTRA_STREAM)) {

            final Uri uri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
            if (uri != null) {
                final Intent passIntent = new Intent();
                // intent.setData(chosenImageUri);
                passIntent.setAction(Intent.ACTION_SEND);
                passIntent.putExtra(Intent.EXTRA_STREAM, uri);
                passIntent.setClass(this, ImgurUpload.class);
                startService(passIntent);
                finish();
                return;
            }
        }

        //continue normally :D
        if (histDB == null) {
            final HistoryDatabase histData = new HistoryDatabase(this);
            histDB = histData.getWritableDatabase();
        }

        vCursor = getCursor();

        Log
            .i(this.getClass().getName(), "Records found: "
                + vCursor.getCount());

        if (vCursor.getCount() > 0) {
            setContentView(R.layout.history);
            final SimpleCursorAdapter entry = new SimpleCursorAdapter(this,
                R.layout.history_item, vCursor,
                new String[] { "local_thumbnail" },
                new int[] { R.id.ThumbnailImage });
            historyGrid = (GridView) findViewById(R.id.HistoryGridView);
            historyGrid.setAdapter(entry);
            historyGrid.setOnItemClickListener(mMessageClickedHandler);
        } else {
            setContentView(R.layout.info);
        }
    }

    /**
     * Setup menu
     * 
     *  (non-Javadoc)
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);

        // display about page
        menu.findItem(R.id.AboutMenu).setIntent(
            new Intent(this, LaunchedInfo.class));

        menu.findItem(R.id.SettingsMenu).setIntent(
            new Intent(this, ImgurPreferences.class));

        return true;
    }

    /**
     * handle menu selections 
     *  (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case R.id.UploadMenu:
            uploadImageFromProvider();
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (histDB == null) {
            // if the database is closed reopen it
            final HistoryDatabase histData = new HistoryDatabase(this);
            histDB = histData.getWritableDatabase();
        }

        //get updates about new images that have been uploaded
        registerReceiver(receiver, new IntentFilter(
            ImgurUpload.BROADCAST_ACTION));

        refreshImageGrid();
    }

    @Override
    public void onPause() {
        super.onPause();
        // close cursor
        vCursor.close();
        // close database
        histDB.close();
        histDB = null;
        unregisterReceiver(receiver);
    }

    @Override
    public void onStop() {
        super.onStop();

    }

    /**
     * reload data from the db
     */
    public void refreshImageGrid() {
        if (historyGrid != null) {
            vCursor = getCursor();
            ((SimpleCursorAdapter) historyGrid.getAdapter())
                .changeCursor(vCursor);
        }
    }

    /**
     * send an intent that we want an image
     * this will generally launch the gallery, though there are other apps
     * that can serve the same purpose.
     */
    private void uploadImageFromProvider() {
        final Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_PICK);
        startActivityForResult(Intent.createChooser(intent,
            getString(R.string.choose_a_viewer)), CHOOSE_AN_IMAGE_REQUEST);
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (intent.getAction().equals(ImgurUpload.BROADCAST_ACTION)) {
                refreshImageGrid();
            }
        }
    };
}
