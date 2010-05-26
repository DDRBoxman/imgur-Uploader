package com.maass.android.imgur_uploader;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class History extends Activity{

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		HistoryDatabase histData = new HistoryDatabase(this);
		SQLiteDatabase data = histData.getWritableDatabase();
		Cursor vCursor = data.query("imgur_history", null, null, null, null, null, null);
		
		if (vCursor.getCount() > 0) {
			setContentView(R.layout.history);
			SimpleCursorAdapter entry = new SimpleCursorAdapter(this, 
					R.layout.history_item, vCursor , new String[] {"link", "link_delete"} , new int[] {R.id.ImageLink, R.id.ImageLinkDelete}); 
			ListView historyList = (ListView) findViewById(R.id.HistoryListView);
			historyList.setAdapter(entry);
		} else {
			setContentView(R.layout.info);
		}
	}
	
}
