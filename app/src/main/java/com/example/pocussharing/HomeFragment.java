/**
 * 앱의 메인 화면으로, 개인 타이머 기능 제공 및 오늘 집중 기록 표시
 * 사용자의 프로필 정보를 표시하고, 타이머 세션 결과를 Firestore에 저장하며
 * 실시간으로 그룹 멤버들과 상태 공유
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
import android.widget.RadioButton;
import android.widget.RadioGroup;
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

    // [UI 컴포넌트 블록]
    private TimerView timerView;          // 원형 타이머 커스텀 뷰
    private TextView tvDigitalTimer;      // 디지털 형식의 남은 시간 텍스트
    private TextView tvDate;              // 현재 날짜 표시 텍스트
    private ImageView ivProfile;          // 사용자 프로필 이미지 뷰
    private LinearLayout llTable;          // 기록 목록이 추가될 테이블 레이아웃
    private RadioGroup rgStatus; // 집중/휴식 모드 전환용 라디오 그룹
    private RadioButton rbFocus, rbRest;

    // [데이터 및 로직 제어 블록]
    private FirebaseAuth mAuth;           // Firebase 인증 객체
    private FirestoreRepository repository; // Firestore 데이터 저장소
    private RtdbRepository rtdbRepository; // 실시간 데이터베이스(RTDB) 저장소
    private final Handler handler = new Handler(Looper.getMainLooper()); // 1초 단위 카운트다운 핸들러
    private ListenerRegistration logsListener; // 타이머 기록 실시간 감시 리스너

    // [타이머 상태 데이터 블록]
    private long sessionStartTimeMillis;  // 현재 세션 시작 시간 기록
    private long timeLeft = 25 * 60 * 1000; // 남은 밀리초 (기본 25분)
    private long totalSessionTime = 25 * 60 * 1000; // 설정된 총 세션 시간
    private final long FOCUS_TIME = 25 * 60 * 1000; // 집중 기본: 25분
    private final long REST_TIME = 5 * 60 * 1000;   // 휴식 기본: 5분

    private boolean isRunning = false;     // 타이머 작동 여부
    private boolean isFocusMode = true;    // 현재 모드 (True: 집중, False: 휴식)
    private int recordCount = 0;           // 하단 테이블에 표시된 로그 개수
    private long totalCumulativeMillis = 0; // 오늘 하루 전체 누적 집중 시간
    private String userNickname = "GUEST";  // 사용자 닉네임 (기본값 GUEST)
    private final List<String> userGroupIds = new ArrayList<>(); // 현재 가입된 그룹 ID 목록

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        mAuth = FirebaseAuth.getInstance();
        repository = new FirestoreRepository();
        rtdbRepository = new RtdbRepository();
        
        // 뷰 아이디 연결
        timerView = view.findViewById(R.id.timer_view);
        tvDigitalTimer = view.findViewById(R.id.tv_digital_timer);
        tvDate = view.findViewById(R.id.tv_date);
        ivProfile = view.findViewById(R.id.iv_profile);
        llTable = view.findViewById(R.id.ll_table);
        rgStatus = view.findViewById(R.id.rg_status);
        rbFocus = view.findViewById(R.id.rb_focus);
        rbRest = view.findViewById(R.id.rb_rest);

        // 오늘 날짜 텍스트 세팅 (ex: 7월 16일)
        String dateStr = new java.text.SimpleDateFormat("M월 d일", Locale.KOREA).format(new Date());
        tvDate.setText(dateStr);

        // [이벤트 리스너 등록 블록]
        // 타이머 원형 다이얼 직접 조작할 때의 동작 정의
        timerView.setOnTimerDialListener(new TimerView.OnTimerDialListener() {
            @Override
            public void onDialChanged(float progress) {
                if (isRunning) stopTimer(); // 조작 시작 시 작동 중인 타이머 멈춤
                long newTime = (long) (progress * 60 * 60 * 1000); // 최대 60분 기준으로 비례 계산
                timeLeft = newTime;
                totalSessionTime = newTime;
                updateDigitalTimer(timeLeft); // 텍스트 즉시 갱신
            }

            @Override
            public void onDialSelected(float progress) {
                toggleTimer(); // 조작 완료 시 타이머 시작 또는 정지
            }
        });
        
        // 집중/휴식 모드 변경 시 동작
        rgStatus.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_focus) {
                if (!isFocusMode) setMode(true); // 집중 모드로 전환
            } else if (checkedId == R.id.rb_rest) {
                if (isFocusMode) setMode(false); // 휴식 모드로 전환
            }
        });

        // [초기 데이터 로드]
        updateUI(totalSessionTime);
        loadUserProfile();
        loadTodayStats();
        setupLogsListener(); // 타이머 기록 감시 시작
        
        return view;
    }

    /**
     * 타이머 기록(Log)의 실시간 변경을 감시
     * Firestore 리스너를 통해 내가 기록을 올리면 목록에 바로 뜨게
     */
    private void setupLogsListener() {
        if (mAuth.getCurrentUser() == null) return;
        
        String uid = mAuth.getCurrentUser().getUid();
        if (logsListener != null) logsListener.remove(); // 중복 등록 방지

        logsListener = repository.getTimerLogsListener(uid, (value, error) -> {
            if (error != null) {
                Log.e("HomeFragment", "로그 감시 실패", error);
                return;
            }
            if (value != null) {
                updateLogsTable(value.getDocuments()); // 새 데이터로 테이블 갱신
            }
        });
    }

    /**
     * Firestore에서 가져온 로그 목록을 하단 UI 테이블에 그림
     */
    private void updateLogsTable(List<com.google.firebase.firestore.DocumentSnapshot> docs) {
        llTable.removeAllViews(); // 기존 행 모두 삭제
        recordCount = 0;

        List<com.google.firebase.firestore.DocumentSnapshot> mutableDocs = new ArrayList<>(docs);
        // 생성 시간 순으로 정렬하여 최신순 구현
        mutableDocs.sort((d1, d2) -> {
            com.google.firebase.Timestamp t1 = d1.getTimestamp("createdAt");
            com.google.firebase.Timestamp t2 = d2.getTimestamp("createdAt");
            if (t1 == null || t2 == null) return 0;
            return t1.compareTo(t2);
        });

        for (com.google.firebase.firestore.DocumentSnapshot doc : mutableDocs) {
            TimerLog log = doc.toObject(TimerLog.class);
            if (log != null) {
                addLogToTableUI(log); // 개별 행 UI에 추가
            }
        }
    }

    /**
     * 단일 로그 데이터를 XML(table_row)에 입혀서 레이아웃에 추가
     */
    private void addLogToTableUI(TimerLog log) {
        recordCount++;
        int durationSec = log.getDurationSeconds();
        int seconds = durationSec % 60;
        int minutes = (durationSec / 60) % 60;
        int hours = durationSec / 3600;

        // 시간 포맷팅 (ex: 25분 30초)
        String timeStr;
        if (hours > 0) {
            timeStr = String.format(Locale.getDefault(), "%d시간 %d분 %d초", hours, minutes, seconds);
        } else if (minutes > 0) {
            timeStr = String.format(Locale.getDefault(), "%d분 %d초", minutes, seconds);
        } else {
            timeStr = String.format(Locale.getDefault(), "%d초", seconds);
        }

        String typeStr = log.getLogType().equals("FOCUS") ? "집중" : "휴식";

        // [중요 구문] table_row 레이아웃 인플레이트하여 데이터 주입
        View row = getLayoutInflater().inflate(R.layout.table_row, llTable, false);
        ((TextView) row.findViewById(R.id.tv_no)).setText(String.valueOf(recordCount));
        ((TextView) row.findViewById(R.id.tv_time)).setText(timeStr);
        ((TextView) row.findViewById(R.id.tv_type)).setText(typeStr);

        llTable.addView(row, 0); // 항상 가장 위에(최신순) 추가
    }

    /**
     * 사용자 닉네임과 프로필 사진 로드
     * 추가로 사용자가 속한 그룹 ID 목록도 가져와서 실시간 동기화 준비
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
                        // Glide 라이브러리로 이미지 동그랗게 로드
                        Glide.with(this).load(profileImageUrl).circleCrop().into(ivProfile);
                    }
                }
            });

            // 내가 속한 모든 그룹 찾아옴
            repository.getUserGroups(uid).addOnSuccessListener(queryDocumentSnapshots -> {
                userGroupIds.clear();
                for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                    userGroupIds.add(doc.getId());
                }
            });
        }
    }

    /**
     * 오늘 하루 동안 누적된 총 집중 시간을 가져와 UI 세팅
     */
    private void loadTodayStats() {
        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            repository.getDailyFocusTime(uid).addOnSuccessListener(focusSec -> {
                totalCumulativeMillis = focusSec * 1000L;
                updateUI(timeLeft); // 누적 시간 포하여 UI 갱신
            });
        }
    }

    /**
     * 타이머 모드(집중/휴식) 강제 설정
     * 작동 중일 경우 현재까지 한 시간을 기록하고 모드 바꿈
     */
    private void setMode(boolean isFocus) {
        if (isRunning) {
            long elapsed = totalSessionTime - timeLeft;
            if (isFocusMode) totalCumulativeMillis += elapsed;
            stopTimer();
            addRecordToTable(); // 현재까지 세션 저장
        }
        isFocusMode = isFocus;
        timerView.setMode(isFocus); // 타이머 뷰 색상 변경 (집중: 빨강, 휴식: 초록)
        totalSessionTime = isFocus ? FOCUS_TIME : REST_TIME;
        timeLeft = totalSessionTime;
        updateUI(timeLeft);

        if (isFocus) rbFocus.setChecked(true); else rbRest.setChecked(true);

        syncStatusToRtdb(); // 바뀐 모드 실시간 DB에 동기화
    }

    /**
     * 타이머 진행률(원형)과 디지털 텍스트를 한꺼번에 업데이트
     */
    private void updateUI(long millis) {
        float progress = (float) millis / (60 * 60 * 1000); // 60분 대비 진행률
        timerView.setProgress(progress);
        
        if (isRunning) syncStatusToRtdb(); // 작동 중일 때만 주기적으로 동기화
        updateDigitalTimer(millis);
    }

    /**
     * 현재 나의 타이머 상태를 가입된 모든 그룹 노드에 업데이트
     * [주요 로직] 오늘 총 집중 시간에 현재 실시간으로 흐르고 있는 시간까지 합산하여 전송
     */
    private void syncStatusToRtdb() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        
        long totalTodayFocus = totalCumulativeMillis;
        if (isRunning && isFocusMode) {
            totalTodayFocus += (totalSessionTime - timeLeft); // 현재 흐르는 시간 합산
        }

        // 가입된 그룹이 없으면 'main_group'에, 있으면 각 그룹 노드에 정보 저장
        if (userGroupIds.isEmpty()) {
            rtdbRepository.updateUserStatus("main_group", uid, userNickname, isFocusMode, timeLeft, totalTodayFocus);
        } else {
            for (String gid : userGroupIds) {
                rtdbRepository.updateUserStatus(gid, uid, userNickname, isFocusMode, timeLeft, totalTodayFocus);
            }
        }
    }

    /**
     * HH:mm:ss 형식으로 텍스트 변환
     */
    private void updateDigitalTimer(long millis) {
        int seconds = (int) (millis / 1000);
        int m = (seconds / 60) % 60;
        int h = seconds / 3600;
        int s = seconds % 60;
        tvDigitalTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));
    }

    /**
     * 타이머 작동 상태를 토글(Start/Stop)
     */
    private void toggleTimer() {
        if (isRunning) {
            long elapsed = totalSessionTime - timeLeft;
            if (isFocusMode) totalCumulativeMillis += elapsed;
            stopTimer();
            addRecordToTable(); // 결과 저장

            if (isFocusMode) setMode(false); // 집중 끝났으면 자동으로 휴식 모드 제안
        } else {
            if (timeLeft > 0) startTimer();
        }
    }

    /**
     * 타이머 작동 중지하고 콜백 제거
     */
    private void stopTimer() {
        isRunning = false;
        handler.removeCallbacks(timerRunnable);
        updateUI(timeLeft);
        syncStatusToRtdb(); // 최종 멈춘 상태 전송
    }

    /**
     * 타이머 작동 시작하고 세션 시작 시간 기록
     */
    private void startTimer() {
        if (!isRunning) {
            isRunning = true;
            sessionStartTimeMillis = System.currentTimeMillis();
            handler.postDelayed(timerRunnable, 1000); // 1초 뒤부터 반복 실행
        }
    }

    /**
     * 현재 타이머가 진행된 시간을 계산하여 Firestore에 로그 저장 요청
     */
    private void addRecordToTable() {
        long currentSessionElapsed = totalSessionTime - timeLeft;
        if (currentSessionElapsed <= 0) return;

        String uid = mAuth.getCurrentUser().getUid();
        String logType = isFocusMode ? "FOCUS" : "REST";
        int durationSec = (int) (currentSessionElapsed / 1000);

        TimerLog log = new TimerLog(
            uid, logType, durationSec,
            new Timestamp(new Date(sessionStartTimeMillis)),
            Timestamp.now()
        );

        repository.saveTimerLog(log)
            .addOnSuccessListener(aVoid -> Log.d("Firebase", "기록 저장 완료"))
            .addOnFailureListener(e -> Log.e("Firebase", "기록 저장 실패", e));
    }

    /**
     * [핵심 루프] 1초마다 스스로를 호출하여 시간을 줄여나가는 Runnable 객체
     */
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            timeLeft -= 1000; // 1초 뺌
            if (timeLeft <= 0) {
                timeLeft = 0;
                if (isFocusMode) totalCumulativeMillis += totalSessionTime;
                updateUI(timeLeft);
                stopTimer();
                addRecordToTable();
                if (isFocusMode) setMode(false); // 자동 휴식 전환
                return;
            }

            updateUI(timeLeft);
            handler.postDelayed(this, 1000); // 1초 후에 다시 실행
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        // 화면 돌아올 때마다 정보 갱신
        loadUserProfile();
        loadTodayStats();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(timerRunnable);
        if (logsListener != null) logsListener.remove(); // 리스너 해제하여 메모리 관리
    }
}
