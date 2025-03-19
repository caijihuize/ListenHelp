package com.example.listenhelp6.audio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;
import java.util.Arrays;

/**
 * 音频波形显示视图
 */
public class WaveformView extends View {
    
    private static final String TAG = "WaveformView";
    private static final int DEFAULT_SAMPLES_COUNT = 128;
    private static final int HISTORY_SIZE = 50; // 历史数据帧数量
    private static final int DEFAULT_COLOR = Color.parseColor("#1E88E5");
    private static final int DEFAULT_BACKGROUND_COLOR = Color.parseColor("#EEEEEE");
    
    private Paint waveformPaint;
    private Paint bgPaint;
    private Paint textPaint;  // 文字绘制画笔
    private Path waveformPath;
    private RectF drawRect;
    
    private float[] samples;
    private float[] displaySamples;
    private float[][] historyBuffer; // 历史数据缓冲区
    private int historyIndex = 0; // 当前历史缓冲区位置
    private boolean historyBufferFull = false; // 历史缓冲区是否已填满
    
    private boolean isMirrored = true; // 是否显示镜像波形（上下对称）
    private boolean hasReceivedData = false; // 标记是否已收到真实数据
    
    public WaveformView(Context context) {
        super(context);
        init();
    }
    
    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public WaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        waveformPaint = new Paint();
        waveformPaint.setColor(DEFAULT_COLOR);
        waveformPaint.setStyle(Paint.Style.FILL);
        waveformPaint.setAntiAlias(true);
        
        bgPaint = new Paint();
        bgPaint.setColor(DEFAULT_BACKGROUND_COLOR);
        bgPaint.setStyle(Paint.Style.FILL);
        
        textPaint = new Paint();
        textPaint.setColor(Color.GRAY);
        textPaint.setTextSize(30);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        
        waveformPath = new Path();
        drawRect = new RectF();
        
        samples = new float[DEFAULT_SAMPLES_COUNT];
        displaySamples = new float[DEFAULT_SAMPLES_COUNT];
        
        // 初始化历史缓冲区
        historyBuffer = new float[HISTORY_SIZE][DEFAULT_SAMPLES_COUNT];
        
        // 初始化为一个正弦波，使初始波形可见
        for (int i = 0; i < DEFAULT_SAMPLES_COUNT; i++) {
            float phase = (float)i / DEFAULT_SAMPLES_COUNT * 4 * (float)Math.PI;
            samples[i] = 0.3f * (float)Math.sin(phase);
            displaySamples[i] = samples[i];
        }
        
