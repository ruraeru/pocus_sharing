package com.example.pocussharing;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
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
                String nickname = user.getKakaoAccount().getProfile().getNickname();
                String profileImageUrl = user.getKakaoAccount().getProfile().getThumbnailImageUrl();
                String kakaoId = String.valueOf(user.getId());
                firebaseSignInWithKakao(kakaoId, nickname, profileImageUrl);
            }
            return Unit.INSTANCE;
        });

        // 카카오 로그인 버튼 설정
        Button btnKakaoLogin = findViewById(R.id.btn_kakao_login);
        btnKakaoLogin.setOnClickListener(v -> loginWithKakao());
    }

    /**
     * 카카오 로그인을 수행
     */
    private void loginWithKakao() {
        // 로그인 결과 처리를 위한 콜백
        Function2<OAuthToken, Throwable, Unit> callback = (token, error) -> {
            if (error != null) {
                Log.e(TAG, "카카오 로그인 실패", error);
                Toast.makeText(LoginActivity.this, "로그인 실패: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            } else if (token != null) {
                Log.i(TAG, "카카오 로그인 성공");
                fetchKakaoUserInfoAndSignInFirebase();
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
     * 카카오로부터 사용자 정보를 가져와서 Firebase 로그인을 실행함
     */
    private void fetchKakaoUserInfoAndSignInFirebase() {
        UserApiClient.getInstance().me((user, error) -> {
            if (error != null) {
                Log.e(TAG, "카카오 사용자 정보 요청 실패", error);
                Toast.makeText(LoginActivity.this, "사용자 정보 조회 실패", Toast.LENGTH_SHORT).show();
            } else if (user != null) {
                String nickname = user.getKakaoAccount().getProfile().getNickname();
                String profileImageUrl = user.getKakaoAccount().getProfile().getThumbnailImageUrl();
                String kakaoId = String.valueOf(user.getId());
                Log.i(TAG, "사용자 정보 요청 성공. 닉네임: " + nickname);
                firebaseSignInWithKakao(kakaoId, nickname, profileImageUrl);
            }
            return Unit.INSTANCE;
        });
    }

    /**
     * 카카오 ID를 기반으로 Firebase 이메일/비밀번호 로그인을 연동하여
     * 사용자가 기기를 변경하거나 로그아웃 후 다시 로그인하더라도 동일한 UID를 가질 수 있도록 보장함.
     */
    private void firebaseSignInWithKakao(String kakaoId, String nickname, String profileImageUrl) {
        String email = "kakao_" + kakaoId + "@pocussharing.com";
        String password = "kakao_pass_" + kakaoId;

        // 회원 가입을 먼저 시도함 (이미 계정이 있으면 collision 예외가 던져지므로 로그인으로 전환)
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Firebase Kakao 연동 회원가입 및 로그인 성공");
                    syncUserToFirestore(mAuth.getCurrentUser().getUid(), kakaoId, nickname, profileImageUrl);
                } else {
                    Exception exception = task.getException();
                    if (exception instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                        // 이미 가입된 계정이므로 로그인 시도
                        mAuth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener(this, loginTask -> {
                                if (loginTask.isSuccessful()) {
                                    Log.d(TAG, "Firebase Kakao 연동 로그인 성공");
                                    syncUserToFirestore(mAuth.getCurrentUser().getUid(), kakaoId, nickname, profileImageUrl);
                                } else {
                                    Log.e(TAG, "Firebase Kakao 연동 로그인 실패 (collision 후)", loginTask.getException());
                                    signInAnonymouslyFallback(kakaoId, nickname, profileImageUrl);
                                }
                            });
                    } else {
                        Log.e(TAG, "Firebase 회원가입 실패 (기타 에러)", exception);
                        // 기타 에러 시에도 계정이 이미 존재하는지 확인하기 위해 로그인을 한 번 시도함
                        mAuth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener(this, loginTask -> {
                                if (loginTask.isSuccessful()) {
                                    Log.d(TAG, "Firebase Kakao 연동 로그인 성공 (회원가입 기타에러 폴백)");
                                    syncUserToFirestore(mAuth.getCurrentUser().getUid(), kakaoId, nickname, profileImageUrl);
                                } else {
                                    signInAnonymouslyFallback(kakaoId, nickname, profileImageUrl);
                                }
                            });
                    }
                }
            });
    }

    /**
     * 이메일/비밀번호 로그인이 모종의 이유로 실패했을 경우, 서비스 이용이 중단되지 않도록 익명 로그인으로 전환하는 폴백 장치
     */
    private void signInAnonymouslyFallback(String kakaoId, String nickname, String profileImageUrl) {
        mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "익명 로그인 성공 (폴백)");
                syncUserToFirestore(mAuth.getCurrentUser().getUid(), kakaoId, nickname, profileImageUrl);
            } else {
                Log.e(TAG, "익명 로그인 실패 (폴백)", task.getException());
                navigateToMain();
            }
        });
    }

    /**
     * Firebase 인증 UID와 카카오 프로필 데이터를 Firestore 데이터베이스와 연동함.
     * 기존 유저라면 닉네임과 환경설정 데이터를 보존하고 카카오 ID/프로필 이미지만 업데이트함.
     */
    private void syncUserToFirestore(String uid, String kakaoId, String nickname, String profileImageUrl) {
        com.example.pocussharing.repository.FirestoreRepository repo = new com.example.pocussharing.repository.FirestoreRepository();
        repo.getUser(uid).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                // 기존 사용자: 카카오 ID와 프로필 이미지만 업데이트 (닉네임 및 사용자 설정 보존)
                Map<String, Object> updates = new HashMap<>();
                updates.put("kakaoId", kakaoId);
                updates.put("profileImageUrl", profileImageUrl);
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "기존 사용자 정보 업데이트 성공 (닉네임/설정 보존)"))
                    .addOnFailureListener(e -> Log.e(TAG, "기존 사용자 정보 업데이트 실패", e));
            } else {
                // 신규 사용자: 전체 프로필 생성
                com.example.pocussharing.model.User firestoreUser = new com.example.pocussharing.model.User(uid, kakaoId, nickname);
                firestoreUser.setProfileImageUrl(profileImageUrl);
                repo.saveUser(firestoreUser)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "신규 사용자 Firestore 저장 성공"))
                    .addOnFailureListener(e -> Log.e(TAG, "신규 사용자 Firestore 저장 실패", e));
            }
            
            Toast.makeText(this, nickname + "님 환영합니다!", Toast.LENGTH_SHORT).show();
            navigateToMain();
        });
    }

    /**
     * 메인 화면으로 이동
     */
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}