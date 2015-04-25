package com.csc573.sftp;

import java.nio.ByteBuffer;

public class GBNAck {
	private int sequenceNumber;
	byte[] header;

	public GBNAck(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
		header = new byte[8];
		createHeader();
	}

	private void createHeader() {
		// System.out.println(Arrays.toString(new
		// String("1010101010101010").getBytes()));
		// byte b = Byte.parseByte("00101010", 2);
		header[6] = -86;// Setting last 2 bytes to 10101010 to indicate this is
						// an ACK packet
		header[7] = -86;

		byte[] seqBytes = ByteBuffer.allocate(4).putInt(sequenceNumber).array();
		System.arraycopy(seqBytes, 0, header, 0, 4);

		byte[] checksum = ByteBuffer.allocate(4).putInt(0).array();
		System.arraycopy(checksum, 2, header, 4, 2);
	}

	public byte[] getPacketBytes() {
		return header;
	}

}
