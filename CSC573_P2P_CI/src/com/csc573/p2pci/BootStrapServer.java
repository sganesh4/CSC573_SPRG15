package com.csc573.p2pci;

import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import sun.net.ConnectionResetException;
public class BootStrapServer{
	
	private static ArrayList<String> rfcList = new ArrayList<String>();
	private static final String SP = " ";
	private static final String END = "\n";
	private static final String VERSION = "P2P-CI/1.0";
	private static final String END_MESSAGE="END MESSAGE\n";
	private static final ArrayList<String> connectedClients=new ArrayList<String>();
	private ServerSocket bootstrapServerSocket;
	private boolean serverOn = true;
	public BootStrapServer()
	{
		initServer();
		listenForConnections();
		
		
	}
	private void listenForConnections() {
		
		while (serverOn) {
			try {
					System.out.println("Server Running.. Waiting for connections");
					Socket clientSocket = bootstrapServerSocket.accept();
					ClientHandlerService clientHandlerService = new ClientHandlerService(clientSocket);
					(new Thread(clientHandlerService)).start();
			} catch (IOException e) {
				
				e.printStackTrace();
			}
			
			
		}
	}
	private class ClientHandlerService implements Runnable{
		private Socket clientSocket;
		private boolean isReadInput = true;
		private BufferedReader in=null;
		private PrintWriter out=null;
		private static final String RESULT_OK = "200 OK", 
									NOT_FOUND="404 Not Found",
									BAD_REQUEST = "400 Bad Request", 
									UNSUPPORTED_VERSION = "505 P2P-CI Version Not Supported";
		public ClientHandlerService(Socket clientSocket)
		{
			this.clientSocket = clientSocket;
			synchronized (connectedClients) {
				String connectedClientAddress = clientSocket.getInetAddress().getHostName();
				System.out.println("Accepted connection request from: "+connectedClientAddress);
				if(!connectedClients.contains(connectedClientAddress)){
					
					
					connectedClients.add(connectedClientAddress);
				}
			}
		}
		@Override
		public void run() {
				//Insert connection ack here
				
				
				try {
					 in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
					 out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
					while (isReadInput) {
						String msgLine=null;
						StringBuilder receivedMessage=new StringBuilder();
						//System.out.println(msgLine);
						while ((msgLine=in.readLine())!=null&&!msgLine.contains("END MESSAGE")) {
							//System.out.println(msgLine);
							if(msgLine.length()!=0)
							receivedMessage.append(msgLine+"__"); 
						}
						//System.out.println(receivedMessage.toString());
						if(receivedMessage.toString().contains("quit"))
						{
							isReadInput=false;
							//closeConnection();
						}else if(receivedMessage.length()>2){
							String[] decodedMessage = decodeMessage(receivedMessage.toString());
							if(!decodedMessage[5].equals(VERSION)){
								messageToClient(UNSUPPORTED_VERSION,"");
							}
							else{ 
								if(decodedMessage[0].equalsIgnoreCase("ADD")){
									addNewRfcEntry(decodedMessage);
								}
								else if (decodedMessage[0].equalsIgnoreCase("LOOKUP")) {
									findPeerForRfc(decodedMessage);
								}
								else if (decodedMessage[0].equalsIgnoreCase("LIST")) {
									provideRfcList(decodedMessage);
								}
								else{
									messageToClient(BAD_REQUEST, "");
								}
							}
						}
					}
				}catch (ConnectionResetException e) {
					System.out.println("Connection to host: "+clientSocket.getInetAddress().getHostName()+" Terminated unexpectedly");
				} catch (IOException e) {
					e.printStackTrace();
				}
				finally{
					try {
						in.close();
						out.close();
						clientSocket.close();
						closeConnection();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
		}
		private String[] decodeMessage(String msg)
		{
			//System.out.println(msg);
			String[] message = msg.split("__");
			//System.out.println(Arrays.toString(message));
			String[] decodedMessage = {" "," "," "," "," "," "};
			for(int i=0;i<message.length;i++)
			{
				
				switch(i)
				{
					case 0: String line1[] = message[i].split(" ");
					//System.out.println(Arrays.toString(line1));
					decodedMessage[0] = line1[0];
					if(!decodedMessage[0].equals("LIST"))
					{
						decodedMessage[1]=line1[2];
						decodedMessage[5]=line1[3];
					}
					else {
						decodedMessage[5]=line1[2];
					}
					break;
					case 1:
					case 2:
					case 3:decodedMessage[i+1] = message[i].substring(message[i].indexOf(": ")+1);break;
					
					default: break;
					}
				
			}
			return decodedMessage;
		}
		private void provideRfcList(String[] decodedMessage)
		{
			String peer="";
			synchronized (rfcList){
				
				for(String s: rfcList)
				{
						peer += s+"\n";
				}
				
			}
			messageToClient(RESULT_OK, peer);
		}
		private void addNewRfcEntry(String[] decodedMessage)
		{
			String newRfc = "RFC "+ decodedMessage[1]+" "+decodedMessage[4]+" "+decodedMessage[2]+" "+decodedMessage[3];
			synchronized (rfcList) {
				
				if(!rfcList.contains(newRfc))
				{
					rfcList.add(newRfc);
				}
				
			}
			//System.out.println(rfcList);
			messageToClient(RESULT_OK, newRfc);
		}
		private void findPeerForRfc(String[] decodedMessage)
		{
			String peer=null;
			synchronized (rfcList){
				
				for(String s: rfcList)
				{
					if(s.contains("RFC "+decodedMessage[1])){
						peer = s;
						break;
					}
				}
				
			}
			if(peer!=null){
				messageToClient(RESULT_OK, peer);
			}
			else{
				messageToClient(NOT_FOUND, "");
			}
			
		}
		
		private void messageToClient(String status, String message)
		{
			message = "P2P-CI/1.0"+status+"\n"+message+END+END_MESSAGE+END;
			out.println(message);
			out.flush();
		}
		private void closeConnection()
		{
			String hostName=clientSocket.getInetAddress().getHostName();
			String hostIP = clientSocket.getInetAddress().getHostAddress();
			synchronized (connectedClients) {
				connectedClients.remove(hostName);
				
			}
			synchronized (rfcList) {
				ArrayList<String> newRfcList = new ArrayList<String>();
				for(int i=0; i<rfcList.size();i++)
				{
					
					String rfcEntry=rfcList.get(i);
					if(rfcEntry.contains(hostIP))
					{
						continue;
					}
					newRfcList.add(rfcEntry);
					
				}
				rfcList=newRfcList;
				
			}
			System.out.println("Closed connection with "+hostIP);
			//System.out.println(rfcList+" "+connectedClients);
		}
	}
	private void initServer() {
		try {
			bootstrapServerSocket = new ServerSocket(7734);
		} catch (IOException e) {
			System.out.println("Port 7734 is unavailable. Quitting program."); 
			e.printStackTrace();
			System.exit(-1);
		}
		
	}
	public static void main(String[] args) {
		new BootStrapServer();
	}
	

}
