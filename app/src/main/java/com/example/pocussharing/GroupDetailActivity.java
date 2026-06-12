package com.example.pocussharing;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
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
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 특정 그룹의 상세 정보 및 멤버들의 실시간 상태 확인하는 액티비티
 * 개인 타이머 기능과 그룹 관리(정보 수정, 멤버 추방, 그룹 삭제) 기능 포함함
 */
public class GroupDetailActivity extends AppCompatActivity {

    private String groupId;               // 현재 활성화된 그룹 ID
    private String currentUserId;         // 현재 로그인한 내 UID
    private RtdbRepository rtdbRepository; // 실시간 DB 저장소
    private FirestoreRepository firestoreRepository; // Firestore 저장소
    private MemberAdapter adapter;        // 그룹 멤버 리스트 어댑터
    private final List<MemberStatus> memberList = new ArrayList<>(); // 실시간 멤버 데이터 리스트
    private Group group;                  // 현재 그룹의 메타데이터(이름, 초대코드 등) 정보
    private ImageButton btnManage;        // 그룹장 전용 관리 단추

    // 실시간 리스너 및 캐시 스냅샷 관리
    private ListenerRegistration groupListenerRegistration;
    private ValueEventListener presenceListener;
    private DataSnapshot latestPresenceSnapshot;

    private TimerView personalTimerView;  // 중앙 원형 타이머
    private TextView tvPersonalDigitalTimer; // 디지털 시간 텍스트
    private TextView tvGroupCodeValue;    // 초대 코드 표시 텍스트
    private LinearLayout llInviteCodeContainer; // 초대 코드 클릭 영역
    private RadioButton rbPersonalFocus, rbPersonalRest; // 상태 선택 단추
    private final Handler handler = new Handler(Looper.getMainLooper()); // 타이머 카운트다운 핸들러
    
    private long sessionStartTimeMillis; // 현재 타이머 세션 시작 시간
    private long timeLeft = 25 * 60 * 1000; // 남은 시간 (기본 25분)
    private long totalSessionTime = 25 * 60 * 1000; // 현재 세션 총 설정 시간
    private boolean isRunning = false;     // 타이머 작동 여부
    private boolean isFocusMode = true;    // 현재 모드 (집중/휴식)
    private long totalCumulativeMillis = 0; // 오늘 누적 집중 시간
    private String userNickname = "";      // 사용자 닉네임

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 레이아웃 파일 연결함
        setContentView(R.layout.activity_group_detail);

        currentUserId = FirebaseAuth.getInstance().getUid();
        groupId = getIntent().getStringExtra("groupId");
        String groupName = getIntent().getStringExtra("groupName");
        
        // 그룹 ID가 없으면 기본값 세팅함
        if (groupId == null) groupId = "main_group"; 

        initViews(groupName); // UI 컴포넌트 초기화함
        
        rtdbRepository = new RtdbRepository();
        firestoreRepository = new FirestoreRepository();
        
