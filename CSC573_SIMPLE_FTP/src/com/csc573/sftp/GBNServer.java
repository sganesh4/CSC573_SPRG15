package com.csc573.sftp;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
//import java.net.UnknownHostException;
import java.util.Arrays;

public class GBNServer {
	private  int listenPort;
	private  String fileName;
	private  double lossProbability;
	//private  int packetLossCount = 0;
	//private  int receivedPacketCount = 0;
	private  int MSS;
	private  FileOutputStream fileOutputStream;
	private  DatagramSocket udpServerSocket;
	private  DatagramPacket receivedPacket;
	private  int expectedSequenceNumber;
	private ArrayList<byte[]> receivedOutOfOrder = new ArrayList<byte[]>();
	private  boolean end;
	//private static boolean isMSSKnown = false;
	//private int droppedPacketCount=0;

	private void initFields(String[] args) throws IOException {
		if (args.length != 3) {
			System.out.println("Error: Incorrect args list provided");
			System.out
					.println("Please invoke the module as: "
							+ "GBNServer <port_number> <Local File Name> <Loss Probability [0, 1]>");
			System.exit(1);
		} else {
			try {
				listenPort = Integer.parseInt(args[0]);
				fileName = args[1];
				lossProbability = Double.parseDouble(args[2]);
				//lossProbability = 0.5;
				System.out.println("Listening for connection on port: "
						+ listenPort + "\nData will be stored in: " + fileName
						+ "\nLoss Probability: " + lossProbability);
				fileOutputStream = new FileOutputStream(fileName, false);
				udpServerSocket = null;
				byte[] receivedBytes = new byte[2048];
				receivedPacket = new DatagramPacket(receivedBytes,
						receivedBytes.length);
				expectedSequenceNumber = 0;
				end = true;
				udpServerSocket = new DatagramSocket(listenPort);
			} catch (NumberFormatException e) {
				System.out.println("One of the numerical arguments provided is not a valid number");
				System.exit(-4);
			}
			catch (FileNotFoundException e2) {
				System.out.println("Cannot find the specified file");
				System.exit(-4);
			}

		}
	}

	public static void main(String[] args)throws IOException {
		GBNServer gbnServer = new GBNServer();
		gbnServer.initFields(args);
		gbnServer.startReceiverSender();
	}
	private void startReceiverSender()
	{
try {
			
			while (end) {

				udpServerSocket.receive(receivedPacket);
				//receivedPacketCount++;
				byte[] receivedPackBytes = receivedPacket.getData();
				int receivedSequenceNumber = getPacketSequenceNumber(receivedPackBytes);
				//System.out.println("Expected SN: "+expectedSequenceNumber
					//	+" Received Sequence Number: "+receivedSequenceNumber);
				if(receivedSequenceNumber == -4040){
					//MSS size received
					//byte[] packetData = getPacketData(receivedPackBytes);
					byte[] data = new byte[4];
					System.arraycopy(receivedPackBytes, 8, data, 0, data.length);

					
					MSS= Integer.parseInt(new String(trimZeros(data)));
					//System.out.println(MSS);
					continue;
				}
				if (isAcceptPacket()) {

					// System.out.println("Received Packet: "+Arrays.toString(receivedPackBytes));
					byte[] packetData = getPacketData(receivedPackBytes);
					// System.out.println(new String(packetData));
					int checksum = getPacketChecksum(receivedPackBytes);
					boolean checksumStatus = isChecksumOK(receivedPackBytes,
							checksum);
					if (checksumStatus
							&& expectedSequenceNumber == receivedSequenceNumber) {
						if (new String(packetData).contains("END_IS_HERE")) {
							// System.out.println(packetData);
							end = false;
						} else {
							fileOutputStream.write(packetData);
							fileOutputStream.flush();
						}

						GBNAck ackPack = new GBNAck(receivedSequenceNumber);
						byte[] ackBytes = ackPack.getPacketBytes();
						DatagramPacket ack = new DatagramPacket(ackBytes,
								ackBytes.length, receivedPacket.getAddress(),
								receivedPacket.getPort());
						udpServerSocket.send(ack);
						expectedSequenceNumber++;
					} else {
						
						  if(expectedSequenceNumber!=receivedSequenceNumber) {
							  //Selective arq
							  
						  }
						 /*
						 * else if (!checksumStatus)
						 * { System.out.println("Error: Checksum mismatch"
						 * +"\nDropping the packet"); }
						 */
						//droppedPacketCount++;
						// Do Nothing!!
					}
				} else {
					System.out.println("Packet loss, sequence number = "
							+ receivedSequenceNumber);
					//packetLossCount++;
				}
			}
		} catch (IOException e) {

		} finally {
			try {
				fileOutputStream.close();
				udpServerSocket.close();
				
				//System.out.println("Total number of packets received "
					//	+ "(including retransmission): "+receivedPacketCount);
				//System.out.println("Total number of packets lost: "+packetLossCount);
				//System.out.println("Total number of packets dropped: "+droppedPacketCount);
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}
	private int getPacketChecksum(byte[] receivedPackBytes) {

		return ((receivedPackBytes[4] << 8) & 0xFF00)
				| (receivedPackBytes[5] & 0xFF);
	}

	private boolean isChecksumOK(byte[] data, int checksum) {

		// Last packet case
		byte[] dataBytes = new byte[MSS];
		System.arraycopy(data, 8, dataBytes, 0, dataBytes.length);
		int chk = calculateChecksum(dataBytes);
		// System.out.println(chk+" "+checksum);
		if (chk == checksum)
			return true;
		return false;
	}

	private int getPacketSequenceNumber(byte[] data) {
		// The data is received in bytes. The First 4 bytes are the sequence
		// number
		// We can combine these 4 bytes using simple bit manipulation

		return ((data[0] << 24) & 0xFF000000) | ((data[1] << 16) & 0xFF0000)
				| ((data[2] << 8) & 0xFF00) | (data[3] & 0xFF);
	}

	private byte[] getPacketData(byte[] packet) {

		byte[] data = new byte[MSS];
		System.arraycopy(packet, 8, data, 0, data.length);

		return trimZeros(data);
	}

	public byte[] trimZeros(byte[] data) {
		int i = data.length - 1;
		while (i >= 0 && data[i] == 0)
			i--;
		return Arrays.copyOf(data, i + 1);
	}

	private int calculateChecksum(byte[] data) {
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

	private boolean isAcceptPacket() {
		if (Math.random() > lossProbability) {
			return true;
		}
		return false;
	}
}
