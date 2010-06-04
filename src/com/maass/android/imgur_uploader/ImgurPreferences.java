package com.maass.android.imgur_uploader;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class ImgurPreferences extends PreferenceActivity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);
    }
}
