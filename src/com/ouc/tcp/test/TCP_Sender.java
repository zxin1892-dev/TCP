/***************************2.1: ACK/NACK
 **************************** Feng Hong; 2015-12-09*/

package com.ouc.tcp.test;

import java.util.concurrent.ConcurrentHashMap;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_RetransTask;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

public class TCP_Sender extends TCP_Sender_ADT {

    
    private int windowSize = 16; // 窗口大小
    private int base = 1;       // 窗口基序号
    private int nextSeqNum = 1; // 下一个可用序号
    // 用于存储已发送但未确认的数据报，以便重传
    private ConcurrentHashMap<Integer, TCP_PACKET> pendingPackets = new ConcurrentHashMap<Integer, TCP_PACKET>();
    // 为每个分组维护独立的定时器
    private ConcurrentHashMap<Integer, UDT_Timer> timers = new ConcurrentHashMap<Integer, UDT_Timer>();


    /*构造函数*/
    public TCP_Sender() {
        super();	//调用超类构造函数
        super.initTCP_Sender(this);		//初始化TCP发送端
    }

    @Override
    //可靠发送（应用层调用）：封装应用层数据，产生TCP数据报；需要修改
    public void rdt_send(int dataIndex, int[] appData) {
        //计算当前包的序号
        int seq = dataIndex * appData.length + 1;
    	//检查窗口是否已满
        while (seq >= base + windowSize * appData.length) {
            // 窗口满，等待（简单处理：忙等）
            try { Thread.sleep(3); } catch (InterruptedException e) {}
        }


        
        //生成TCP数据报（设置序号和数据字段/校验和),注意打包的顺序
        tcpH.setTh_seq(seq);
        tcpS.setData(appData);
        TCP_PACKET tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);
        //更新带有checksum的TCP 报文头
        tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
        tcpPack.setTcpH(tcpH);

        //发送TCP数据报
        udt_send(tcpPack);
        UDT_Timer timer = new UDT_Timer();
        UDT_RetransTask task = new UDT_RetransTask(client, tcpPack);
        timer.schedule(task, 1500, 1500); // 设置超时时间为1.5秒
        pendingPackets.put(seq, tcpPack);
        timers.put(seq, timer);

        // 更新下一个期待发送的序号
        nextSeqNum = seq + appData.length;
        
     
    }

    @Override
    //不可靠发送：将打包好的TCP数据报通过不可靠传输信道发送；仅需修改错误标志
    public void udt_send(TCP_PACKET stcpPack) {
        //设置错误控制标志
        tcpH.setTh_eflag((byte)4);  //eFlag=4，模拟位错和丢包环境
        //System.out.println("to send: "+stcpPack.getTcpH().getTh_seq());
        //发送数据报
        client.send(stcpPack);
    }

    @Override
    //需要修改
    public void waitACK() {
      
    }

    @Override
    //接收到ACK报文：发送方检查接收方发回的 ACK/NACK包好坏
    public void recv(TCP_PACKET recvPack) {

        if (CheckSum.computeChkSum(recvPack) != recvPack.getTcpH().getTh_sum()) {
            System.out.println("检测到损坏的 ACK/NACK 报文!");
 
            return; 
        }
        int ackNum = recvPack.getTcpH().getTh_ack();
        // 2. 如果收到对应的 ACK，停止该包的计时器并从集合移除
        if (timers.containsKey(ackNum)) {
            System.out.println("收到正确 ACK: " + ackNum + "，停止计时器");
            timers.get(ackNum).cancel();
            timers.remove(ackNum);
            pendingPackets.remove(ackNum);
        }

        // 3. 如果收到的 ACK 是当前的 base，滑动窗口
        if (ackNum == base) {
            // 窗口滑动：将 base 移动到待确认集合中最小的那个序号
            if (pendingPackets.isEmpty()) {
                base = nextSeqNum;
            } else {
                int minSeq = nextSeqNum;
                for (int s : pendingPackets.keySet()) {
                    if (s < minSeq) minSeq = s;
                }
                base = minSeq;
            }
            System.out.println("窗口滑动，新 base: " + base);
        }

    }

}
