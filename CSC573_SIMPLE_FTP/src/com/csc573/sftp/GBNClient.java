package com.csc573.sftp;

//import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
//import java.util.Date;

public class GBNClient {

	private InetAddress IPAddress;
	private ArrayList<byte[]> sentPackets = new ArrayList<byte[]>();
	private DatagramSocket udpClientSocket;
	// private static int acked = 0; //Bux Fix: Cannot have acked as 0. What if
	// 0th Packet is dropped?
	private int acked = -1; // Corrected default acked
													// value
	private int sentNotAcked = -1; // Sequence number of the last sent
											// packet
	private  String serverHostName; // Server hostname
	private  int serverPort; // Server port
	private  String fileName; // Name of the file to be transferred
	private  int lastSequenceNumber; // This is the sequence number of the
											// last packet
	private  int MSS; // MSS size
	private  int windowSize; // window size at client side
	private  final int RETRANS_TIMER = 100;
	//private  int sentPacketCount = 0;
	//private  int retransmissionCount=0;
	private  byte[] fileBytes;
	private  Path filePath;
	private  boolean end = false;
	private  byte[] ackContent;
	private  DatagramPacket ackPacket;
	private  int windowUsed = 0;
	//private long transferTimeTaken = 0;
	private void initFields(String[] args)throws IOException {
		if (args.length != 5) {
			System.out.println("Error: Incorrect args list provided");
			System.out
					.println("Please invoke the module as: "
							+ "GBNClient <IPv4 Server IP> <Server Port> <File Name> <Window Size> <MSS>");
			System.exit(1);
		} else {
			try {
				serverHostName = args[0];
				serverPort = Integer.parseInt(args[1]);
				fileName = args[2];
				windowSize = Integer.parseInt(args[3]);
				windowSize = 1024;
				setMSS(Integer.parseInt(args[4]));
				GBNPacket.setMSS(MSS);
				// Read the file into a byte array
				filePath = Paths.get(fileName);
				fileBytes = Files.readAllBytes(filePath);
				// sequence number of the last packet
				lastSequenceNumber = (int) Math
						.floor(0.5 + fileBytes.length / getMSS());
				udpClientSocket = new DatagramSocket();
				IPAddress = InetAddress.getByName(serverHostName);
				// System.out.println(lastSequenceNumber);
				ackContent = new byte[8];
				ackPacket = new DatagramPacket(ackContent,
						ackContent.length);
			} catch (NumberFormatException e) {
				System.out.println("One of the numerical arguments provided is not a valid number");
				System.exit(-4);
			}catch (UnknownHostException ue) {
				System.out.println("Cannot find the host specified");
				System.exit(-4);
			}
			catch (IOException e2) {
				System.out.println("Cannot find the specified file");
				System.exit(-4);
			}
		}
	}

	public static void main(String[] args) throws IOException {
		GBNClient gbnClient = new GBNClient();
		gbnClient.initFields(args);
		gbnClient.startSenderReceiver();
		
		//System.out.println(sentPacketCount);
	}
	private void startSenderReceiver()
	{
		Sender sender = new Sender();
		Thread senderThread = new Thread(sender);
		senderThread.start();
		
	}
	private int readPacketSequenceNumber(byte[] data) {
		return ((data[0] << 24) & 0xFF000000) | ((data[1] << 16) & 0xFF0000)
				| ((data[2] << 8) & 0xFF00) | (data[3] & 0xFF);

	}

	public int getMSS() {
		return this.MSS;
	}

	public void setMSS(int mSS) {
		this.MSS = mSS;
	}
	private class Sender implements Runnable{

		@Override
		public void run() {
			//transferTimeTaken = new Date().getTime();
			//send MSS packet with sequence number -4040
			GBNPacket mssPacket = new GBNPacket(-4040,
					new String(Integer.toString(MSS)).getBytes());
			byte[] mssBytes = mssPacket.getPacketBytes();
			DatagramPacket mssDP = new DatagramPacket(mssPacket.getPacketBytes(),
					mssBytes.length, IPAddress, serverPort);
			try {
				udpClientSocket.send(mssDP);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			Receiver receiver = new Receiver();
			Thread receiverThread = new Thread(receiver);
			receiverThread.start();
			while(!end){
				//System.out.println(windowUsed);
				
				synchronized (sentPackets) {
					
				
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
					//sentPacketCount++;
					
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
					
					
					try {
						
						
						// within timeout period specified do nothing
						// Exception thrown if timer expires without read
						udpClientSocket.receive(ackPacket); // read a packet from the
															// server
						byte[] receivedAckBytes = ackPacket.getData();
						int receivedAckSeqNo = readPacketSequenceNumber(receivedAckBytes);
						//System.out.println("ACK: "+receivedAckSeqNo);
						acked = receivedAckSeqNo;
						windowUsed = sentNotAcked - acked;
						//if(sentNotAcked==acked)windowUsed = 0;
						synchronized (sentPackets) {
							
						
						if (receivedAckSeqNo == lastSequenceNumber) {

							GBNPacket endPacket = new GBNPacket(lastSequenceNumber + 1,
									new String("END_IS_HERE").getBytes());
							byte[] endBytes = endPacket.getPacketBytes();
							DatagramPacket sendPacket = new DatagramPacket(endBytes,
									endBytes.length, IPAddress, serverPort);
							udpClientSocket.send(sendPacket);
							sentNotAcked++;
							//sentPacketCount++;
							sentPackets.add(endBytes);
						}
						}
						if(receivedAckSeqNo==lastSequenceNumber+1)
						{
							//Bug fix wait for ack on last packet
							//System.out.println("Total packets sent: "+sentPacketCount);
							//System.out.println("Total retransmissions: "+retransmissionCount);
							//transferTimeTaken=new Date().getTime()-transferTimeTaken;
							//System.out.println("Total Transfer Time: "+transferTimeTaken);
							end=true;
						}
						
					} catch (SocketTimeoutException e) {
						// retransmission logic send all packets
						//System.out.println("Timeout on packet = " + (acked+1));
						synchronized (sentPackets) {
							
						for (int i = acked+1; i <= sentNotAcked; i++) {
							//System.out.println("Sending " + (i));
							byte[] retransBytes = sentPackets.get(i);
							DatagramPacket reTrans = new DatagramPacket(retransBytes,
									retransBytes.length, IPAddress, serverPort);
							udpClientSocket.send(reTrans);
							//sentPacketCount++;
							//retransmissionCount++;
							
						}
						}
						
					}
					
				}
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {

				e.printStackTrace();
			}finally{
				udpClientSocket.close();
			}
			
		}
		
	}
} // End of class
