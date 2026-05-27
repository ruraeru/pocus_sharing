/**
 * TimerView.java
 * 타이머의 원형 다이얼을 표시하고 사용자의 터치 입력을 통해 시간을 설정할 수 있는 커스텀 뷰입니다.
 * 집중(FOCUS) 및 휴식(REST) 모드에 따라 색상이 변경되며, 60분 단위의 눈금과 숫자를 표시합니다.
 */
package com.example.pocussharing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class TimerView extends View {

    /**
     * 타이머 다이얼 조작 시 발생하는 이벤트를 처리하기 위한 인터페이스입니다.
     */
    public interface OnTimerDialListener {
        /**
         * 다이얼을 돌리는 동안 진행률이 변경될 때 호출됩니다.
         * @param progress 현재 선택된 시간의 진행률 (0.0 ~ 1.0)
         */
        void onDialChanged(float progress);
        
        /**
         * 다이얼 조작을 마치고 손을 뗐을 때 최종 선택된 진행률을 전달합니다.
         * @param progress 최종 선택된 시간의 진행률 (0.0 ~ 1.0)
         */
        void onDialSelected(float progress);
    }

    private OnTimerDialListener dialListener;

    /**
     * 다이얼 리스너를 설정합니다.
     */
    public void setOnTimerDialListener(OnTimerDialListener listener) {
        this.dialListener = listener;
    }

    private Paint backgroundPaint; // 배경용 Paint
    private Paint arcPaint;        // 타이머 진행 표시용 Paint
    private Paint tickPaint;       // 눈금용 Paint
    private Paint textPaint;       // 숫자 텍스트용 Paint
    private Paint centerPaint;     // 중심점용 Paint
    private RectF arcBounds;       // 호(Arc)를 그릴 범위
    private float progress = 0.0f; // 현재 진행률 (0.0 ~ 1.0)
    private int lastSnappedMinute = -1; // 마지막으로 진동이 발생한 분(Minute)

    /**
     * 타이머의 현재 진행률을 설정하고 뷰를 갱신합니다.
     * @param progress 설정할 진행률 (0.0 ~ 1.0)
     */
    public void setProgress(float progress) {
        this.progress = Math.min(1.0f, Math.max(0.0f, progress));
        invalidate();
    }

    /**
     * 타이머 모드(집중/휴식)에 따라 다이얼의 색상을 변경합니다.
     * @param isFocus true이면 집중 모드(빨간색), false이면 휴식 모드(초록색)
     */
    public void setMode(boolean isFocus) {
        if (isFocus) {
            arcPaint.setColor(0xFFCC3333); // pocus_red: 집중 모드 색상
        } else {
            arcPaint.setColor(0xFF4CAF50); // pocus_green: 휴식 모드 색상
        }
        invalidate();
    }

    public TimerView(Context context) {
        super(context);
        init();
    }

    public TimerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 뷰 초기화 및 Paint 객체들을 설정합니다.
     */
    private void init() {
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.WHITE);
        backgroundPaint.setStyle(Paint.Style.FILL);

        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setColor(0xFFCC3333); // 기본값은 집중 모드 색상
        arcPaint.setStyle(Paint.Style.FILL);

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setColor(Color.BLACK);
        tickPaint.setStrokeWidth(2f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(30f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(0xFFCCCCCC); // pocus_gray
        centerPaint.setStyle(Paint.Style.FILL);

        arcBounds = new RectF();
    }

    /**
     * 커스텀 뷰를 그리는 로직입니다.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int size = Math.min(width, height);
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = size / 2 - 40; // 여백 확보

        // 1. 둥근 사각형 배경 그리기
        RectF bgRect = new RectF(centerX - size/2f, centerY - size/2f, centerX + size/2f, centerY + size/2f);
        canvas.drawRoundRect(bgRect, 40, 40, backgroundPaint);

        // 2. 타이머 진행률(호) 그리기
        arcBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        float sweepAngle = progress * 360f;
        canvas.drawArc(arcBounds, -90, sweepAngle, true, arcPaint); // -90도(12시 방향)부터 시작

        // 3. 5분 단위 숫자 그리기
        for (int i = 0; i < 60; i += 5) {
            double angle = Math.toRadians(i * 6 - 90);

            float numX = (float) (centerX + (radius + 25) * Math.cos(angle));
            float numY = (float) (centerY + (radius + 25) * Math.sin(angle));

            canvas.drawText(String.valueOf(i), numX, numY + 10, textPaint);
        }
        
        // 4. 1분 단위 눈금 그리기
        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 6 - 90);
            float startX = (float) (centerX + radius * 0.95 * Math.cos(angle));
            float startY = (float) (centerY + radius * 0.95 * Math.sin(angle));
            float endX = (float) (centerX + radius * Math.cos(angle));
            float endY = (float) (centerY + radius * Math.sin(angle));
            canvas.drawLine(startX, startY, endX, endY, tickPaint);
        }

        // 5. 중심 원 그리기
        canvas.drawCircle(centerX, centerY, 40, centerPaint);
    }

    /**
     * 터치 이벤트를 처리하여 다이얼 조작 기능을 구현합니다.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                // 중심점 기준 터치 좌표의 각도 계산
                double angle = Math.toDegrees(Math.atan2(y - centerY, x - centerX));
                angle += 90; // 12시 방향을 0도로 설정
                if (angle < 0) angle += 360;
                
                float rawProgress = (float) (angle / 360f);
                int minutes = Math.round(rawProgress * 60); // 분 단위로 스냅(Snap)
                
                if (minutes != lastSnappedMinute) {
                    lastSnappedMinute = minutes;
                    float snappedProgress = minutes / 60f;
                    setProgress(snappedProgress);
                    
                    if (dialListener != null) {
                        dialListener.onDialChanged(snappedProgress);
                    }
                    // 햅틱 피드백 발생
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
                return true;
            case MotionEvent.ACTION_UP:
                lastSnappedMinute = -1;
                if (dialListener != null) {
                    dialListener.onDialSelected(progress);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}
