package com.example.ffmpegtest;

public class VideoInfo {
    private static int videoWidth;
    private static int videoHeight;

    public static int getVideoWidth() {
        return videoWidth;
    }

    public static void setVideoWidth(int videoWidth) {
        VideoInfo.videoWidth = videoWidth;
    }

    public static int getVideoHeight() {
        return videoHeight;
    }

    public static void setVideoHeight(int videoHeight) {
        VideoInfo.videoHeight = videoHeight;
    }
}
