package com.csc573.sftp;

import java.nio.ByteBuffer;

public class GBNPacket {
	private int sequenceNumber;
	private static int MSS;
	public static void setMSS(int mSS) {
		MSS = mSS;
	}

	private byte[] data;
	byte[] header;

	public GBNPacket(int sequenceNumber, byte[] data) {
		// System.out.println();
		this.sequenceNumber = sequenceNumber;
		this.data = new byte[MSS];
		System.arraycopy(data, 0, this.data, 0, data.length);
		header = new byte[8];
		createHeader();
	}

	private void createHeader() {
		header[6] = 85;// Setting last 2 bytes to 85 to indicate this is a data
						// packet
		header[7] = 85;

		byte[] seqBytes = ByteBuffer.allocate(4).putInt(sequenceNumber).array();
		System.arraycopy(seqBytes, 0, header, 0, 4);
		int checksum1 = calculateChecksum();
		// System.out.println(checksum1);
		byte[] checksum = ByteBuffer.allocate(4).putInt(checksum1).array();
		// System.out.println(Arrays.toString(checksum));
		System.arraycopy(checksum, 2, header, 4, 2);
	}

	private int calculateChecksum() {
		int sum = 0, tempData = 0;
		for (int i = 0; i < data.length; i += 2) {
			if (i == data.length - 1) {
				tempData = ((data[i] << 8) & 0xFF00);

			} else {
				tempData = ((data[i] << 8) & 0xFF00) | (data[i + 1] & 0xFF);
			}
			sum += tempData;
			if ((sum & 0xFFFF0000) > 0) {
				sum += 1;
			}
		}
		sum = ~sum;
		sum = sum & 0xFFFF;
		return sum;
	}

	public byte[] getPacketBytes() {
		return formPacketToSend();
	}

	private byte[] formPacketToSend() {
		byte[] packet = new byte[MSS + 8];
		System.arraycopy(header, 0, packet, 0, 8);
		// System.out.println(Arrays.toString(data));
		System.arraycopy(data, 0, packet, 8, MSS);
		// System.out.println(Arrays.toString(packet));
		return packet;
	}
}
