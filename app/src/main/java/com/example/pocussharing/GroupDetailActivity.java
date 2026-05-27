/**
 * GroupDetailActivity.java
 * 특정 그룹의 상세 정보를 보여주고, 그룹 멤버들의 실시간 타이머 상태를 확인하며
 * 개인 타이머 기능을 수행하는 액티비티입니다.
 * 그룹 관리자 기능(정보 수정, 멤버 추방, 그룹 삭제)과 실시간 상태 동기화 기능을 포함합니다.
 */
package com.example.pocussharing;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pocussharing.model.Group;
import com.example.pocussharing.model.MemberStatus;
import com.example.pocussharing.model.TimerLog;
import com.example.pocussharing.repository.FirestoreRepository;
import com.example.pocussharing.repository.RtdbRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroupDetailActivity extends AppCompatActivity {

    private String groupId;               // 현재 그룹 ID
    private String currentUserId;         // 현재 사용자 ID
    private RtdbRepository rtdbRepository; // 실시간 데이터베이스 저장소
    private FirestoreRepository firestoreRepository; // Firestore 저장소
    private MemberAdapter adapter;        // 그룹 멤버 목록 어댑터
    private List<MemberStatus> memberList = new ArrayList<>(); // 멤버 상태 리스트
    private Group group;                  // 그룹 정보 객체
    private ImageButton btnManage;        // 그룹 관리 버튼 (방장 전용)

    // 개인 타이머 관련 뷰 및 필드
    private TimerView personalTimerView;
    private TextView tvPersonalDigitalTimer;
    private TextView tvGroupCodeValue;
    private android.widget.LinearLayout llInviteCodeContainer;
    private android.widget.RadioGroup rgPersonalStatus;
    private android.widget.RadioButton rbPersonalFocus, rbPersonalRest;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    private long sessionStartTimeMillis; // 세션 시작 시간
    private long timeLeft = 25 * 60 * 1000; // 남은 시간 (기본 25분)
    private long totalSessionTime = 25 * 60 * 1000; // 설정된 전체 세션 시간
    private final long FOCUS_TIME = 25 * 60 * 1000; // 집중 기본 시간 (25분)
    private final long REST_TIME = 5 * 60 * 1000;   // 휴식 기본 시간 (5분)

    private boolean isRunning = false;     // 타이머 실행 여부
    private boolean isFocusMode = true;    // 집중 모드 여부
    private long totalCumulativeMillis = 0; // 오늘 누적 집중 시간
    private String userNickname = "GUEST";  // 사용자 닉네임

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        currentUserId = FirebaseAuth.getInstance().getUid();
        groupId = getIntent().getStringExtra("groupId");
        String groupName = getIntent().getStringExtra("groupName");
        
        // 기본값 설정 (에러 방지)
        if (groupId == null) groupId = "main_group"; 

        initViews(groupName);
        
        rtdbRepository = new RtdbRepository();
        firestoreRepository = new FirestoreRepository();
        
        // 데이터 로딩 시작
        loadGroupInfo();
        loadUserProfile();
        loadTodayStats();
        listenToPresence(); // 실시간 상태 감시 시작
    }

    /**
     * 레이아웃 뷰 초기화 및 이벤트 리스너를 설정합니다.
     */
    private void initViews(String groupName) {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(groupName != null ? groupName : "그룹 상세");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        btnManage = findViewById(R.id.btn_manage);
        btnManage.setOnClickListener(v -> showManageGroupDialog());

        // 개인 타이머 관련 뷰 할당
        personalTimerView = findViewById(R.id.personal_timer_view);
        tvPersonalDigitalTimer = findViewById(R.id.tv_personal_digital_timer);
        tvGroupCodeValue = findViewById(R.id.tv_group_code_value);
        llInviteCodeContainer = findViewById(R.id.ll_invite_code_container);
        rgPersonalStatus = findViewById(R.id.rg_personal_status);
        rbPersonalFocus = findViewById(R.id.rb_personal_focus);
        rbPersonalRest = findViewById(R.id.rb_personal_rest);

        // 타이머 다이얼 조작 리스너
        personalTimerView.setOnTimerDialListener(new TimerView.OnTimerDialListener() {
            @Override
            public void onDialChanged(float progress) {
                if (isRunning) stopTimer();
                long newTime = (long) (progress * 60 * 60 * 1000); // 최대 60분 기준
                timeLeft = newTime;
                totalSessionTime = newTime;
                updatePersonalUI(timeLeft);
            }

            @Override
            public void onDialSelected(float progress) {
                toggleTimer(); // 다이얼에서 손을 떼면 타이머 시작/정지
            }
        });

        // 상태 선택 라디오 그룹 리스너 (집중/휴식)
        rgPersonalStatus.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_personal_focus) {
                if (!isFocusMode) setMode(true);
            } else if (checkedId == R.id.rb_personal_rest) {
                if (isFocusMode) setMode(false);
            }
        });

        // 그룹 멤버 리사이클러뷰 설정
        RecyclerView rvMembers = findViewById(R.id.rv_members);
        rvMembers.setLayoutManager(new GridLayoutManager(this, 2)); // 2열 그리드
        adapter = new MemberAdapter(memberList, this::onMemberLongClick);
        rvMembers.setAdapter(adapter);

        updatePersonalUI(totalSessionTime);
    }

    /**
     * 사용자의 프로필(닉네임)을 Firestore에서 불러옵니다.
     */
    private void loadUserProfile() {
        if (currentUserId != null) {
            firestoreRepository.getUser(currentUserId).addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    userNickname = documentSnapshot.getString("nickname");
                    if (userNickname == null) userNickname = "GUEST";
                }
            });
        }
    }

    /**
     * 오늘의 누적 집중 시간을 불러옵니다.
     */
    private void loadTodayStats() {
        if (currentUserId != null) {
            firestoreRepository.getDailyFocusTime(currentUserId).addOnSuccessListener(focusSec -> {
                totalCumulativeMillis = focusSec * 1000L;
                updatePersonalUI(timeLeft);
            });
        }
    }

    /**
     * 타이머 모드를 변경합니다. (집중 <-> 휴식)
     */
    private void setMode(boolean isFocus) {
        if (isRunning) {
            stopTimer();
            saveLogToFirebase();
        }
        isFocusMode = isFocus;
        personalTimerView.setMode(isFocus);
        totalSessionTime = isFocus ? FOCUS_TIME : REST_TIME;
        timeLeft = totalSessionTime;
        updatePersonalUI(timeLeft);

        if (isFocus) {
            rbPersonalFocus.setChecked(true);
        } else {
            rbPersonalRest.setChecked(true);
        }
    }

    /**
     * 개인 타이머의 UI를 업데이트하고 실시간 상태를 서버에 동기화합니다.
     */
    private void updatePersonalUI(long millis) {
        float progress = (float) millis / (60 * 60 * 1000); 
        personalTimerView.setProgress(progress);
        
        if (isRunning) {
            syncStatusToRtdb();
        }
        updateDigitalTimer(millis);
    }

    /**
     * 디지털 타이머 형식(HH:mm:ss)으로 텍스트를 업데이트합니다.
     */
    private void updateDigitalTimer(long millis) {
        int seconds = (int) (millis / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        seconds = seconds % 60;
        minutes = minutes % 60;
        tvPersonalDigitalTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
    }

    /**
     * 타이머 시작 또는 일시정지를 토글합니다.
     */
    private void toggleTimer() {
        if (isRunning) {
            long elapsed = totalSessionTime - timeLeft;
            if (isFocusMode) {
                totalCumulativeMillis += elapsed;
            }
            stopTimer();
            saveLogToFirebase();
        } else {
            if (timeLeft > 0) {
                startTimer();
            }
        }
    }

    /**
     * 타이머를 중지하고 핸들러 콜백을 제거합니다.
     */
    private void stopTimer() {
        isRunning = false;
        handler.removeCallbacks(timerRunnable);
        updatePersonalUI(timeLeft);
        syncStatusToRtdb(); // 최종 상태 동기화
    }

    /**
     * 타이머를 시작합니다.
     */
    private void startTimer() {
        if (!isRunning) {
            isRunning = true;
            sessionStartTimeMillis = System.currentTimeMillis();
            handler.postDelayed(timerRunnable, 1000);
        }
    }

    /**
     * 완료된 타이머 세션 기록을 Firestore에 저장합니다.
     */
    private void saveLogToFirebase() {
        long currentSessionElapsed = totalSessionTime - timeLeft;
        if (currentSessionElapsed <= 0) return;

        String logType = isFocusMode ? "FOCUS" : "REST";
        int durationSec = (int) (currentSessionElapsed / 1000);
        
        TimerLog log = new TimerLog(
            currentUserId,
            logType,
            durationSec,
            new Timestamp(new Date(sessionStartTimeMillis)),
            Timestamp.now()
        );

        firestoreRepository.saveTimerLog(log)
            .addOnSuccessListener(aVoid -> Log.d("GroupDetail", "Timer log saved"))
            .addOnFailureListener(e -> Log.e("GroupDetail", "Failed to save log", e));
    }

    /**
     * 타이머를 1초마다 감소시키는 런어블 객체입니다.
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
                updatePersonalUI(timeLeft);
                stopTimer();
                saveLogToFirebase();
                return;
            }

            updatePersonalUI(timeLeft);
            handler.postDelayed(this, 1000);
        }
    };

    /**
     * 현재 사용자의 상태를 실시간 데이터베이스에 업데이트합니다.
     */
    private void syncStatusToRtdb() {
        if (currentUserId == null) return;
        
        long totalTodayFocus = totalCumulativeMillis;
        if (isRunning && isFocusMode) {
            totalTodayFocus += (totalSessionTime - timeLeft);
        }

        rtdbRepository.updateUserStatus(groupId, currentUserId, userNickname, isFocusMode, timeLeft, totalTodayFocus);
    }

    /**
     * 그룹 메타데이터 정보를 Firestore에서 불러옵니다.
     */
    private void loadGroupInfo() {
        if (groupId.equals("main_group")) return;

        firestoreRepository.getGroup(groupId).addOnSuccessListener(documentSnapshot -> {
            group = documentSnapshot.toObject(Group.class);
            if (group != null) {
                group.setGroupId(documentSnapshot.getId());
                // 방장인 경우 관리 버튼 표시
                if (group.getAdminId().equals(currentUserId)) {
                    btnManage.setVisibility(View.VISIBLE);
                }

                // 그룹 초대 코드 설정 및 복사 기능
                String groupCode = group.getGroupCode();
                tvGroupCodeValue.setText(groupCode != null ? groupCode : "없음");
                llInviteCodeContainer.setOnClickListener(v -> {
                    if (groupCode != null) {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("Group Code", groupCode);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this, "초대 코드가 복사되었습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * 그룹 관리(수정/삭제) 다이얼로그를 표시합니다.
     */
    private void showManageGroupDialog() {
        if (group == null) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_manage_group, null);
        EditText etName = dialogView.findViewById(R.id.et_group_name);
        EditText etMax = dialogView.findViewById(R.id.et_max_members);
        Button btnDelete = dialogView.findViewById(R.id.btn_delete_group);

        etName.setText(group.getGroupName());
        etMax.setText(String.valueOf(group.getMaxMembers()));

        AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("저장", (d, which) -> {
                    String newName = etName.getText().toString().trim();
                    String maxStr = etMax.getText().toString().trim();
                    if (newName.isEmpty() || maxStr.isEmpty()) return;

                    int newMax = Integer.parseInt(maxStr);
                    
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("groupName", newName);
                    updates.put("maxMembers", newMax);
                    
                    firestoreRepository.updateGroup(groupId, updates).addOnSuccessListener(aVoid -> {
                        if (getSupportActionBar() != null) {
                            getSupportActionBar().setTitle(newName);
                        }
                        group.setGroupName(newName);
                        group.setMaxMembers(newMax);
                        Toast.makeText(this, "그룹 정보가 수정되었습니다.", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("취소", null)
                .create();

        // 그룹 삭제 버튼 클릭 시 재확인
        btnDelete.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("그룹 삭제")
                    .setMessage("정말로 이 그룹을 삭제하시겠습니까? 모든 데이터가 영구적으로 삭제됩니다.")
                    .setPositiveButton("삭제", (d2, which2) -> {
                        dialog.dismiss();
                        deleteGroup();
                    })
                    .setNegativeButton("취소", null)
                    .show();
        });

        dialog.show();
    }

    /**
     * 그룹을 Firestore 및 실시간 데이터베이스에서 삭제합니다.
     */
    private void deleteGroup() {
        firestoreRepository.deleteGroup(groupId).addOnSuccessListener(aVoid -> {
            rtdbRepository.deleteGroupPresence(groupId);
            Toast.makeText(this, "그룹이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
            finish();
        }).addOnFailureListener(e -> {
            Log.e("GroupDetail", "Failed to delete group", e);
            Toast.makeText(this, "그룹 삭제 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 멤버 카드 롱클릭 시 멤버 내보내기 다이얼로그를 표시합니다.
     */
    private void onMemberLongClick(MemberStatus status) {
        if (group != null && group.getAdminId().equals(currentUserId) && !status.getUserId().equals(currentUserId)) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("멤버 내보내기")
                    .setMessage(status.getName() + "님을 그룹에서 내보내시겠습니까?")
                    .setPositiveButton("내보내기", (dialog, which) -> {
                        firestoreRepository.leaveGroup(groupId, status.getUserId()).addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, status.getName() + "님이 내보내졌습니다.", Toast.LENGTH_SHORT).show();
                        });
                    })
                    .setNegativeButton("취소", null)
                    .show();
        }
    }

    /**
     * 실시간 데이터베이스로부터 그룹 멤버들의 상태 변경을 감시합니다.
     */
    private void listenToPresence() {
        rtdbRepository.getGroupPresenceRef(groupId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                memberList.clear();
                for (DataSnapshot memberSnap : snapshot.getChildren()) {
                    MemberStatus status = memberSnap.getValue(MemberStatus.class);
                    if (status != null) {
                        memberList.add(status);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerRunnable);
    }

    /**
     * 그룹 멤버 목록을 표시하기 위한 리사이클러뷰 어댑터입니다.
     */
    private static class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.ViewHolder> {
        private List<MemberStatus> list;
        private OnMemberLongClickListener longClickListener;

        interface OnMemberLongClickListener {
            void onLongClick(MemberStatus status);
        }

        MemberAdapter(List<MemberStatus> list, OnMemberLongClickListener listener) { 
            this.list = list;
            this.longClickListener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MemberStatus status = list.get(position);
            holder.tvName.setText(status.getName());
            
            // 멤버의 현재 상태(집중/휴식) 및 진행률 설정
            holder.timerView.setMode(status.isFocus());
            float progress = (float) status.getTimeLeft() / (60 * 60 * 1000);
            holder.timerView.setProgress(progress);
            
            // 남은 시간 텍스트 설정
            int seconds = (int) (status.getTimeLeft() / 1000);
            int h = seconds / 3600;
            int m = (seconds % 3600) / 60;
            int s = seconds % 60;
            holder.tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));

            // 오늘 총 집중 시간 표시
            long totalSec = status.getTodayFocusTime() / 1000;
            long th = totalSec / 3600;
            long tm = (totalSec % 3600) / 60;
            long ts = totalSec % 60;
            holder.tvTotalToday.setText(String.format(Locale.getDefault(), "오늘: %d시간 %d분 %d초", th, tm, ts));

            holder.itemView.setOnLongClickListener(v -> {
                longClickListener.onLongClick(status);
                return true;
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvTime, tvTotalToday;
            TimerView timerView;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_name);
                tvTime = v.findViewById(R.id.tv_time);
                tvTotalToday = v.findViewById(R.id.tv_total_today);
                timerView = v.findViewById(R.id.timer_view);
            }
        }
    }
}
