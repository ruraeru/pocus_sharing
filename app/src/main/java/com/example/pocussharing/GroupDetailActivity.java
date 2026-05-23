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

    private String groupId;
    private String currentUserId;
    private RtdbRepository rtdbRepository;
    private FirestoreRepository firestoreRepository;
    private MemberAdapter adapter;
    private List<MemberStatus> memberList = new ArrayList<>();
    private Group group;
    private ImageButton btnManage;

    // Personal Timer Fields
    private TimerView personalTimerView;
    private TextView tvPersonalDigitalTimer;
    private View personalDotFocus, personalDotRest;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    private long sessionStartTimeMillis;
    private long timeLeft = 25 * 60 * 1000;
    private long totalSessionTime = 25 * 60 * 1000;
    private final long FOCUS_TIME = 25 * 60 * 1000;
    private final long REST_TIME = 5 * 60 * 1000;

    private boolean isRunning = false;
    private boolean isFocusMode = true;
    private long totalCumulativeMillis = 0;
    private String userNickname = "GUEST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        currentUserId = FirebaseAuth.getInstance().getUid();
        groupId = getIntent().getStringExtra("groupId");
        String groupName = getIntent().getStringExtra("groupName");
        if (groupId == null) groupId = "main_group"; 

        initViews(groupName);
        
        rtdbRepository = new RtdbRepository();
        firestoreRepository = new FirestoreRepository();
        
        loadGroupInfo();
        loadUserProfile();
        loadTodayStats();
        listenToPresence();
    }

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

        // Personal Timer Views
        personalTimerView = findViewById(R.id.personal_timer_view);
        tvPersonalDigitalTimer = findViewById(R.id.tv_personal_digital_timer);
        personalDotFocus = findViewById(R.id.personal_dot_focus);
        personalDotRest = findViewById(R.id.personal_dot_rest);

        personalTimerView.setOnTimerDialListener(new TimerView.OnTimerDialListener() {
            @Override
            public void onDialChanged(float progress) {
                if (isRunning) stopTimer();
                long newTime = (long) (progress * 60 * 60 * 1000);
                timeLeft = newTime;
                totalSessionTime = newTime;
                updatePersonalUI(timeLeft);
            }

            @Override
            public void onDialSelected(float progress) {
                toggleTimer();
            }
        });

        personalDotFocus.setOnClickListener(v -> setMode(true));
        personalDotRest.setOnClickListener(v -> setMode(false));

        RecyclerView rvMembers = findViewById(R.id.rv_members);
        rvMembers.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new MemberAdapter(memberList, this::onMemberLongClick);
        rvMembers.setAdapter(adapter);

        updatePersonalUI(totalSessionTime);
    }

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

    private void loadTodayStats() {
        if (currentUserId != null) {
            firestoreRepository.getDailyFocusTime(currentUserId).addOnSuccessListener(focusSec -> {
                totalCumulativeMillis = focusSec * 1000L;
                updatePersonalUI(timeLeft);
            });
        }
    }

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
    }

    private void updatePersonalUI(long millis) {
        float progress = (float) millis / (60 * 60 * 1000); 
        personalTimerView.setProgress(progress);
        
        long displayMillis = totalCumulativeMillis;
        if (isRunning && isFocusMode) {
            displayMillis += (totalSessionTime - timeLeft);
        }
        
        if (isRunning) {
            syncStatusToRtdb();
        }
        updateDigitalTimer(displayMillis);
    }

    private void updateDigitalTimer(long millis) {
        int seconds = (int) (millis / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        seconds = seconds % 60;
        minutes = minutes % 60;
        tvPersonalDigitalTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
    }

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

    private void stopTimer() {
        isRunning = false;
        handler.removeCallbacks(timerRunnable);
        updatePersonalUI(timeLeft);
        syncStatusToRtdb(); // Send final status
    }

    private void startTimer() {
        if (!isRunning) {
            isRunning = true;
            sessionStartTimeMillis = System.currentTimeMillis();
            handler.postDelayed(timerRunnable, 1000);
        }
    }

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

    private void syncStatusToRtdb() {
        if (currentUserId == null) return;
        
        long totalTodayFocus = totalCumulativeMillis;
        if (isRunning && isFocusMode) {
            totalTodayFocus += (totalSessionTime - timeLeft);
        }

        rtdbRepository.updateUserStatus(groupId, currentUserId, userNickname, isFocusMode, timeLeft, totalTodayFocus);
    }

    private void loadGroupInfo() {
        if (groupId.equals("main_group")) return;

        firestoreRepository.getGroup(groupId).addOnSuccessListener(documentSnapshot -> {
            group = documentSnapshot.toObject(Group.class);
            if (group != null) {
                group.setGroupId(documentSnapshot.getId());
                if (group.getAdminId().equals(currentUserId)) {
                    btnManage.setVisibility(View.VISIBLE);
                }
            }
        });
    }

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
            
            holder.timerView.setMode(status.isFocus());
            float progress = (float) status.getTimeLeft() / (60 * 60 * 1000);
            holder.timerView.setProgress(progress);
            
            int seconds = (int) (status.getTimeLeft() / 1000);
            int h = seconds / 3600;
            int m = (seconds % 3600) / 60;
            int s = seconds % 60;
            holder.tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));

            holder.tvTotalToday.setText(String.format(Locale.getDefault(), "오늘: %d시간 %d분 %d초", h, m, s));

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