/***************************2.1: ACK/NACK
 **************************** Feng Hong; 2015-12-09*/

package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_RetransTask;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

public class TCP_Sender extends TCP_Sender_ADT {

    private TCP_PACKET tcpPack;	//待发送的TCP数据报
    private volatile int flag = 0;
    private UDT_Timer timer; //新增定时器

    /*构造函数*/
    public TCP_Sender() {
        super();	//调用超类构造函数
        super.initTCP_Sender(this);		//初始化TCP发送端
    }

    @Override
    //可靠发送（应用层调用）：封装应用层数据，产生TCP数据报；需要修改
    public void rdt_send(int dataIndex, int[] appData) {

        //生成TCP数据报（设置序号和数据字段/校验和),注意打包的顺序
        tcpH.setTh_seq(dataIndex * appData.length + 1);//包序号设置为字节流号：
        tcpS.setData(appData);
        tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);
        //更新带有checksum的TCP 报文头
        tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
        tcpPack.setTcpH(tcpH);

        //发送TCP数据报
        udt_send(tcpPack);
        //启动定时器：1000ms后超时
        timer = new UDT_Timer(); //无参构造
        //传入父类的 client 成员
        UDT_RetransTask task = new UDT_RetransTask(client, tcpPack); 
        //必须包含时间参数（延迟, 间隔）
        timer.schedule(task, 1000, 1000);  
        flag = 0;
        
        //等待ACK报文
        //waitACK();
        while (flag==0);
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
        //循环检查ackQueue
        //循环检查确认号对列中是否有新收到的ACK
        if(!ackQueue.isEmpty()){
            int currentAck=ackQueue.poll();
            // System.out.println("CurrentAck: "+currentAck);
            //判断收到的确认号是否等于当前发送包的序号
            if (currentAck == tcpPack.getTcpH().getTh_seq()){
                System.out.println("Clear: "+tcpPack.getTcpH().getTh_seq());
                //收到正确确认，必须关闭当前定时器
                if (timer != null) {
                    timer.cancel();
                }
                flag = 1;
                //break;
            }else{
                System.out.println("收到冗余ACK，准备重传并重启定时器");
                if (timer != null) timer.cancel(); // 关掉旧的
                udt_send(tcpPack);
                timer = new UDT_Timer(); // 开个新的继续等
                timer.schedule(new UDT_RetransTask(client, tcpPack), 1000, 1000);
                
                flag = 0;
            }
        }
    }

    @Override
    //接收到ACK报文：发送方检查接收方发回的 ACK/NACK包好坏
    public void recv(TCP_PACKET recvPack) {

        if (CheckSum.computeChkSum(recvPack) != recvPack.getTcpH().getTh_sum()) {
            System.out.println("检测到损坏的 ACK/NACK 报文！准备重传");
            // 校验和错误，视为 NACK，触发重传
            udt_send(tcpPack); 
            return; 
        }

        // 校验通过，处理确认号
        System.out.println("接收到正确的确认报文，ACK: " + recvPack.getTcpH().getTh_ack());
        ackQueue.add(recvPack.getTcpH().getTh_ack());
        waitACK();

    }

}
