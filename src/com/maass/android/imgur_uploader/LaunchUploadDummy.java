package com.maass.android.imgur_uploader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class LaunchUploadDummy extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        intent.setClass(context, ImgurUpload.class);
        context.startService(intent);
    }
}
