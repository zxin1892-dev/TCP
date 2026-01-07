/***************************SR*****************/
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
    private int windowSize = 10;
   
    /*构造函数*/
    public TCP_Receiver() {
        super();	//调用超类构造函数
        super.initTCP_Receiver(this);	//初始化TCP接收端
    }

    @Override
    public void rdt_recv(TCP_PACKET recvPack) {
        // 1. 检查校验码
        if (CheckSum.computeChkSum(recvPack) != recvPack.getTcpH().getTh_sum()) {
            System.out.println("检测到位错，SR 协议直接丢弃，等待发送方超时");
            return; 
        }

        int currentSeq = recvPack.getTcpH().getTh_seq();
        int dataLen = recvPack.getTcpS().getData().length;

        //2.逻辑判断：SR接收窗口范围是 [rcv_base, rcv_base+N-1]
        //情况 1：序号在接收窗口内[rcv_base, rcv_base+windowSize)
        if (currentSeq >= rcv_base && currentSeq < rcv_base + windowSize * dataLen) {
            
            //无论是否是期望的rcv_base，只要在窗口内，就必须发送对应序号的独立 ACK
            tcpH.setTh_ack(currentSeq); 
            ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
            tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
            reply(ackPack);
            System.out.println("窗口内接收，发送独立 ACK: " + currentSeq);

            //如果该包还没在缓存里，则存入缓存
            if (!recvBuffer.containsKey(currentSeq)) {
                recvBuffer.put(currentSeq, recvPack.getTcpS().getData());
            }

            //如果收到的正是窗口左边界 (rcv_base)，则可以滑动窗口
            if (currentSeq == rcv_base) {
                //循环检查缓存，交付连续的数据包
                while (recvBuffer.containsKey(rcv_base)) {
                    int[] data = recvBuffer.remove(rcv_base);
                    dataQueue.add(data);
                    rcv_base += data.length; //滑动接收窗口左边界
                    System.out.println("交付数据，rcv_base 推进至: " + rcv_base);
                }
            }
        } 
        //情况2：序号在[rcv_base-N, rcv_base-1]之间
        //这说明该包之前收过，但发送方可能没收到ACK，所以必须重发该包的独立ACK
        else if (currentSeq >= rcv_base - windowSize * dataLen && currentSeq < rcv_base) {
            tcpH.setTh_ack(currentSeq);
            ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
            tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
            reply(ackPack);
            System.out.println("收到已确认过的老包，重发独立 ACK: " + currentSeq);
        } 
        //情况 3：其他序号（超出窗口范围），直接忽略
        else {
            System.out.println("收到超出窗口范围的包: " + currentSeq + "，忽略");
        }

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