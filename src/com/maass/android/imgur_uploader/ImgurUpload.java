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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

public class ImgurUpload extends Activity {
	private static final int CHUNK_SIZE = 9000;
	private static final int READ_BUFFER_SIZE_BYTES = (3 * CHUNK_SIZE) / 4;
	private static final String API_KEY = "e67bb2d5ceb42e43f8f7fc38e7ca7376";

	private ProgressDialog mDialogWait;
	private Map<String, String> mImgurResponse;

	private TextView mEditURL;
	private TextView mEditDelete;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mEditURL = (TextView) findViewById(R.id.url);
		mEditDelete = (TextView) findViewById(R.id.delete);

		setEventHandlers();

		mDialogWait = new ProgressDialog(this);
		mDialogWait.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mDialogWait.setTitle(R.string.uploading_image);
		mDialogWait.setIcon(R.drawable.icon);
		mDialogWait.show();

		Thread loadWorker = new Thread() {
			public void run() {
				mImgurResponse = handleSendIntent(getIntent());
				handler.sendEmptyMessage(0);
			}
		};

		loadWorker.start();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		mEditURL = (TextView) findViewById(R.id.url);
		mEditDelete = (TextView) findViewById(R.id.delete);
	}

	private void setEventHandlers() {
		// Clicking the url copy button copies the original url
		// to the global clipboard
		findViewById(R.id.copyURL).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
				clipboard.setText(mEditURL.getText());
			}
		});

		// Clicking url share button displays screen to select how
		// to share the image link
		findViewById(R.id.shareURL).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent shareLinkIntent = new Intent(Intent.ACTION_SEND);

				shareLinkIntent.putExtra(Intent.EXTRA_TEXT, mEditURL.getText()
						.toString());
				shareLinkIntent.setType("text/plain");

				ImgurUpload.this.startActivity(Intent.createChooser(
						shareLinkIntent, getResources().getString(
								R.string.share_via)));
			}
		});

		// Clicking the delete copy button copies the delete url
		// to the global clipboard
		findViewById(R.id.copyDelete).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
				clipboard.setText(mEditDelete.getText());
			}
		});

		// Clicking delete share button displays screen to select how
		// to share the delete link
		findViewById(R.id.shareDelete).setOnClickListener(
				new OnClickListener() {
					public void onClick(View v) {
						Intent shareLinkIntent = new Intent(Intent.ACTION_SEND);

						shareLinkIntent.putExtra(Intent.EXTRA_TEXT, mEditDelete
								.getText().toString());
						shareLinkIntent.setType("text/plain");

						ImgurUpload.this.startActivity(Intent.createChooser(
								shareLinkIntent, getResources().getString(
										R.string.share_via)));
					}
				});
	}

	private Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			mDialogWait.dismiss();
			if (mImgurResponse == null) {
				Toast.makeText(ImgurUpload.this,
						getResources().getString(R.string.connection_error),
						Toast.LENGTH_SHORT).show();
			} else if (mImgurResponse.get("error") != null) {
				Toast.makeText(ImgurUpload.this, mImgurResponse.get("error"),
						Toast.LENGTH_SHORT).show();
			} else {
				mEditURL.setText(mImgurResponse.get("original"));
				mEditDelete.setText(mImgurResponse.get("delete"));
			}
		}
	};

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
		Log.d(this.getClass().getName(), intent.toString());
		Bundle extras = intent.getExtras();
		try {
			if (Intent.ACTION_SEND.equals(intent.getAction())
					&& (extras != null)
					&& extras.containsKey(Intent.EXTRA_STREAM)) {

				Uri uri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
				if (uri != null) {
					Log.d(this.getClass().getName(), uri.toString());
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
			mDialogWait.setMax((int) dlen);

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
						if (lastLogTime < (System.currentTimeMillis() - 100)) {
							lastLogTime = System.currentTimeMillis();
							Log.d(this.getClass().getName(), "Loaded "
									+ totalRead + " of " + dlen + " bytes ("
									+ (100 * totalRead) / dlen + "%)");
							mDialogWait.setProgress(totalRead);
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
				mDialogWait.setProgress(totalRead);
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

	private Map<String, String> parseJSONResponse(String response) {
		try {
			Log.d(this.getClass().getName(), response);

			JSONObject json = new JSONObject(response);
			JSONObject data = json.getJSONObject("rsp").getJSONObject("image");

			HashMap<String, String> ret = new HashMap<String, String>();
			ret.put("delete", data.getString("delete_page"));
			ret.put("original", data.getString("original_image"));

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
}