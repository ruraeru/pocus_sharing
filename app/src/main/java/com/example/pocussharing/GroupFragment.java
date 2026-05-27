/**
 * GroupFragment.java
 * 사용자가 속한 그룹 목록을 표시하고, 새로운 그룹을 생성하거나 초대 코드를 통해 기존 그룹에 참여할 수 있는 화면입니다.
 * Firestore의 실시간 업데이트를 감시하여 그룹 목록을 최신 상태로 유지합니다.
 */
package com.example.pocussharing;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pocussharing.model.Group;
import com.example.pocussharing.repository.FirestoreRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GroupFragment extends Fragment {

    private RecyclerView rvGroups;          // 그룹 목록 리사이클러뷰
    private FloatingActionButton fabAdd, fabJoin; // 그룹 생성 및 참여 버튼
    private GroupAdapter adapter;           // 그룹 목록 어댑터
    private List<Group> groupList = new ArrayList<>(); // 그룹 데이터 리스트
    private FirestoreRepository repository;  // Firestore 데이터 저장소
    private ListenerRegistration groupsListener; // 실시간 업데이트 리스너

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 레이아웃 인플레이트
        View view = inflater.inflate(R.layout.fragment_group, container, false);

        repository = new FirestoreRepository();
        rvGroups = view.findViewById(R.id.rv_groups);
        fabAdd = view.findViewById(R.id.fab_add);
        fabJoin = view.findViewById(R.id.fab_join);

        setupRecyclerView();
        setupGroupsListener(); // 실시간 감시 설정

        // 버튼 클릭 이벤트 설정
        fabAdd.setOnClickListener(v -> showCreateGroupDialog());
        fabJoin.setOnClickListener(v -> showJoinGroupDialog());

        return view;
    }

    /**
     * 리사이클러뷰와 어댑터를 연결하고 레이아웃 매니저를 설정합니다.
     */
    private void setupRecyclerView() {
        adapter = new GroupAdapter(groupList);
        rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        rvGroups.setAdapter(adapter);
    }

    /**
     * 사용자가 속한 그룹 목록의 변경 사항을 Firestore에서 실시간으로 감시합니다.
     */
    private void setupGroupsListener() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        // 기존 리스너가 있으면 먼저 제거
        if (groupsListener != null) logsListenerRemove(); 

        groupsListener = repository.getUserGroupsListener(uid, (value, error) -> {
            if (error != null) {
                Log.e("GroupFragment", "Listen failed.", error);
                return;
            }

            groupList.clear();
            if (value != null) {
                for (com.google.firebase.firestore.DocumentSnapshot doc : value.getDocuments()) {
                    Group group = doc.toObject(Group.class);
                    if (group != null) {
                        group.setGroupId(doc.getId());
                        groupList.add(group);
                    }
                }
            }
            adapter.notifyDataSetChanged();
        });
    }

    /**
     * 리스너 등록을 해제합니다.
     */
    private void logsListenerRemove() {
        if (groupsListener != null) {
            groupsListener.remove();
            groupsListener = null;
        }
    }

    /**
     * 그룹 생성을 위한 이름을 입력받는 다이얼로그를 표시합니다.
     */
    private void showCreateGroupDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_group, null);
        EditText etName = dialogView.findViewById(R.id.et_group_name);

        new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setPositiveButton("생성", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        createGroup(name);
                    } else {
                        Toast.makeText(getContext(), "이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * 6자리 초대 코드를 입력받아 그룹에 참여하는 다이얼로그를 표시합니다.
     */
    private void showJoinGroupDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_join_group, null);
        EditText etCode = dialogView.findViewById(R.id.et_group_code);

        new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setPositiveButton("참여", (dialog, which) -> {
                    String code = etCode.getText().toString().trim().toUpperCase();
                    if (!code.isEmpty()) {
                        joinGroupByCode(code);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * 입력된 코드로 그룹을 찾아 현재 사용자를 멤버로 추가합니다.
     */
    private void joinGroupByCode(String code) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        repository.findGroupByCode(code).addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                com.google.firebase.firestore.DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                Group group = doc.toObject(Group.class);
                if (group != null) {
                    // 이미 멤버인지, 정원 초과인지 확인
                    if (group.getMemberIds() != null && group.getMemberIds().contains(uid)) {
                        Toast.makeText(getContext(), "이미 참여 중인 그룹입니다.", Toast.LENGTH_SHORT).show();
                    } else if (group.getMemberIds() != null && group.getMemberIds().size() >= group.getMaxMembers()) {
                        Toast.makeText(getContext(), "그룹 인원이 가득 찼습니다.", Toast.LENGTH_SHORT).show();
                    } else {
                        repository.joinGroup(doc.getId(), uid)
                                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), group.getGroupName() + " 그룹에 참여했습니다!", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(getContext(), "참여 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }
            } else {
                Toast.makeText(getContext(), "유효하지 않은 코드입니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 임의의 6자리 코드를 생성하여 새로운 그룹을 Firestore에 등록합니다.
     */
    private void createGroup(String name) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        String randomCode = generateRandomCode(6);
        Group newGroup = new Group(name, randomCode, uid);
        repository.createGroup(newGroup)
                .addOnSuccessListener(docRef -> {
                    Toast.makeText(getContext(), "그룹이 생성되었습니다! 코드: " + randomCode, Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    Log.e("GroupFragment", "Group creation failed", e);
                    Toast.makeText(getContext(), "그룹 생성 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * 알파벳 대문자와 숫자를 조합하여 임의의 문자열을 생성합니다.
     */
    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        logsListenerRemove(); // 화면 종료 시 리스너 해제
    }

    /**
     * 그룹 목록을 표시하기 위한 리사이클러뷰 어댑터 클래스입니다.
     */
    private class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {
        private List<Group> groups;

        public GroupAdapter(List<Group> groups) {
            this.groups = groups;
        }

        @NonNull
        @Override
        public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
            return new GroupViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
            Group group = groups.get(position);
            holder.tvName.setText(group.getGroupName());
            holder.tvCode.setText("코드: " + group.getGroupCode());
            int count = group.getMemberIds() != null ? group.getMemberIds().size() : 0;
            holder.tvMemberCount.setText(count + "/" + group.getMaxMembers());
            
            // 아이템 클릭 시 그룹 상세 화면으로 이동
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), GroupDetailActivity.class);
                intent.putExtra("groupId", group.getGroupId());
                intent.putExtra("groupName", group.getGroupName());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return groups.size();
        }

        class GroupViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvCode, tvMemberCount;

            public GroupViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_group_name);
                tvCode = itemView.findViewById(R.id.tv_group_code);
                tvMemberCount = itemView.findViewById(R.id.tv_member_count);
            }
        }
    }
}
