package com.example.pocussharing;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.pocussharing.repository.FirestoreRepository;
import com.example.pocussharing.repository.RtdbRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroupDetailActivity extends AppCompatActivity {

    private String groupId;
    private String currentUserId;
    private RtdbRepository repository;
    private FirestoreRepository firestoreRepository;
    private MemberAdapter adapter;
    private List<MemberStatus> memberList = new ArrayList<>();
    private Group group;
    private ImageButton btnManage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        currentUserId = FirebaseAuth.getInstance().getUid();
        groupId = getIntent().getStringExtra("groupId");
        String groupName = getIntent().getStringExtra("groupName");
        if (groupId == null) groupId = "main_group"; 

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(groupName != null ? groupName : "그룹 상세");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        btnManage = findViewById(R.id.btn_manage);
        btnManage.setOnClickListener(v -> showManageGroupDialog());

        RecyclerView rvMembers = findViewById(R.id.rv_members);
        rvMembers.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new MemberAdapter(memberList, this::onMemberLongClick);
        rvMembers.setAdapter(adapter);

        repository = new RtdbRepository();
        firestoreRepository = new FirestoreRepository();
        
        loadGroupInfo();
        listenToPresence();
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

        etName.setText(group.getGroupName());
        etMax.setText(String.valueOf(group.getMaxMembers()));

        new android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("저장", (dialog, which) -> {
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
                .show();
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
        repository.getGroupPresenceRef(groupId).addValueEventListener(new ValueEventListener() {
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
            int mins = seconds / 60;
            int secs = seconds % 60;
            holder.tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));

            // Today's total focus time
            long totalSec = status.getTodayFocusTime() / 1000;
            long h = totalSec / 3600;
            long m = (totalSec % 3600) / 60;
            holder.tvTotalToday.setText(String.format(Locale.getDefault(), "오늘: %d시간 %d분", h, m));

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