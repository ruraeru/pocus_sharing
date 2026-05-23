package com.example.pocussharing;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.pocussharing.model.TimerLog;
import com.example.pocussharing.repository.FirestoreRepository;
import com.example.pocussharing.repository.RtdbRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private TimerView timerView;
    private TextView tvDigitalTimer;
    private TextView tvDate;
    private FirebaseAuth mAuth;
    private FirestoreRepository repository;
    private RtdbRepository rtdbRepository;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    private long sessionStartTimeMillis;
    private long timeLeft = 25 * 60 * 1000;
    private long totalSessionTime = 25 * 60 * 1000;
    private final long FOCUS_TIME = 25 * 60 * 1000;
    private final long REST_TIME = 5 * 60 * 1000;

    private boolean isRunning = false;
    private boolean isFocusMode = true;
    private View dotFocus, dotRest;
    private LinearLayout llTable;
    private int recordCount = 0; 
    private long totalCumulativeMillis = 0;

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
        llTable = view.findViewById(R.id.ll_table);
        dotFocus = view.findViewById(R.id.dot_focus);
        dotRest = view.findViewById(R.id.dot_rest);

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
        
        dotFocus.setOnClickListener(v -> setMode(true));
        dotRest.setOnClickListener(v -> setMode(false));

        updateUI(totalSessionTime);
        loadTodayStats();
        
        return view;
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
    }

    private void updateUI(long millis) {
        float progress = (float) millis / (60 * 60 * 1000); 
        timerView.setProgress(progress);
        
        long displayMillis = totalCumulativeMillis;
        if (isRunning) {
            displayMillis += (totalSessionTime - timeLeft);
            syncStatusToRtdb();
        }
        updateDigitalTimer(displayMillis);
    }

    private void syncStatusToRtdb() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        // Fixed group for now
        rtdbRepository.updateUserStatus("main_group", uid, isFocusMode, timeLeft);
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
            totalCumulativeMillis += elapsed;
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
        recordCount++;
        
        long currentSessionElapsed = totalSessionTime - timeLeft;
        if (currentSessionElapsed <= 0) return;

        int seconds = (int) (currentSessionElapsed / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        seconds = seconds % 60;
        minutes = minutes % 60;

        String timeStr;
        if (hours > 0) {
            timeStr = String.format(Locale.getDefault(), "%d시간 %d분 %d초", hours, minutes, seconds);
        } else if (minutes > 0) {
            timeStr = String.format(Locale.getDefault(), "%d분 %d초", minutes, seconds);
        } else {
            timeStr = String.format(Locale.getDefault(), "%d초", seconds);
        }
        
        String typeStr = isFocusMode ? "집중" : "휴식";

        View row = getLayoutInflater().inflate(R.layout.table_row, llTable, false);
        ((TextView) row.findViewById(R.id.tv_no)).setText(String.valueOf(recordCount));
        ((TextView) row.findViewById(R.id.tv_time)).setText(timeStr);
        ((TextView) row.findViewById(R.id.tv_type)).setText(typeStr);
        
        llTable.addView(row, 1);

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
            .addOnSuccessListener(aVoid -> Log.d("Firebase", "Timer log and stats updated!"))
            .addOnFailureListener(e -> Log.e("Firebase", "Failed to save log", e));
    }

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            timeLeft -= 1000;
            if (timeLeft <= 0) {
                timeLeft = 0;
                totalCumulativeMillis += totalSessionTime;
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
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(timerRunnable);
    }
}