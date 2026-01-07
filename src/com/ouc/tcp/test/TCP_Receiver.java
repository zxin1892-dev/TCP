/***************************2.1: ACK/NACK*****************/
/***** Feng Hong; 2015-12-09******************************/
package com.ouc.tcp.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

public class TCP_Receiver extends TCP_Receiver_ADT {

    private TCP_PACKET ackPack;	//回复的ACK报文段
    int sequence=1;//用于记录当前待接收的包序号
    private int lastAck = -1; //用于记录上一个成功接收并返回ACK的序号
   
    /*构造函数*/
    public TCP_Receiver() {
        super();	//调用超类构造函数
        super.initTCP_Receiver(this);	//初始化TCP接收端
    }

    @Override
    //接收到数据报：检查校验和， 检查序号，设置回复的ACK报文段
    public void rdt_recv(TCP_PACKET recvPack) {
    	 //1.检查校验码
        if(CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
            int currentSeq = recvPack.getTcpH().getTh_seq();
            
            //2.检查是否是期望的序号 (GBN 严格按序接收)
            if (currentSeq == sequence) {
                // 正确的新分组
                tcpH.setTh_ack(currentSeq);
                lastAck = currentSeq; // 更新最后一次成功的 ACK 序号
                
                ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
                tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
                reply(ackPack);

                dataQueue.add(recvPack.getTcpS().getData());
                sequence += recvPack.getTcpS().getData().length; 
                
                System.out.println("按序接收: " + currentSeq + ", 下一个期望: " + sequence);
            } else {
                // 3. 序号不对（乱序），回复上一个成功的 lastAck (累积确认)
                System.out.println("序号不对 (期望 " + sequence + " 但收到 " + currentSeq + "), 重发 ACK: " + lastAck);
                if (lastAck != -1) {
                    tcpH.setTh_ack(lastAck); 
                    ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
                    tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
                    reply(ackPack);
                }
            }
        } else {
            // 校验和错误，回复上一个成功的 ACK，触发发送方可能的重传
            System.out.println("检测到位错，重发 ACK: " + lastAck);
            if (lastAck != -1) {
                tcpH.setTh_ack(lastAck); 
                ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
                tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
                reply(ackPack);
            }
        }


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
