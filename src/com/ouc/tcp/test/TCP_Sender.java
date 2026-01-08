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
import java.util.Map;
import java.util.TimerTask;
import java.util.TreeMap;

public class TCP_Sender extends TCP_Sender_ADT {

    private UDT_Timer timer;
    
    //GBN 核心变量
    private int base = 1;
    private int nextSeqNum = 1;
    //private int windowSize = 10;
    // 修改 window 存储的对象为 SR_Packet
    //private List<SR_Packet> window = Collections.synchronizedList(new ArrayList<SR_Packet>());
 // tcp-tahoe--- 新增 ---
    private float cwnd = 1.0f;          // 拥塞窗口，初始为1
    private int ssthresh = 16;          // 门限值 (以报文段个数为单位)
    private int dupAckCount = 0;        // 重复ACK计数器
    private int lastAck = -1;           // 上一次收到的累积确认序号
    private int singleDataSize = 100;
 // 缓存已发送但尚未确认的包，用于重传
    private Map<Integer, TCP_PACKET> sentSegments = new TreeMap<Integer, TCP_PACKET>();
    
    public TCP_Sender() {
        super();
        super.initTCP_Sender(this);
    }
    //新增计时器与超时处理，Tahoe 在超时后必须将 cwnd 降为 1，并重新开始慢启动。
    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new UDT_Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("超时！重回慢启动，base: " + base);
                // Tahoe 超时动作
                int oldSsthresh = ssthresh;
                ssthresh = Math.max((int)cwnd / 2, 2);
                System.out.println("ssthresh 调整: " + oldSsthresh + " -> " + ssthresh);
                System.out.println("cwnd " + (int)cwnd + " -> 1\n");
                
                cwnd = 1.0f;
                dupAckCount = 0;
                
                // 重传 base 包
                if (sentSegments.containsKey(base)) {
                    udt_send(sentSegments.get(base));
                }
                startTimer(); // 递归重设计时器
            }
        };
        timer.schedule(task, 1000, 1000);
    }
    
    @Override
    public void rdt_send(int dataIndex, int[] appData) {
    	 int dataLen = appData.length;
    	 // 1. 检查拥塞窗口是否允许发送
        // (int)cwnd 将浮点数转为整数报文个数
        while (nextSeqNum >= base + (int)cwnd * dataLen) {
            // 窗口已满，等待 (在真实系统是阻塞，模拟器中通常是等待下一次调用)
            System.out.println("窗口已满，等待中... cwnd: " + cwnd);
            try { Thread.sleep(5); } catch (Exception e) {}
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
                
        // 3. 存入缓存并发送
        sentSegments.put(nextSeqNum, packet);
        udt_send(packet);
        
        // 4. 单计时器逻辑：如果这是窗口中第一个包（即 base 刚刚被发送）
        if (base == nextSeqNum) {
            startTimer();
        }


        // 更新序号
        nextSeqNum += appData.length;
    }



    @Override
    public void udt_send(TCP_PACKET stcpPack) {
        stcpPack.getTcpH().setTh_eflag((byte)7);
        client.send(stcpPack);
    }
    //Tahoe 采用 累计确认
    @Override
    public void waitACK() {
        if (ackQueue.isEmpty()) return;
        int currentAck = ackQueue.poll();
        
        // 记录旧的 cwnd 用于日志打印
        int oldCwnd = (int)cwnd;

        // Tahoe 累计确认逻辑：currentAck是接收方收到的最高序号
        if (currentAck >=base) {
            // --- A. 收到新的有效确认 ---
            // 收到新确认
            System.out.println("Receive ACK Number: " + currentAck);
            
            // 滑动窗口：移除所有 <= currentAck 的包
            for (int i = base; i <= currentAck; i += singleDataSize) {
                sentSegments.remove(i);
            }
            
            base = currentAck + singleDataSize; // 更新 base 为期待的下一个
//            // 1. 滑动窗口：移除所有序号小于 currentAck 的已确认包
//            synchronized (sentSegments) {
//                java.util.Iterator<Integer> it = sentSegments.keySet().iterator();
//                while (it.hasNext()) {
//                    if (it.next() < currentAck) it.remove();
//                    else break;
//                }
//            }
            
            // 2. 更新 base
            base = currentAck+ singleDataSize; // 更新 base 为期待的下一个
            
            // 3. 拥塞窗口调整 (Tahoe 逻辑)
            if (cwnd < ssthresh) {
                // 慢启动：每收到一个新ACK，cwnd + 1
                cwnd += 1.0f;
                System.out.println("cwnd " + oldCwnd + " -> " + (int)cwnd);
            } else {
                // 拥塞避免：每收到一个新ACK，增加 1/cwnd
                cwnd += 1.0f / oldCwnd;
                // 只有当整数部分发生变化时才打印，或者按你的截图每确认一个包就打印浮点转整数的结果
                if ((int)cwnd > oldCwnd) {
                    System.out.println("cwnd " + oldCwnd + " -> " + (int)cwnd + "\n");
                }
            }

           
            
            
            // 4. 重置重复计数
            dupAckCount = 0;
            lastAck = currentAck;

            // 5. 计时器管理：如果还有没确认的包，重启计时器；否则停止
            if (base < nextSeqNum) {
                startTimer();
            } else {
                if (timer != null) timer.cancel();
            }

        } else if (currentAck == lastAck&& currentAck != -1) {
            // --- B. 收到重复确认 ---
            dupAckCount++;
            if (dupAckCount == 3) {
                System.out.println("检测到3次重复ACK，触发 Tahoe 快重传！序号: " + base);
                // Tahoe 特有动作：门限减半，窗口设为 1
                
             // 打印 ssthresh 调整信息
                int oldSsthresh = ssthresh;
                ssthresh = Math.max((int)cwnd / 2, 2);
                cwnd = 1.0f;// Tahoe 特色：快重传后立即回到 cwnd=1
                System.out.println("cwnd " + oldCwnd + " -> 1");
                System.out.println("ssthresh 调整: " + oldSsthresh + " -> " + ssthresh + "\n");
                
                // 快重传：立即发送 base 处的包
                if (sentSegments.containsKey(base)) {
                    udt_send(sentSegments.get(base));
                    startTimer(); // 重传后重新计时
                }
                dupAckCount = 0;
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



