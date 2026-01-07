/***************************2.1: ACK/NACK*****************/
/***** Feng Hong; 2015-12-09******************************/
package com.ouc.tcp.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TreeMap;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

public class TCP_Receiver extends TCP_Receiver_ADT {
	 private TCP_PACKET ackPack; 
    private int expectedSeq = 1;       // 期待接收的基序号
    private int windowSize = 16;       // 接收窗口大小
    // 接收缓存，自动按序号排序 (Seq -> Data)
    private TreeMap<Integer, int[]> receiveBuffer = new TreeMap<Integer, int[]>();  
    
    /*构造函数*/
    public TCP_Receiver() {
        super();	//调用超类构造函数
        super.initTCP_Receiver(this);	//初始化TCP接收端
    }

    @Override
    //SR
public void rdt_recv(TCP_PACKET recvPack) {
        // 1. 校验和检查
        if (CheckSum.computeChkSum(recvPack) != recvPack.getTcpH().getTh_sum()) {
            return; 
        }

        int currentSeq = recvPack.getTcpH().getTh_seq();
        int dataLen = recvPack.getTcpS().getData().length;

        // 2. 选择响应逻辑
        // 情况 A: 落在接收窗口内 [expectedSeq, expectedSeq + N]
        if (currentSeq >= expectedSeq && currentSeq < expectedSeq + windowSize * dataLen) {
            
            // 无论是否是 expected，只要在窗口内，就回发该包的 ACK (SR 特点：逐个确认)
            sendAck(currentSeq, recvPack);

            // 存入缓存
            if (!receiveBuffer.containsKey(currentSeq)) {
                receiveBuffer.put(currentSeq, recvPack.getTcpS().getData());
            }

            // 如果正好是期望的那个包，滑动窗口并交付数据
            if (currentSeq == expectedSeq) {
                while (receiveBuffer.containsKey(expectedSeq)) {
                    int[] data = receiveBuffer.remove(expectedSeq);
                    dataQueue.add(data);
                    expectedSeq += data.length;
                }
                deliver_data();
            }
        } 
        // 情况 B: 落在 [expectedSeq - window, expectedSeq - 1] 之间
        // 说明这是之前发过 ACK 但发送方没收到的包，必须重发 ACK，否则发送方会一直重传
        else if (currentSeq < expectedSeq && currentSeq >= expectedSeq - windowSize * dataLen) {
            sendAck(currentSeq, recvPack);
        }
    }
    
private void sendAck(int ackNum, TCP_PACKET recvPack) {
        tcpH.setTh_ack(ackNum);
        ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
        tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
        reply(ackPack);
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
        tcpH.setTh_eflag((byte)4);	//eFlag=4，通道可能会出错，丢包或延迟
        //发送数据报
        client.send(replyPack);
    }

}
