package com.example.listenhelp6.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;

import com.example.listenhelp6.R;

public class EqualizerDialog extends Dialog {
    private static final int BAND_COUNT = 8;
    
    private final Context context;
    private final EqualizerListener listener;
    private final short[] currentLevels;
    private final Pair<Short, Short> levelRange;
    private final float[] centerFrequencies;
    private final SeekBar[] bandControls = new SeekBar[BAND_COUNT];

    public interface EqualizerListener {
        void onEqualizerBandChanged(int band, short level);
        void onEqualizerSettingsSaved();
    }

    public EqualizerDialog(Context context, EqualizerListener listener, 
                          short[] levels, Pair<Short, Short> levelRange,
                          float[] centerFrequencies) {
        super(context);
        this.context = context;
        this.listener = listener;
        this.currentLevels = levels.clone();
        this.levelRange = levelRange;
        this.centerFrequencies = centerFrequencies;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_equalizer, null);
        setContentView(view);
        
        // 设置对话框标题
        setTitle("均衡器设置");
        
        // 初始化均衡器滑块控件
        bandControls[0] = view.findViewById(R.id.equalizer_band_1);
        bandControls[1] = view.findViewById(R.id.equalizer_band_2);
        bandControls[2] = view.findViewById(R.id.equalizer_band_3);
        bandControls[3] = view.findViewById(R.id.equalizer_band_4);
        bandControls[4] = view.findViewById(R.id.equalizer_band_5);
        bandControls[5] = view.findViewById(R.id.equalizer_band_6);
        bandControls[6] = view.findViewById(R.id.equalizer_band_7);
        bandControls[7] = view.findViewById(R.id.equalizer_band_8);
        
        // 配置每个滑块
        setupBandControls();
        
        // 设置保存按钮点击事件
        Button saveButton = view.findViewById(R.id.button_save_equalizer);
        saveButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEqualizerSettingsSaved();
            }
            dismiss();
        });
    }

    private void setupBandControls() {
        // 计算滑块范围映射关系
        final short minLevel = levelRange.first;
        final short maxLevel = levelRange.second;
        final int seekBarMax = 30; // SeekBar范围0-30
        
        for (int i = 0; i < BAND_COUNT && i < bandControls.length; i++) {
            final int bandIndex = i;
            SeekBar seekBar = bandControls[i];
            
            // 设置当前值
            int progress = mapLevelToProgress(currentLevels[i], minLevel, maxLevel, seekBarMax);
            seekBar.setMax(seekBarMax);
            seekBar.setProgress(progress);
            
            // 设置滑块变化监听器
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && listener != null) {
                        short level = mapProgressToLevel(progress, minLevel, maxLevel, seekBarMax);
                        currentLevels[bandIndex] = level;
                        listener.onEqualizerBandChanged(bandIndex, level);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // 不需要处理
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // 不需要处理
                }
            });
        }
    }
    
    // 将均衡器级别映射到SeekBar的进度值
    private int mapLevelToProgress(short level, short minLevel, short maxLevel, int seekBarMax) {
        return (int) ((level - minLevel) / (float) (maxLevel - minLevel) * seekBarMax);
    }
    
    // 将SeekBar的进度值映射回均衡器级别
    private short mapProgressToLevel(int progress, short minLevel, short maxLevel, int seekBarMax) {
        return (short) (minLevel + (progress / (float) seekBarMax) * (maxLevel - minLevel));
    }
} 