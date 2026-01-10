/***************************GO-BACK-N*****************/
/***** Feng Hong; 2015-12-09******************************/
package com.ouc.tcp.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

public class TCP_Receiver extends TCP_Receiver_ADT {

    private TCP_PACKET ackPack;	//回复的ACK报文段
    int sequence=1;//用于记录当前待接收的包序号
    private int lastAck = -1; //用于记录上一个成功接收并返回ACK的序号
    
    private Map<Integer, int[]> recvBuffer = new TreeMap<Integer, int[]>(); //缓存乱序包
    private int rcv_base = 1; //接收窗口左边界
    private int singleDataSize = 100;
   
    /*构造函数*/
    public TCP_Receiver() {
        super();	//调用超类构造函数
        super.initTCP_Receiver(this);	//初始化TCP接收端
    }

    @Override
    public void rdt_recv(TCP_PACKET recvPack) {
        // 1. 检查校验码
        if (CheckSum.computeChkSum(recvPack) != recvPack.getTcpH().getTh_sum()) {
            System.out.println("检测到位错，丢弃该包");
            return; 
        }

        int currentSeq = recvPack.getTcpH().getTh_seq();
        int dataLen = recvPack.getTcpS().getData().length;

        // 2. 处理报文
        if (currentSeq == rcv_base) {
            //情况 A：收到的正是期待的按序包
            System.out.println("收到按序包，序号: " + currentSeq);
            //放入数据队列（暂存，等待滑动窗口逻辑统一处理）
            recvBuffer.put(currentSeq, recvPack.getTcpS().getData());
            //检查缓存，尝试滑动窗口并交付连续数据
            while (recvBuffer.containsKey(rcv_base)) {
                int[] data = recvBuffer.remove(rcv_base);
                dataQueue.add(data);
                //lastAck为当前按序收到的最高包序号
                lastAck = rcv_base; 
                rcv_base += data.length; //增加期待的下一个序号
                System.out.println("交付数据，rcv_base 推进至: " + rcv_base);
            }
        } else if (currentSeq > rcv_base) {
            //情况 B：收到乱序包（序号跳跃）
            System.out.println("收到乱序包: " + currentSeq + "，期待: " + rcv_base + "。放入缓存。");
            if (!recvBuffer.containsKey(currentSeq)) {
                recvBuffer.put(currentSeq, recvPack.getTcpS().getData());
            }
            //乱序时不更新 lastAck，稍后回复的依然是旧的 lastAck
        } else {
            //情况 C：收到重复的老包 (currentSeq < rcv_base)
            System.out.println("收到重复包: " + currentSeq + "，忽略数据，仅重发 ACK");
        }

        //3.发送累计确认
        tcpH.setTh_ack(lastAck);     
        ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
        tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
        reply(ackPack);
        
        System.out.println("发送累计确认，ACK 序号: " + lastAck);

        // 交付应用层
        if(dataQueue.size() >= 20)
            deliver_data();
    }
    

    @Override
    //交付数据（将数据写入文件）；不需要修改
    public void deliver_data() {
        //检查dataQueue，将数据写入文件
        File fw = new File("recvData.txt");
        BufferedWriter writer;

        try {
            writer = new BufferedWriter(new FileWriter(fw, true));

            //循环检查data队列中是否有新交付数据
            while(!dataQueue.isEmpty()) {
                int[] data = dataQueue.poll();

                //将数据写入文件
                for(int i = 0; i < data.length; i++) {
                    writer.write(data[i] + "\n");
                }

                writer.flush();		//清空输出缓存
            }
            writer.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    //回复ACK报文段
    public void reply(TCP_PACKET replyPack) {
    	//设置错误控制标志
        tcpH.setTh_eflag((byte)7);	//eFlag=7
        //发送数据报
        client.send(replyPack);
    }

}
