package com.example.pocussharing;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.pocussharing.model.TimerLog;
import com.example.pocussharing.repository.FirestoreRepository;
import com.example.pocussharing.repository.RtdbRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private TimerView timerView;
    private TextView tvDigitalTimer;
    private TextView tvDate;
    private ImageView ivProfile;
    private FirebaseAuth mAuth;
    private FirestoreRepository repository;
    private RtdbRepository rtdbRepository;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ListenerRegistration logsListener;
    
    private long sessionStartTimeMillis;
    private long timeLeft = 25 * 60 * 1000;
    private long totalSessionTime = 25 * 60 * 1000;
    private final long FOCUS_TIME = 25 * 60 * 1000;
    private final long REST_TIME = 5 * 60 * 1000;

    private boolean isRunning = false;
    private boolean isFocusMode = true;
    private android.widget.RadioGroup rgStatus;
    private android.widget.RadioButton rbFocus, rbRest;
    private LinearLayout llTable;
    private int recordCount = 0; 
    private long totalCumulativeMillis = 0;
    private String userNickname = "GUEST";
    private List<String> userGroupIds = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        mAuth = FirebaseAuth.getInstance();
        repository = new FirestoreRepository();
        rtdbRepository = new RtdbRepository();
        
        timerView = view.findViewById(R.id.timer_view);
        tvDigitalTimer = view.findViewById(R.id.tv_digital_timer);
        tvDate = view.findViewById(R.id.tv_date);
        ivProfile = view.findViewById(R.id.iv_profile);
        llTable = view.findViewById(R.id.ll_table);
        rgStatus = view.findViewById(R.id.rg_status);
        rbFocus = view.findViewById(R.id.rb_focus);
        rbRest = view.findViewById(R.id.rb_rest);

        // Set date
        String dateStr = new java.text.SimpleDateFormat("M월 d일", Locale.KOREA).format(new Date());
        tvDate.setText(dateStr);

        timerView.setOnTimerDialListener(new TimerView.OnTimerDialListener() {
            @Override
            public void onDialChanged(float progress) {
                if (isRunning) stopTimer();
                long newTime = (long) (progress * 60 * 60 * 1000);
                timeLeft = newTime;
                totalSessionTime = newTime;
                updateDigitalTimer(timeLeft);
            }

            @Override
            public void onDialSelected(float progress) {
                toggleTimer();
            }
        });
        
        rgStatus.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_focus) {
                if (!isFocusMode) setMode(true);
            } else if (checkedId == R.id.rb_rest) {
                if (isFocusMode) setMode(false);
            }
        });

        updateUI(totalSessionTime);
        loadUserProfile();
        loadTodayStats();
        setupLogsListener();
        
        return view;
    }

    private void setupLogsListener() {
        if (mAuth.getCurrentUser() == null) return;
        
        String uid = mAuth.getCurrentUser().getUid();
        if (logsListener != null) logsListener.remove();

        logsListener = repository.getTimerLogsListener(uid, (value, error) -> {
            if (error != null) {
                Log.e("HomeFragment", "Logs listener failed", error);
                return;
            }
            if (value != null) {
                Log.d("HomeFragment", "Real-time logs update. Count: " + value.size());
                updateLogsTable(value.getDocuments());
            }
        });
    }

    private void updateLogsTable(List<com.google.firebase.firestore.DocumentSnapshot> docs) {
        // Clear current table
        llTable.removeAllViews();
        recordCount = 0;

        List<com.google.firebase.firestore.DocumentSnapshot> mutableDocs = new ArrayList<>(docs);
        // Sort in Java: oldest to newest (to add to table at index 0, effectively newest at top)
        Collections.sort(mutableDocs, (d1, d2) -> {
            com.google.firebase.Timestamp t1 = d1.getTimestamp("createdAt");
            com.google.firebase.Timestamp t2 = d2.getTimestamp("createdAt");
            if (t1 == null || t2 == null) return 0;
            return t1.compareTo(t2);
        });

        for (com.google.firebase.firestore.DocumentSnapshot doc : mutableDocs) {
            TimerLog log = doc.toObject(TimerLog.class);
            if (log != null) {
                addLogToTableUI(log);
            }
        }
        
        if (mutableDocs.isEmpty()) {
            Log.d("HomeFragment", "No logs found in Firestore.");
        }
    }

    private void addLogToTableUI(TimerLog log) {
        recordCount++;
        int durationSec = log.getDurationSeconds();
        int seconds = durationSec % 60;
        int minutes = (durationSec / 60) % 60;
        int hours = durationSec / 3600;

        String timeStr;
        if (hours > 0) {
            timeStr = String.format(Locale.getDefault(), "%d시간 %d분 %d초", hours, minutes, seconds);
        } else if (minutes > 0) {
            timeStr = String.format(Locale.getDefault(), "%d분 %d초", minutes, seconds);
        } else {
            timeStr = String.format(Locale.getDefault(), "%d초", seconds);
        }

        String typeStr = log.getLogType().equals("FOCUS") ? "집중" : "휴식";

        View row = getLayoutInflater().inflate(R.layout.table_row, llTable, false);
        ((TextView) row.findViewById(R.id.tv_no)).setText(String.valueOf(recordCount));
        ((TextView) row.findViewById(R.id.tv_time)).setText(timeStr);
        ((TextView) row.findViewById(R.id.tv_type)).setText(typeStr);

        llTable.addView(row, 0);
    }

    private void loadUserProfile() {
        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            repository.getUser(uid).addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    userNickname = documentSnapshot.getString("nickname");
                    if (userNickname == null) userNickname = "GUEST";

                    String profileImageUrl = documentSnapshot.getString("profileImageUrl");
                    if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                        Glide.with(this)
                            .load(profileImageUrl)
                            .circleCrop()
                            .into(ivProfile);
                    }
                }
            });

            // Load user's groups for real-time status sync
            repository.getUserGroups(uid).addOnSuccessListener(queryDocumentSnapshots -> {
                userGroupIds.clear();
                for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                    userGroupIds.add(doc.getId());
                }
            });
        }
    }

    private void loadTodayStats() {
        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            repository.getDailyFocusTime(uid).addOnSuccessListener(focusSec -> {
                totalCumulativeMillis = focusSec * 1000L;
                updateUI(timeLeft);
            }).addOnFailureListener(e -> {
                Log.e("HomeFragment", "Failed to load daily stats", e);
            });
        }
    }

    private void setMode(boolean isFocus) {
        if (isRunning) {
            stopTimer();
            addRecordToTable();
        }
        isFocusMode = isFocus;
        timerView.setMode(isFocus);
        totalSessionTime = isFocus ? FOCUS_TIME : REST_TIME;
        timeLeft = totalSessionTime;
        updateUI(timeLeft);

        if (isFocus) {
            rbFocus.setChecked(true);
        } else {
            rbRest.setChecked(true);
        }
    }

    private void updateUI(long millis) {
        float progress = (float) millis / (60 * 60 * 1000); 
        timerView.setProgress(progress);
        
        if (isRunning) {
            syncStatusToRtdb();
        }
        updateDigitalTimer(millis);
    }

    private void syncStatusToRtdb() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        
        long totalTodayFocus = totalCumulativeMillis;
        if (isRunning && isFocusMode) {
            totalTodayFocus += (totalSessionTime - timeLeft);
        }

        // Sync to all groups the user belongs to
        if (userGroupIds.isEmpty()) {
            rtdbRepository.updateUserStatus("main_group", uid, userNickname, isFocusMode, timeLeft, totalTodayFocus);
        } else {
            for (String gid : userGroupIds) {
                rtdbRepository.updateUserStatus(gid, uid, userNickname, isFocusMode, timeLeft, totalTodayFocus);
            }
        }
    }

    private void updateDigitalTimer(long millis) {
        int seconds = (int) (millis / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        seconds = seconds % 60;
        minutes = minutes % 60;
        tvDigitalTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
    }

    private void toggleTimer() {
        if (isRunning) {
            long elapsed = totalSessionTime - timeLeft;
            if (isFocusMode) {
                totalCumulativeMillis += elapsed;
            }
            stopTimer();
            addRecordToTable();
        } else {
            if (timeLeft > 0) {
                startTimer();
            }
        }
    }

    private void stopTimer() {
        isRunning = false;
        handler.removeCallbacks(timerRunnable);
        updateUI(timeLeft);
    }

    private void startTimer() {
        if (!isRunning) {
            isRunning = true;
            sessionStartTimeMillis = System.currentTimeMillis();
            handler.postDelayed(timerRunnable, 1000);
        }
    }

    private void addRecordToTable() {
        long currentSessionElapsed = totalSessionTime - timeLeft;
        if (currentSessionElapsed <= 0) return;

        saveLogToFirebase(currentSessionElapsed);
    }

    private void saveLogToFirebase(long durationMillis) {
        if (mAuth.getCurrentUser() == null) return;

        String uid = mAuth.getCurrentUser().getUid();
        String logType = isFocusMode ? "FOCUS" : "REST";
        int durationSec = (int) (durationMillis / 1000);
        
        if (durationSec <= 0) return;

        TimerLog log = new TimerLog(
            uid,
            logType,
            durationSec,
            new Timestamp(new Date(sessionStartTimeMillis)),
            Timestamp.now()
        );

        repository.saveTimerLog(log)
            .addOnSuccessListener(aVoid -> {
                Log.d("Firebase", "Timer log and stats updated!");
                // Table will be refreshed automatically by snapshots listener
            })
            .addOnFailureListener(e -> Log.e("Firebase", "Failed to save log", e));
    }

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            timeLeft -= 1000;
            if (timeLeft <= 0) {
                timeLeft = 0;
                if (isFocusMode) {
                    totalCumulativeMillis += totalSessionTime;
                }
                updateUI(timeLeft);
                stopTimer();
                addRecordToTable();
                return;
            }

            updateUI(timeLeft);
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        loadUserProfile();
        loadTodayStats();
        setupLogsListener();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(timerRunnable);
        if (logsListener != null) {
            logsListener.remove();
        }
    }
}