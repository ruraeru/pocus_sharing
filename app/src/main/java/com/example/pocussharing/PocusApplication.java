package com.example.pocussharing;

import android.app.Application;
import com.kakao.sdk.common.KakaoSdk;

public class PocusApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize Kakao SDK only if the key is valid
        String kakaoKey = getString(R.string.kakao_app_key);
        if (!kakaoKey.equals("YOUR_KAKAO_APP_KEY") && !kakaoKey.isEmpty()) {
            KakaoSdk.init(this, kakaoKey);
        }
    }
}