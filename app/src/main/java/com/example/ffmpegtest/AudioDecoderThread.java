package com.example.ffmpegtest;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaSync;
import android.media.PlaybackParams;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioDecoderThread extends Thread {
    private static final String AUDIO="audio/";
    private static final String TAG="AudioDecoderThread";
    private MediaExtractor mExtractor;
    private MediaCodec mAudioDecoder;
    private boolean eosReceived;
    private int audioIndex;
    private AudioTrack mAudioTrack;
    private MediaSync sync;

    public boolean init(String filePath,MediaSync sync){
        eosReceived=false;
        this.sync=sync;
        try{
            mExtractor=new MediaExtractor();
            mExtractor.setDataSource(filePath);
            //分离出音轨和视轨
            Log.d(TAG, "getTrackCount: " + mExtractor.getTrackCount() );
            for (int i = 0; i < mExtractor.getTrackCount(); i++) {
                MediaFormat format = mExtractor.getTrackFormat(i);

                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith(AUDIO)) {
                    audioIndex=i;
                    mExtractor.selectTrack(audioIndex);
                    mAudioDecoder = MediaCodec.createDecoderByType(mime);
                    try {
                        Log.d(TAG, "format : " + format);
                        mAudioDecoder.configure(format, null, null, 0 /* Decoder */);

                    } catch (IllegalStateException e) {
                        Log.e(TAG, "audioCodec '" + mime + "' failed configuration. " + e);
                        return false;
                    }
                    int minBufferSize=AudioTrack.getMinBufferSize(format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);
                    mAudioTrack=new AudioTrack(AudioManager.STREAM_MUSIC,format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,minBufferSize, AudioTrack.MODE_STREAM);
                    mAudioTrack.play();
                    /*sync.setAudioTrack(mAudioTrack);*/
                    mAudioDecoder.start();

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
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        /*ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();This method was deprecated in API level 21.*/
        //存放目标文件的数据
        ByteBuffer inputBuffer = null;
        ByteBuffer outputBuffer=null;
        mAudioDecoder.getOutputBuffers();
        boolean isInput = true;
        boolean first = false;
        long startWhen = 0;
        int frameCount=1;
        while (!eosReceived) {
            if (isInput) {
                int inputIndex = mAudioDecoder.dequeueInputBuffer(10000);
                if (inputIndex >= 0) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        inputBuffer =  mAudioDecoder.getInputBuffers()[inputIndex];
                        inputBuffer.clear();
                    } else {
                        inputBuffer =  mAudioDecoder.getInputBuffer(inputIndex);
                    }
                    int sampleSize = mExtractor.readSampleData(inputBuffer, 0);

                    if (mExtractor.advance() && sampleSize > 0) {
                        mAudioDecoder.queueInputBuffer(inputIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);

                    } else {
                        Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                        mAudioDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isInput = false;
                    }
                }
            }

            int outIndex = mAudioDecoder.dequeueOutputBuffer(info, 10000);
            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    mAudioDecoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    MediaFormat format = mAudioDecoder.getOutputFormat();
                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED format : " + format);
                    mAudioTrack.setPlaybackRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
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

                    //用来保存解码后的数据
                    outputBuffer=mAudioDecoder.getOutputBuffer(outIndex);
                    byte[] outData=new byte[info.size];
                    outputBuffer.get(outData);
                    outputBuffer.clear();
                    //播放解码后的数据
                    mAudioTrack.write(outData,info.offset,info.offset+info.size);
                    mAudioDecoder.releaseOutputBuffer(outIndex,false);
                    /*sync.setPlaybackParams(new PlaybackParams().setSpeed(1.f));
                    sync.queueAudio(outputBuffer,outIndex,info.presentationTimeUs);
                    sync.setCallback(new MediaSync.Callback() {
                        @Override
                        public void onAudioBufferConsumed(@NonNull MediaSync sync, @NonNull ByteBuffer audioBuffer, int bufferId) {
                            mAudioDecoder.releaseOutputBuffer(bufferId, false );
                        }
                    },null);*/
                    break;
            }

            // All decoded frames have been rendered, we can stop playing now
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                break;
            }
        }
        /*sync.setPlaybackParams(new PlaybackParams().setSpeed(0.f));
        sync.release();
        sync=null;*/
        mAudioDecoder.stop();
        mAudioDecoder.release();
        mAudioTrack.stop();
        mAudioTrack.release();
        mExtractor.release();
    }
    public void close() {
        eosReceived = true;

    }

}
