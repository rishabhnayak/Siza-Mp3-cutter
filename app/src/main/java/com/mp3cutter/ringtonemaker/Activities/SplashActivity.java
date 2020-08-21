package com.mp3cutter.ringtonemaker.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.mp3cutter.ringtonemaker.R;

public class SplashActivity extends AppCompatActivity {

    private ImageView mSizaMp3CutterImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        mSizaMp3CutterImageView = (ImageView) findViewById(R.id.image_view_bell);
        mSizaMp3CutterImageView.startAnimation(shake);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mSizaMp3CutterImageView.clearAnimation();
                Intent i = new Intent(SplashActivity.this, RingdroidSelectActivity.class);
                startActivity(i);
                finish();
            }
        }, 1500);
    }
}
