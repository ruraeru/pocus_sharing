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

    public interface OnTimerDialListener {
        void onDialChanged(float progress);
        void onDialSelected(float progress);
    }

    private OnTimerDialListener dialListener;

    public void setOnTimerDialListener(OnTimerDialListener listener) {
        this.dialListener = listener;
    }

    private Paint backgroundPaint;
    private Paint arcPaint;
    private Paint tickPaint;
    private Paint textPaint;
    private Paint centerPaint;
    private RectF arcBounds;
    private float progress = 0.0f;
    private int lastSnappedMinute = -1;

    public void setProgress(float progress) {
        this.progress = Math.min(1.0f, Math.max(0.0f, progress));
        invalidate();
    }

    public void setMode(boolean isFocus) {
        if (isFocus) {
            arcPaint.setColor(0xFFCC3333); // pocus_red
        } else {
            arcPaint.setColor(0xFF4CAF50); // pocus_green
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

    private void init() {
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.WHITE);
        backgroundPaint.setStyle(Paint.Style.FILL);

        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setColor(0xFFCC3333); // pocus_red
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int size = Math.min(width, height);
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = size / 2 - 40;

        // Draw rounded background square
        RectF bgRect = new RectF(centerX - size/2f, centerY - size/2f, centerX + size/2f, centerY + size/2f);
        canvas.drawRoundRect(bgRect, 40, 40, backgroundPaint);

        // Draw the red arc
        arcBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        float sweepAngle = progress * 360f;
        canvas.drawArc(arcBounds, -90, sweepAngle, true, arcPaint);

        // Draw ticks and numbers
        for (int i = 0; i < 60; i += 5) {
            double angle = Math.toRadians(i * 6 - 90);
            float tickX = (float) (centerX + radius * Math.cos(angle));
            float tickY = (float) (centerY + radius * Math.sin(angle));
            
            float numX = (float) (centerX + (radius + 25) * Math.cos(angle));
            float numY = (float) (centerY + (radius + 25) * Math.sin(angle));

            canvas.drawText(String.valueOf(i), numX, numY + 10, textPaint);
        }
        
        // Minor ticks
        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 6 - 90);
            float startX = (float) (centerX + radius * 0.95 * Math.cos(angle));
            float startY = (float) (centerY + radius * 0.95 * Math.sin(angle));
            float endX = (float) (centerX + radius * Math.cos(angle));
            float endY = (float) (centerY + radius * Math.sin(angle));
            canvas.drawLine(startX, startY, endX, endY, tickPaint);
        }

        // Draw center circle
        canvas.drawCircle(centerX, centerY, 40, centerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                double angle = Math.toDegrees(Math.atan2(y - centerY, x - centerX));
                angle += 90;
                if (angle < 0) angle += 360;
                
                float rawProgress = (float) (angle / 360f);
                int minutes = Math.round(rawProgress * 60);
                
                if (minutes != lastSnappedMinute) {
                    lastSnappedMinute = minutes;
                    float snappedProgress = minutes / 60f;
                    setProgress(snappedProgress);
                    
                    if (dialListener != null) {
                        dialListener.onDialChanged(snappedProgress);
                    }
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
                return true;
            case MotionEvent.ACTION_UP:
                lastSnappedMinute = -1;
                // If it was just a click (or very small move), toggle timer
                // For simplicity here, just callback to select
                if (dialListener != null) {
                    dialListener.onDialSelected(progress);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}