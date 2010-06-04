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
        ((Button) findViewById(R.id.LocalDelete))
            .setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View arg0) {
                    deleteClick(0);
                }
            });

        ((Button) findViewById(R.id.RemoteDelete))
            .setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View arg0) {
                    deleteClick(1);
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

        ((Button) findViewById(R.id.shareURL))
            .setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View arg0) {
                    final Intent shareLinkIntent = new Intent(
                        Intent.ACTION_SEND);

                    shareLinkIntent.putExtra(Intent.EXTRA_TEXT, mImageUrl);
                    shareLinkIntent.setType("text/plain");

                    ImageDetails.this.startActivity(Intent.createChooser(
                        shareLinkIntent, getResources().getString(
                            R.string.share_via)));
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

        ((Button) findViewById(R.id.shareDelete))
            .setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View arg0) {
                    final Intent shareLinkIntent = new Intent(
                        Intent.ACTION_SEND);

                    shareLinkIntent.putExtra(Intent.EXTRA_TEXT,
                        "http://imgur.com/delete/" + mImageDeleteHash);
                    shareLinkIntent.setType("text/plain");

                    ImageDetails.this.startActivity(Intent.createChooser(
                        shareLinkIntent, getResources().getString(
                            R.string.share_via)));
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

    /*
     * 0 for local delete only
     * 1 for local and remote delete
     */
    public void deleteClick(final int deleteType) {
        deleteImage(mImageDeleteHash, mImageHash, mImageLocalThumbnail,
            deleteType);
    }

    //TODO: Move this into the imgur service
    private void deleteImage(final String deleteHash, final String imageHash,
        final String localThumbnail, final int deleteType) {

        final Handler mHandler = new Handler();

        //load worker to stop hangs
        final Thread loadWorker = new Thread() {
            @Override
            public void run() {
                try {

                    final JSONObject data;
                    final StringBuilder rData = new StringBuilder();

                    if (deleteType == 1) {
                        final HttpURLConnection conn = (HttpURLConnection) (new URL(
                            "http://imgur.com/api/delete/" + deleteHash
                                + ".json")).openConnection();
                        final BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));

                        String line;
                        while ((line = reader.readLine()) != null) {
                            rData.append(line).append('\n');
                        }

                        final JSONObject json = new JSONObject(rData.toString());

                        if (json.has("rsp")) {
                            data = json.getJSONObject("rsp");
                        } else if (json.has("error")) {
                            data = json.getJSONObject("error");
                        } else {
                            data = null;
                        }
                    } else {
                        final JSONObject json = new JSONObject();
                        data = json.put("stat", "ok");
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
                            histDB.close();
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
}
