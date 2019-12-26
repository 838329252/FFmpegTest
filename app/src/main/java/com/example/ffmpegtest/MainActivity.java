package com.example.ffmpegtest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SurfaceHolder.Callback {
    private FFmpegUtil player;
    private VideoView videoView;
    private Button startSoftDecoding;
    private Button startHardDecoding;
    private String inputPath;
    private String TAG="MainActivity";
    private AvSync avSync;
    private SurfaceHolder mHolder;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //去掉TitleBar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        inputPath=Environment.getExternalStorageDirectory()+"/Pictures/Screenshots/SVID_20191211_143759_1.mp4";
        player=new FFmpegUtil();
        initView();
        initSurfaceHolder();
        avSync=new AvSync();

    }


    private void initView(){
        videoView = findViewById(R.id.videoView);
        startSoftDecoding=findViewById(R.id.startSoftDecoding);
        startHardDecoding=findViewById(R.id.startHardDecoding);
    }
    private void initSurfaceHolder(){
        mHolder=videoView.getHolder();
        player.display(mHolder.getSurface());
        mHolder.addCallback(MainActivity.this);
    }
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated=" + System.currentTimeMillis());
        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);
        }else{
            avSync.initAvSync(videoView,inputPath);
            setOrientation(VideoInfo.getVideoWidth(),VideoInfo.getVideoHeight());
            Log.d(TAG,"initDecodeThread"+System.currentTimeMillis());
            startHardDecoding.setOnClickListener(this);
            startSoftDecoding.setOnClickListener(this);
        }
    }
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        player.display(mHolder.getSurface());
        Log.d(TAG, "surfaceChanged=" + System.currentTimeMillis());
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if(avSync.isThreadStarted()){
            avSync.stopDecodeThread();
        }
        player.stop();
        player.release();
        Log.d(TAG, "surfaceDestroyed=" + System.currentTimeMillis());
    }

    @Override
    public void onClick(View v){
        switch (v.getId()){
            case R.id.startSoftDecoding:

                if(avSync.isThreadStarted()){
                    avSync.stopDecodeThread();
                    player.play(inputPath);
                }
                break;
            case R.id.startHardDecoding:
                avSync.startDecodeThread();
                break;
            default:break;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,String [] permissions,int[] grantResult){
        switch(requestCode){
            case 1:
                if(grantResult.length>0&&grantResult[0]==PackageManager.PERMISSION_GRANTED){
                    avSync.initAvSync(videoView,inputPath);
                    setOrientation(VideoInfo.getVideoWidth(),VideoInfo.getVideoHeight());
                    startHardDecoding.setOnClickListener(this);
                    startSoftDecoding.setOnClickListener(this);
                }else{
                    Toast.makeText(MainActivity.this,"拒绝访问权限",Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        videoView.requestLayout();
    }
    //根据视频尺寸来设置横竖屏
    private void  setOrientation(int width,int height){
        if (width > height) {
            if (getResources().getConfiguration().orientation!= ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        } else {
            if (getResources().getConfiguration().orientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }

    }

}
