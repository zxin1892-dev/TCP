package com.ouc.tcp.test;

import java.util.zip.CRC32;
import com.ouc.tcp.message.TCP_PACKET;

public class CheckSum {

    /* 计算TCP报文段校验和：只需校验TCP首部中的seq、ack和sum，以及TCP数据字段 */
    public static short computeChkSum(TCP_PACKET tcpPack) {
        CRC32 crc = new CRC32();
        
        // 1. 校验序号 (seq) - 将int拆分为4个字节
        int seq = tcpPack.getTcpH().getTh_seq();
        crc.update((seq >> 24) & 0xFF);
        crc.update((seq >> 16) & 0xFF);
        crc.update((seq >> 8) & 0xFF);
        crc.update(seq & 0xFF);

        // 2. 校验确认号 (ack) - 将int拆分为4个字节
        int ack = tcpPack.getTcpH().getTh_ack();
        crc.update((ack >> 24) & 0xFF);
        crc.update((ack >> 16) & 0xFF);
        crc.update((ack >> 8) & 0xFF);
        crc.update(ack & 0xFF);

        // 3. 校验数据字段 (data)
        int[] data = tcpPack.getTcpS().getData();
        if (data != null) {
            for (int val : data) {
                // 将数据中的每个int拆分校验
                crc.update((val >> 24) & 0xFF);
                crc.update((val >> 16) & 0xFF);
                crc.update((val >> 8) & 0xFF);
                crc.update(val & 0xFF);
            }
        }

        // CRC32返回的是long类型，题目要求返回short
        // 取CRC32结果的低16位作为校验和
        return (short) (crc.getValue() & 0xFFFF);
    }
}