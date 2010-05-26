package com.maass.android.imgur_uploader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

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
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.OnItemClickListener;

public class History extends Activity{

	private SQLiteDatabase histDB;
	private int pickedItem;
	private GridView historyGrid;
	private RadioButton currentRadio;
	final int CHOOSE_AN_IMAGE_REQUEST = 2910;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (histDB == null) {
			HistoryDatabase histData = new HistoryDatabase(this);
			histDB = histData.getWritableDatabase();
		}
		
		Cursor vCursor = histDB.query("imgur_history", null, null, null, null, null, null);
		
		if (vCursor.getCount() > 0) {
			setContentView(R.layout.history);
			SimpleCursorAdapter entry = new SimpleCursorAdapter(this, 
					R.layout.history_item, vCursor , new String[] {"local_thumbnail"} , new int[] {R.id.ThumbnailImage}); 
			historyGrid = (GridView) findViewById(R.id.HistoryGridView);
			historyGrid.setAdapter(entry);
			historyGrid.setOnItemClickListener(mMessageClickedHandler);
		} else {
			setContentView(R.layout.info);
		}
	}
	
	//setup menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.options_menu, menu);
	    
	    //display about page
	    menu.findItem(R.id.AboutMenu).setIntent(new Intent(this, LaunchedInfo.class));
	    
	    return true;
	}
	
	//handle menu selections
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case R.id.UploadMenu:
	    	uploadImageFromProvider();
	        return true;
	    }
	    return false;
	}
	
	//send an intent that we want an image
	private void uploadImageFromProvider () {
		Intent intent = new Intent(); 
		intent.setType("image/*"); 
		intent.setAction(Intent.ACTION_PICK);
		startActivityForResult(Intent.createChooser(intent, "Choose a Viewer"), CHOOSE_AN_IMAGE_REQUEST);
	}
	
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			//we got an Image now upload it :D
			if (requestCode == CHOOSE_AN_IMAGE_REQUEST) {
				Uri chosenImageUri = data.getData();
				Intent intent = new Intent(); 
				intent.setData(chosenImageUri); 
				intent.setAction(Intent.ACTION_SEND);
				intent.setClass(this, ImgurUpload.class);
				startActivity(intent);
			}
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (histDB == null) {
			//if the database is closed reopen it
			HistoryDatabase histData = new HistoryDatabase(this);
			histDB = histData.getWritableDatabase();
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		//close database
		histDB.close();
	}
	
	private void refreshImageGrid () {
		Cursor vCursor = histDB.query("imgur_history", null, null, null, null, null, null);
		((SimpleCursorAdapter)historyGrid.getAdapter()).changeCursor(vCursor);
	}
	
	private OnItemClickListener mMessageClickedHandler = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView parent, View v, int position, long id) {
			//remove check from old image
			for (int i=0; i<parent.getChildCount(); i++) {
				((RadioButton) (parent.getChildAt(i).findViewById(R.id.ImageRadio))).setChecked(false);
			}
			
			//set radio on current thumbnail
			RadioButton radio = (RadioButton) v.findViewById(R.id.ImageRadio);
			radio.setChecked(true);
			currentRadio = radio;
			
			Cursor test = (Cursor) parent.getItemAtPosition(position);
			pickedItem = position;
		}
	};
	
	public void deleteClick(View target) {
		//ensure that there are images left
		if (historyGrid.getAdapter().getCount() > 0) {
			Cursor item = (Cursor) historyGrid.getItemAtPosition(pickedItem);
			if (item != null) {
				deleteImage(item.getString(item.getColumnIndex("delete_hash")));
			}
		}
	}
	
	public void viewImage(View target) {
		//ensure that there are images left
		if (historyGrid.getAdapter().getCount() > 0) {
			Cursor item = (Cursor) historyGrid.getItemAtPosition(pickedItem);
			if (item != null) {
				Intent viewIntent = new Intent("android.intent.action.VIEW", Uri.parse("http://www.imgur.com/" + item.getString(item.getColumnIndex("image_hash")) + ".png"));
				startActivity(viewIntent);
			}
		}
	}
	
	public void shareImage(View target) {
		//ensure that there are images left
		if (historyGrid.getAdapter().getCount() > 0) {
			Cursor item = (Cursor) historyGrid.getItemAtPosition(pickedItem);
			if (item != null) {
				Intent shareLinkIntent = new Intent(Intent.ACTION_SEND);
	
				shareLinkIntent.putExtra(Intent.EXTRA_TEXT, "http://www.imgur.com/" + item.getString(item.getColumnIndex("image_hash")) + ".png"
						.toString());
				shareLinkIntent.setType("text/plain");
	
				History.this.startActivity(Intent.createChooser(
						shareLinkIntent, getResources().getString(
								R.string.share_via)));
			}
		}
	}
	
	private void deleteImage(String deleteHash) {
		try {
			HttpURLConnection conn = (HttpURLConnection) (new URL(
			"http://imgur.com/api/delete/" + deleteHash + ".json")).openConnection();
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					conn.getInputStream()));
			StringBuilder rData = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				rData.append(line).append('\n');
			}
			
			JSONObject json = new JSONObject(rData.toString());
			JSONObject data;
			if (json.has("rsp")) {
				data = json.getJSONObject("rsp");
			} else if (json.has("error")){
				data = json.getJSONObject("error");
			} else {
				data = null;
			}
			
			if (data != null) {
				if ((data.has("stat") && data.getString("stat") == "ok") || data.has("error_code") && data.getInt("error_code") == 4002) {
					Log.i("",deleteHash);
					histDB.delete("imgur_history", "delete_hash='" + deleteHash + "'", null);
					refreshImageGrid();
				}
			}
			
		} catch (Exception e) {
			Log.e(this.getClass().getName(), "Delete failed", e);
		}
	}
}
