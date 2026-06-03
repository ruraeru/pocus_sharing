package com.example.pocussharing;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pocussharing.model.MemberStatus;

import java.util.List;
import java.util.Locale;

/**
 * 그룹 멤버 목록을 표시하기 위한 리사이클러뷰 어댑터
 */
public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.ViewHolder> {
    private final List<MemberStatus> list;
    private final OnMemberLongClickListener longClickListener;

    interface OnMemberLongClickListener {
        void onLongClick(MemberStatus status);
    }

    MemberAdapter(List<MemberStatus> list, OnMemberLongClickListener listener) {
        this.list = list;
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        MemberStatus status = list.get(position);

        // 순위 표시 (1부터 시작)
        holder.tvRank.setText(String.valueOf(position + 1));

        if(position == 0) holder.tvRank.setTextColor(0xFFCC3333);

        holder.tvName.setText(status.getName());

        // 멤버의 현재 상태(집중/휴식) 설정
        holder.tvFocus.setText(status.isFocus() ? "집중" : "휴식");
        holder.tvFocus.setTextColor(status.isFocus() ? 0xFFCC3333 : 0xFF4CAF50);

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
        holder.tvTotalToday.setText(String.format(Locale.getDefault(), "오늘\n %d시간 %d분 %d초", th, tm, ts));

        holder.itemView.setOnLongClickListener(v -> {
            longClickListener.onLongClick(status);
            return true;
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTime, tvTotalToday, tvFocus, tvRank;
        ViewHolder(View v) {
            super(v);
            tvRank = v.findViewById(R.id.tv_rank);
            tvName = v.findViewById(R.id.tv_name);
            tvTime = v.findViewById(R.id.tv_time);
            tvTotalToday = v.findViewById(R.id.tv_total_today);
            tvFocus = v.findViewById(R.id.tv_focus);
        }
    }
}
