package com.ouc.tcp.test;

import java.util.zip.CRC32;

import com.ouc.tcp.message.TCP_HEADER;
import com.ouc.tcp.message.TCP_PACKET;

public class CheckSum {

    /*计算TCP报文段校验和：只需校验TCP首部中的seq、ack和sum，以及TCP数据字段*/
	public static short computeChkSum(TCP_PACKET tcpPack) {
	    CRC32 crc = new CRC32();
	    // 1. 校验首部：序号和确认号
	    crc.update(tcpPack.getTcpH().getTh_seq());
	    crc.update(tcpPack.getTcpH().getTh_ack());
	    // 2. 校验数据部分
	    if (tcpPack.getTcpS().getData() != null) {
	        for (int val : tcpPack.getTcpS().getData()) {
	            crc.update(val);
	        }
	    }
	    return (short) crc.getValue();
	}
}

