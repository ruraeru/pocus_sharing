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
        prefs = requireActivity().getSharedPreferences("PocusPrefs", Context.MODE_PRIVATE);

        ivProfile = view.findViewById(R.id.iv_profile);
        tvNicknameDisplay = view.findViewById(R.id.tv_nickname_display);
        etNickname = view.findViewById(R.id.et_nickname);
        btnSaveNickname = view.findViewById(R.id.btn_save_nickname);
        btnLogout = view.findViewById(R.id.btn_logout);
        switchAlarm = view.findViewById(R.id.switch_alarm);
        switchScreenOn = view.findViewById(R.id.switch_screen_on);
        switchAppExit = view.findViewById(R.id.switch_app_exit_prevention);

        loadUserProfile();
        loadLocalSettings();

        btnSaveNickname.setOnClickListener(v -> saveNickname());
        btnLogout.setOnClickListener(v -> logout());
        
        switchScreenOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("screen_on", isChecked).apply();
            updateScreenOn(isChecked);
            updateFirestoreSettings("keepScreenOn", isChecked);
        });

        switchAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("alarm_off", isChecked).apply();
            updateFirestoreSettings("muteAlarms", isChecked);
        });

        switchAppExit.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("app_exit_prevention", isChecked).apply();
            updateFirestoreSettings("preventExit", isChecked);
        });

        return view;
    }

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

                    // Load settings from Firestore
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

    private void loadLocalSettings() {
        switchAlarm.setChecked(prefs.getBoolean("alarm_off", false));
        switchScreenOn.setChecked(prefs.getBoolean("screen_on", true));
        switchAppExit.setChecked(prefs.getBoolean("app_exit_prevention", false));
        updateScreenOn(switchScreenOn.isChecked());
    }

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
                    Log.d(TAG, "Nickname successfully set in Firestore: " + nickname);
                    Toast.makeText(getContext(), "닉네임이 변경되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to set nickname in Firestore", e);
                    Toast.makeText(getContext(), "저장 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
        }
    }

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

    private void logout() {
        // Kakao Logout
        UserApiClient.getInstance().logout(error -> {
            if (error != null) {
                Log.e(TAG, "Kakao logout failed", error);
            } else {
                Log.i(TAG, "Kakao logout success");
            }
            
            // Firebase Logout
            mAuth.signOut();
            
            // Navigate to LoginActivity
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return Unit.INSTANCE;
        });
    }

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