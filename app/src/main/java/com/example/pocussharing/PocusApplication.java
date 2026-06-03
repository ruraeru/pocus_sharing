package com.example.pocussharing;

import android.app.Application;
import com.kakao.sdk.common.KakaoSdk;

public class PocusApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 카카오 SDK 초기화: strings.xml에 정의된 카카오 앱 키를 사용
        String kakaoKey = getString(R.string.kakao_app_key);
        if (!kakaoKey.isEmpty()) {
            KakaoSdk.init(this, kakaoKey);
        }
    }
}