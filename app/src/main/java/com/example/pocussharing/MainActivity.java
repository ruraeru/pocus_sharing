package com.example.pocussharing;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.Timestamp;
import com.example.pocussharing.repository.FirestoreRepository;
import com.example.pocussharing.model.*;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private TimerView timerView;
    private TextView tvDigitalTimer;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirestoreRepository repository;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    private long sessionStartTimeMillis;
    private long timeLeft = 25 * 60 * 1000; // Default 25 min for Focus
    private long totalSessionTime = 25 * 60 * 1000;
    private final long FOCUS_TIME = 25 * 60 * 1000;
    private final long REST_TIME = 5 * 60 * 1000;

    private boolean isRunning = false;
    private boolean isFocusMode = true;
    private View dotFocus, dotRest;
    private LinearLayout llTable;
    private int recordCount = 3; 
    private long totalCumulativeMillis = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        repository = new FirestoreRepository();
        signInAnonymously();
        
        timerView = findViewById(R.id.timer_view);
        tvDigitalTimer = findViewById(R.id.tv_digital_timer);
        llTable = findViewById(R.id.ll_table);
        
        View root = findViewById(R.id.main);
        dotFocus = root.findViewWithTag("dot_focus");
        dotRest = root.findViewWithTag("dot_rest");

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Dial Interaction
        timerView.setOnTimerDialListener(new TimerView.OnTimerDialListener() {
            @Override
            public void onDialChanged(float progress) {
                if (isRunning) stopTimer();
                // Map progress (0-1) to 60 minutes
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
    }

    private void signInAnonymously() {
        mAuth.signInAnonymously()
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    Log.d("FirebaseTest", "signInAnonymously:success");
                    ensureUserProfile();
                    checkFirebaseConnection();
                } else {
                    Log.w("FirebaseTest", "signInAnonymously:failure", task.getException());
                }
            });
    }

    private void ensureUserProfile() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        repository.getUser(uid).addOnSuccessListener(documentSnapshot -> {
            if (!documentSnapshot.exists()) {
                User newUser = new User(uid, "anon_" + uid.substring(0, 5), "GUEST_" + uid.substring(0, 4));
                repository.saveUser(newUser);
            }
        });
    }

    private void initializeFirestoreTables() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        Log.d("Firebase", "Initializing 'tables' (collections) in Firestore...");

        // 1. users & user_settings (Integrated)
        ensureUserProfile();

        // 2. groups & group_members (Initial Dummy Group)
        Group initGroup = new Group("초기 시스템 그룹", "INIT001", uid);
        db.collection("groups").document("initial_group_id").set(initGroup)
            .addOnSuccessListener(aVoid -> {
                GroupMember admin = new GroupMember(uid, "ADMIN");
                db.collection("groups").document("initial_group_id")
                    .collection("members").document(uid).set(admin);
            });

        // 3. timer_logs (Initial Dummy Log)
        TimerLog initLog = new TimerLog(uid, "FOCUS", 0, Timestamp.now(), Timestamp.now());
        db.collection("timer_logs").document("initial_log_id").set(initLog);

        // 4. daily_stats (User Subcollection)
        Map<String, Object> initStat = new HashMap<>();
        initStat.put("totalFocusSec", 0);
        initStat.put("totalRestSec", 0);
        initStat.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("users").document(uid).collection("daily_stats").document("2026-05-12").set(initStat);

        Log.d("Firebase", "모든 컬렉션 생성 신호를 보냈습니다. 이제 Firebase Console에 나타납니다.");
    }

    private void checkFirebaseConnection() {
        initializeFirestoreTables(); // 모든 테이블 강제 생성

        Map<String, Object> testData = new HashMap<>();
        testData.put("last_connected", com.google.firebase.Timestamp.now());

        db.collection("connection_test").document("status")
            .set(testData)
            .addOnSuccessListener(aVoid -> Log.d("FirebaseTest", "Connection Successful!"))
            .addOnFailureListener(e -> Log.e("FirebaseTest", "Connection Failed: " + e.getMessage()));
    }
    private void setMode(boolean isFocus) {
        if (isRunning) {
            stopTimer();
            addRecordToTable(totalSessionTime - timeLeft);
        }
        isFocusMode = isFocus;
        timerView.setMode(isFocus);
        totalSessionTime = isFocus ? FOCUS_TIME : REST_TIME;
        timeLeft = totalSessionTime;
        updateUI(timeLeft);
    }

    private void updateUI(long millis) {
        // 아날로그 다이얼은 여전히 남은 시간(timeLeft)을 시각적으로 보여줍니다.
        float progress = (float) millis / (60 * 60 * 1000); 
        timerView.setProgress(progress);
        
        // 디지털 타이머는 누적 시간을 보여줍니다.
        long displayMillis = totalCumulativeMillis;
        if (isRunning) {
            displayMillis += (totalSessionTime - timeLeft);
        }
        updateDigitalTimer(displayMillis);
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
            addRecordToTable(0); // 0을 전달해도 내부에서 totalCumulativeMillis를 사용하도록 수정 필요
        } else {
            if (timeLeft > 0) {
                startTimer();
            }
        }
    }

    private void stopTimer() {
        isRunning = false;
        handler.removeCallbacks(timerRunnable);
        updateUI(timeLeft); // 멈췄을 때 최종 누적 시간 표시
    }

    private void startTimer() {
        if (!isRunning) {
            isRunning = true;
            sessionStartTimeMillis = System.currentTimeMillis();
            handler.postDelayed(timerRunnable, 1000);
        }
    }

    private void addRecordToTable(long unused) {
        // 이미 totalCumulativeMillis에 더해진 상태이므로 매개변수는 무시하거나 로직을 정리합니다.
        recordCount++;
        
        int seconds = (int) (totalCumulativeMillis / 1000);
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

        saveLogToFirebase();
    }

    private void saveLogToFirebase() {
        if (mAuth.getCurrentUser() == null) return;

        String uid = mAuth.getCurrentUser().getUid();
        String logType = isFocusMode ? "FOCUS" : "REST";
        int durationSec = (int) ((totalSessionTime - timeLeft) / 1000);
        
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
                totalCumulativeMillis += totalSessionTime; // 세션 완료 시 전체 시간 가산
                updateUI(timeLeft);
                stopTimer();
                addRecordToTable(0);
                return;
            }

            updateUI(timeLeft);
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerRunnable);
    }
}