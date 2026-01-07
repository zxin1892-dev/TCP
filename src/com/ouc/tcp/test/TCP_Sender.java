///***************************2.1: ACK/NACK
// **************************** Feng Hong; 2015-12-09*/
//
//package com.ouc.tcp.test;
//
//import com.ouc.tcp.client.TCP_Sender_ADT;
//import com.ouc.tcp.client.UDT_RetransTask;
//import com.ouc.tcp.client.UDT_Timer;
//import com.ouc.tcp.message.*;
//import com.ouc.tcp.tool.TCP_TOOL;
//import java.util.ArrayList;
//import java.util.Iterator;
//import java.util.List;
//
//public class TCP_Sender extends TCP_Sender_ADT {
//
//    private TCP_PACKET tcpPack;	//待发送的TCP数据报
//    private volatile int flag = 0;
//    private UDT_Timer timer; //新增定时器
//    
//    /* GBN新增变量 */
//    private int base = 1;           // 窗口基序号
//    private int nextSeqNum = 1;     // 下一个发送序号
//    private int windowSize = 10;    // 窗口大小（可调整）
//    private List<TCP_PACKET> window = new ArrayList<TCP_PACKET>(); // 发送窗口缓存
//
//    /*构造函数*/
//    public TCP_Sender() {
//        super();	//调用超类构造函数
//        super.initTCP_Sender(this);		//初始化TCP接收端
//    }
//
//    @Override
//    //可靠发送（应用层调用）：封装应用层数据，产生TCP数据报；需要修改
//    public void rdt_send(int dataIndex, int[] appData) {
//        
//        // 1. 检查窗口是否已满 (GBN核心：当窗口满时阻塞)
//        while (nextSeqNum >= base + windowSize * appData.length) {
//            // 窗口满，等待 waitACK 推进 base
//        }
//
//        //生成TCP数据报（设置序号和数据字段/校验和),注意打包的顺序
//        tcpH.setTh_seq(nextSeqNum);//包序号设置为字节流号：
//        tcpS.setData(appData);
//        tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);
//        //更新带有checksum的TCP 报文头
//        tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
//        tcpPack.setTcpH(tcpH);
//
//        // 2. 发送TCP数据报并加入窗口缓存
//        udt_send(tcpPack);
//        window.add(new TCP_PACKET(tcpPack.getTcpH(), tcpPack.getTcpS(), tcpPack.getDestinAddr()));
//        
//        // 3. 如果是窗口中的第一个包，启动计时器
//        if (base == nextSeqNum) {
//            timer = new UDT_Timer();
//            UDT_RetransTask task = new UDT_RetransTask(client, tcpPack); 
//            timer.schedule(task, 1000, 1000);  
//        }
//
//        // 更新下一个期待发送的字节序号
//        nextSeqNum += appData.length;
//    }
//
//    @Override
//    //不可靠发送：将打包好的TCP数据报通过不可靠传输信道发送；仅需修改错误标志
//    public void udt_send(TCP_PACKET stcpPack) {
//        //设置错误控制标志
//        tcpH.setTh_eflag((byte)4);  //eFlag=4，模拟位错和丢包环境
//        //发送数据报
//        client.send(stcpPack);
//    }
//
//    @Override
//    //需要修改
//    public void waitACK() {
//        //循环检查确认号对列中是否有新收到的ACK
//        if(!ackQueue.isEmpty()){
//            int currentAck = ackQueue.poll();
//            
//            // 判断是否是有效的确认 (GBN 累积确认)
//            if (currentAck >= base){
//                System.out.println("收到累积确认 ACK: " + currentAck);
//                
//                // 1. 从窗口缓存中移除所有已被确认的包
//                Iterator<TCP_PACKET> it = window.iterator();
//                while (it.hasNext()) {
//                    if (it.next().getTcpH().getTh_seq() <= currentAck) {
//                        it.remove();
//                    }
//                }
//                
//                // 2. 推进 base (假设包长固定，此逻辑需与 rdt_send 序号逻辑一致)
//                // 这里简便起见，假设每个包长度一致。如果不一致，应从包的 data 长度获取
//                base = currentAck + tcpPack.getTcpS().getData().length;
//                
//                // 3. 重置计时器
//                if (timer != null) timer.cancel();
//                
//                if (base != nextSeqNum) {
//                    // 窗口内还有未确认的包，重新为最老的包启动计时器
//                    timer = new UDT_Timer();
//                    TCP_PACKET oldestPack = window.get(0);
//                    timer.schedule(new UDT_RetransTask(client, oldestPack), 1000, 1000);
//                }
//            } else {
//                // 收到冗余ACK，通常说明乱序或丢包，GBN在超时处重传，或在此处实现快速重传
//                System.out.println("收到冗余/旧 ACK: " + currentAck + ", 不移动窗口");
//            }
//        }
//    }
//
//    @Override
//    //接收到ACK报文：发送方检查接收方发回的 ACK/NACK包好坏
//    public void recv(TCP_PACKET recvPack) {
//
//        if (CheckSum.computeChkSum(recvPack) != recvPack.getTcpH().getTh_sum()) {
//            System.out.println("检测到损坏的 ACK 报文！等待超时重传");
//            return; 
//        }
//
//        // 校验通过，处理确认号
//        ackQueue.add(recvPack.getTcpH().getTh_ack());
//        waitACK();
//    }
//}
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
        header.setTh_eflag((byte)4); // 设置错误控制标志
        
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