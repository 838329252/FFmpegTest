package com.example.ffmpegtest;

import android.media.AudioTrack;
import android.media.MediaSync;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;


public class AvSync{
    private MediaSync sync;
    private Surface surface;
    private Surface inputSurface;
    private VideoDecoderThread mVideoDecoderThread;
    private AudioDecoderThread mAudioDecoderThread;
    private String inputPath;
    private boolean vResult;
    private boolean aResult;
    private VideoView mVideoView;
    private String TAG="Avsync";
    private boolean isStart=false;


    public void initAvSync(VideoView videoView, String inputPath){
        mVideoView=videoView;
        surface=videoView.getHolder().getSurface();
        this.inputPath=inputPath;
        sync=new MediaSync();
        /*if(surface!=null){
            sync.setSurface(surface);
            inputSurface=sync.createInputSurface();
        }*/

        initDecodeThread();
    }

    private void initDecodeThread(){
        mVideoDecoderThread=new VideoDecoderThread();
        if(mVideoDecoderThread!=null){
            vResult=mVideoDecoderThread.init(surface,inputPath);

        }
        mAudioDecoderThread=new AudioDecoderThread();
        if(mAudioDecoderThread!=null){
            aResult=mAudioDecoderThread.init(inputPath,sync);
        }
    }
    public void startDecodeThread(){

        if(vResult&&aResult){
            if(!isStart){
                mVideoDecoderThread.start();
                mAudioDecoderThread.start();
                isStart=true;
            }else{
                Log.d(TAG,"The Thread has already started");
            }
        }else{
            mVideoDecoderThread=null;
            mAudioDecoderThread=null;
            isStart=false;
        }
    }
    public void stopDecodeThread(){
        if(mVideoDecoderThread!=null &&isStart ){
            mVideoDecoderThread.close();
            mVideoDecoderThread=null;
        }else {
            mVideoDecoderThread=null;
        }
        if(mAudioDecoderThread!=null&&isStart){
            mAudioDecoderThread.close();
            mVideoDecoderThread=null;
        }else{
            mAudioDecoderThread=null;
        }
        isStart=false;
    }
    public boolean isThreadStarted(){
        return isStart;
    }


}
