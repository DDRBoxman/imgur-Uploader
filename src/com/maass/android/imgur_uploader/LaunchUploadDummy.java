package com.maass.android.imgur_uploader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class LaunchUploadDummy extends Activity {

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        Log.i(this.getClass().getName(), "in onCreate(Bundle)");
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        intent.setClass(this, ImgurUpload.class);
        startService(intent);
        finish();
    }

}
