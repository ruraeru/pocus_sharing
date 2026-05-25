package com.example.pocussharing;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.kakao.sdk.common.KakaoSdk;

public class PocusApplication extends Application implements DefaultLifecycleObserver {
    private static final String TAG = "PocusApplication";
    public static final String ACTION_APP_BACKGROUND = "com.example.pocussharing.ACTION_APP_BACKGROUND";
    public static final String ACTION_APP_FOREGROUND = "com.example.pocussharing.ACTION_APP_FOREGROUND";

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize Kakao SDK only if the key is valid
        String kakaoKey = getString(R.string.kakao_app_key);
        if (!kakaoKey.equals("YOUR_KAKAO_APP_KEY") && !kakaoKey.isEmpty()) {
            KakaoSdk.init(this, kakaoKey);
        }

        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        Log.d(TAG, "App in foreground");
        sendBroadcast(new Intent(ACTION_APP_FOREGROUND));
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        Log.d(TAG, "App in background");
        sendBroadcast(new Intent(ACTION_APP_BACKGROUND));
    }
}