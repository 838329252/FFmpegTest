package com.example.ffmpegtest;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoDecoderThread extends Thread{
    private static final String VIDEO="video/";
    private static final String TAG="VideoDecoderThread";
    private MediaExtractor mExtractor;
    private MediaCodec mVideoDecoder;
    private boolean eosReceived;
    private int videoIndex;
    private Surface surface;


    public boolean init(Surface surface, String filePath){
        eosReceived=false;
        this.surface=surface;
        try{
            mExtractor=new MediaExtractor();
            mExtractor.setDataSource(filePath);
            //分离出音轨和视轨
            Log.d(TAG, "getTrackCount: " + mExtractor.getTrackCount() );
            for (int i = 0; i < mExtractor.getTrackCount(); i++) {
                MediaFormat format = mExtractor.getTrackFormat(i);

                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith(VIDEO)) {
                    videoIndex=i;
                    mExtractor.selectTrack(videoIndex);
                    VideoInfo.setVideoWidth(format.getInteger(MediaFormat.KEY_WIDTH));
                    VideoInfo.setVideoHeight(format.getInteger(MediaFormat.KEY_HEIGHT));
                    Log.d(TAG,"VideoSize:"+"width="+format.getInteger(MediaFormat.KEY_WIDTH)+
                            "height="+format.getInteger(MediaFormat.KEY_HEIGHT));
                    mVideoDecoder = MediaCodec.createDecoderByType(mime);
                    try {
                        Log.d(TAG, "format : " + format);
                        mVideoDecoder.configure(format, surface, null, 0 /* Decoder */);

                    } catch (IllegalStateException e) {
                        Log.e(TAG, "videoCodec '" + mime + "' failed configuration. " + e);
                        return false;
                    }

                    mVideoDecoder.start();
                    break;
                }
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        return  true;
    }

    @Override
    public void run() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
        /*ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();This method was deprecated in API level 21.*/
        //存放目标文件的数据
        ByteBuffer inputBuffer = null;
        mVideoDecoder.getOutputBuffers();
        boolean isInput = true;
        boolean first = false;
        long startWhen = 0;

        while (!eosReceived && !this.isInterrupted()) {
            if (isInput) {
                int inputIndex = mVideoDecoder.dequeueInputBuffer(10000);
                if (inputIndex >= 0) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        inputBuffer =  mVideoDecoder.getInputBuffers()[inputIndex];
                        inputBuffer.clear();
                    } else {
                        inputBuffer =  mVideoDecoder.getInputBuffer(inputIndex);
                    }
                    int sampleSize = mExtractor.readSampleData(inputBuffer, 0);

                    if (mExtractor.advance() && sampleSize > 0) {
                        mVideoDecoder.queueInputBuffer(inputIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);

                    } else {
                        Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                        mVideoDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isInput = false;
                    }
                }
            }

            int outIndex = mVideoDecoder.dequeueOutputBuffer(info, 10000);
            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    mVideoDecoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED format : " + mVideoDecoder.getOutputFormat());
                    break;

                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d(TAG, "INFO_TRY_AGAIN_LATER");
                    break;

                default:
                    if (!first) {
                        startWhen = System.currentTimeMillis();
                        first = true;
                    }
                    //帧时间控制
                    try {
                        long sleepTime = (info.presentationTimeUs / 1000) - (System.currentTimeMillis() - startWhen);
                        //Log.d(TAG, "info.presentationTimeUs : " + (info.presentationTimeUs / 1000) + " playTime: " + (System.currentTimeMillis() - startWhen) + " sleepTime : " + sleepTime);

                        if (sleepTime > 0)
                            Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    mVideoDecoder.releaseOutputBuffer(outIndex, true /* Surface init */);
                    break;
            }

            // All decoded frames have been rendered, we can stop playing now
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                break;
            }
        }
        mVideoDecoder.stop();
        mVideoDecoder.release();
        mExtractor.release();
        surface.release();

    }

    public void close() {
        eosReceived = true;

    }
}
