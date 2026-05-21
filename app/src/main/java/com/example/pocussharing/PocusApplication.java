package com.example.pocussharing;

import android.app.Application;
import com.kakao.sdk.common.KakaoSdk;

public class PocusApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize Kakao SDK
        KakaoSdk.init(this, getString(R.string.kakao_app_key));
    }
}