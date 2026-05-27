package com.example.pocussharing;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.kakao.sdk.auth.model.OAuthToken;
import com.kakao.sdk.user.UserApiClient;

import java.util.HashMap;
import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;

/**
 * LoginActivity: 카카오 로그인 및 Firebase 익명 인증을 처리하는 액티비티
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // 자동 로그인 확인: 카카오 SDK를 통해 현재 로그인된 사용자가 있는지 확인
        UserApiClient.getInstance().me((user, error) -> {
            if (user != null) {
                Log.i(TAG, "자동 로그인 성공. 사용자 닉네임: " + user.getKakaoAccount().getProfile().getNickname());
                firebaseSignIn();
            }
            return Unit.INSTANCE;
        });

        // 카카오 로그인 버튼 설정
        ImageButton btnKakaoLogin = findViewById(R.id.btn_kakao_login);
        btnKakaoLogin.setOnClickListener(v -> loginWithKakao());
    }

    /**
     * 카카오 로그인을 수행합니다.
     */
    private void loginWithKakao() {
        // 로그인 결과 처리를 위한 콜백
        Function2<OAuthToken, Throwable, Unit> callback = (token, error) -> {
            if (error != null) {
                Log.e(TAG, "카카오 로그인 실패", error);
                Toast.makeText(LoginActivity.this, "로그인 실패: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            } else if (token != null) {
                Log.i(TAG, "카카오 로그인 성공");
                firebaseSignIn();
            }
            return Unit.INSTANCE;
        };

        // 카카오톡이 설치되어 있으면 카카오톡으로 로그인, 아니면 카카오 계정(웹 브라우저)으로 로그인
        if (UserApiClient.getInstance().isKakaoTalkLoginAvailable(this)) {
            UserApiClient.getInstance().loginWithKakaoTalk(this, callback);
        } else {
            UserApiClient.getInstance().loginWithKakaoAccount(this, callback);
        }
    }

    /**
     * Firestore 보안 규칙을 충족하기 위해 Firebase 익명 인증을 수행합니다.
     */
    private void firebaseSignIn() {
        // 실제 앱에서는 카카오 토큰을 사용하여 커스텀 토큰 방식으로 로그인하는 것이 좋으나,
        // 여기서는 간단하게 익명 인증을 사용합니다.
        mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Firebase 익명 로그인 성공");
                fetchUserInfo();
            } else {
                Log.e(TAG, "Firebase 익명 로그인 실패", task.getException());
                fetchUserInfo(); // 정보 조회 및 메인 이동을 위해 계속 진행
            }
        });
    }

    /**
     * 카카오로부터 사용자 정보를 가져오고 Firestore에 동기화합니다.
     */
    private void fetchUserInfo() {
        UserApiClient.getInstance().me((user, error) -> {
            if (error != null) {
                Log.e(TAG, "사용자 정보 요청 실패", error);
                navigateToMain(); // 정보 요청에 실패해도 일단 메인으로 이동
            } else if (user != null) {
                String nickname = user.getKakaoAccount().getProfile().getNickname();
                String profileImageUrl = user.getKakaoAccount().getProfile().getThumbnailImageUrl();
                String kakaoId = String.valueOf(user.getId());
                Log.i(TAG, "사용자 정보 요청 성공. 닉네임: " + nickname);
                
                // Firebase 인증 사용자가 있는 경우 Firestore에 사용자 정보 저장 또는 업데이트
                if (mAuth.getCurrentUser() != null) {
                    String uid = mAuth.getCurrentUser().getUid();
                    com.example.pocussharing.repository.FirestoreRepository repo = new com.example.pocussharing.repository.FirestoreRepository();
                    
                    repo.getUser(uid).addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                            // 기존 사용자: 카카오 ID와 프로필 이미지만 업데이트 (닉네임은 사용자가 변경했을 수 있으므로 유지)
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("kakaoId", kakaoId);
                            updates.put("profileImageUrl", profileImageUrl);
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users").document(uid)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "기존 사용자 정보 업데이트 성공 (닉네임 보존)"))
                                .addOnFailureListener(e -> Log.e(TAG, "기존 사용자 정보 업데이트 실패", e));
                        } else {
                            // 신규 사용자: 전체 프로필 생성
                            com.example.pocussharing.model.User firestoreUser = new com.example.pocussharing.model.User(uid, kakaoId, nickname);
                            firestoreUser.setProfileImageUrl(profileImageUrl);
                            repo.saveUser(firestoreUser)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "신규 사용자 Firestore 저장 성공"))
                                .addOnFailureListener(e -> Log.e(TAG, "신규 사용자 Firestore 저장 실패", e));
                        }
                    });
                }

                Toast.makeText(this, nickname + "님 환영합니다!", Toast.LENGTH_SHORT).show();
                navigateToMain();
            }
            return Unit.INSTANCE;
        });
    }

    /**
     * 메인 화면으로 이동합니다.
     */
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}