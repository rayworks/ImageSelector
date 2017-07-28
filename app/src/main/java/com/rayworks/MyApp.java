package com.rayworks;

import android.app.Application;

import com.rayworks.imageselector.BuildConfig;

import timber.log.Timber;

/**
 * Created by Sean on 7/28/17.
 */

public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }
}
