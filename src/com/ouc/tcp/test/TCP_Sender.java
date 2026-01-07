///***************************SR
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
    
    //GBN 核心变量
    private int base = 1;
    private int nextSeqNum = 1;
    private int windowSize = 10;
    // 修改 window 存储的对象为 SR_Packet
    private List<SR_Packet> window = Collections.synchronizedList(new ArrayList<SR_Packet>());
    public TCP_Sender() {
        super();
        super.initTCP_Sender(this);
    }
    //创建一个内部类 SR_Packet，绑定数据包、定时器和状态。
    class SR_Packet {
        TCP_PACKET packet;
        UDT_Timer timer;
        boolean isAcked = false;

        public SR_Packet(TCP_PACKET p) { this.packet = p; }
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
        
        //3.创建 SR_Packet 对象
        final SR_Packet srPack = new SR_Packet(packet);
        //4.为这个包启动独立的计时器
        srPack.timer = new UDT_Timer();
        //定义该包的重传任务
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("SR 超时重传单包，序号: " + srPack.packet.getTcpH().getTh_seq());
                client.send(srPack.packet); 
            }
        };
        srPack.timer.schedule(task, 1000, 1000); //1秒后重传，每隔1秒循环

        // 5. 加入窗口并发送
        window.add(srPack);
        udt_send(packet);

        // 更新序号
        nextSeqNum += appData.length;
    }



    @Override
    public void udt_send(TCP_PACKET stcpPack) {
        stcpPack.getTcpH().setTh_eflag((byte)4);
        client.send(stcpPack);
    }

    //收到一个 ACK，只标记对应的那个包。
    @Override
    public void waitACK() {
        if(!ackQueue.isEmpty()){
            int currentAck = ackQueue.poll();
            System.out.println("收到确认 ACK: " + currentAck);
            
            synchronized (window) {
                //1.在窗口中找到对应的包并标记
                for (SR_Packet srp : window) {
                    if (srp.packet.getTcpH().getTh_seq() == currentAck) {
                        if (!srp.isAcked) {
                            srp.isAcked = true;
                            //停止该包的独立计时器
                            if (srp.timer != null) {
                                srp.timer.cancel();
                            }
                            System.out.println("包 " + currentAck + " 已成功确认，停止计时器");
                        }
                        break; 
                    }
                }

                //2.滑动窗口：如果窗口最左边的包已被确认，则滑动
                while (!window.isEmpty() && window.get(0).isAcked) {
                    SR_Packet removed = window.remove(0);
                    // 更新 base 为被移除包的下一个序号
                    base = removed.packet.getTcpH().getTh_seq() + removed.packet.getTcpS().getData().length;
                    System.out.println("滑动窗口，新 base: " + base);
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



