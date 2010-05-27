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
import java.io.FileNotFoundException;
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
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class ImgurUpload extends Service {
	private static final int PROGRESS_UPDATE_INTERVAL_MS = 250;
	private static final int CHUNK_SIZE = 9000;
	private static final int READ_BUFFER_SIZE_BYTES = (3 * CHUNK_SIZE) / 4;
	private static final String API_KEY = "e67bb2d5ceb42e43f8f7fc38e7ca7376";

	private Notification mProgressNotification;
	private static final int NOTIFICATION_ID = 999;
	private NotificationManager mNotificationManager;
	
	private Map<String, String> mImgurResponse;
	private Uri imageLocation;

	@Override
	public void onStart(Intent intent, int startId) {
		passIntent(intent);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		passIntent(intent);
	    // We want this service to continue running until it is explicitly
	    // stopped, so return sticky.
	    return START_NOT_STICKY;
	}
	
	private void passIntent(Intent intent) {
		mImgurResponse = handleSendIntent(intent);
		handleResponse();
	}
	

	private void handleResponse() {
		
		//close progress notification
		mNotificationManager.cancel(NOTIFICATION_ID);
		
		String notificationMessage = getString(R.string.upload_success);
		
		if (mImgurResponse == null) {
			notificationMessage = getString(R.string.connection_failed);
		} else if (mImgurResponse.get("error") != null) {
			notificationMessage = getString(R.string.unknown_error) + mImgurResponse.get("error");
		} else {				
			//create thumbnail
			if (mImgurResponse.get("image_hash").length() > 0) {
				createThumbnail(imageLocation);
			}
			
			//store result in database
			HistoryDatabase histData = new HistoryDatabase(getBaseContext());
			SQLiteDatabase data = histData.getWritableDatabase();
			ContentValues content = new ContentValues();
			content.put("image_hash", mImgurResponse.get("image_hash"));
			content.put("delete_hash", mImgurResponse.get("delete_hash"));
			content.put("image_url", mImgurResponse.get("original_image"));
			Uri imageUri = Uri.parse(getFilesDir() + "/" + mImgurResponse.get("image_hash") + "s.png");
			content.put("local_thumbnail", imageUri.toString());
			data.insert("imgur_history",null,content);
			data.close();
			histData.close();
			
		}
		
		//notification with result
		Intent mtActivity = new Intent(this,History.class);
		Notification notification = new Notification(R.drawable.icon, notificationMessage, System.currentTimeMillis());
	    notification.setLatestEventInfo(this,getString(R.string.app_name),notificationMessage,PendingIntent.getActivity(this.getBaseContext(), 0, mtActivity,PendingIntent.FLAG_CANCEL_CURRENT));
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
	private Map<String, String> handleSendIntent(Intent intent) {
		
		String ns = Context.NOTIFICATION_SERVICE;
		mNotificationManager = (NotificationManager) getSystemService(ns);
		
		Log.d(this.getClass().getName(), intent.toString());
		Bundle extras = intent.getExtras();
		try {
			if (Intent.ACTION_SEND.equals(intent.getAction())
					&& (extras != null)
					&& extras.containsKey(Intent.EXTRA_STREAM)) {

				Uri uri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
				if (uri != null) {
					Log.d(this.getClass().getName(), uri.toString());
					//store uri so we can create the thumbnail if we succeed
					imageLocation = uri;
					final String jsonOutput = readPictureDataAndUpload(uri);
					return parseJSONResponse(jsonOutput);
				}
				Log.e(this.getClass().getName(), "URI null");
			}
		} catch (Exception e) {
			Log.e(this.getClass().getName(), "Completely unexpected error", e);
		}
		return null;
	}

	/**
	 * This method uploads an image from the given uri. It does the uploading in
	 * small chunks to make sure it doesn't over run the memory.
	 * 
	 * @param uri
	 *            image location
	 * @return map containing data from interaction
	 */
	private String readPictureDataAndUpload(Uri uri) {
		try {
			final AssetFileDescriptor afd = this.getContentResolver()
					.openAssetFileDescriptor(uri, "r");
			final long dlen = afd.getLength();
			afd.close();
			
			//Create custom progress notification
			mProgressNotification = new Notification();
			//set as ongoing
			mProgressNotification.flags |= Notification.FLAG_ONGOING_EVENT;
			//set custom view to notification
			RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notification_layout_upload);
			contentView.setImageViewResource(R.id.image, R.drawable.icon);
			contentView.setTextViewText(R.id.text, getString(R.string.upload_in_progress));
			contentView.setProgressBar(R.id.UploadProgress, (int) dlen, 0, false);
			mProgressNotification.contentView = contentView;
			//add notification to manager
			mNotificationManager.notify(NOTIFICATION_ID, mProgressNotification);

			InputStream inputStream = this.getContentResolver()
					.openInputStream(uri);

			final String boundaryString = "Z."
					+ Long.toHexString(System.currentTimeMillis())
					+ Long.toHexString((new Random()).nextLong());
			final String boundary = "--" + boundaryString;
			HttpURLConnection conn = (HttpURLConnection) (new URL(
					"http://imgur.com/api/upload.json")).openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-type",
					"multipart/form-data; boundary=\"" + boundaryString + "\"");
			conn.setUseCaches(false);
			conn.setDoInput(true);
			conn.setDoOutput(true);
			conn.setChunkedStreamingMode(CHUNK_SIZE);
			OutputStream hrout = conn.getOutputStream();
			PrintStream hout = new PrintStream(hrout);
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
				Base64OutputStream bhout = new Base64OutputStream(hrout);
				byte[] pictureData = new byte[READ_BUFFER_SIZE_BYTES];
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
							Log.d(this.getClass().getName(), "Loaded "
									+ totalRead + " of " + dlen + " bytes ("
									+ (100 * totalRead) / dlen + "%)");
							contentView.setProgressBar(R.id.UploadProgress, (int) dlen, totalRead, false);
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
				contentView.setProgressBar(R.id.UploadProgress, (int) dlen, totalRead, false);
			}

			hout.println(boundary);
			hout.flush();
			hrout.close();

			inputStream.close();

			Log.d(this.getClass().getName(), "streams closed, "
					+ "now waiting for response from server");

			BufferedReader reader = new BufferedReader(new InputStreamReader(
					conn.getInputStream()));
			StringBuilder rData = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				rData.append(line).append('\n');
			}

			return rData.toString();
		} catch (IOException e) {
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
	private void createThumbnail(Uri uri) {
		Bitmap bitmapOrg;
		try {
			bitmapOrg = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
			Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmapOrg, 200, 200, false);
	        
	        FileOutputStream f = openFileOutput(mImgurResponse.get("image_hash") + "s.png", Context.MODE_PRIVATE);
	
	        resizedBitmap.compress(Bitmap.CompressFormat.PNG, 90, f);
        
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	private Map<String, String> parseJSONResponse(String response) {
		try {
			Log.d(this.getClass().getName(), response);

			JSONObject json = new JSONObject(response);
			JSONObject data = json.getJSONObject("rsp").getJSONObject("image");
			
			HashMap<String, String> ret = new HashMap<String, String>();
			ret.put("delete", data.getString("delete_page"));
			ret.put("original", data.getString("original_image"));
			ret.put("delete_hash", data.getString("delete_hash"));
			ret.put("image_hash", data.getString("image_hash"));
			ret.put("small_thumbnail", data.getString("small_thumbnail"));

			return ret;
		} catch (Exception e) {
			Log.e(this.getClass().getName(),
					"Error parsing response from imgur", e);
		}
		try {
			Log.d(this.getClass().getName(), response);

			JSONObject json = new JSONObject(response);
			JSONObject data = json.getJSONObject("rsp");

			HashMap<String, String> ret = new HashMap<String, String>();
			ret.put("error", data.getString("error_code") + ", "
					+ data.getString("stat") + ", "
					+ data.getString("error_msg"));

			return ret;
		} catch (Exception e) {
			Log.e(this.getClass().getName(), "Error parsing error from imgur",
					e);
		}
		return null;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
}