/*
 * Copyright (C) Michael Maass 2009 <code.realm@gmail.com>
 * 
 * main.c is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * main.c is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.maass.android.imgur_uploader;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.commons.codec_1_4.binary.Base64OutputStream;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.RemoteViews;

public class ImgurUpload extends Service {
    private static final String THUMBNAIL_POSTFIX = ".thumb.jpg";
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 250;
    private static final int CHUNK_SIZE = 9000;
    private static final int READ_BUFFER_SIZE_BYTES = (3 * CHUNK_SIZE) / 4;
    private static final String API_KEY = "e67bb2d5ceb42e43f8f7fc38e7ca7376";
    private static final int THUMBNAIL_MAX_SIZE = 200;

    public static final String BROADCAST_ACTION = "com.maass.android.imgur_uploader.ImageUploadedEvent";
    private Notification mProgressNotification;
    private static final int NOTIFICATION_ID = 42;
    private NotificationManager mNotificationManager;

    private Map<String, String> mImgurResponse;
    private Uri imageLocation;

    private Handler mHandler = new Handler();

    @Override
    public void onStart(final Intent intent, final int startId) {
        Log.i(this.getClass().getName(), "in onStart(Intent, int)");
        passIntent(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags,
        final int startId) {
        Log.i(this.getClass().getName(), "in onStartCommand(Intent, int, int)");
        passIntent(intent);
        return START_STICKY_COMPATIBILITY;
    }

    private void passIntent(final Intent intent) {
        try {
            Log.i(this.getClass().getName(), "in passIntent(Intent)");

            //Start a thread so that android can continue to do things,
            //services are not a separate thread and will make an application hang
            final Thread loadWorker = new Thread() {
                @Override
                public void run() {
                    mImgurResponse = handleSendIntent(intent);
                    handleResponse();
                }
            };

            loadWorker.start();

        } finally {
            stopSelf();
        }
    }

    private void handleResponse() {
        Log.i(this.getClass().getName(), "in handleResponse()");
        // close progress notification
        mNotificationManager.cancel(NOTIFICATION_ID);

        String notificationMessage = getString(R.string.upload_success);

        // notification intent with result
        final Intent notificationIntent = new Intent(getBaseContext(),
            ImageDetails.class);

        if (mImgurResponse == null) {
            notificationMessage = getString(R.string.connection_failed);
        } else if (mImgurResponse.get("error") != null) {
            notificationMessage = getString(R.string.unknown_error)
                + mImgurResponse.get("error");
        } else {
            // create thumbnail
            if (mImgurResponse.get("image_hash").length() > 0) {
                createThumbnail(imageLocation);
            }

            // store result in database
            final HistoryDatabase histData = new HistoryDatabase(
                getBaseContext());
            final SQLiteDatabase data = histData.getWritableDatabase();

            final HashMap<String, String> dataToSave = new HashMap<String, String>();
            dataToSave.put("delete_hash", mImgurResponse.get("delete_hash"));
            dataToSave.put("image_url", mImgurResponse.get("original"));
            final Uri imageUri = Uri.parse(getFilesDir() + "/"
                + mImgurResponse.get("image_hash") + THUMBNAIL_POSTFIX);
            dataToSave.put("local_thumbnail", imageUri.toString());
            dataToSave.put("upload_time", "" + System.currentTimeMillis());

            for (final Map.Entry<String, String> entry : dataToSave.entrySet()) {
                final ContentValues content = new ContentValues();
                content.put("hash", mImgurResponse.get("image_hash"));
                content.put("key", entry.getKey());
                content.put("value", entry.getValue());
                data.insert("imgur_history", null, content);
            }

            //set intent to go to image details
            notificationIntent.putExtra("hash", mImgurResponse
                .get("image_hash"));
            notificationIntent.putExtra("image_url", mImgurResponse
                .get("original"));
            notificationIntent.putExtra("delete_hash", mImgurResponse
                .get("delete_hash"));
            notificationIntent.putExtra("local_thumbnail", imageUri.toString());

            data.close();
            histData.close();

            // if the main activity is already open then refresh the gridview
            sendBroadcast(new Intent(BROADCAST_ACTION));
        }

        //assemble notification
        final Notification notification = new Notification(R.drawable.icon,
            notificationMessage, System.currentTimeMillis());
        notification.setLatestEventInfo(this, getString(R.string.app_name),
            notificationMessage, PendingIntent.getActivity(getBaseContext(), 0,
                notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT));
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        mNotificationManager.notify(NOTIFICATION_ID, notification);

    }

    /**
     * 
     * @return a map that contains objects with the following keys:
     * 
     *         delete - the url used to delete the uploaded image (null if
     *         error).
     * 
     *         original - the url to the uploaded image (null if error) The map
     *         is null if error
     */
    private Map<String, String> handleSendIntent(final Intent intent) {
        Log.i(this.getClass().getName(), "in handleResponse()");

        Log.d(this.getClass().getName(), intent.toString());
        final Bundle extras = intent.getExtras();
        try {
            //upload a new image
            if (Intent.ACTION_SEND.equals(intent.getAction())
                && (extras != null) && extras.containsKey(Intent.EXTRA_STREAM)) {

                final Uri uri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
                if (uri != null) {
                    Log.d(this.getClass().getName(), uri.toString());
                    // store uri so we can create the thumbnail if we succeed
                    imageLocation = uri;
                    final String jsonOutput = readPictureDataAndUpload(uri);
                    return parseJSONResponse(jsonOutput);
                }
                Log.e(this.getClass().getName(), "URI null");
            }
        } catch (final Exception e) {
            Log.e(this.getClass().getName(), "Completely unexpected error", e);
        }
        return null;
    }

    /**
     * Method to generate the remote view for the progress notification
     * 
     * 
     */
    private RemoteViews generateProgressNotificationView(final int progress,
        final int total) {
        final RemoteViews contentView = new RemoteViews(getPackageName(),
            R.layout.notification_layout_upload);
        contentView.setProgressBar(R.id.UploadProgress, total, progress, false);
        contentView.setTextViewText(R.id.text, "Uploaded " + progress + " of "
            + total + " bytes");
        return contentView;
    }

    /**
     * This method uploads an image from the given uri. It does the uploading in
     * small chunks to make sure it doesn't over run the memory.
     * 
     * @param uri
     *            image location
     * @return map containing data from interaction
     */
    private String readPictureDataAndUpload(final Uri uri) {
        Log.i(this.getClass().getName(), "in readPictureDataAndUpload(Uri)");
        try {
            final AssetFileDescriptor assetFileDescriptor = getContentResolver()
                .openAssetFileDescriptor(uri, "r");
            final int totalFileLength = (int) assetFileDescriptor.getLength();
            assetFileDescriptor.close();

            // Create custom progress notification
            mProgressNotification = new Notification(R.drawable.icon,
                getString(R.string.upload_in_progress), System
                    .currentTimeMillis());
            // set as ongoing
            mProgressNotification.flags |= Notification.FLAG_ONGOING_EVENT;
            // set custom view to notification
            mProgressNotification.contentView = generateProgressNotificationView(
                0, totalFileLength);
            //empty intent for the notification
            final Intent progressIntent = new Intent();
            final PendingIntent contentIntent = PendingIntent.getActivity(this,
                0, progressIntent, 0);
            mProgressNotification.contentIntent = contentIntent;
            // add notification to manager
            mNotificationManager.notify(NOTIFICATION_ID, mProgressNotification);

            final InputStream inputStream = getContentResolver()
                .openInputStream(uri);

            final String boundaryString = "Z."
                + Long.toHexString(System.currentTimeMillis())
                + Long.toHexString((new Random()).nextLong());
            final String boundary = "--" + boundaryString;
            final HttpURLConnection conn = (HttpURLConnection) (new URL(
                "http://imgur.com/api/upload.json")).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-type",
                "multipart/form-data; boundary=\"" + boundaryString + "\"");
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setChunkedStreamingMode(CHUNK_SIZE);
            final OutputStream hrout = conn.getOutputStream();
            final PrintStream hout = new PrintStream(hrout);
            hout.println(boundary);
            hout.println("Content-Disposition: form-data; name=\"key\"");
            hout.println("Content-Type: text/plain");
            hout.println();
            hout.println(API_KEY);
            hout.println(boundary);
            hout.println("Content-Disposition: form-data; name=\"image\"");
            hout.println("Content-Transfer-Encoding: base64");
            hout.println();
            hout.flush();
            {
                final Base64OutputStream bhout = new Base64OutputStream(hrout);
                final byte[] pictureData = new byte[READ_BUFFER_SIZE_BYTES];
                int read = 0;
                int totalRead = 0;
                long lastLogTime = 0;
                while (read >= 0) {
                    read = inputStream.read(pictureData);
                    if (read > 0) {
                        bhout.write(pictureData, 0, read);
                        totalRead += read;
                        if (lastLogTime < (System.currentTimeMillis() - PROGRESS_UPDATE_INTERVAL_MS)) {
                            lastLogTime = System.currentTimeMillis();
                            Log.d(this.getClass().getName(), "Uploaded "
                                + totalRead + " of " + totalFileLength
                                + " bytes (" + (100 * totalRead)
                                / totalFileLength + "%)");

                            //make a final version of the total read to make the handler happy
                            final int totalReadFinal = totalRead;
                            mHandler.post(new Runnable() {
                                public void run() {
                                    mProgressNotification.contentView = generateProgressNotificationView(
                                        totalReadFinal, totalFileLength);
                                    mNotificationManager.notify(
                                        NOTIFICATION_ID, mProgressNotification);
                                }
                            });
                        }
                        bhout.flush();
                        hrout.flush();
                    }
                }
                Log.d(this.getClass().getName(), "Finishing upload...");
                // This close is absolutely necessary, this tells the
                // Base64OutputStream to finish writing the last of the data
                // (and including the padding). Without this line, it will miss
                // the last 4 chars in the output, missing up to 3 bytes in the
                // final output.
                bhout.close();
                Log.d(this.getClass().getName(), "Upload complete...");
                mProgressNotification.contentView.setProgressBar(
                    R.id.UploadProgress, totalFileLength, totalRead, false);
            }

            hout.println(boundary);
            hout.flush();
            hrout.close();

            inputStream.close();

            Log.d(this.getClass().getName(), "streams closed, "
                + "now waiting for response from server");

            final BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
            final StringBuilder rData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                rData.append(line).append('\n');
            }

            return rData.toString();
        } catch (final IOException e) {
            Log.e(this.getClass().getName(), "Upload failed", e);
        }

        return null;
    }

    /**
     * This method uploads create a thumbnail for local use from the uri
     * 
     * @param uri
     *            image location
     */
    private void createThumbnail(final Uri uri) {
        Log.i(this.getClass().getName(), "in createThumbnail(Uri)");
        try {
            final Bitmap image = MediaStore.Images.Media.getBitmap(
                getContentResolver(), uri);
            int height = image.getHeight();
            int width = image.getWidth();
            if (width > height) {
                height = height * THUMBNAIL_MAX_SIZE / width;
                width = THUMBNAIL_MAX_SIZE;
            } else {
                width = width * THUMBNAIL_MAX_SIZE / height;
                height = THUMBNAIL_MAX_SIZE;
            }
            final Bitmap resizedBitmap = Bitmap.createScaledBitmap(image,
                width, height, false);

            final FileOutputStream f = openFileOutput(mImgurResponse
                .get("image_hash")
                + THUMBNAIL_POSTFIX, Context.MODE_PRIVATE);

            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 75, f);

        } catch (final IOException e1) {
            Log.e(this.getClass().getName(), "Error creating thumbnail", e1);
        }
    }

    private Map<String, String> parseJSONResponse(final String response) {
        Log.i(this.getClass().getName(), "in parseJSONResponse(String)");
        try {
            Log.d(this.getClass().getName(), response);

            final JSONObject json = new JSONObject(response);
            final JSONObject data = json.getJSONObject("rsp").getJSONObject(
                "image");

            final HashMap<String, String> ret = new HashMap<String, String>();
            ret.put("delete", data.getString("delete_page"));
            ret.put("original", data.getString("original_image"));
            ret.put("delete_hash", data.getString("delete_hash"));
            ret.put("image_hash", data.getString("image_hash"));
            ret.put("small_thumbnail", data.getString("small_thumbnail"));

            return ret;
        } catch (final Exception e) {
            Log.e(this.getClass().getName(),
                "Error parsing response from imgur", e);
        }
        try {
            Log.d(this.getClass().getName(), response);

            final JSONObject json = new JSONObject(response);
            final JSONObject data = json.getJSONObject("rsp");

            final HashMap<String, String> ret = new HashMap<String, String>();
            ret.put("error", data.getString("error_code") + ", "
                + data.getString("stat") + ", " + data.getString("error_msg"));

            return ret;
        } catch (final Exception e) {
            Log.e(this.getClass().getName(), "Error parsing error from imgur",
                e);
        }
        return null;
    }

    @Override
    public IBinder onBind(final Intent arg0) {
        Log.i(this.getClass().getName(), "in onBind(Intent)");
        return null;
    }
}
