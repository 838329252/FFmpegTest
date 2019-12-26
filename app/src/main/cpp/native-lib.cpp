#include <jni.h>
#include <string>
#include "FFmpegMusic.h"
#include "FFmpegVideo.h"
#include <android/native_window_jni.h>


extern "C" {
//编码
#include "libavcodec/avcodec.h"
//封装格式处理
#include "libavformat/avformat.h"
#include "libswresample/swresample.h"
//像素处理
#include "libswscale/swscale.h"

#include <unistd.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include "Log.h"
}
const char *inputPath;
int64_t *totalTime;
FFmpegVideo *ffmpegVideo;
FFmpegMusic *ffmpegMusic;
pthread_t p_tid;
int isPlay;
ANativeWindow *window = 0;
int64_t duration;
AVFormatContext *pFormatCtx;
AVPacket *packet;
int step = 0;
jboolean isSeek = false;
JNIEnv *env1;
jobject instance1;
jclass david_player;
jmethodID createAudio;
jmethodID audio_write;

//把frame渲染播放在surface
void call_video_play(AVFrame *frame) {
    if (!window) {
        return;
    }
    ANativeWindow_Buffer window_buffer;
    if (ANativeWindow_lock(window, &window_buffer, NULL)) {
        LOGE("native-lib:无法lock surface");
        return;
    }
    LOGE("native-lib:将frame渲染播放在surface")
    uint8_t *dst = (uint8_t *) window_buffer.bits;
    int dstStride = window_buffer.stride * 4;
    uint8_t *src = frame->data[0];
    int srcStride = frame->linesize[0];
    for (int i = 0; i < ffmpegVideo->codec->height; ++i) {
        memcpy(dst + i * dstStride, src + i * srcStride, srcStride);
    }
    ANativeWindow_unlockAndPost(window);
}

//解封装，获取视频信息
void init() {
    LOGE("native-lib:解封装，获取视频信息");
    //1.注册组件
    av_register_all();
    //若要打开网络流
    avformat_network_init();
    //封装格式上下文
    pFormatCtx = avformat_alloc_context();

    //2.打开输入视频文件
    if (avformat_open_input(&pFormatCtx, inputPath, NULL, NULL) != 0) {
        LOGE("native-lib:打开输入视频文件失败");
    }
    //3.获取视频信息
    if (avformat_find_stream_info(pFormatCtx, NULL) < 0) {
        LOGE("native-lib:获取视频信息失败");
    }

    //得到播放总时间
    if (pFormatCtx->duration != AV_NOPTS_VALUE) {
        duration = pFormatCtx->duration;//微秒
    }
}

