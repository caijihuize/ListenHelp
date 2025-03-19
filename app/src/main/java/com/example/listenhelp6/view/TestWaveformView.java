package com.example.listenhelp6.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import java.util.Arrays;

/**
 * 用于测试的简单波形视图
 * 不依赖任何外部数据源，自带波形动画
 */
public class TestWaveformView extends View {
    
    private static final int SAMPLE_COUNT = 100;
    private static final int HISTORY_SIZE = 50; // 历史数据帧数量
    
    private Paint paint;
    private Paint linePaint;
    private Paint textPaint;
    private Path path;
    
    private float[][] historyBuffer; // 历史数据缓冲区
    private int historyIndex = 0; // 当前历史缓冲区位置
    private boolean historyBufferFull = false; // 历史缓冲区是否已填满
    
    private float phase = 0;
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable animator = new Runnable() {
        @Override
        public void run() {
            updateWaveform();
            handler.postDelayed(this, 100); // 降低到10fps，使波形变化更慢
        }
    };
    
    public TestWaveformView(Context context) {
        super(context);
        init();
    }
    
    public TestWaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public TestWaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        
        linePaint = new Paint();
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(2);
        
        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(30);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        
        path = new Path();
        
        // 初始化历史缓冲区
        historyBuffer = new float[HISTORY_SIZE][SAMPLE_COUNT];
        
        // 初始化样本
        for (int i = 0; i < HISTORY_SIZE; i++) {
            Arrays.fill(historyBuffer[i], 0);
        }
    }
    
    private void updateWaveform() {
        phase += 0.05f; // 减慢相位变化，使波形变化更加缓慢
        
        // 生成新一帧的波形数据
        float[] newFrame = new float[SAMPLE_COUNT];
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            float x = (float)i / SAMPLE_COUNT;
            // 使用多个正弦波合成一个更复杂的波形
            newFrame[i] = (float)(Math.sin(x * 10 + phase) * 0.5 + Math.sin(x * 20 + phase * 0.7) * 0.3);
        }
        
        // 添加到历史缓冲区
        System.arraycopy(newFrame, 0, historyBuffer[historyIndex], 0, SAMPLE_COUNT);
        
        // 更新历史缓冲区索引
        historyIndex = (historyIndex + 1) % HISTORY_SIZE;
        
        // 检查是否已填满历史缓冲区
        if (historyIndex == 0) {
            historyBufferFull = true;
        }
        
        invalidate();
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }
    
    public void startAnimation() {
        handler.removeCallbacks(animator);
        handler.post(animator);
    }
    
    public void stopAnimation() {
        handler.removeCallbacks(animator);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        float centerY = height / 2f;
        
        // 绘制背景
        canvas.drawColor(Color.LTGRAY);
        
        // 绘制中心线
        canvas.drawLine(0, centerY, width, centerY, linePaint);
        
        // 绘制说明文字
        canvas.drawText("测试波形", width/2, 40, textPaint);
        
        // 从历史缓冲区中准备显示数据
        int availableFrames = historyBufferFull ? HISTORY_SIZE : historyIndex;
        
        // 计算每帧占用的宽度
        float frameWidth = width / (float)HISTORY_SIZE;
        
        // 绘制历史数据波形
        for (int frame = 0; frame < availableFrames; frame++) {
            int bufferIndex = (historyIndex - frame - 1 + HISTORY_SIZE) % HISTORY_SIZE;
            float[] frameData = historyBuffer[bufferIndex];
            
            // 构建波形路径
            path.reset();
            
            float startX = width - (frame + 1) * frameWidth;
            float endX = width - frame * frameWidth;
            
            path.moveTo(startX, centerY);
            
            // 绘制波形上半部分
            int samplePoints = 20; // 每帧绘制的采样点数
            for (int i = 0; i <= samplePoints; i++) {
                float sampleIndex = (float)i / samplePoints * (SAMPLE_COUNT - 1);
                int idx = (int)sampleIndex;
                float amplitude = frameData[idx] * height / 3;
                
                float x = startX + (endX - startX) * ((float)i / samplePoints);
                path.lineTo(x, centerY - amplitude);
            }
            
            // 绘制波形下半部分（镜像）
            for (int i = samplePoints; i >= 0; i--) {
                float sampleIndex = (float)i / samplePoints * (SAMPLE_COUNT - 1);
                int idx = (int)sampleIndex;
                float amplitude = frameData[idx] * height / 3;
                
                float x = startX + (endX - startX) * ((float)i / samplePoints);
                path.lineTo(x, centerY + amplitude);
            }
            
            path.close();
            canvas.drawPath(path, paint);
        }
    }
} 