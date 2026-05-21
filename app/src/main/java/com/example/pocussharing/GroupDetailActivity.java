package com.example.pocussharing;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pocussharing.model.MemberStatus;
import com.example.pocussharing.repository.RtdbRepository;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class GroupDetailActivity extends AppCompatActivity {

    private String groupId;
    private RtdbRepository repository;
    private MemberAdapter adapter;
    private List<MemberStatus> memberList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        groupId = getIntent().getStringExtra("groupId");
        if (groupId == null) groupId = "main_group"; // fallback

        RecyclerView rvMembers = findViewById(R.id.rv_members);
        rvMembers.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new MemberAdapter(memberList);
        rvMembers.setAdapter(adapter);

        repository = new RtdbRepository();
        listenToPresence();
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

        MemberAdapter(List<MemberStatus> list) { this.list = list; }

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
            
            // UI updates
            holder.timerView.setMode(status.isFocus());
            float progress = (float) status.getTimeLeft() / (60 * 60 * 1000);
            holder.timerView.setProgress(progress);
            
            int seconds = (int) (status.getTimeLeft() / 1000);
            int mins = seconds / 60;
            int secs = seconds % 60;
            holder.tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
        }

        @Override
        public int getItemCount() { return list.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvTime;
            TimerView timerView;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_name);
                tvTime = v.findViewById(R.id.tv_time);
                timerView = v.findViewById(R.id.timer_view);
            }
        }
    }
}