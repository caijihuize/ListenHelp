package com.example.listenhelp6.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class WaveformView extends View {
    private static final int DEFAULT_NUM_SAMPLES = 128;
    private static final int DEFAULT_LINE_COLOR = Color.BLUE;
    private static final int DEFAULT_BACKGROUND_COLOR = Color.WHITE;
    
    private final Paint linePaint;
    private final Path path;
    private float[] samples;
    private int numSamples;
    
    public WaveformView(Context context) {
        this(context, null);
    }
    
    public WaveformView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public WaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        
        linePaint = new Paint();
        linePaint.setColor(DEFAULT_LINE_COLOR);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2f);
        linePaint.setAntiAlias(true);
        
        path = new Path();
        numSamples = DEFAULT_NUM_SAMPLES;
        samples = new float[numSamples];
        
        setBackgroundColor(DEFAULT_BACKGROUND_COLOR);
    }
    
    /**
     * 设置波形颜色
     */
    public void setWaveColor(int color) {
        linePaint.setColor(color);
        invalidate();
    }
    
    /**
     * 设置样本数量
     */
    public void setNumSamples(int numSamples) {
        this.numSamples = numSamples;
        samples = new float[numSamples];
        invalidate();
    }
    
    /**
     * 更新波形数据
     */
    public void updateAudioData(short[] buffer, int bufferSize) {
        // 将buffer中的数据规范化并映射到samples数组
        int step = bufferSize / numSamples;
        if (step <= 0) step = 1;
        
        for (int i = 0; i < numSamples; i++) {
            int bufferIndex = i * step;
            if (bufferIndex < bufferSize) {
                // 将short范围(-32768~32767)映射到0~1
                float normalized = (float) buffer[bufferIndex] / Short.MAX_VALUE;
                samples[i] = normalized;
            } else {
                samples[i] = 0;
            }
        }
        
        // 请求重绘
        postInvalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        
        if (width <= 0 || height <= 0 || samples.length == 0) {
            return;
        }
        
        // 清除现有路径
        path.reset();
        
        // 计算每个采样点之间的间隔
        float xStep = (float) width / numSamples;
        float centerY = height / 2f;
        float amplitude = height / 2f * 0.9f; // 使用90%的高度作为振幅
        
        // 开始绘制波形
        for (int i = 0; i < numSamples; i++) {
            float x = i * xStep;
            float y = centerY - samples[i] * amplitude;
            
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        
        // 绘制路径
        canvas.drawPath(path, linePaint);
    }
} 