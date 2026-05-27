/**
 * HomeFragment.java
 * 앱의 메인 화면으로, 개인 타이머 기능을 제공하고 오늘의 집중 기록을 보여줍니다.
 * 사용자의 프로필 정보를 표시하고, 타이머 세션 결과를 Firestore에 저장하며
 * 실시간으로 그룹 멤버들과 상태를 공유합니다.
 */
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

    private TimerView timerView;          // 원형 타이머 커스텀 뷰
    private TextView tvDigitalTimer;      // 디지털 형식의 남은 시간 텍스트
    private TextView tvDate;              // 현재 날짜 표시 텍스트
    private ImageView ivProfile;          // 사용자 프로필 이미지 뷰
    private FirebaseAuth mAuth;           // Firebase 인증 객체
    private FirestoreRepository repository; // Firestore 데이터 저장소
    private RtdbRepository rtdbRepository; // 실시간 데이터베이스 저장소
    private Handler handler = new Handler(Looper.getMainLooper()); // 타이머 카운트다운용 핸들러
    private ListenerRegistration logsListener; // 타이머 로그 실시간 감시 리스너
    
    private long sessionStartTimeMillis;  // 현재 세션 시작 시간
    private long timeLeft = 25 * 60 * 1000; // 남은 시간 (기본 25분)
    private long totalSessionTime = 25 * 60 * 1000; // 현재 설정된 총 세션 시간
    private final long FOCUS_TIME = 25 * 60 * 1000; // 집중 모드 기본 시간 (25분)
    private final long REST_TIME = 5 * 60 * 1000;   // 휴식 모드 기본 시간 (5분)

    private boolean isRunning = false;     // 타이머 실행 중 여부
    private boolean isFocusMode = true;    // 현재 집중 모드 여부
    private android.widget.RadioGroup rgStatus; // 상태 선택(집중/휴식) 라디오 그룹
    private android.widget.RadioButton rbFocus, rbRest;
    private LinearLayout llTable;          // 기록 목록이 추가될 테이블 레이아웃
    private int recordCount = 0;           // 표시된 기록 개수
    private long totalCumulativeMillis = 0; // 오늘 총 누적 집중 시간
    private String userNickname = "GUEST";  // 사용자 닉네임
    private List<String> userGroupIds = new ArrayList<>(); // 사용자가 속한 그룹 ID 리스트

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 레이아웃 인플레이트
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

        // 현재 날짜 설정 (M월 d일 형식)
        String dateStr = new java.text.SimpleDateFormat("M월 d일", Locale.KOREA).format(new Date());
        tvDate.setText(dateStr);

        // 타이머 다이얼 조작 리스너 설정
        timerView.setOnTimerDialListener(new TimerView.OnTimerDialListener() {
            @Override
            public void onDialChanged(float progress) {
                // 다이얼 조작 중에는 실행 중인 타이머 중지
                if (isRunning) stopTimer();
                long newTime = (long) (progress * 60 * 60 * 1000); // 60분 기준 진행률
                timeLeft = newTime;
                totalSessionTime = newTime;
                updateDigitalTimer(timeLeft);
            }

            @Override
            public void onDialSelected(float progress) {
                toggleTimer(); // 다이얼 조작 완료 시 시작/정지 토글
            }
        });
        
        // 상태 변경(집중/휴식) 리스너 설정
        rgStatus.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_focus) {
                if (!isFocusMode) setMode(true);
            } else if (checkedId == R.id.rb_rest) {
                if (isFocusMode) setMode(false);
            }
        });

        // 초기 데이터 로딩
        updateUI(totalSessionTime);
        loadUserProfile();
        loadTodayStats();
        setupLogsListener();
        
        return view;
    }

    /**
     * 사용자의 타이머 로그 목록의 변경 사항을 실시간으로 감시합니다.
     */
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

    /**
     * 불러온 로그 목록을 사용하여 하단 기록 테이블을 갱신합니다.
     */
    private void updateLogsTable(List<com.google.firebase.firestore.DocumentSnapshot> docs) {
        // 기존 뷰 제거 및 카운트 초기화
        llTable.removeAllViews();
        recordCount = 0;

        List<com.google.firebase.firestore.DocumentSnapshot> mutableDocs = new ArrayList<>(docs);
        // 생성 시간 순으로 정렬 (오래된 순으로 정렬하여 테이블의 0번 인덱스에 추가함으로써 최신순 구현)
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

    /**
     * 개별 로그 기록을 UI 테이블(LinearLayout)에 행(Row)으로 추가합니다.
     */
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

        // 행 레이아웃 인플레이트 및 텍스트 설정
        View row = getLayoutInflater().inflate(R.layout.table_row, llTable, false);
        ((TextView) row.findViewById(R.id.tv_no)).setText(String.valueOf(recordCount));
        ((TextView) row.findViewById(R.id.tv_time)).setText(timeStr);
        ((TextView) row.findViewById(R.id.tv_type)).setText(typeStr);

        // 가장 위에 추가
        llTable.addView(row, 0);
    }

    /**
     * 사용자의 프로필 정보(닉네임, 이미지)와 참여 중인 그룹 목록을 불러옵니다.
     */
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

            // 실시간 상태 공유를 위해 사용자가 참여 중인 그룹 ID 목록을 가져옵니다.
            repository.getUserGroups(uid).addOnSuccessListener(queryDocumentSnapshots -> {
                userGroupIds.clear();
                for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                    userGroupIds.add(doc.getId());
                }
            });
        }
    }

    /**
     * 오늘 총 누적 집중 시간을 불러와 UI를 갱신합니다.
     */
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

    /**
     * 타이머 모드(집중/휴식)를 설정하고 관련된 UI와 필드를 초기화합니다.
     */
    private void setMode(boolean isFocus) {
        if (isRunning) {
            stopTimer();
            addRecordToTable(); // 현재까지의 기록 저장
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

    /**
     * 타이머 뷰와 디지털 타이머 텍스트를 업데이트하고 상태를 동기화합니다.
     */
    private void updateUI(long millis) {
        float progress = (float) millis / (60 * 60 * 1000); 
        timerView.setProgress(progress);
        
        if (isRunning) {
            syncStatusToRtdb();
        }
        updateDigitalTimer(millis);
    }

    /**
     * 현재 사용자의 실시간 타이머 상태를 참여 중인 모든 그룹에 동기화합니다.
     */
    private void syncStatusToRtdb() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        
        long totalTodayFocus = totalCumulativeMillis;
        if (isRunning && isFocusMode) {
            totalTodayFocus += (totalSessionTime - timeLeft);
        }

        // 참여 중인 그룹이 없는 경우 기본 그룹에 동기화, 있는 경우 모든 그룹에 동기화
        if (userGroupIds.isEmpty()) {
            rtdbRepository.updateUserStatus("main_group", uid, userNickname, isFocusMode, timeLeft, totalTodayFocus);
        } else {
            for (String gid : userGroupIds) {
                rtdbRepository.updateUserStatus(gid, uid, userNickname, isFocusMode, timeLeft, totalTodayFocus);
            }
        }
    }

    /**
     * 디지털 타이머 형식(HH:mm:ss)으로 시간을 표시합니다.
     */
    private void updateDigitalTimer(long millis) {
        int seconds = (int) (millis / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        seconds = seconds % 60;
        minutes = minutes % 60;
        tvDigitalTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
    }

    /**
     * 타이머 시작/정지를 전환합니다. 정지 시 기록을 저장합니다.
     */
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

    /**
     * 타이머 작동을 멈추고 핸들러 콜백을 제거합니다.
     */
    private void stopTimer() {
        isRunning = false;
        handler.removeCallbacks(timerRunnable);
        updateUI(timeLeft);
    }

    /**
     * 타이머를 시작하고 카운트다운 핸들러를 실행합니다.
     */
    private void startTimer() {
        if (!isRunning) {
            isRunning = true;
            sessionStartTimeMillis = System.currentTimeMillis();
            handler.postDelayed(timerRunnable, 1000);
        }
    }

    /**
     * 현재 세션의 경과 시간을 확인하여 Firebase에 기록을 요청합니다.
     */
    private void addRecordToTable() {
        long currentSessionElapsed = totalSessionTime - timeLeft;
        if (currentSessionElapsed <= 0) return;

        saveLogToFirebase(currentSessionElapsed);
    }

    /**
     * 타이머 로그 객체를 생성하여 Firestore에 저장합니다.
     */
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
                // 리스너에 의해 테이블은 자동으로 갱신됩니다.
            })
            .addOnFailureListener(e -> Log.e("Firebase", "Failed to save log", e));
    }

    /**
     * 1초마다 남은 시간을 줄이고 UI를 갱신하는 런어블 객체입니다.
     */
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
        // 화면으로 돌아올 때마다 최신 정보 로딩
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
