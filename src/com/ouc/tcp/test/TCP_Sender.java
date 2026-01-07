///***************************2.1: ACK/NACK
// **************************** Feng Hong; 2015-12-09*/
package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimerTask;

public class TCP_Sender extends TCP_Sender_ADT {

    private TCP_PACKET tcpPack;
    private UDT_Timer timer;
    
    /* GBN 核心变量 */
    private int base = 1;
    private int nextSeqNum = 1;
    private int windowSize = 10;
    // 使用线程安全的 List 存储窗口报文
    private List<TCP_PACKET> window = Collections.synchronizedList(new ArrayList<TCP_PACKET>());

    public TCP_Sender() {
        super();
        super.initTCP_Sender(this);
    }

    @Override
    public void rdt_send(int dataIndex, int[] appData) {
        // 1. 检查窗口是否已满
        while (nextSeqNum >= base + windowSize * appData.length) {
            // 阻塞等待
        }
        // 2. 深度复制 Header 和 Segment，防止引用污染
        // 必须为每个包创建独立的 Header 和 Segment 对象
        TCP_HEADER header = new TCP_HEADER();
        header.setTh_seq(nextSeqNum);
        header.setTh_eflag((byte)7); // 设置错误控制标志       
        TCP_SEGMENT segment = new TCP_SEGMENT();
        segment.setData(appData);        
        TCP_PACKET packet = new TCP_PACKET(header, segment, destinAddr);
        header.setTh_sum(CheckSum.computeChkSum(packet));
        packet.setTcpH(header);
        // 3. 发送并缓存
        udt_send(packet);
        window.add(packet);
        
        // 4. 如果是窗口第一个包，启动计时器
        if (base == nextSeqNum) {
            startTimer();
        }

        nextSeqNum += appData.length;
    }

    // 启动/重启计时器的方法
    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new UDT_Timer();
        // 自定义任务：超时则重传整个窗口
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("超时，GBN 重传窗口内所有包...");
                synchronized (window) {
                    for (TCP_PACKET p : window) {
                        client.send(p); // 这里的 client 来自父类
                    }
                }
            }
        };
        timer.schedule(task, 1000, 1000);
    }

    @Override
    public void udt_send(TCP_PACKET stcpPack) {
        stcpPack.getTcpH().setTh_eflag((byte)4);
        client.send(stcpPack);
    }

    @Override
    public void waitACK() {
        if(!ackQueue.isEmpty()){
            int currentAck = ackQueue.poll();
            
            if (currentAck >= base){
                System.out.println("收到累积确认 ACK: " + currentAck);
                
                // 1. 滑动窗口：移除已确认的包
                synchronized (window) {
                    while (!window.isEmpty() && window.get(0).getTcpH().getTh_seq() <= currentAck) {
                        TCP_PACKET ackedPack = window.remove(0);
                        // 更新 base 为被确认包的下一个序号
                        base = ackedPack.getTcpH().getTh_seq() + ackedPack.getTcpS().getData().length;
                    }
                }
                
                // 2. 更新计时器
                if (timer != null) timer.cancel();
                if (base != nextSeqNum) {
                    startTimer(); // 还有未确认的包，为新的 base 启动计时器
                }
            }
        }
    }

    @Override
    public void recv(TCP_PACKET recvPack) {
        if (CheckSum.computeChkSum(recvPack) != recvPack.getTcpH().getTh_sum()) {
            return; 
        }
        ackQueue.add(recvPack.getTcpH().getTh_ack());
        waitACK();
    }
}
