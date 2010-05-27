package com.maass.android.imgur_uploader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;


public class LaunchUploadDummy extends Activity{

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();
		intent.setClass(this,ImgurUpload.class);
		startService(intent);
		finish();
	}
	
}
