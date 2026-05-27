package com.example.pocussharing;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.pocussharing.repository.FirestoreRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.kakao.sdk.user.UserApiClient;

import java.util.HashMap;
import java.util.Map;

import kotlin.Unit;

/**
 * SettingsFragment: 사용자 프로필 수정 및 앱 설정을 관리하는 프래그먼트
 */
public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";
    private ImageView ivProfile;
    private TextView tvNicknameDisplay;
    private EditText etNickname;
    private Button btnSaveNickname, btnLogout;
    private SwitchCompat switchAlarm, switchScreenOn, switchAppExit;
    private SharedPreferences prefs;
    private FirestoreRepository repository;
    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        mAuth = FirebaseAuth.getInstance();
        repository = new FirestoreRepository();
        // 로컬 설정을 저장하기 위한 SharedPreferences 초기화
        prefs = requireActivity().getSharedPreferences("PocusPrefs", Context.MODE_PRIVATE);

        ivProfile = view.findViewById(R.id.iv_profile);
        tvNicknameDisplay = view.findViewById(R.id.tv_nickname_display);
        etNickname = view.findViewById(R.id.et_nickname);
        btnSaveNickname = view.findViewById(R.id.btn_save_nickname);
        btnLogout = view.findViewById(R.id.btn_logout);
        switchAlarm = view.findViewById(R.id.switch_alarm);
        switchScreenOn = view.findViewById(R.id.switch_screen_on);
        switchAppExit = view.findViewById(R.id.switch_app_exit_prevention);

        // 프로필 및 설정 데이터 로드
        loadUserProfile();
        loadLocalSettings();

        // 이벤트 리스너 설정
        btnSaveNickname.setOnClickListener(v -> saveNickname());
        btnLogout.setOnClickListener(v -> logout());
        
        // 화면 켜짐 유지 스위치 변경 리스너
        switchScreenOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("screen_on", isChecked).apply();
            updateScreenOn(isChecked);
            updateFirestoreSettings("keepScreenOn", isChecked);
        });

        // 알람 무음 스위치 변경 리스너
        switchAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("alarm_off", isChecked).apply();
            updateFirestoreSettings("muteAlarms", isChecked);
        });

        // 앱 종료 방지 스위치 변경 리스너
        switchAppExit.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("app_exit_prevention", isChecked).apply();
            updateFirestoreSettings("preventExit", isChecked);
        });

        return view;
    }

    /**
     * Firestore로부터 사용자의 프로필 정보와 저장된 설정을 불러옵니다.
     */
    private void loadUserProfile() {
        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            repository.getUser(uid).addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String nickname = documentSnapshot.getString("nickname");
                    String profileImageUrl = documentSnapshot.getString("profileImageUrl");
                    
                    tvNicknameDisplay.setText(nickname);
                    etNickname.setText(nickname);
                    
                    if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                        Glide.with(this)
                            .load(profileImageUrl)
                            .circleCrop()
                            .into(ivProfile);
                    }

                    // Firestore에서 설정 정보 로드 및 UI 동기화
                    Map<String, Object> settings = (Map<String, Object>) documentSnapshot.get("settings");
                    if (settings != null) {
                        if (settings.containsKey("muteAlarms")) {
                            boolean val = (boolean) settings.get("muteAlarms");
                            switchAlarm.setChecked(val);
                            prefs.edit().putBoolean("alarm_off", val).apply();
                        }
                        if (settings.containsKey("keepScreenOn")) {
                            boolean val = (boolean) settings.get("keepScreenOn");
                            switchScreenOn.setChecked(val);
                            prefs.edit().putBoolean("screen_on", val).apply();
                            updateScreenOn(val);
                        }
                        if (settings.containsKey("preventExit")) {
                            boolean val = (boolean) settings.get("preventExit");
                            switchAppExit.setChecked(val);
                            prefs.edit().putBoolean("app_exit_prevention", val).apply();
                        }
                    }
                }
            });
        }
    }

    /**
     * 기기 로컬에 저장된 설정을 불러옵니다.
     */
    private void loadLocalSettings() {
        switchAlarm.setChecked(prefs.getBoolean("alarm_off", false));
        switchScreenOn.setChecked(prefs.getBoolean("screen_on", true));
        switchAppExit.setChecked(prefs.getBoolean("app_exit_prevention", false));
        updateScreenOn(switchScreenOn.isChecked());
    }

    /**
     * 입력된 닉네임을 Firestore에 저장합니다.
     */
    private void saveNickname() {
        String nickname = etNickname.getText().toString().trim();
        if (nickname.isEmpty()) return;

        String uid = mAuth.getUid();
        if (uid != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("nickname", nickname);
            
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    tvNicknameDisplay.setText(nickname);
                    Log.d(TAG, "닉네임이 Firestore에 성공적으로 저장됨: " + nickname);
                    Toast.makeText(getContext(), "닉네임이 변경되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "닉네임 Firestore 저장 실패", e);
                    Toast.makeText(getContext(), "저장 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
        }
    }

    /**
     * Firestore에 사용자 개별 설정 변경 사항을 업데이트합니다.
     */
    private void updateFirestoreSettings(String field, boolean value) {
        String uid = mAuth.getUid();
        if (uid != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("settings." + field, value);
            updates.put("settings.updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update(updates);
        }
    }

    /**
     * 카카오 및 Firebase 로그아웃을 수행하고 로그인 화면으로 이동합니다.
     */
    private void logout() {
        // 카카오 로그아웃
        UserApiClient.getInstance().logout(error -> {
            if (error != null) {
                Log.e(TAG, "카카오 로그아웃 실패", error);
            } else {
                Log.i(TAG, "카카오 로그아웃 성공");
            }
            
            // Firebase 로그아웃
            mAuth.signOut();
            
            // 로그인 화면으로 전환
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return Unit.INSTANCE;
        });
    }

    /**
     * 설정에 따라 화면이 항상 켜져 있을지 여부를 결정합니다.
     */
    private void updateScreenOn(boolean keepOn) {
        if (getActivity() != null) {
            if (keepOn) {
                getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    }
}