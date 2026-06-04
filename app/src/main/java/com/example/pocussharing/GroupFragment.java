/**
 * 참여 중인 그룹 목록을 표시하고, 새 그룹 생성 및 초대 코드로 가입 기능 제공
 * Firestore 실시간 리스너를 통해 그룹 목록 최신 상태 유지
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

    // [UI 및 데이터 블록]
    private RecyclerView rvGroups;          // 그룹 카드들이 표시될 리사이클러뷰
    private FloatingActionButton fabAdd, fabJoin; // 생성 및 가입 단추
    private GroupAdapter adapter;           // 리사이클러뷰용 어댑터
    private final List<Group> groupList = new ArrayList<>(); // 그룹 데이터 담는 리스트
    private FirestoreRepository repository;  // DB 접근소
    private ListenerRegistration groupsListener; // 실시간 변경 감시용 등록 객체

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_group, container, false);

        repository = new FirestoreRepository();
        rvGroups = view.findViewById(R.id.rv_groups);
        fabAdd = view.findViewById(R.id.fab_add);
        fabJoin = view.findViewById(R.id.fab_join);

        setupRecyclerView(); // 리스트 세팅
        setupGroupsListener(); // 실시간 데이터 감시 시작

        // 단추 클릭 이벤트 등록함
        fabAdd.setOnClickListener(v -> showCreateGroupDialog());
        fabJoin.setOnClickListener(v -> showJoinGroupDialog());

        return view;
    }

    /**
     * 리사이클러뷰와 어댑터 연결하고 레이아웃 형식(Linear) 정함
     */
    private void setupRecyclerView() {
        adapter = new GroupAdapter(groupList);
        rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        rvGroups.setAdapter(adapter);
    }

    /**
     * 내가 속한 그룹 목록의 변화를 Firestore에서 실시간으로 감시
     * [중요] 새로운 멤버가 들어오거나 그룹 정보가 바뀌면 즉시 화면 갱신
     */
    private void setupGroupsListener() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        groupsListenerRemove(); // 기존 중복 리스너 있으면 지움

        groupsListener = repository.getUserGroupsListener(uid, (value, error) -> {
            if (error != null) {
                Log.e("GroupFragment", "데이터 구독 실패함", error);
                return;
            }

            groupList.clear(); // 목록 비우고 새로 채움
            if (value != null) {
                for (com.google.firebase.firestore.DocumentSnapshot doc : value.getDocuments()) {
                    Group group = doc.toObject(Group.class);
                    if (group != null) {
                        group.setGroupId(doc.getId()); // 문서 ID 주입
                        groupList.add(group);
                    }
                }
            }
            adapter.notifyDataSetChanged(); // UI 다시 그림
        });
    }

    /**
     * 리스너 등록 해제하여 자원 낭비 막음
     */
    private void groupsListenerRemove() {
        if (groupsListener != null) {
            groupsListener.remove();
            groupsListener = null;
        }
    }

    /**
     * 그룹 생성 팝업창 띄움
     */
    private void showCreateGroupDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_group, null);
        EditText etName = dialogView.findViewById(R.id.et_group_name);

        new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setPositiveButton("생성", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (!name.isEmpty()) createGroup(name); // 이름 있으면 생성 진행
                    else Toast.makeText(getContext(), "이름을 입력함", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * 초대 코드 입력 팝업창 띄움
     */
    private void showJoinGroupDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_join_group, null);
        EditText etCode = dialogView.findViewById(R.id.et_group_code);

        new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setPositiveButton("참여", (dialog, which) -> {
                    String code = etCode.getText().toString().trim().toUpperCase();
                    if (!code.isEmpty()) joinGroupByCode(code); // 코드 있으면 가입 진행
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * 입력받은 코드로 DB 검색해서 그룹에 가입
     * [중요 로직] 이미 가입했는지, 인원이 꽉 찼는지 검증
     */
    private void joinGroupByCode(String code) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        repository.findGroupByCode(code).addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                com.google.firebase.firestore.DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                Group group = doc.toObject(Group.class);
                if (group != null) {
                    // 예외 처리
                    if (group.getMemberIds() != null && group.getMemberIds().contains(uid)) {
                        Toast.makeText(getContext(), "이미 가입된 그룹임", Toast.LENGTH_SHORT).show();
                    } else if (group.getMemberIds() != null && group.getMemberIds().size() >= group.getMaxMembers()) {
                        Toast.makeText(getContext(), "인원이 가득 참", Toast.LENGTH_SHORT).show();
                    } else {
                        // 최종 가입 처리
                        repository.joinGroup(doc.getId(), uid)
                                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), group.getGroupName() + " 가입함", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(getContext(), "가입 실패함", Toast.LENGTH_SHORT).show());
                    }
                }
            } else {
                Toast.makeText(getContext(), "잘못된 코드임", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 랜덤 6자리 코드를 생성해서 새로운 그룹 문서를 Firestore에 만듦
     */
    private void createGroup(String name) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        String randomCode = generateRandomCode();
        Group newGroup = new Group(name, randomCode, uid);
        repository.createGroup(newGroup)
                .addOnSuccessListener(docRef -> Toast.makeText(getContext(), "그룹 생성됨! 코드: " + randomCode, Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(), "생성 실패함", Toast.LENGTH_SHORT).show());
    }

    /**
     * 영문 대문자와 숫자를 섞어서 랜덤 문자열 생성
     */
    private String generateRandomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        groupsListenerRemove(); // 화면 파괴 시 감시 중단
    }

    /**
     * GroupAdapter: 리사이클러뷰에 그룹 정보를 바인딩
     */
    private class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {
        private final List<Group> groups;

        public GroupAdapter(List<Group> groups) { this.groups = groups; }

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
            
            // 클릭 시 그룹 상세 화면으로 이동
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), GroupDetailActivity.class);
                intent.putExtra("groupId", group.getGroupId());
                intent.putExtra("groupName", group.getGroupName());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return groups.size(); }

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