        // 初始化历史缓冲区
        for (int i = 0; i < HISTORY_SIZE; i++) {
            for (int j = 0; j < DEFAULT_SAMPLES_COUNT; j++) {
                float phase = (float)j / DEFAULT_SAMPLES_COUNT * 4 * (float)Math.PI;
                historyBuffer[i][j] = 0.3f * (float)Math.sin(phase);
            }
        }
    }
    
    /**
     * 更新波形样本数据
     * @param data 音频样本数据（范围[-1,1]）
     */
    public void updateWaveform(float[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        
        hasReceivedData = true;
        
        // 将输入数据重采样到我们的样本数
        int inputLength = data.length;
        for (int i = 0; i < DEFAULT_SAMPLES_COUNT; i++) {
            int inputIndex = (int) ((float) i / DEFAULT_SAMPLES_COUNT * inputLength);
            if (inputIndex < inputLength) {
                // 平滑过渡系数
                samples[i] = 0.3f * samples[i] + 0.7f * Math.abs(data[inputIndex]);
            }
        }
        
        // 添加到历史缓冲区
        addToHistory(samples);
        
        // 立即重绘
        postInvalidateOnAnimation();
    }

    /**
     * 添加数据到历史缓冲区
     */
    private void addToHistory(float[] newData) {
        // 将新数据复制到历史缓冲区当前位置
        System.arraycopy(newData, 0, historyBuffer[historyIndex], 0, DEFAULT_SAMPLES_COUNT);
        
        // 更新历史缓冲区索引
        historyIndex = (historyIndex + 1) % HISTORY_SIZE;
        
        // 检查是否已填满历史缓冲区
        if (historyIndex == 0) {
            historyBufferFull = true;
        }
    }

    /**
     * 添加随机数据，用于测试显示效果
     */
    public void addRandomSample() {
        // 创建一组新的随机样本
        float[] randomSamples = new float[DEFAULT_SAMPLES_COUNT];
        for (int i = 0; i < DEFAULT_SAMPLES_COUNT; i++) {
            randomSamples[i] = (float) (Math.random() * 0.7);
        }
        
        // 添加到历史缓冲区
        addToHistory(randomSamples);
        
        postInvalidateOnAnimation();
    }
    
    /**
     * 设置波形颜色
     */
    public void setWaveformColor(int color) {
        waveformPaint.setColor(color);
        invalidate();
    }
    
    /**
     * 设置是否显示镜像波形
     */
    public void setMirrored(boolean mirrored) {
        this.isMirrored = mirrored;
        invalidate();
    }
    
    /**
     * 清除波形数据
     */
    public void clearWaveform() {
        for (int i = 0; i < DEFAULT_SAMPLES_COUNT; i++) {
            samples[i] = 0;
            displaySamples[i] = 0;
        }
        
        // 清空历史缓冲区
        for (int i = 0; i < HISTORY_SIZE; i++) {
            Arrays.fill(historyBuffer[i], 0);
        }
        
        historyIndex = 0;
        historyBufferFull = false;
        hasReceivedData = false;  // 重置数据接收标志
        invalidate();
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        drawRect.set(0, 0, w, h);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        final int width = getWidth();
        final int height = getHeight();
        
        // 绘制背景
        canvas.drawRect(0, 0, width, height, bgPaint);
        
        if (!hasReceivedData) {
            // 如果还没有收到真实数据，显示提示信息
            canvas.drawText("等待音频数据...", width/2, height/2 - 10, textPaint);
            return;
        }
        
        float centerY = height / 2f;
        
        // 从历史缓冲区中准备显示数据
        int availableFrames = historyBufferFull ? HISTORY_SIZE : historyIndex;
        
        // 找出最大振幅用于缩放
        float maxAmplitude = 0.0001f; // 避免除以零
        for (int frame = 0; frame < availableFrames; frame++) {
            int bufferIndex = (historyIndex - frame - 1 + HISTORY_SIZE) % HISTORY_SIZE;
            for (float sample : historyBuffer[bufferIndex]) {
                if (Math.abs(sample) > maxAmplitude) {
                    maxAmplitude = Math.abs(sample);
                }
            }
        }
        // 确保最小振幅，以便在低音量或静音时仍能看到波形
        maxAmplitude = Math.max(maxAmplitude, 0.05f);
        
        // 绘制历史数据波形
        float frameWidth = width / (float)HISTORY_SIZE;
        
        for (int frame = 0; frame < availableFrames; frame++) {
            int bufferIndex = (historyIndex - frame - 1 + HISTORY_SIZE) % HISTORY_SIZE;
            float[] frameData = historyBuffer[bufferIndex];
            
            // 创建波形路径
            waveformPath.reset();
            
            float startX = width - (frame + 1) * frameWidth;
            float endX = width - frame * frameWidth;
            
            waveformPath.moveTo(startX, centerY);
            
            int samplePoints = 20; // 每帧绘制的采样点数
            for (int i = 0; i <= samplePoints; i++) {
                float sampleIndex = (float)i / samplePoints * (DEFAULT_SAMPLES_COUNT - 1);
                int idx = (int)sampleIndex;
                float sample = frameData[idx] / maxAmplitude;
                
                float x = startX + (endX - startX) * ((float)i / samplePoints);
                float amplitude = sample * (height / 2 - 4);
                
                if (isMirrored) {
                    waveformPath.lineTo(x, centerY - amplitude);
                } else {
                    waveformPath.lineTo(x, centerY - amplitude);
                }
            }
            
            if (isMirrored) {
                // 绘制镜像波形（下半部分）
                for (int i = samplePoints; i >= 0; i--) {
                    float sampleIndex = (float)i / samplePoints * (DEFAULT_SAMPLES_COUNT - 1);
                    int idx = (int)sampleIndex;
                    float sample = frameData[idx] / maxAmplitude;
                    
                    float x = startX + (endX - startX) * ((float)i / samplePoints);
                    float amplitude = sample * (height / 2 - 4);
                    
                    waveformPath.lineTo(x, centerY + amplitude);
                }
            } else {
                waveformPath.lineTo(endX, centerY);
            }
            
            waveformPath.close();
            canvas.drawPath(waveformPath, waveformPaint);
        }
        
        // 绘制中心线
        Paint linePaint = new Paint();
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(1);
        canvas.drawLine(0, centerY, width, centerY, linePaint);
    }
} 