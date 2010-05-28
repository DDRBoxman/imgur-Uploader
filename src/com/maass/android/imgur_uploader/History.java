package com.maass.android.imgur_uploader;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
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
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

/**
 * This is now the main activity, 
 *
 */
public class History extends Activity {

    private SQLiteDatabase histDB = null;
    private Cursor vCursor;
    private int pickedItem;
    private GridView historyGrid;
    final int CHOOSE_AN_IMAGE_REQUEST = 2910;

    private OnItemClickListener mMessageClickedHandler = new OnItemClickListener() {
        @Override
        public void onItemClick(
            @SuppressWarnings("unchecked") final AdapterView parent,
            final View v, final int position, final long id) {
            // remove check from old image
            for (int i = 0; i < parent.getChildCount(); i++) {
                ((RadioButton) (parent.getChildAt(i)
                    .findViewById(R.id.ImageRadio))).setChecked(false);
            }

            // set radio on current thumbnail
            final RadioButton radio = (RadioButton) v
                .findViewById(R.id.ImageRadio);
            radio.setChecked(true);

            pickedItem = position;
        }
    };

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
                return c.getString(c.getColumnIndex("value"));
            }
        } catch (final Exception e) {
            Log.e(this.getClass().getName(), " error while getting " + col
                + " from " + hash, e);
        }
        return null;
    }

    public void deleteClick(final View target) {
        // ensure that there are images left
        if ((historyGrid.getAdapter().getCount() > 0) && (pickedItem > -1)) {
            final Cursor item = (Cursor) historyGrid
                .getItemAtPosition(pickedItem);
            if (item != null) {
                // remove check from old image
                for (int i = 0; i < historyGrid.getChildCount(); i++) {
                    ((RadioButton) (historyGrid.getChildAt(i)
                        .findViewById(R.id.ImageRadio))).setChecked(false);
                }
                final String hash = item.getString(item.getColumnIndex("hash"));
                deleteImage(dbGetString(hash, "delete_hash"), hash,
                    dbGetString(hash, "local_thumbnail"));
                pickedItem = -1;
                refreshImageGrid();
            }
        }
    }

    private void deleteImage(final String deleteHash, final String imageHash,
        final String localThumbnail) {
        try {
            final HttpURLConnection conn = (HttpURLConnection) (new URL(
                "http://imgur.com/api/delete/" + deleteHash + ".json"))
                .openConnection();
            final BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
            final StringBuilder rData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                rData.append(line).append('\n');
            }

            final JSONObject json = new JSONObject(rData.toString());
            JSONObject data;
            if (json.has("rsp")) {
                data = json.getJSONObject("rsp");
            } else if (json.has("error")) {
                data = json.getJSONObject("error");
            } else {
                data = null;
            }

            if (data != null) {
                if ((data.has("stat") && data.getString("stat").equals("ok"))
                    || (data.has("error_code") && (data.getInt("error_code") == 4002))) {
                    histDB.delete("imgur_history", "hash='" + imageHash + "'",
                        null);
                    try {
                        final File f = new File(localThumbnail);
                        f.delete();
                    } catch (final NullPointerException e) {
                    }
                }
            } else {
                Toast.makeText(this,
                    getString(R.string.delete_failed) + " " + rData.toString(),
                    Toast.LENGTH_SHORT).show();
                Log.i(this.getClass().getName(), "Delete Failed "
                    + rData.toString());
            }

        } catch (final Exception e) {
            Log.e(this.getClass().getName(), "Delete failed", e);
            Toast.makeText(this,
                getString(R.string.delete_failed) + " " + e.toString(),
                Toast.LENGTH_SHORT).show();
        }
    }

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
                intent.setClass(this, LaunchUploadDummy.class);
                startActivity(intent);
            }
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

            {
                ((ImageButton) findViewById(R.id.ImageButtonShare))
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View arg0) {
                            shareImage(arg0);
                        }
                    });
                ((ImageButton) findViewById(R.id.ImageButtonDelete))
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View arg0) {
                            deleteClick(arg0);
                        }
                    });
                ((ImageButton) findViewById(R.id.ImageButtonLaunchBrowser))
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View arg0) {
                            viewImage(arg0);
                        }
                    });
            }
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
        refreshImageGrid();
    }

    @Override
    public void onStop() {
        super.onStop();
        // close cursor
        vCursor.close();
        // close database
        histDB.close();
        histDB = null;
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
     * This is the callback from the UI share button
     */
    public void shareImage(final View target) {
        // ensure that there are images left
        if ((historyGrid.getAdapter().getCount() > 0) && (pickedItem > -1)) {
            final Cursor item = (Cursor) historyGrid
                .getItemAtPosition(pickedItem);
            if (item != null) {
                final Intent shareLinkIntent = new Intent(Intent.ACTION_SEND);

                shareLinkIntent.putExtra(Intent.EXTRA_TEXT, dbGetString(item
                    .getString(item.getColumnIndex("hash")), "image_url"));
                shareLinkIntent.setType("text/plain");

                History.this.startActivity(Intent.createChooser(
                    shareLinkIntent, getResources().getString(
                        R.string.share_via)));
            }
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

    /**
     * This is the call back from the browser launch button
     */
    public void viewImage(final View target) {
        // ensure that there are images left
        if ((historyGrid.getAdapter().getCount() > 0) && (pickedItem > -1)) {
            final Cursor item = (Cursor) historyGrid
                .getItemAtPosition(pickedItem);
            if (item != null) {
                final Intent viewIntent = new Intent(
                    "android.intent.action.VIEW", Uri.parse(dbGetString(item
                        .getString(item.getColumnIndex("hash")), "image_url")));
                startActivity(viewIntent);
            }
        }
    }
}