//解码并播放
void *begin(void *args) {
    LOGE("native-lib:开始执行begin方法");
    //找到视频流和音频流
    for (int i = 0; i < pFormatCtx->nb_streams; ++i) {
        //获取解码器
        AVCodecContext *avCodecContext=avcodec_alloc_context3(NULL);
        avcodec_parameters_to_context(avCodecContext,pFormatCtx->streams[i]->codecpar);
        /*AVCodecContext *avCodecContext = pFormatCtx->streams[i]->codec;*/
        AVCodec *avCodec = avcodec_find_decoder(avCodecContext->codec_id);
        if(avCodec==NULL){
            LOGE("native-lib:获取解码器失败");
            break;
        }
        if (avcodec_open2(avCodecContext, avCodec, NULL) < 0) {
            LOGE("native-lib:打开失败")
            continue;
        }
        //如果是视频流
        if (pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            ffmpegVideo->index = i;
            ffmpegVideo->setAvCodecContext(avCodecContext);
            ffmpegVideo->time_base = pFormatCtx->streams[i]->time_base;
            if (window) {
                ANativeWindow_setBuffersGeometry(window, ffmpegVideo->codec->width,
                                                 ffmpegVideo->codec->height,
                                                 WINDOW_FORMAT_RGBA_8888);
            }
        }//如果是音频流
        else if (pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            ffmpegMusic->index = i;
            ffmpegMusic->setAvCodecContext(avCodecContext);
            ffmpegMusic->time_base = pFormatCtx->streams[i]->time_base;
        }
    }
//开启播放
    ffmpegVideo->setFFmepegMusic(ffmpegMusic);
    ffmpegMusic->play();
    ffmpegVideo->play();
    isPlay = 1;

    //解码packet,并压入队列中
    packet = (AVPacket *) av_mallocz(sizeof(AVPacket));
    //跳转到某一个特定的帧上面播放
    int ret;
    while (isPlay) {
        //读取一帧压缩数据
        ret = av_read_frame(pFormatCtx, packet);
        if (ret == 0) {
            if (ffmpegVideo && ffmpegVideo->isPlay && packet->stream_index == ffmpegVideo->index
                    ) {
                //将视频packet压入队列
                ffmpegVideo->put(packet);
            } else if (ffmpegMusic && ffmpegMusic->isPlay &&
                       packet->stream_index == ffmpegMusic->index) {
                //将音频packet压入队列
                ffmpegMusic->put(packet);
            }
            av_packet_unref(packet);
        } else if (ret == AVERROR_EOF) {
            // 读完了
            //读取完毕 但是不一定播放完毕
            while (isPlay) {
                if (ffmpegVideo->queue.empty() && ffmpegMusic->queue.empty()) {
                    isPlay = 0;
                    break;
                }
                // LOGE("等待播放完成");
                av_usleep(10000);
            }
        }
    }
    //解码完过后可能还没有播放完
    isPlay = 0;
    if (ffmpegMusic && ffmpegMusic->isPlay) {
        ffmpegMusic->stop();
    }
    if (ffmpegVideo && ffmpegVideo->isPlay) {
        ffmpegVideo->stop();
    }
    LOGE("native-lib:播放完成");
    //释放
    ANativeWindow_release(window);
    av_packet_unref(packet);
    avformat_free_context(pFormatCtx);
    pthread_exit(0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_ffmpegtest_FFmpegUtil_play(JNIEnv *env, jobject instance, jstring inputPath_) {
    inputPath = env->GetStringUTFChars(inputPath_, 0);
    init();//解封装，获得封装格式上下文
    ffmpegVideo = new FFmpegVideo;
    ffmpegMusic = new FFmpegMusic;
    ffmpegVideo->setPlayCall(call_video_play);//将frame渲染播放在surfaceView上
    pthread_create(&p_tid, NULL, begin, NULL);//开启begin线程，解码并播放
    env->ReleaseStringUTFChars(inputPath_, inputPath);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_ffmpegtest_FFmpegUtil_display(JNIEnv *env, jobject instance, jobject surface) {
    //得到界面
    LOGE("native-lib:执行display方法");
    if (window) {
        ANativeWindow_release(window);
        ANativeWindow_release(window);
        window = 0;
    }
    window = ANativeWindow_fromSurface(env, surface);
    if (ffmpegVideo && ffmpegVideo->codec) {
        ANativeWindow_setBuffersGeometry(window, ffmpegVideo->codec->width,
                                         ffmpegVideo->codec->height,
                                         WINDOW_FORMAT_RGBA_8888);
    }

}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_ffmpegtest_FFmpegUtil_release(JNIEnv *env, jobject instance) {
    //释放资源
    if (isPlay) {
        isPlay = 0;
        //等待线程的结束
        pthread_join(p_tid, 0);
    }
    if (ffmpegVideo) {
        if (ffmpegVideo->isPlay) {
            ffmpegVideo->stop();
        }
        delete (ffmpegVideo);
        ffmpegVideo = 0;
    }
    if (ffmpegMusic) {
        if (ffmpegMusic->isPlay) {
            ffmpegMusic->stop();
        }
        delete (ffmpegMusic);
        ffmpegMusic = 0;
    }
}extern "C"
JNIEXPORT void JNICALL
Java_com_example_ffmpegtest_FFmpegUtil_stop(JNIEnv *env, jobject instance) {
    //点击暂停按钮
    ffmpegMusic->pause();
    ffmpegVideo->pause();

}
