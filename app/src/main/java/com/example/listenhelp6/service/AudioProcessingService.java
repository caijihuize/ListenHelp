package com.example.listenhelp6.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.listenhelp6.MainActivity;
import com.example.listenhelp6.R;
import com.example.listenhelp6.audio.AAudioManager;

public class AudioProcessingService extends Service {
    private static final String TAG = "AudioProcessingService";
    
    // 通知相关常量
    private static final String CHANNEL_ID = "listen_help_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    // 服务状态
    private boolean isRunning = false;
    
    // 音频管理器
    private AAudioManager audioManager;
    
    // Binder给客户端
    private final IBinder binder = new LocalBinder();
    
    // 电源锁定，防止CPU休眠
    private PowerManager.WakeLock wakeLock;
    
    public class LocalBinder extends Binder {
        public AudioProcessingService getService() {
            return AudioProcessingService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "服务创建");
        
        // 获取PowerManager服务并创建WakeLock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, 
                "ListenHelp6:AudioProcessingWakeLock");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "服务开始");
        
        // 创建通知渠道
        createNotificationChannel();
        
        // 创建通知
        Notification notification = createNotification();
        
        // 启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        
        // 获取WakeLock
        acquireWakeLock();
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "服务销毁");
        
        // 释放WakeLock
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        // 停止音频处理
        stopAudioProcessing();
        
        // 停止前台服务
        stopForeground(true);
    }
    
    /**
     * 创建通知渠道（Android 8.0及以上需要）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "音频处理服务",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("用于保持音频处理服务在后台运行");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * 构建前台服务通知
     */
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("音频处理服务")
                .setContentText("正在运行中...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        return builder.build();
    }
    
    /**
     * 设置音频管理器
     */
    public void setAudioManager(AAudioManager audioManager) {
        this.audioManager = audioManager;
    }
    
    /**
     * 开始音频处理
     */
    public void startAudioProcessing() {
        if (audioManager != null && !isRunning) {
            // 假设audioManager.start()是启动音频处理的方法
            // 如果音频处理已在MainActivity中启动，则无需在此处重新启动
            isRunning = true;
            updateNotification("听力辅助正在处理音频");
        }
    }
    
    /**
     * 停止音频处理
     */
    public void stopAudioProcessing() {
        if (audioManager != null && isRunning) {
            // 假设audioManager.stop()是停止音频处理的方法
            // 如果音频处理已在MainActivity中停止，则无需在此处重新停止
            isRunning = false;
            updateNotification("听力辅助待机中");
        }
    }
    
    /**
     * 更新通知内容
     */
    private void updateNotification(String contentText) {
        NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, createNotification());
    }

    private void acquireWakeLock() {
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }
    }
} 