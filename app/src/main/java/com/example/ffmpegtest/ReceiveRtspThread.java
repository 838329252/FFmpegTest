package com.example.ffmpegtest;

import android.util.Log;
import android.widget.VideoView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ReceiveRtspThread extends Thread {
    private String TAG="ReceiveRtspThread";
    private BlockingQueue<byte[]> video_data_Queue = new ArrayBlockingQueue<byte[]>(1000);
    private String playUrl;
    private String playUrlIp;
    private boolean isPlaying;
    private int playUrlPort;
    private int mCSeq = 0;
    private String sessionId;
    private int videoPort = 50001;
    private int audioPort = 50002;
    private final int FRAME_MAX_LEN = 300 * 1024;
    private BufferedReader reader;
    private BufferedWriter writer;


    public void setPlayInfo(boolean isPlaying){
        this.isPlaying=isPlaying;
    }
    @Override
    public void run() {
        DatagramSocket dataSocket=null;
        DatagramPacket dataPacket;

        Socket socket;
        OutputStream outputStream;

        byte frame_head_1 = (byte)0x00;
        byte frame_head_2 = (byte)0x00;
        byte frame_head_3 = (byte)0x00;
        byte frame_head_4 = (byte)0x01;
        byte frame_head_I = (byte)0x65;
        byte frame_head_P = (byte)0x61;

        int nal_unit_type;

        long lastSq = 0;
        long currSq = 0;

        Log.d(TAG,"MediaCodecThread running.");
        try {
            socket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(playUrlIp, playUrlPort);
            socket.connect(socketAddress, 3000);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            outputStream = socket.getOutputStream();
            writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write(sendOptions());
            writer.flush();
            getResponse();
            writer.write(sendDescribe());
            writer.flush();
            getResponse();
            writer.write(sendSetup());
            writer.flush();
            getResponse();
            writer.write(sendPlay());
            writer.flush();
            getResponse();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    while(isPlaying){
                        try {
                            writer.write(sendParam());
                            writer.flush();
                            Thread.sleep(3*1000);
                        } catch (InterruptedException | IOException e) {
                            e.printStackTrace();
                            if(e instanceof IOException){
                                isPlaying = false;
                                videoPort = videoPort + 2;
                                audioPort = audioPort + 2;
                            }
                        }
                    }
                }
            }).start();

            Log.d(TAG,"start DatagramSocket.");
            dataSocket = new DatagramSocket(videoPort);
            dataSocket.setSoTimeout(3000);
            byte[] receiveByte = new byte[48*1024];//96
            //从udp读取的数据长度
            int offHeadsize = 0;
            //当前帧长度
            int frameLen = 0;
            //完整帧筛选用缓冲区
            byte[] frame = new byte[FRAME_MAX_LEN];

            dataPacket = new DatagramPacket(receiveByte, receiveByte.length);

            Log.d(TAG,"start receive data from socket.");
            while(isPlaying){
                //Log.d(TAG, "T=" + test);
                dataSocket.receive(dataPacket);
                offHeadsize = dataPacket.getLength() - 12;

                if (offHeadsize > 2) {

                    lastSq = currSq;
                    currSq = ((receiveByte[2] & 0xFF)<<8) + (receiveByte[3] & 0xFF);
                    //Log.d(".", "~~" + currSq);
                    if(lastSq != 0){
                        if(lastSq != currSq - 1){
                            Log.d(TAG, "frame data maybe lost.last=" + lastSq + ",curr=" + currSq);
                        }
                    }

                    if (frameLen + offHeadsize < FRAME_MAX_LEN) {

                        nal_unit_type = receiveByte[12]&0xFF;

                        if(nal_unit_type == 0x67 /*SPS*/
                                || nal_unit_type == 0x68 /*PPS*/
                                || nal_unit_type == 0x6 /*SEI*/){
                            //加上头部
                            receiveByte[8] = frame_head_1;
                            receiveByte[9] = frame_head_2;
                            receiveByte[10] = frame_head_3;
                            receiveByte[11] = frame_head_4;
                            //Log.d(TAG, "ppp=" + Arrays.toString(receiveByte));
                            video_data_Queue.put(Arrays.copyOfRange(receiveByte, 8, offHeadsize + 12));
//                                    if(isFirstPacket){
//                                        header_sps = Arrays.copyOfRange(receiveByte, 8, offHeadsize + 12);
//                                        mediaCodec = null;
//                                        initMediaCodec();
//                                        startDecodecThread();
//                                        isFirstPacket = false;
//                                    }
                            //修改frameLen
                            frameLen = 0;
                        }else if((nal_unit_type&0x1F) == 28){//分片NAL包，可能是I或者P帧
                            if((receiveByte[13]&0xFF) == 0x85){
                                //I帧的第一包
//                                        Log.e(TAG, "I1=" + System.currentTimeMillis());
                                receiveByte[9] = frame_head_1;
                                receiveByte[10] = frame_head_2;
                                receiveByte[11] = frame_head_3;
                                receiveByte[12] = frame_head_4;
                                receiveByte[13] = frame_head_I;
                                System.arraycopy(receiveByte, 9, frame, frameLen, offHeadsize + 3);
                                frameLen += offHeadsize + 3;
                            }else if((receiveByte[13]&0xFF) == 0x81){
                                //P帧的第一包
//                                        Log.e(TAG, "P1=" + System.currentTimeMillis());
                                receiveByte[9] = frame_head_1;
                                receiveByte[10] = frame_head_2;
                                receiveByte[11] = frame_head_3;
                                receiveByte[12] = frame_head_4;
                                receiveByte[13] = frame_head_P;
                                System.arraycopy(receiveByte, 9, frame, frameLen, offHeadsize + 3);
                                frameLen += offHeadsize + 3;
                            }else{
                                System.arraycopy(receiveByte, 14, frame, frameLen, offHeadsize - 2);
                                //修改frameLen
                                frameLen += offHeadsize - 2;
                            }

                            if(((receiveByte[13]&0xFF) == 0x45)){
//                                        Log.e(TAG, "II1=" + System.currentTimeMillis());
                                video_data_Queue.put(Arrays.copyOfRange(frame, 0, frameLen));
                                frameLen = 0;
                            }else if(((receiveByte[13]&0xFF) == 0x41)){
//                                        Log.e(TAG, "PP2=" + System.currentTimeMillis());
                                video_data_Queue.put(Arrays.copyOfRange(frame, 0, frameLen));
                                Log.d(TAG," video_data_Queue"+ video_data_Queue);
                                frameLen = 0;
                            }
                        }
                        //Log.d(TAG, "SQ:" + (((receiveByte[2] & 0xFF)<<8) + (receiveByte[3] & 0xFF)));
                        //Log.d(TAG, "udp data=" + Arrays.toString(dataPacket.getData()));
                        //Log.d(TAG, "data=" + Arrays.toString(receiveByte));
//                                Log.d(TAG, "-------");
//                                Log.d(TAG, "rtp V:" + ((receiveByte[0] & 0xC0)>>6));
//                                Log.d(TAG, "rtp P:" + ((receiveByte[0] & 0x20)>>5));
//                                Log.d(TAG, "rtp X:" + ((receiveByte[0] & 0x10)>>4));
//                                Log.d(TAG, "rtp M:" + ((receiveByte[1] & 0x80)>>7));
//                                Log.d(TAG, "rtp PT:" + (receiveByte[1] & 0x7F));
//                                Log.d(TAG, "rtp SQ:" + (((receiveByte[2] & 0xFF)<<8) + (receiveByte[3] & 0xFF)));
//                                Log.d(TAG, "rtp TS:" + (((receiveByte[7] & 0xFF)<<24) + ((receiveByte[6] & 0xFF)<<16) + ((receiveByte[5] & 0xFF)<<8) + (receiveByte[4] & 0xFF)));
//                                Log.d(TAG, "-------");
                    }
                }else{
                    isPlaying = false;
                    Log.e(TAG, "udp port receive stream failed.");
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            isPlaying = false;
            Log.d(TAG,"receive data from socket failed.");
        }finally {
            video_data_Queue.clear();
            if (dataSocket != null) {
                try {
                    writer.write(sendTearDown());
                    writer.flush();
                    dataSocket.close();
                    Log.e(TAG, "dataSocket close ok.");
                } catch (Exception e) {
                    //dataSocket.close();
                    Log.e(TAG, "dataSocket close failed.",e);
                }
            }
        }
    }

    /*
    设置视频流
     */
    public void setPlayUrl(String playUrl){
        //rtsp://192.168.8.8:8554/stream
        String str[] = playUrl.split("//");
        if(str.length == 2 && str[0].equals("rtsp:")){
            if(str[1].contains(":")){
                this.playUrl = playUrl;
                str = str[1].split(":");

                playUrlIp = str[0];
                Log.d("playUrlIp",playUrlIp);
                playUrlPort = Integer.parseInt(str[1].split("/")[0]);
                Log.d("playUrlPort",playUrlPort+"");
            }
        }else{
            Log.e(TAG, "setPlayUrl failed,playUrl illegality.");
        }
    }

    /*
    rtsp协议：OPTIONS
    }
     */
    private String sendOptions() {
        String options =
                "OPTIONS " + playUrl + " RTSP/1.0\r\n" + addHeaders();
        Log.i(TAG, options);
        return options;
    }
    /*
    rtsp协议：DESCRIBE
     */
    private String sendDescribe() {
        String describe =
                "DESCRIBE " + playUrl + " RTSP/1.0\r\n" + addHeaders();
        Log.i(TAG, describe);
        return describe;
    }
    /*
    rtsp协议：SETUP
     */
    private String sendSetup() {
        String setup =
                "SETUP " + playUrl + "/track0" + " RTSP/1.0\r\n"
                        + "Transport: RTP/AVP;unicast;client_port=" + videoPort + "-" + audioPort + "\r\n"
                        + addHeaders();
        Log.i(TAG, setup);
        return setup;
    }
    /*
    rtsp协议：PLAY
     */
    private String sendPlay(){
        String play =
                "PLAY " + playUrl + " RTSP/1.0\r\n"
                        + (sessionId != null ? "Session: " + sessionId + "\r\n":"")
                        + addHeaders();
        Log.i(TAG, play);
        return play;
    }
    /*
    rtst协议:GET_PARAMETER
     */
    private String sendParam(){
        String param =
                "GET_PARAMETER " + playUrl + " RTSP/1.0\r\n"
                        + (sessionId != null ? "Session: " + sessionId + "\r\n":"")
                        + addHeaders();
        Log.i(TAG, param);
        return param;
    }
    /*
    rtsp协议：TEARDOWN
     */
    private String sendTearDown() {
        String teardown =
                "TEARDOWN " + playUrl + " RTSP/1.0\r\n"
                        + (sessionId != null ? "Session: " + sessionId + "\r\n":"")
                        + addHeaders();
        Log.i(TAG, teardown);
        return teardown;
    }
    private String addHeaders() {
        return "CSeq: "
                + (++mCSeq)
                + "\r\n"
                + "User-Agent: GeneROV/Android MediaCodec\r\n"
                + "\r\n";
    }

    /*
    rtsp协议：解析返回
     */
    private void getResponse(){
        try {
            String line;
            int cnt = 0;
            while ((line = reader.readLine()) != null) {
                Log.d(TAG, line);

                if(line.contains("Session:")){
                    sessionId = line.split(";")[0].split(":")[1].trim();
                }
                if(line.contains("sdp")){
                    cnt = 2;//10;
                }
                cnt--;
                if( line.length() < 2 && cnt <= 0)break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public BlockingQueue<byte[]> getVideoData(){
        return video_data_Queue;
    }


}
