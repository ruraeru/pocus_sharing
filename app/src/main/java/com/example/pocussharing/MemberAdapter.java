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
 * 그룹 멤버 목록을 리사이클러뷰에 표시
 * 각 멤버의 순위, 이름, 현재 상태(집중/휴식), 타이머 시간, 누적 시간을 보여줌
 */
public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.ViewHolder> {

    private final List<MemberStatus> list; // 멤버 상태 데이터 리스트
    private final OnMemberLongClickListener longClickListener; // 롱클릭(추방) 콜백 인터페이스

    /**
     * 멤버 카드 롱클릭 이벤트를 처리하기 위한 내부 인터페이스
     */
    interface OnMemberLongClickListener {
        void onLongClick(MemberStatus status);
    }

    /**
     * 생성자: 데이터 리스트와 리스너를 전달받아 초기화
     */
    MemberAdapter(List<MemberStatus> list, OnMemberLongClickListener listener) {
        this.list = list;
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // [중요 구문] item_member 레이아웃을 가져와서 뷰홀더 만듦
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // 현재 행의 데이터 모델을 가져옴
        MemberStatus status = list.get(position);

        // 순위 표시 (리스트 인덱스는 0부터이므로 +1 해줌)
        holder.tvRank.setText(String.valueOf(position + 1));

        // 1등은 특별히 빨간색으로 강조
        if(position == 0) {
            holder.tvRank.setTextColor(0xFFCC3333); // 강조색
        } else {
            holder.tvRank.setTextColor(0xFF666666); // 기본 회색
        }

        // 멤버 닉네임 세팅
        holder.tvName.setText(status.getName());

        // 현재 모드(집중/휴식) 및 색상 설정
        holder.tvFocus.setText(status.isFocus() ? "집중" : "휴식");
        holder.tvFocus.setTextColor(status.isFocus() ? 0xFFCC3333 : 0xFF4CAF50);

        // 타이머 남은 시간 포맷팅 (HH:mm:ss)
        int seconds = (int) (status.getTimeLeft() / 1000);
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        holder.tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));

        // 오늘 하루 총 누적 집중 시간 표시
        long totalSec = status.getTodayFocusTime() / 1000;
        long th = totalSec / 3600;
        long tm = (totalSec % 3600) / 60;
        long ts = totalSec % 60;
        holder.tvTotalToday.setText(String.format(Locale.getDefault(), "오늘\n %d시간 %d분 %d초", th, tm, ts));

        // 롱클릭 시 추방 다이얼로그 호출용 리스너 연결
        holder.itemView.setOnLongClickListener(v -> {
            longClickListener.onLongClick(status);
            return true; // 이벤트 소비
        });
    }

    @Override
    public int getItemCount() { 
        return list.size(); // 리스트 전체 개수 반환
    }

    /**
     * ViewHolder: 리사이클러뷰의 각 아이템 뷰를 보관
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTime, tvTotalToday, tvFocus, tvRank;
        
        ViewHolder(View v) {
            super(v);
            // 레이아웃의 ID와 자바 객체 연결
            tvRank = v.findViewById(R.id.tv_rank);
            tvName = v.findViewById(R.id.tv_name);
            tvTime = v.findViewById(R.id.tv_time);
            tvTotalToday = v.findViewById(R.id.tv_total_today);
            tvFocus = v.findViewById(R.id.tv_focus);
        }
    }
}
