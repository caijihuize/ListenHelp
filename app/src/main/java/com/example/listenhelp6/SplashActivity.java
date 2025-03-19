package com.example.listenhelp6;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DISPLAY_TIME = 3000; // 3秒
    private ImageView splashImage;
    private TextView appNameText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // 查找视图
        splashImage = findViewById(R.id.splash_image);
        appNameText = findViewById(R.id.app_name_text);
        
        // 加载图片动画
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        
        // 应用动画
        splashImage.startAnimation(fadeIn);
        
        // 使用延迟显示文本效果
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                // 文本从底部滑入的动画
                appNameText.setAlpha(0f);
                appNameText.setVisibility(android.view.View.VISIBLE);
                appNameText.animate()
                    .alpha(1f)
                    .translationYBy(-50)
                    .setDuration(800)
                    .start();
            }
        }, 700);

        // 使用Handler延迟3秒后跳转到MainActivity
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                // 准备淡出动画
                Animation fadeOut = AnimationUtils.loadAnimation(SplashActivity.this, R.anim.fade_out);
                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {}

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        // 动画结束后跳转
                        Intent mainIntent = new Intent(SplashActivity.this, MainActivity.class);
                        startActivity(mainIntent);
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                        finish(); // 结束SplashActivity
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {}
                });
                
                // 应用淡出动画
                splashImage.startAnimation(fadeOut);
            }
        }, SPLASH_DISPLAY_TIME);
    }
} 