package com.example.pocussharing;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.kakao.sdk.auth.model.OAuthToken;
import com.kakao.sdk.common.util.Utility;
import com.kakao.sdk.user.UserApiClient;

import java.util.HashMap;
import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        Log.d("KeyHash", "Current KeyHash: " + Utility.INSTANCE.getKeyHash(this));
        mAuth = FirebaseAuth.getInstance();
        // Auto Login Check
        UserApiClient.getInstance().me((user, error) -> {
            if (user != null) {
                Log.i(TAG, "Auto login success. User nickname: " + user.getKakaoAccount().getProfile().getNickname());
                firebaseSignIn();
            }
            return Unit.INSTANCE;
        });

        ImageButton btnKakaoLogin = findViewById(R.id.btn_kakao_login);
        btnKakaoLogin.setOnClickListener(v -> loginWithKakao());

        findViewById(R.id.btn_bypass_login).setOnClickListener(v -> navigateToMain());
    }

    private void loginWithKakao() {
        // Callback for login
        Function2<OAuthToken, Throwable, Unit> callback = (token, error) -> {
            if (error != null) {
                Log.e(TAG, "Kakao Login failed", error);
                Toast.makeText(LoginActivity.this, "로그인 실패: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            } else if (token != null) {
                Log.i(TAG, "Kakao Login success");
                firebaseSignIn();
            }
            return Unit.INSTANCE;
        };

        // If KakaoTalk is installed, login via KakaoTalk. Otherwise, via KakaoAccount (browser).
        if (UserApiClient.getInstance().isKakaoTalkLoginAvailable(this)) {
            UserApiClient.getInstance().loginWithKakaoTalk(this, callback);
        } else {
            UserApiClient.getInstance().loginWithKakaoAccount(this, callback);
        }
    }

    private void firebaseSignIn() {
        // To satisfy Firestore rules, we sign in to Firebase anonymously
        // In a real app, you would use Kakao's token to sign in to Firebase via a Custom Token
        mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Firebase sign in success");
                fetchUserInfo();
            } else {
                Log.e(TAG, "Firebase sign in failure", task.getException());
                fetchUserInfo(); // Still try to fetch info and navigate
            }
        });
    }

    private void fetchUserInfo() {
        UserApiClient.getInstance().me((user, error) -> {
            if (error != null) {
                Log.e(TAG, "Failed to fetch user info", error);
                navigateToMain(); // Proceed to main even if info fetch fails
            } else if (user != null) {
                String nickname = user.getKakaoAccount().getProfile().getNickname();
                String profileImageUrl = user.getKakaoAccount().getProfile().getThumbnailImageUrl();
                String kakaoId = String.valueOf(user.getId());
                Log.i(TAG, "User info fetch success. Nickname: " + nickname);
                
                // Save/Update user in Firestore only if needed
                if (mAuth.getCurrentUser() != null) {
                    String uid = mAuth.getCurrentUser().getUid();
                    com.example.pocussharing.repository.FirestoreRepository repo = new com.example.pocussharing.repository.FirestoreRepository();
                    
                    repo.getUser(uid).addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                            // User exists, only update profile image and kakaoId, keep current nickname
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("kakaoId", kakaoId);
                            updates.put("profileImageUrl", profileImageUrl);
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users").document(uid)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Existing user updated (preserved nickname)"))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to update existing user", e));
                        } else {
                            // New user, create full profile
                            com.example.pocussharing.model.User firestoreUser = new com.example.pocussharing.model.User(uid, kakaoId, nickname);
                            firestoreUser.setProfileImageUrl(profileImageUrl);
                            repo.saveUser(firestoreUser)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "New user saved to Firestore"))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to save new user", e));
                        }
                    });
                }

                Toast.makeText(this, nickname + "님 환영합니다!", Toast.LENGTH_SHORT).show();
                navigateToMain();
            }
            return Unit.INSTANCE;
        });
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}