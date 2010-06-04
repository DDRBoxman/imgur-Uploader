package com.maass.android.imgur_uploader;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class ImageDetails extends Activity {

    String mImageHash, mImageUrl, mImageDeleteHash, mImageLocalThumbnail;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.image_details);

        //add actions for button clicks
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

        ((Button) findViewById(R.id.copyURL))
            .setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View arg0) {
                    final ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setText(mImageUrl);
                    Toast.makeText(ImageDetails.this,
                        getString(R.string.clipboard_success),
                        Toast.LENGTH_SHORT).show();
                }
            });

        ((Button) findViewById(R.id.copyDelete))
            .setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View arg0) {
                    final ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setText("http://imgur.com/delete/"
                        + mImageDeleteHash);
                    Toast.makeText(ImageDetails.this,
                        getString(R.string.clipboard_success),
                        Toast.LENGTH_SHORT).show();
                }
            });

        //load image info from intent extras
        final Intent intent = getIntent();
        mImageHash = intent.getStringExtra("hash");
        mImageUrl = intent.getStringExtra("image_url");
        mImageDeleteHash = intent.getStringExtra("delete_hash");
        mImageLocalThumbnail = intent.getStringExtra("local_thumbnail");

        //set items in view to image details
        ((ImageView) findViewById(R.id.ImageDetailsImage)).setImageURI(Uri
            .parse(mImageLocalThumbnail));
        ((TextView) findViewById(R.id.url)).setText(mImageUrl);
        ((TextView) findViewById(R.id.delete))
            .setText("http://imgur.com/delete/" + mImageDeleteHash);
    }

    public void deleteClick(final View target) {
        deleteImage(mImageDeleteHash, mImageHash, mImageLocalThumbnail);
    }

    private void deleteImage(final String deleteHash, final String imageHash,
        final String localThumbnail) {

        final Handler mHandler = new Handler();

        //load worker to stop hangs
        final Thread loadWorker = new Thread() {
            @Override
            public void run() {
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
                        if ((data.has("stat") && data.getString("stat").equals(
                            "ok"))
                            || (data.has("error_code") && (data
                                .getInt("error_code") == 4002))) {
                            final HistoryDatabase histData = new HistoryDatabase(
                                ImageDetails.this);
                            final SQLiteDatabase histDB = histData
                                .getWritableDatabase();
                            histDB.delete("imgur_history", "hash='" + imageHash
                                + "'", null);
                            try {
                                final File f = new File(localThumbnail);
                                f.delete();
                            } catch (final NullPointerException e) {
                            }
                            //send broadcast to update the grid now that the database has changed
                            sendBroadcast(new Intent(
                                ImgurUpload.BROADCAST_ACTION));

                            //close image details now that it no longer exists
                            finish();
                        }
                    } else {
                        mHandler.post(new Runnable() {
                            public void run() {
                                Toast.makeText(
                                    ImageDetails.this,
                                    getString(R.string.delete_failed) + " "
                                        + rData.toString(), Toast.LENGTH_SHORT)
                                    .show();
                            }
                        });
                        Log.i(this.getClass().getName(), "Delete Failed "
                            + rData.toString());
                    }

                } catch (final Exception e) {
                    mHandler.post(new Runnable() {
                        public void run() {
                            Toast.makeText(
                                ImageDetails.this,
                                getString(R.string.delete_failed) + " "
                                    + e.toString(), Toast.LENGTH_SHORT).show();
                        }
                    });
                    Log.e(this.getClass().getName(), "Delete failed", e);
                }
            }
        };

        loadWorker.start();
    }

    /**
     * This is the callback from the UI share button
     */
    public void shareImage(final View target) {
        final Intent shareLinkIntent = new Intent(Intent.ACTION_SEND);

        shareLinkIntent.putExtra(Intent.EXTRA_TEXT, mImageUrl);
        shareLinkIntent.setType("text/plain");

        ImageDetails.this.startActivity(Intent.createChooser(shareLinkIntent,
            getResources().getString(R.string.share_via)));
    }

    /**
     * This is the call back from the browser launch button
     */
    public void viewImage(final View target) {
        final Intent viewIntent = new Intent("android.intent.action.VIEW", Uri
            .parse(mImageUrl));
        startActivity(viewIntent);
    }
}
