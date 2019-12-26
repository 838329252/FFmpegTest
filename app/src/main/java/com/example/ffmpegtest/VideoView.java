package com.example.ffmpegtest;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

public class VideoView extends SurfaceView implements Runnable{
    private SurfaceHolder mHolder;
    private String TAG="VideoView";
    private int width1;
    private int height1;

    public VideoView(Context context) {
        super(context);

    }

    public VideoView(Context context, AttributeSet attrs) {
        super(context, attrs);

    }

    public VideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

    }

    @Override
    public void run() {

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width=MeasureSpec.getSize(widthMeasureSpec);
        int widthMode=MeasureSpec.getMode(widthMeasureSpec);
        int height=MeasureSpec.getSize(heightMeasureSpec);
        int heightMode=MeasureSpec.getMode(heightMeasureSpec);
        if(changeVideoSize()==0){
            this.getHolder().setFixedSize(width,height);
            setMeasuredDimension(width,height);
        }else{
            switch (widthMode){
                case MeasureSpec.AT_MOST:
                case MeasureSpec.EXACTLY:
                    if(width1>0 &&width1<=width){
                        width=width1;
                    }
                    break;
                case MeasureSpec.UNSPECIFIED:
                    width=width1;
            }
            switch (heightMode){
                case MeasureSpec.AT_MOST:
                case MeasureSpec.EXACTLY:
                    if(height1>0&& height1<=height ){
                        height=height1;
                    }
                    break;
                case MeasureSpec.UNSPECIFIED:
                    height=height1;
            }
            setMeasuredDimension(width,height);
        }

    }
    private int changeVideoSize(){
        int videoWidth=VideoInfo.getVideoWidth();
        int videoHeight=VideoInfo.getVideoHeight();
        if(videoWidth==0 || videoHeight==0){

            return 0;
        }
        Log.d(TAG,"VideoSize:"+"width="+videoWidth+"height="+videoHeight);
        int screenWidth=getContext().getResources().getDisplayMetrics().widthPixels;
        int screenHeight=getContext().getResources().getDisplayMetrics().heightPixels;
        Log.d(TAG,"ScreenSize:"+"width="+screenWidth+"height="+screenHeight);
        float max;
        if (getResources().getConfiguration().orientation== ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            //竖屏模式下按视频宽度计算放大倍数值
            max = Math.max((float) videoWidth / (float) screenWidth,(float) videoHeight / (float) screenHeight);
        } else{
            //横屏模式下按视频高度计算放大倍数值
            max = Math.max(((float) videoWidth/(float) screenWidth),(float) videoHeight/(float) screenHeight);

            Log.d(TAG,"max="+max);
        }
        width1 = (int) Math.ceil((float) videoWidth /max);
        height1 = (int) Math.ceil((float) videoHeight/max);
        Log.d(TAG,"VideoSizeAfterChange:"+"width="+width1+"height="+height1);
        this.getHolder().setFixedSize(width1,height1);
        return 1;
    }


}
