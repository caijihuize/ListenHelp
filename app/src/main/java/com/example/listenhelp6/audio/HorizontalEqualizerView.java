package com.example.listenhelp6.audio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * 横向均衡器视图 - 滑块水平方向移动，避免与ScrollView冲突
 */
public class HorizontalEqualizerView extends View {
    private static final String TAG = "HorizontalEqualizerView";
    private static final int DEFAULT_BAND_COUNT = 8;
    private static final int DEFAULT_COLOR = Color.parseColor("#1E88E5");
    private static final int DISABLED_COLOR = Color.parseColor("#9E9E9E");
    private static final int MAX_GAIN = 15;
    private static final int MIN_GAIN = -15;
    
    private Paint sliderPaint;
    private Paint trackPaint;
    private Paint textPaint;
    private Paint linePaint;
    private RectF sliderRect;
    
    private int bandCount = DEFAULT_BAND_COUNT;
    private int[] bandGains;
    private String[] bandLabels = {"60Hz", "230Hz", "910Hz", "1.8kHz", "3.6kHz", "7.2kHz", "14kHz", "20kHz"};
    private int selectedBand = -1;
    
    private OnEqualizerChangeListener listener;
    private boolean isControlEnabled = true;
    
    public interface OnEqualizerChangeListener {
        void onBandChanged(int band, int gain);
    }
    
    public HorizontalEqualizerView(Context context) {
        super(context);
        init();
    }
    
    public HorizontalEqualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public HorizontalEqualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        sliderPaint = new Paint();
        sliderPaint.setColor(DEFAULT_COLOR);
        sliderPaint.setStyle(Paint.Style.FILL);
        sliderPaint.setAntiAlias(true);
        
        trackPaint = new Paint();
        trackPaint.setColor(Color.LTGRAY);
        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setAntiAlias(true);
        
        textPaint = new Paint();
        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(24);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        
        linePaint = new Paint();
        linePaint.setColor(Color.GRAY);
        linePaint.setStrokeWidth(1);
        linePaint.setStyle(Paint.Style.STROKE);
        
        sliderRect = new RectF();
        
        bandGains = new int[bandCount];
        // 初始化为0dB
        for (int i = 0; i < bandCount; i++) {
            bandGains[i] = 0;
        }
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        if (this.isControlEnabled != enabled) {
            this.isControlEnabled = enabled;
            Log.d(TAG, "均衡器滑块状态改变: " + (enabled ? "启用" : "禁用"));
            invalidate();
        }
        super.setEnabled(enabled);
    }
    
    @Override
    public boolean isEnabled() {
        return isControlEnabled;
    }
    
    public void setOnEqualizerChangeListener(OnEqualizerChangeListener listener) {
        this.listener = listener;
    }
    
    public void setBandLevels(short[] levels) {
        if (levels != null && levels.length == bandCount) {
            for (int i = 0; i < bandCount; i++) {
                bandGains[i] = levels[i];
            }
            invalidate();
        }
    }
    
    public int[] getBandLevels() {
        return bandGains.clone();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        
        // 计算每个频段的高度
        float bandHeight = height / bandCount;
        float sliderWidth = width * 0.8f;
        float leftMargin = (width - sliderWidth) / 2;
        
        // 根据启用状态设置滑块颜色
        sliderPaint.setColor(isControlEnabled ? DEFAULT_COLOR : DISABLED_COLOR);
        
        // 绘制频段控制条
        for (int i = 0; i < bandCount; i++) {
            float top = i * bandHeight + bandHeight * 0.2f;
            float bottom = (i + 1) * bandHeight - bandHeight * 0.2f;
            float centerY = (top + bottom) / 2;
            
            // 绘制滑轨背景
            RectF trackRect = new RectF(leftMargin, centerY - 4, leftMargin + sliderWidth, centerY + 4);
            canvas.drawRect(trackRect, trackPaint);
            
            // 中点（0dB）标记
            float centerX = leftMargin + sliderWidth / 2;
            canvas.drawLine(centerX, top, centerX, bottom, linePaint);
            
            // 计算滑块位置（水平方向）
            float gainRatio = (float) (bandGains[i] - MIN_GAIN) / (MAX_GAIN - MIN_GAIN);
            float sliderX = leftMargin + gainRatio * sliderWidth;
            
            // 绘制滑块
            float sliderRadius = bandHeight * 0.3f;
            canvas.drawCircle(sliderX, centerY, sliderRadius, sliderPaint);
            
            // 绘制频段标签
            float labelX = leftMargin - 10;
            canvas.drawText(bandLabels[i], labelX, centerY + 8, textPaint);
            
            // 绘制增益值
            String gainText = bandGains[i] > 0 ? "+" + bandGains[i] : String.valueOf(bandGains[i]);
            float gainX = leftMargin + sliderWidth + 10;
            canvas.drawText(gainText, gainX, centerY + 8, textPaint);
        }
        
        // 如果禁用，绘制提示信息
        if (!isControlEnabled) {
            textPaint.setTextSize(30);
            textPaint.setColor(Color.GRAY);
            canvas.drawText("选择'自定义'模式才能调整均衡器", width / 2, height / 2 + 40, textPaint);
            textPaint.setTextSize(24);
            textPaint.setColor(Color.DKGRAY);
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 如果控件被禁用，不处理触摸事件
        if (!isControlEnabled || !isEnabled()) {
            return false;
        }
        
        float x = event.getX();
        float y = event.getY();
        int width = getWidth();
        int height = getHeight();
        float bandHeight = height / bandCount;
        
        // 计算滑块区域
        float sliderWidth = width * 0.8f;
        float leftMargin = (width - sliderWidth) / 2;
        
        // 判断是否在滑块区域内
        if (x < leftMargin || x > leftMargin + sliderWidth) {
            return false;
        }
        
        // 计算触摸的频段
        int band = (int) (y / bandHeight);
        if (band < 0) band = 0;
        if (band >= bandCount) band = bandCount - 1;
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                selectedBand = band;
                updateBandGain(selectedBand, x, leftMargin, sliderWidth);
                return true;
                
            case MotionEvent.ACTION_MOVE:
                if (selectedBand >= 0) {
                    updateBandGain(selectedBand, x, leftMargin, sliderWidth);
                }
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                selectedBand = -1;
                return true;
        }
        
        return super.onTouchEvent(event);
    }
    
    private void updateBandGain(int band, float x, float leftMargin, float sliderWidth) {
        if (band < 0 || band >= bandCount) return;
        
        // 计算增益值，将x坐标从滑块区域映射到MIN_GAIN到MAX_GAIN的范围
        float gainRatio = (x - leftMargin) / sliderWidth;
        gainRatio = Math.max(0, Math.min(1, gainRatio)); // 确保在0-1范围内
        int gain = (int) (MIN_GAIN + gainRatio * (MAX_GAIN - MIN_GAIN));
        
        // 限制在合理范围内并取整
        gain = Math.max(MIN_GAIN, Math.min(MAX_GAIN, gain));
        
        if (bandGains[band] != gain) {
            bandGains[band] = gain;
            invalidate();
            
            // 回调监听器
            if (listener != null) {
                listener.onBandChanged(band, gain);
            }
        }
    }
} 