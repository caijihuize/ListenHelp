package com.example.listenhelp6;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.listenhelp6.audio.AAudioManager;
import com.example.listenhelp6.audio.WaveformCallback;
import com.example.listenhelp6.audio.WaveformView;
import com.example.listenhelp6.service.AudioProcessingService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 200;
    private static final int PERMISSION_REQUEST_FOREGROUND_SERVICE = 201;
    
    // SharedPreferences常量
    private static final String PREFS_NAME = "ListenHelpSettings";
    private static final String KEY_INPUT_VOLUME = "input_volume";
    private static final String KEY_OUTPUT_VOLUME = "output_volume";
    private static final String KEY_AMPLIFICATION = "amplification";
    private static final String KEY_NOISE_REDUCTION = "noise_reduction";
    private static final String KEY_EQUALIZER_PRESET = "equalizer_preset";
    private static final String KEY_EQUALIZER_BAND_PREFIX = "equalizer_band_";

    // 均衡器预设
    private static final int PRESET_CUSTOM = 0;
    private static final int PRESET_FLAT = 1;
    private static final int PRESET_BASS_BOOST = 2;
    private static final int PRESET_TREBLE_BOOST = 3;
    private static final int PRESET_VOCAL_BOOST = 4;
    private static final int PRESET_BASS_REDUCTION = 5;

    // 音频管理器
    private AAudioManager audioManager;

    // 设备控制组件
    private Button buttonRefreshDevices;
    private Spinner spinnerMicrophone;
    private Spinner spinnerSpeaker;
    private List<AudioDeviceInfo> inputDevices;
    private List<AudioDeviceInfo> outputDevices;

    // 音量控制组件
    private SeekBar seekBarInputVolume;
    private SeekBar seekBarOutputVolume;
    private SeekBar seekBarAmplification;
    private TextView textAmplificationValue;
    private Switch switchNoiseReduction;

    // 服务启动和停止按钮
    private Button buttonAudioControl;
    private boolean isAudioRunning = false;

    // 均衡器滑块和显示
    private Spinner spinnerEqualizerPreset;
    private SeekBar[] eqSeekBars = new SeekBar[8];
    private TextView[] eqTextViews = new TextView[8];
    private int currentEqualizerPreset = PRESET_CUSTOM;

    // 波形显示组件
    private WaveformView inputWaveformView;
    private WaveformView outputWaveformView;

    private final Handler waveformHandler = new Handler(Looper.getMainLooper());
    private final Runnable waveformUpdater = new Runnable() {
        @Override
        public void run() {
            if (inputWaveformView != null) {
                inputWaveformView.addRandomSample();
            }
            if (outputWaveformView != null) {
                outputWaveformView.addRandomSample();
            }
            waveformHandler.postDelayed(this, 50); // 20fps
        }
    };

    // 服务相关
    private AudioProcessingService audioProcessingService;
    private boolean isServiceBound = false;
    
    // 用于绑定服务的ServiceConnection
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioProcessingService.LocalBinder binder = (AudioProcessingService.LocalBinder) service;
            audioProcessingService = binder.getService();
            audioProcessingService.setAudioManager(audioManager);
            isServiceBound = true;
            
            // 如果音频已经在运行，通知服务
            if (isAudioRunning) {
                audioProcessingService.startAudioProcessing();
            }
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            audioProcessingService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化视图
        initViews();
        
        // 初始化音频管理器
        audioManager = new AAudioManager(this);
        
        // 设置波形回调
        setupWaveformCallbacks();
        
        // 初始化均衡器预设
        setupEqualizerPresets();
        
        // 检查并请求权限
        checkAndRequestPermissions();
        
        // 请求忽略电池优化
        requestBatteryOptimizationExemption();
        
        // 启动和绑定服务
        startAndBindService();
    }

    private void initViews() {
        // 初始化UI组件
        spinnerMicrophone = findViewById(R.id.spinner_microphone);
        spinnerSpeaker = findViewById(R.id.spinner_speaker);
        seekBarInputVolume = findViewById(R.id.seekbar_input_volume);
        seekBarOutputVolume = findViewById(R.id.seekbar_output_volume);
        seekBarAmplification = findViewById(R.id.seekbar_amplification);
        textAmplificationValue = findViewById(R.id.text_amplification_value);
        switchNoiseReduction = findViewById(R.id.switch_noise_reduction);
        buttonAudioControl = findViewById(R.id.button_audio_control);
        buttonRefreshDevices = findViewById(R.id.button_refresh_devices);
        inputWaveformView = findViewById(R.id.input_waveform);
        outputWaveformView = findViewById(R.id.output_waveform);
        spinnerEqualizerPreset = findViewById(R.id.spinner_equalizer_preset);
        
        // 设置波形颜色
        inputWaveformView.setWaveformColor(ContextCompat.getColor(this, android.R.color.holo_blue_light));
        outputWaveformView.setWaveformColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
        
        // 初始化均衡器滑块和显示
        initEqualizerControls();
        
        // 设置监听器
        setupListeners();
    }
    
    private void initEqualizerControls() {
        // 初始化均衡器滑块和显示
        eqSeekBars[0] = findViewById(R.id.seekbar_eq_1);
        eqSeekBars[1] = findViewById(R.id.seekbar_eq_2);
        eqSeekBars[2] = findViewById(R.id.seekbar_eq_3);
        eqSeekBars[3] = findViewById(R.id.seekbar_eq_4);
        eqSeekBars[4] = findViewById(R.id.seekbar_eq_5);
        eqSeekBars[5] = findViewById(R.id.seekbar_eq_6);
        eqSeekBars[6] = findViewById(R.id.seekbar_eq_7);
        eqSeekBars[7] = findViewById(R.id.seekbar_eq_8);
        
        eqTextViews[0] = findViewById(R.id.text_eq_1);
        eqTextViews[1] = findViewById(R.id.text_eq_2);
        eqTextViews[2] = findViewById(R.id.text_eq_3);
        eqTextViews[3] = findViewById(R.id.text_eq_4);
        eqTextViews[4] = findViewById(R.id.text_eq_5);
        eqTextViews[5] = findViewById(R.id.text_eq_6);
        eqTextViews[6] = findViewById(R.id.text_eq_7);
        eqTextViews[7] = findViewById(R.id.text_eq_8);
        
        // 设置每个均衡器滑块的监听器
        for (int i = 0; i < eqSeekBars.length; i++) {
            final int band = i;
            eqSeekBars[i].setMax(30);
            eqSeekBars[i].setProgress(15); // 默认为中间值 (0dB)
            
            eqSeekBars[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        // 将0-30映射到-15到15 dB
                        short gain = (short) (progress - 15);
                        // 更新显示
                        updateEqualizerDisplay(band, gain);
                        // 如果是用户操作，则发送到音频处理器
                        if (audioManager != null) {
                            audioManager.setEqualizerBand(band, gain);
                            // 如果当前不是自定义模式，切换到自定义模式
                            if (currentEqualizerPreset != PRESET_CUSTOM) {
                                currentEqualizerPreset = PRESET_CUSTOM;
                                spinnerEqualizerPreset.setSelection(PRESET_CUSTOM, true);
                            }
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }
    
    private void updateEqualizerDisplay(int band, short gain) {
        if (band >= 0 && band < eqTextViews.length) {
            String text = (gain > 0 ? "+" : "") + gain + " dB";
            eqTextViews[band].setText(text);
        }
    }

    private void setupEqualizerPresets() {
        // 创建均衡器预设列表
        String[] presets = new String[] {
            "自定义",
            "平坦",
            "低音增强",
            "高音增强",
            "人声增强",
            "低音抑制"
        };
        
        // 创建适配器
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, presets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        // 设置适配器
        spinnerEqualizerPreset.setAdapter(adapter);
        
        // 设置监听器
        spinnerEqualizerPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != currentEqualizerPreset) {
                    applyEqualizerPreset(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
    
    private void applyEqualizerPreset(int preset) {
        currentEqualizerPreset = preset;
        
        // 处理所有滑块的可用性
        boolean isCustomMode = (preset == PRESET_CUSTOM);
        for (SeekBar seekBar : eqSeekBars) {
            seekBar.setEnabled(isCustomMode);
        }
        
        // 如果选择自定义预设，不修改当前值，但确保滑块可用
        if (preset == PRESET_CUSTOM) {
            return;
        }
        
        short[] levels;
        switch (preset) {
            case PRESET_FLAT:
                levels = new short[]{0, 0, 0, 0, 0, 0, 0, 0};
                break;
            case PRESET_BASS_BOOST:
                levels = new short[]{12, 8, 4, 0, 0, 0, 0, 0};
                break;
            case PRESET_TREBLE_BOOST:
                levels = new short[]{0, 0, 0, 0, 4, 8, 12, 15};
                break;
            case PRESET_VOCAL_BOOST:
                levels = new short[]{-5, -2, 0, 4, 8, 4, 0, -2};
                break;
            case PRESET_BASS_REDUCTION:
                levels = new short[]{-12, -8, -4, 0, 0, 0, 0, 0};
                break;
            default:
                return;
        }
        
        // 更新均衡器UI
        for (int i = 0; i < levels.length; i++) {
            // 更新显示文本
            updateEqualizerDisplay(i, levels[i]);
            // 更新滑块位置(0-30范围)，将-15到15映射到0-30
            eqSeekBars[i].setProgress(levels[i] + 15);
        }
        
        // 应用到音频处理器
        if (audioManager != null) {
            for (int i = 0; i < levels.length; i++) {
                audioManager.setEqualizerBand(i, levels[i]);
            }
        }
    }

    private void setupListeners() {
        // 刷新设备按钮
        buttonRefreshDevices.setOnClickListener(v -> {
            Toast.makeText(this, "正在刷新音频设备列表...", Toast.LENGTH_SHORT).show();
            refreshAudioDevices();
        });
        
        // 音频控制按钮
        buttonAudioControl.setOnClickListener(v -> {
            toggleAudioProcessing();
        });
        
        // 输入音量滑块
        seekBarInputVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && audioManager != null) {
                    audioManager.setInputVolume(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 输出音量滑块
        seekBarOutputVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && audioManager != null) {
                    audioManager.setOutputVolume(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 放大倍数滑块
        seekBarAmplification.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && audioManager != null) {
                    // 将0-100转换为0.1-100的浮点数
                    float factor = Math.max(0.1f, progress);
                    audioManager.setAmplificationFactor(factor);
                    // 更新显示
                    textAmplificationValue.setText(String.format("当前系数: %.1f", factor));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 当用户停止拖动且放大倍数很大时显示详细警告
            }
        });
        
        // 降噪开关
        switchNoiseReduction.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (audioManager != null) {
                audioManager.setNoiseReduction(isChecked);
            }
        });
        
        // 麦克风选择下拉框
        spinnerMicrophone.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    // 选择了"跟随系统"
                    Log.i(TAG, "输入设备设置为跟随系统");
                    
                    if (audioManager != null) {
                        audioManager.clearInputDevice();
                        
                        // 如果音频正在运行，重启以应用新设备
                        if (isAudioRunning) {
                            restartAudioProcessing();
                        }
                    }
                } else if (inputDevices != null && (position - 1) < inputDevices.size() && audioManager != null) {
                    // 调整索引，因为添加了"跟随系统"选项
                    AudioDeviceInfo selectedDevice = inputDevices.get(position - 1);
                    Log.i(TAG, "尝试切换输入设备到: " + getDeviceName(selectedDevice));
                            
                    audioManager.setInputDevice(selectedDevice);
                    
                    // 如果音频正在运行，重启以应用新设备
                    if (isAudioRunning) {
                        restartAudioProcessing();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        // 扬声器选择下拉框
        spinnerSpeaker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    // 选择了"跟随系统"
                    Log.i(TAG, "输出设备设置为跟随系统");
                    
                    if (audioManager != null) {
                        audioManager.clearOutputDevice();
                        
                        // 如果音频正在运行，重启以应用新设备
                        if (isAudioRunning) {
                            restartAudioProcessing();
                        }
                    }
                } else if (outputDevices != null && (position - 1) < outputDevices.size() && audioManager != null) {
                    // 调整索引，因为添加了"跟随系统"选项
                    AudioDeviceInfo selectedDevice = outputDevices.get(position - 1);
                    Log.i(TAG, "尝试切换输出设备到: " + getDeviceName(selectedDevice));
                            
                    audioManager.setOutputDevice(selectedDevice);
                    
                    // 如果音频正在运行，重启以应用新设备
                    if (isAudioRunning) {
                        restartAudioProcessing();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
    
    private void setupWaveformCallbacks() {
        // 创建输入波形回调
        WaveformCallback inputCallback = new WaveformCallback() {
            @Override
            public void onWaveformData(float[] data) {
                runOnUiThread(() -> {
                    if (inputWaveformView != null) {
                        inputWaveformView.updateWaveform(data);
                    }
                });
            }
        };
        
        // 创建输出波形回调
        WaveformCallback outputCallback = new WaveformCallback() {
            @Override
            public void onWaveformData(float[] data) {
                runOnUiThread(() -> {
                    if (outputWaveformView != null) {
                        outputWaveformView.updateWaveform(data);
                    }
                });
            }
        };
        
        // 设置回调
        audioManager.setWaveformCallback(inputCallback, outputCallback);
    }

    private void setupAudioDevices() {
        // 获取并填充输入设备列表
        inputDevices = audioManager.getAvailableInputDevices();
        List<String> inputDeviceNames = new ArrayList<>();
        
        // 添加"跟随系统"选项
        inputDeviceNames.add("跟随系统");
        
        for (AudioDeviceInfo device : inputDevices) {
            inputDeviceNames.add(getDeviceName(device));
        }
        
        ArrayAdapter<String> inputAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, inputDeviceNames);
        inputAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMicrophone.setAdapter(inputAdapter);
        
        // 获取并填充输出设备列表
        outputDevices = audioManager.getAvailableOutputDevices();
        List<String> outputDeviceNames = new ArrayList<>();
        
        // 添加"跟随系统"选项
        outputDeviceNames.add("跟随系统");
        
        for (AudioDeviceInfo device : outputDevices) {
            outputDeviceNames.add(getDeviceName(device));
        }
        
        ArrayAdapter<String> outputAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, outputDeviceNames);
        outputAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSpeaker.setAdapter(outputAdapter);
        
        // 默认选择"跟随系统"
        spinnerMicrophone.setSelection(0);
        spinnerSpeaker.setSelection(0);
        
        // 初始化均衡器
        short[] equalizerLevels = audioManager.getEqualizerBands();
        for (int i = 0; i < equalizerLevels.length && i < eqSeekBars.length; i++) {
            // 更新显示
            updateEqualizerDisplay(i, equalizerLevels[i]);
            // 更新滑块位置(0-30范围)，将-15到15映射到0-30
            eqSeekBars[i].setProgress(equalizerLevels[i] + 15);
        }
    }

    private String getDeviceName(AudioDeviceInfo device) {
        String name = device.getProductName().toString();
        if (name.isEmpty()) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                    return "内置麦克风";
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                    return "内置扬声器";
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    return "有线耳机";
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                    return "蓝牙设备";
                default:
                    return "音频设备 " + device.getId();
            }
        }
        return name;
    }

    // 重启音频处理
    private void restartAudioProcessing() {
        if (!isAudioRunning) return;
        
        stopAudioProcessing();
        startAudioProcessing();
    }

    private void startAudioProcessing() {
        if (audioManager == null) {
            return;
        }
        
        // 清空当前波形
        inputWaveformView.clearWaveform();
        outputWaveformView.clearWaveform();
        
        // 启动AAudio处理
        boolean success = audioManager.startAudio();
        if (!success) {
            Log.e(TAG, "启动音频处理失败");
            
            // 更新按钮状态
            isAudioRunning = false;
            buttonAudioControl.setText("开始音频处理");
        } else {
            Log.d(TAG, "音频处理已启动");
            isAudioRunning = true;
            buttonAudioControl.setText("停止音频处理");
        }
        
        // 通知服务音频已开始处理
        if (isServiceBound && audioProcessingService != null) {
            audioProcessingService.startAudioProcessing();
        }
    }

    private void stopAudioProcessing() {
        // 停止真实音频处理
        if (audioManager != null) {
            audioManager.stopAudio();
        }

        // 通知服务音频已停止处理
        if (isServiceBound && audioProcessingService != null) {
            audioProcessingService.stopAudioProcessing();
        }

        // 更新按钮状态
        isAudioRunning = false;
        buttonAudioControl.setText("开始音频处理");
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        
        // 检查录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
        }
        
        // 检查前台服务麦克风权限（Android 14及以上）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE);
            }
        }
        
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_RECORD_AUDIO);
        } else {
            setupAudioDevices();
            loadSettings();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                setupAudioDevices();
                loadSettings();
            } else {
                Toast.makeText(this, "需要相关权限才能继续", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 从锁屏中恢复
        if (audioManager != null) {
            audioManager.resumeFromLock();
        }

        loadSettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // 不在onPause中停止音频处理，只告诉AAudioManager我们将要锁屏
        if (audioManager != null) {
            audioManager.prepareForLock();
        }

        saveSettings();
    }

    @Override
    protected void onDestroy() {
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        
        // 如果应用正被完全关闭（而不仅仅是锁屏），且音频不再运行，则停止服务
        if (!isAudioRunning) {
            stopService(new Intent(this, AudioProcessingService.class));
        }
        
        if (audioManager != null && audioManager.isRunning()) {
            // 仅在应用暂停时暂停音频，用户可以在回来后手动重启
            stopAudioProcessing();
            buttonAudioControl.setText("开始音频处理");
            isAudioRunning = false;
        }
        
        if (audioManager != null) {
            audioManager.release();
        }

        saveSettings();
        super.onDestroy();
    }

    // 刷新音频设备列表
    private void refreshAudioDevices() {
        // 如果音频正在运行，先停止
        if (isAudioRunning) {
            stopAudioProcessing();
        }
        
        // 重新获取设备列表
        setupAudioDevices();
        
        // 显示刷新成功提示
        Toast.makeText(this, "设备列表已更新", Toast.LENGTH_SHORT).show();
    }

    /**
     * 保存所有设置到SharedPreferences
     */
    private void saveSettings() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        
        // 保存基本设置
        editor.putInt(KEY_INPUT_VOLUME, seekBarInputVolume.getProgress());
        editor.putInt(KEY_OUTPUT_VOLUME, seekBarOutputVolume.getProgress());
        editor.putInt(KEY_AMPLIFICATION, seekBarAmplification.getProgress());
        editor.putBoolean(KEY_NOISE_REDUCTION, switchNoiseReduction.isChecked());
        editor.putInt(KEY_EQUALIZER_PRESET, currentEqualizerPreset);
        
        // 保存自定义均衡器设置
        for (int i = 0; i < eqSeekBars.length; i++) {
            // 保存滑块位置，从0-30变换到-15到15
            int gainValue = eqSeekBars[i].getProgress() - 15;
            editor.putInt(KEY_EQUALIZER_BAND_PREFIX + i, gainValue);
        }
        
        editor.apply();
        Log.d(TAG, "设置已保存");
    }
    
    /**
     * 从SharedPreferences加载设置
     */
    private void loadSettings() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // 加载基本设置
        int inputVolume = settings.getInt(KEY_INPUT_VOLUME, 80);
        int outputVolume = settings.getInt(KEY_OUTPUT_VOLUME, 80);
        int amplification = settings.getInt(KEY_AMPLIFICATION, 1);
        boolean noiseReduction = settings.getBoolean(KEY_NOISE_REDUCTION, false);
        int equalizerPreset = settings.getInt(KEY_EQUALIZER_PRESET, PRESET_FLAT);
        
        // 应用音量设置
        seekBarInputVolume.setProgress(inputVolume);
        seekBarOutputVolume.setProgress(outputVolume);
        audioManager.setInputVolume(inputVolume);
        audioManager.setOutputVolume(outputVolume);
        
        // 应用放大倍数
        seekBarAmplification.setProgress(amplification);
        float factor = Math.max(0.1f, amplification);
        audioManager.setAmplificationFactor(factor);
        textAmplificationValue.setText(String.format("当前倍数: %.1f倍", factor));
        
        // 应用降噪设置
        switchNoiseReduction.setChecked(noiseReduction);
        audioManager.setNoiseReduction(noiseReduction);
        
        // 如果是自定义预设，先加载自定义均衡器设置
        if (equalizerPreset == PRESET_CUSTOM) {
            // 加载并应用自定义均衡器设置
            for (int i = 0; i < eqSeekBars.length; i++) {
                int gain = settings.getInt(KEY_EQUALIZER_BAND_PREFIX + i, 0);
                // 滑块位置从-15到15变换到0-30
                eqSeekBars[i].setProgress(gain + 15);
                audioManager.setEqualizerBand(i, (short)gain);
                // 更新显示
                updateEqualizerDisplay(i, (short)gain);
            }
        }
        
        // 应用均衡器预设（必须在加载自定义设置之后）
        spinnerEqualizerPreset.setSelection(equalizerPreset, false);
        // 如果不是自定义预设，应用预设
        if (equalizerPreset != PRESET_CUSTOM) {
            applyEqualizerPreset(equalizerPreset);
        } else {
            // 确保自定义模式下滑块是启用的
            for (SeekBar seekBar : eqSeekBars) {
                seekBar.setEnabled(true);
            }
            currentEqualizerPreset = PRESET_CUSTOM;
        }
        
        Log.d(TAG, "设置已加载");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isServiceBound) {
            bindService();
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        // 为了允许服务在后台继续运行，我们不在这里解绑服务
        // 只有在onDestroy时才解绑
    }

    /**
     * 启动并绑定前台服务
     */
    private void startAndBindService() {
        Intent serviceIntent = new Intent(this, AudioProcessingService.class);
        
        // 启动服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        // 绑定服务
        bindService();
    }
    
    /**
     * 绑定服务
     */
    private void bindService() {
        Intent serviceIntent = new Intent(this, AudioProcessingService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    // 修改原有的开始/停止音频处理的方法，增加与服务的通信
    private void toggleAudioProcessing() {
        if (!isAudioRunning) {
            startAudioProcessing();
        } else {
            stopAudioProcessing();
        }
    }

    /**
     * 请求忽略电池优化
     */
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            String packageName = getPackageName();
            
            // 检查是否已经在白名单中
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                // 显示对话框请求用户允许忽略电池优化
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("电池优化")
                    .setMessage("为了保证应用在后台稳定运行，需要将此应用加入电池优化白名单。请在接下来的系统页面中，选择\"允许\"。")
                    .setPositiveButton("确定", (dialog, which) -> {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + packageName));
                        startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        }
    }
}