        // 데이터 로딩 시작
        loadGroupInfo();    // 그룹 이름, 방장 여부 등 로드
        loadUserProfile();  // 닉네임 로드
        loadTodayStats();   // 오늘 집중 시간 로드
        listenToPresence(); // 다른 멤버들 상태 감시 시작
    }

    /**
     * 레이아웃의 각종 뷰들을 찾아서 연결하고 리스너 등록
     */
    private void initViews(String groupName) {
        // 툴바 설정 (상단 앱바)
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(groupName != null ? groupName : "그룹 상세");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // 뒤로가기 버튼 활성화
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // 그룹 관리 단추 (초기에는 숨김, 내가 방장일 때만 보여줌)
        btnManage = findViewById(R.id.btn_manage);
        btnManage.setOnClickListener(v -> showManageGroupDialog());

        // 개인 타이머 컴포넌트 연결
        personalTimerView = findViewById(R.id.personal_timer_view);
        tvPersonalDigitalTimer = findViewById(R.id.tv_personal_digital_timer);
        tvGroupCodeValue = findViewById(R.id.tv_group_code_value);
        llInviteCodeContainer = findViewById(R.id.ll_invite_code_container);
        RadioGroup rgPersonalStatus = findViewById(R.id.rg_personal_status);
        rbPersonalFocus = findViewById(R.id.rb_personal_focus);
        rbPersonalRest = findViewById(R.id.rb_personal_rest);

        // 타이머 다이얼 조작 리스너
        personalTimerView.setOnTimerDialListener(new TimerView.OnTimerDialListener() {
            @Override
            public void onDialChanged(float progress) {
                if (isRunning) stopTimer(); // 조작 중엔 멈춤
                long newTime = (long) (progress * 60 * 60 * 1000); // 60분 대비 진행률 계산
                timeLeft = newTime;
                totalSessionTime = newTime;
                updatePersonalUI(timeLeft);
            }

            @Override
            public void onDialSelected(float progress) {
                toggleTimer(); // 조작 끝나면 시작
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

        // 그룹 멤버 리사이클러뷰 설정 멤버 목록을 세로 리스트로 보여줌
        RecyclerView rvMembers = findViewById(R.id.rv_members);
        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MemberAdapter(memberList, this::onMemberLongClick); // 롱클릭(추방) 리스너 연결
        rvMembers.setAdapter(adapter);

        updatePersonalUI(totalSessionTime);
    }

    /**
     * 사용자의 닉네임을 Firestore에서 가져와 동기화 준비
     */
    private void loadUserProfile() {
        if (currentUserId != null) {
            firestoreRepository.getUser(currentUserId).addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    userNickname = documentSnapshot.getString("nickname");
                }
            });
        }
    }

    /**
     * 오늘의 누적 집중 시간을 불러오는 함수
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
     * 집중 모드 또는 휴식 모드로 전환
     * 모드 전환 시 이전 기록은 DB에 저장
     */
    private void setMode(boolean isFocus) {
        if (isRunning) {
            long elapsed = totalSessionTime - timeLeft;
            if (isFocusMode) totalCumulativeMillis += elapsed; // 집중 중이었으면 누적
            stopTimer();
            saveLogToFirebase(); // 이전 세션 기록
        }
        isFocusMode = isFocus;
        personalTimerView.setMode(isFocus);
        
        long FOCUS_TIME = 25 * 60 * 1000;
        long REST_TIME = 5 * 60 * 1000;

        totalSessionTime = isFocus ? FOCUS_TIME : REST_TIME;
        timeLeft = totalSessionTime;
        updatePersonalUI(timeLeft); // UI 색상 및 시간 초기화

        if (isFocus) rbPersonalFocus.setChecked(true); else rbPersonalRest.setChecked(true);

        syncStatusToRtdb(); // 바뀐 모드 즉시 실시간 DB에 전송
    }

    /**
     * 개인 타이머의 UI(원형 진행률, 디지털 시간) 업데이트
     */
    private void updatePersonalUI(long millis) {
        float progress = (float) millis / (60 * 60 * 1000); 
        personalTimerView.setProgress(progress);
        
        if (isRunning) syncStatusToRtdb(); // 타이머 도는 동안에는 계속 동기화
        updateDigitalTimer(millis);
    }

    /**
     * 시간을 00:00:00 형식으로 변환하여 텍스트뷰에 뿌림
     */
    private void updateDigitalTimer(long millis) {
        int seconds = (int) (millis / 1000);
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        tvPersonalDigitalTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));
    }

    /**
     * 타이머가 돌아가고 있으면 멈추고, 멈춰있으면 시작
     */
    private void toggleTimer() {
        if (isRunning) {
            long elapsed = totalSessionTime - timeLeft;
            if (isFocusMode) totalCumulativeMillis += elapsed;
            stopTimer();
            saveLogToFirebase(); // 멈추면 결과 저장

            if (isFocusMode) setMode(false); // 집중 끝났으면 자동으로 휴식 모드 전환
        } else {
            if (timeLeft > 0) startTimer();
        }
    }

    /**
     * 타이머 작동 정지하고 반복 작업(Runnable) 제거
     */
    private void stopTimer() {
        isRunning = false;
        handler.removeCallbacks(timerRunnable);
        updatePersonalUI(timeLeft);
        syncStatusToRtdb(); // 정지 상태 동기화
    }

    /**
     * 타이머 작동 시작하고 현재 시각 기록
     */
    private void startTimer() {
        if (!isRunning) {
            isRunning = true;
            sessionStartTimeMillis = System.currentTimeMillis();
            handler.postDelayed(timerRunnable, 1000);
        }
    }

    /**
     * 세션 종료 후 Firestore에 집중/휴식 로그 저장
     */
    private void saveLogToFirebase() {
        long currentSessionElapsed = totalSessionTime - timeLeft;
        if (currentSessionElapsed <= 0) return;

        String logType = isFocusMode ? "FOCUS" : "REST";
        int durationSec = (int) (currentSessionElapsed / 1000);
        
        TimerLog log = new TimerLog(
            currentUserId, logType, durationSec,
            new Timestamp(new Date(sessionStartTimeMillis)),
            Timestamp.now()
        );

        firestoreRepository.saveTimerLog(log)
            .addOnSuccessListener(aVoid -> Log.d("GroupDetail", "로그 저장함"))
            .addOnFailureListener(e -> Log.e("GroupDetail", "로그 저장 실패함", e));
    }

    /**
     * [핵심 루프] 1초마다 남은 시간 줄이고 UI 갱신
     */
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            timeLeft -= 1000;
            if (timeLeft <= 0) {
                timeLeft = 0;
                if (isFocusMode) totalCumulativeMillis += totalSessionTime;
                updatePersonalUI(timeLeft);
                stopTimer();
                saveLogToFirebase();
                if (isFocusMode) setMode(false);
                return;
            }
            updatePersonalUI(timeLeft);
            handler.postDelayed(this, 1000);
        }
    };

    /**
     * 현재 내 실시간 정보(모드, 남은 시간, 오늘 총 시간)를 Firebase RTDB에 저장
     * 이를 통해 다른 그룹 멤버들이 내 상태를 볼 수 있게 됨
     */
    private void syncStatusToRtdb() {
        if (currentUserId == null) return;
        
        long totalTodayFocus = totalCumulativeMillis;
        // 타이머가 돌고 있으면 서버에 아직 기록 안 된 현재 진행분까지 합산하여 실시간성 높임
        if (isRunning && isFocusMode) {
            totalTodayFocus += (totalSessionTime - timeLeft);
        }

        rtdbRepository.updateUserStatus(groupId, currentUserId, userNickname, isFocusMode, timeLeft, totalTodayFocus);
    }

    /**
     * 그룹 메타데이터 로드하고 내가 방장인지 확인 (실시간 감시)
     */
    private void loadGroupInfo() {
        if (groupId.equals("main_group")) return;

        groupListenerRegistration = firestoreRepository.listenToGroup(groupId, (documentSnapshot, e) -> {
            if (e != null) {
                Log.e("GroupDetail", "그룹 정보 감시 실패", e);
                return;
            }
            if (documentSnapshot != null && documentSnapshot.exists()) {
                group = documentSnapshot.toObject(Group.class);
                if (group != null) {
                    group.setGroupId(documentSnapshot.getId());

                    // 그룹명 실시간 반영
                    if (getSupportActionBar() != null && group.getGroupName() != null) {
                        getSupportActionBar().setTitle(group.getGroupName());
                    }

                    // 방장 UID와 내 UID가 같으면 관리 버튼 노출
                    if (group.getAdminId().equals(currentUserId)) {
                        btnManage.setVisibility(View.VISIBLE);
                    } else {
                        btnManage.setVisibility(View.GONE);
                    }

                    // 초대 코드 텍스트 세팅 및 복사 기능 활성화
                    String groupCode = group.getGroupCode();
                    tvGroupCodeValue.setText(groupCode != null ? groupCode : "-");
                    llInviteCodeContainer.setOnClickListener(v -> {
                        if (groupCode != null) {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("Group Code", groupCode);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(this, "초대 코드를 복사함", Toast.LENGTH_SHORT).show();
                        }
                    });

                    // 그룹 멤버 갱신에 따른 UI 리스트 새로고침
                    refreshMemberList();
                }
            }
        });
    }

    /**
     * 그룹 관리(이름 수정, 삭제) 팝업창 띄움
     */
    private void showManageGroupDialog() {
        if (group == null) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_manage_group, null);
        EditText etName = dialogView.findViewById(R.id.et_group_name);
        EditText etMax = dialogView.findViewById(R.id.et_max_members);
        Button btnDelete = dialogView.findViewById(R.id.btn_delete_group);

        etName.setText(group.getGroupName());
        etMax.setText(String.valueOf(group.getMaxMembers()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("저장", (d, which) -> {
                    String newName = etName.getText().toString().trim();
                    String maxStr = etMax.getText().toString().trim();
                    if (newName.isEmpty() || maxStr.isEmpty()) return;

                    int newMax = Integer.parseInt(maxStr);
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("groupName", newName);
                    updates.put("maxMembers", newMax);
                    
                    // Firestore에 수정된 정보 저장
                    firestoreRepository.updateGroup(groupId, updates).addOnSuccessListener(aVoid -> {
                        if (getSupportActionBar() != null) getSupportActionBar().setTitle(newName);
                        group.setGroupName(newName);
                        group.setMaxMembers(newMax);
                        Toast.makeText(this, "그룹 정보를 수정함", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("취소", null)
                .create();

        // 그룹 영구 삭제 로직
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("그룹 삭제")
                    .setMessage("정말로 이 그룹을 삭제하시겠습니까?\n 데이터가 모두 지워집니다.")
                    .setPositiveButton("삭제", (d2, which2) -> {
                        dialog.dismiss();
                        deleteGroup(); // DB에서 완전 삭제
                    })
                    .setNegativeButton("취소", null)
                    .show();
        });

        dialog.show();
    }

    /**
     * Firestore와 RTDB 양쪽에서 그룹 데이터 완전 지움
     */
    private void deleteGroup() {
        firestoreRepository.deleteGroup(groupId).addOnSuccessListener(aVoid -> {
            rtdbRepository.deleteGroupPresence(groupId); // 실시간 상태 노드도 함께 지움
            Toast.makeText(this, "그룹을 삭제함", Toast.LENGTH_SHORT).show();
            finish(); // 삭제 후 화면 닫음
        }).addOnFailureListener(e -> Toast.makeText(this, "삭제 실패함: " + e.getMessage(), Toast.LENGTH_SHORT).show() );
    }

    /**
     * 멤버 카드 롱클릭 시 추방(내보내기) 기능 수행
     */
    private void onMemberLongClick(MemberStatus status) {
        // 내가 방장이고, 클릭한 대상이 내가 아닐 때만 추방 가능
        if (group != null && group.getAdminId().equals(currentUserId) && !status.getUserId().equals(currentUserId)) {
            new AlertDialog.Builder(this)
                    .setTitle("멤버 내보내기")
                    .setMessage(status.getName() + " 님을 그룹에서 내보내시겠습니까?")
                    .setPositiveButton("내보내기", (dialog, which) -> {
                        firestoreRepository.leaveGroup(groupId, status.getUserId()).addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, status.getName() + "님을 내보냄", Toast.LENGTH_SHORT).show();
                            // RTDB에서도 해당 유저의 상태 노드를 즉각 제거 (Best-effort 클린업)
                            rtdbRepository.removeUserStatus(groupId, status.getUserId());
                        });
                    })
                    .setNegativeButton("취소", null)
                    .show();
        }
    }

    /**
     * Firebase 실시간 DB(RTDB)로부터 멤버들의 현재 상태를 구독
     */
    private void listenToPresence() {
        presenceListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                latestPresenceSnapshot = snapshot;
                refreshMemberList();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("GroupDetail", "RTDB 감시 에러 발생함", error.toException());
            }
        };
        rtdbRepository.getGroupPresenceRef(groupId).addValueEventListener(presenceListener);
    }

    /**
     * 현재 수신된 실시간 상태 스냅샷을 기반으로 그룹원 리스트를 정렬 및 갱신함
     * (Firestore의 실제 그룹 멤버 목록에 속한 유저만 노출하여 추방 직후 즉시 리스트에서 사라지게 함)
     */
    private void refreshMemberList() {
        if (latestPresenceSnapshot == null) return;

        memberList.clear(); // 기존 목록 비움
        for (DataSnapshot memberSnap : latestPresenceSnapshot.getChildren()) {
            MemberStatus status = memberSnap.getValue(MemberStatus.class);
            if (status != null) {
                // 메인 그룹(전체)이거나, 일반 그룹이면서 그룹 멤버 ID 목록에 존재하는 유저만 리스트에 추가
                if (groupId.equals("main_group") || (group != null && group.getMemberIds() != null && group.getMemberIds().contains(status.getUserId()))) {
                    memberList.add(status);
                }
            }
        }

        // 오늘 가장 많이 집중한 사람이 1등(상단)으로 오도록 정렬 (내림차순)
        memberList.sort((m1, m2) ->
                Long.compare(m2.getTodayFocusTime(), m1.getTodayFocusTime()));

        adapter.notifyDataSetChanged(); // UI 갱신
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (groupListenerRegistration != null) {
            groupListenerRegistration.remove();
        }
        if (presenceListener != null) {
            rtdbRepository.getGroupPresenceRef(groupId).removeEventListener(presenceListener);
        }
        handler.removeCallbacks(timerRunnable); // 메모리 누수 방지를 위해 콜백 제거
    }
}
