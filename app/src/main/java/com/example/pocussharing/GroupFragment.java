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

    private RecyclerView rvGroups;
    private FloatingActionButton fabAdd, fabJoin;
    private GroupAdapter adapter;
    private List<Group> groupList = new ArrayList<>();
    private FirestoreRepository repository;
    private ListenerRegistration groupsListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_group, container, false);

        repository = new FirestoreRepository();
        rvGroups = view.findViewById(R.id.rv_groups);
        fabAdd = view.findViewById(R.id.fab_add);
        fabJoin = view.findViewById(R.id.fab_join);

        setupRecyclerView();
        setupGroupsListener();

        fabAdd.setOnClickListener(v -> showCreateGroupDialog());
        fabJoin.setOnClickListener(v -> showJoinGroupDialog());

        return view;
    }

    private void setupRecyclerView() {
        adapter = new GroupAdapter(groupList);
        rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        rvGroups.setAdapter(adapter);
    }

    private void setupGroupsListener() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

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

    private void logsListenerRemove() {
        if (groupsListener != null) {
            groupsListener.remove();
        }
    }

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

    private void joinGroupByCode(String code) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        repository.findGroupByCode(code).addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                com.google.firebase.firestore.DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                Group group = doc.toObject(Group.class);
                if (group != null) {
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
        logsListenerRemove();
    }

    // --- RecyclerView Adapter ---
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