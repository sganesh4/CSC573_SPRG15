package com.csc573.sftp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

public class GBNClient {

	private static InetAddress IPAddress;
	private static ArrayList<byte[]> sentPackets = new ArrayList<byte[]>();
	private static DatagramSocket udpClientSocket;
	// private static int acked = 0; //Bux Fix: Cannot have acked as 0. What if
	// 0th Packet is dropped?
	private static int acked = -1; // Corrected default acked
													// value
	private static int sentNotAcked = -1; // Sequence number of the last sent
											// packet
	private static String serverHostName; // Server hostname
	private static int serverPort; // Server port
	private static String fileName; // Name of the file to be transferred
	private static int lastSequenceNumber; // This is the sequence number of the
											// last packet
	private static int MSS; // MSS size
	private static int windowSize; // window size at client side
	private static final int RETRANS_TIMER = 100;
	private static int sentPacketCount = 0;
	private static byte[] fileBytes;
	private static Path filePath;
	private static boolean end = false;
	private static byte[] ackContent;
	private static DatagramPacket ackPacket;
	private static int windowUsed = 0;
	private static void initFields(String[] args)throws IOException {
		if (args.length != 5) {
			System.out.println("Error: Incorrect args list provided");
			System.out
					.println("Please invoke the module as: "
							+ "GBNClient <IPv4 Server IP> <Server Port> <File Name> <Window Size> <MSS>");
			System.exit(1);
		} else {
			serverHostName = args[0];
			serverPort = Integer.parseInt(args[1]);
			fileName = args[2];
			windowSize = Integer.parseInt(args[3]);
			windowSize = 1024;
			setMSS(Integer.parseInt(args[4]));
			// Read the file into a byte array
			filePath = Paths.get(fileName);
			fileBytes = Files.readAllBytes(filePath);
			// System.out.println(Arrays.toString(fileBytes));
			// sequence number of the last packet
			lastSequenceNumber = (int) Math
					.floor(0.5 + fileBytes.length / getMSS());
			udpClientSocket = new DatagramSocket();
			IPAddress = InetAddress.getByName(serverHostName);
			// System.out.println(lastSequenceNumber);
			ackContent = new byte[8];
			ackPacket = new DatagramPacket(ackContent,
					ackContent.length);
		}
	}

	public static void main(String[] args) throws Exception {

		initFields(args);
		new GBNClient().startSenderReceiver();
		
		//System.out.println(sentPacketCount);
	}
	private void startSenderReceiver()
	{
		Sender sender = new Sender();
		Thread senderThread = new Thread(sender);
		senderThread.start();
		Receiver receiver = new Receiver();
		Thread receiverThread = new Thread(receiver);
		receiverThread.start();
	}
	private static int readPacketSequenceNumber(byte[] data) {
		return ((data[0] << 24) & 0xFF000000) | ((data[1] << 16) & 0xFF0000)
				| ((data[2] << 8) & 0xFF00) | (data[3] & 0xFF);

	}

	public static int getMSS() {
		return MSS;
	}

	public static void setMSS(int mSS) {
		MSS = mSS;
	}
	private class Sender implements Runnable{

		@Override
		public void run() {
			//send MSS packet with sequence number -4040
			GBNPacket mssPacket = new GBNPacket(-4040,
					new String(Integer.toString(MSS)).getBytes());
			byte[] mssBytes = mssPacket.getPacketBytes();
			DatagramPacket mssDP = new DatagramPacket(mssPacket.getPacketBytes(),
					mssBytes.length, IPAddress, serverPort);
			try {
				udpClientSocket.send(mssDP);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			while(!end){
				//System.out.println(windowUsed);
				
				synchronized (this) {
					
				
				while ( windowUsed < windowSize && sentNotAcked < lastSequenceNumber) {
					
					byte[] dataBytes = new byte[MSS];
					++sentNotAcked;
					dataBytes = Arrays.copyOfRange(fileBytes, sentNotAcked
							* getMSS(), (sentNotAcked + 1) * getMSS());
					// System.out.println(Arrays.toString(dataBytes));
					GBNPacket packet = new GBNPacket(sentNotAcked, dataBytes);
					byte[] sendDataBytes = packet.getPacketBytes();
					DatagramPacket sendPacket = new DatagramPacket(sendDataBytes,
							sendDataBytes.length, IPAddress, serverPort);

					try {
						udpClientSocket.send(sendPacket);
					} catch (IOException e) {
						
						e.printStackTrace();
					}
					sentPacketCount++;
					
					windowUsed = sentNotAcked - acked;
					sentPackets.add(sendDataBytes);
					//System.out.println("Sent Packet "+sentNotAcked);
					
					

				} // End of while for outstanding <= window size
				}
			}
			
		}
		
	}
	private class Receiver implements Runnable{

		@Override
		public void run() {
			try {udpClientSocket.setSoTimeout(RETRANS_TIMER);
				while (!end) { // Outer loop. Keep on sending
					synchronized (this) {
						
					
					try {
						
						
						// within timeout period specified do nothing
						// Exception thrown if timer expires without read
						udpClientSocket.receive(ackPacket); // read a packet from the
															// server
						byte[] receivedAckBytes = ackPacket.getData();
						int receivedAckSeqNo = readPacketSequenceNumber(receivedAckBytes);
						System.out.println("ACK: "+receivedAckSeqNo);
						acked = receivedAckSeqNo;
						windowUsed = sentNotAcked - acked;
						//if(sentNotAcked==acked)windowUsed = 0;
						if (receivedAckSeqNo == lastSequenceNumber) {

							GBNPacket endPacket = new GBNPacket(lastSequenceNumber + 1,
									new String("END_IS_HERE").getBytes());
							byte[] endBytes = endPacket.getPacketBytes();
							DatagramPacket sendPacket = new DatagramPacket(endBytes,
									endBytes.length, IPAddress, serverPort);
							udpClientSocket.send(sendPacket);
							sentNotAcked++;
							sentPackets.add(endBytes);
						}
						if(receivedAckSeqNo==lastSequenceNumber+1)
						{
							//Bug fix wait for ack on last packet
							end=true;
						}
						
					} catch (SocketTimeoutException e) {
						// retransmission logic send all packets
						//System.out.println(acked+" "+sentNotAcked+" "+windowUsed+" "+sentPackets.size());
						//if(sentNotAcked==acked)continue;
						System.out.println("Timeout on packet = " + (acked+1));
						
						for (int i = acked+1; i <= sentNotAcked; i++) {
							System.out.println("Sending " + (i));
							byte[] retransBytes = sentPackets.get(i);
							DatagramPacket reTrans = new DatagramPacket(retransBytes,
									retransBytes.length, IPAddress, serverPort);
							udpClientSocket.send(reTrans);
							sentPacketCount++;
							
						}
						
					}
					}
				}
			} catch (SocketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}finally{
				udpClientSocket.close();
			}
			
		}
		
	}
} // End of class
