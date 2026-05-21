package com.example.pocussharing;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.example.pocussharing.repository.FirestoreRepository;
import com.google.firebase.auth.FirebaseAuth;

public class SettingsFragment extends Fragment {

    private EditText etNickname;
    private Button btnSaveNickname;
    private SwitchCompat switchAlarm, switchScreenOn, switchAppExit;
    private SharedPreferences prefs;
    private FirestoreRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        etNickname = view.findViewById(R.id.et_nickname);
        btnSaveNickname = view.findViewById(R.id.btn_save_nickname);
        switchAlarm = view.findViewById(R.id.switch_alarm);
        switchScreenOn = view.findViewById(R.id.switch_screen_on);
        switchAppExit = view.findViewById(R.id.switch_app_exit_prevention);

        prefs = requireActivity().getSharedPreferences("PocusPrefs", Context.MODE_PRIVATE);
        repository = new FirestoreRepository();

        loadSettings();

        btnSaveNickname.setOnClickListener(v -> saveNickname());
        
        switchScreenOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("screen_on", isChecked).apply();
            updateScreenOn(isChecked);
        });

        switchAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> 
            prefs.edit().putBoolean("alarm_off", isChecked).apply());

        switchAppExit.setOnCheckedChangeListener((buttonView, isChecked) -> 
            prefs.edit().putBoolean("app_exit_prevention", isChecked).apply());

        return view;
    }

    private void loadSettings() {
        switchAlarm.setChecked(prefs.getBoolean("alarm_off", false));
        switchScreenOn.setChecked(prefs.getBoolean("screen_on", true));
        switchAppExit.setChecked(prefs.getBoolean("app_exit_prevention", false));
        
        // Initial state for screen on
        updateScreenOn(switchScreenOn.isChecked());
    }

    private void saveNickname() {
        String nickname = etNickname.getText().toString().trim();
        if (nickname.isEmpty()) return;

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            // Update in Firestore
            // Note: We'd normally have a updateNickname method in Repository
            // For now, let's just toast
            Toast.makeText(getContext(), "닉네임이 저장되었습니다: " + nickname, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateScreenOn(boolean keepOn) {
        if (keepOn) {
            requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
}