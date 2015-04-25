package com.csc573.p2pci;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;

public class P2PClient {
	private static final String SP = " ";
	private static final String END = "\n";
	private static final String VERSION = "P2P-CI/1.0";
	private static final String END_MESSAGE = "END MESSAGE";
	private BufferedReader in = null;
	private PrintWriter out = null;
	private int listenPort;
	private String hostname;
	private Socket p2pClientSocket;
	private static final int ALL_RFC = 0, SINGLE_RFC = 1;
	private HashMap < String, String > myRfcList;
	private int BOOTSTRAP_SERVER_PORT;
	private final String rfcPath = ".//RFCs";
	private static boolean isKeepAlive = true;
	private String serverIP;
	private Thread peerListenerThread;
	private PeerListener pl;
	private static final String RESULT_OK = "200 OK",
			NOT_FOUND = "404 Not Found", BAD_REQUEST = "400 Bad Request",
			UNSUPPORTED_VERSION = "505 P2P-CI Version Not Supported";

	public P2PClient(String serverIP, String serverPort, String listenPort) {
		this.serverIP = serverIP;
		this.BOOTSTRAP_SERVER_PORT = Integer.parseInt(serverPort);
		this.listenPort = Integer.parseInt(listenPort);
		try {
			this.hostname = Inet4Address.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			
			p2pClientSocket = new Socket(this.serverIP, BOOTSTRAP_SERVER_PORT); 
			in = new BufferedReader(new InputStreamReader(
			p2pClientSocket.getInputStream()));
			out = new PrintWriter(new OutputStreamWriter(
			p2pClientSocket.getOutputStream()));
			initRfcCollection();
		}catch(ConnectException connectException){
			System.out.println("Connection To Host Refused. Check if Server is running");
			System.exit(-2);
		} 
		catch (IOException e1) {
			e1.printStackTrace();
		}
		
		this.pl = new PeerListener(this.listenPort);
		peerListenerThread = new Thread(pl);
		peerListenerThread.start();
	}

	private void initRfcCollection() throws IOException {
		File f = new File(rfcPath);
		File[] myRfcs = f.listFiles();
		myRfcList = new HashMap < String, String > ();
		for (File file: myRfcs) {
			String s = file.getName();
			String rfcNum = s.substring(s.lastIndexOf("_") + 1,
			s.indexOf(".txt"));
			if (file.isFile()) {
				BufferedReader reader = new BufferedReader(new FileReader(file));
				String line;
				String rfcTitle = "";
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.length() > 0) {
						String[] titleLine = line.split("Title: ");
						if(titleLine.length>1)
						{
							rfcTitle = titleLine[1];
							break;
						}
					}
				}
				myRfcList.put(rfcNum, rfcTitle);
				reader.close();
			}
		}
	}

	public static void main(String[] args) throws IOException {
		boolean isClentActive = true;
		if(args.length!=3)
		{
			System.out.println("Please invoke the program with the following arguments");
			System.out.println("P2PClient <Server IP> <Server Port> <Listen Port>");
			System.exit(-1);
		}
		BufferedReader br = new BufferedReader(new InputStreamReader(System. in ));
		
		String serverIP = args[0];
		String serverPort = args[1];
		String listenPort =args[2];
		P2PClient p = new P2PClient(serverIP, serverPort, listenPort);
		while (isClentActive) {
			//System.out.print("\033[H\033[2J");
			//System.out.flush();
			System.out.println("MENU\n---------");
			System.out.println("1. Get RFC List from Server");
			System.out.println("2. Add my RFCs to Server Index");
			System.out.println("3. Get RFC from Peer");
			System.out.println("4. Leave Network");
			int option = Integer.parseInt(br.readLine());
			switch (option) {
				case 1:
					p.getRfcList();
					break;
				case 2:
					p.addRfcs(ALL_RFC, "");
					break;
				case 3:
					p.getRfcFromPeer();
					break;
				case 4:
					isClentActive = false;
					p.disconnectFromNetwork();
					break;
				default:
					break;
			}
		}
	}

	private void getRfcList() {
		String request = "LIST ALL P2P-CI/1.0\n" 
						+ "Hostname: " + this.hostname + END 
						+ "Port: " + this.listenPort + END 
						+ END_MESSAGE + END;
		sendRequestToServer(request);
	}

	private void addRfcs(int mode, String rfcId) {

		if (mode == ALL_RFC) {
			for (String rfcID: myRfcList.keySet()) {
				String request = "ADD RFC" + SP + rfcID + SP + VERSION + END 
								+ "Host: " + this.hostname + END 
								+ "Port: " + this.listenPort + END 
								+ "Title: " + myRfcList.get(rfcID) + END 
								+ END_MESSAGE + END;
				sendRequestToServer(request);
			}
		} else if (mode == SINGLE_RFC) {
			String request = "ADD RFC" + SP + rfcId + SP + VERSION + END 
							+ "Host: " + this.hostname + END 
							+ "Port: " + this.listenPort + END 
							+ "Title: " + myRfcList.get(rfcId) + END 
							+ END_MESSAGE + END;
			sendRequestToServer(request);
		}
	}

	private void printResponse() throws IOException {
		String response = null;
		while ((response = in.readLine()) != null && !response.contains(END_MESSAGE)) {
			if (response.length() != 0) System.out.println(response);
		}
	}

	private void sendRequestToServer(String request) {
		out.println(request);
		out.flush();
		try {
			printResponse();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void disconnectFromNetwork() {
		String request = "quit" + END + END_MESSAGE + END;
		sendRequestToServer(request);
		isKeepAlive = false;
		try {
			this.pl.peerListenerSocket.close();
			this.p2pClientSocket.close();
			//peerListenerThread.interrupt();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void getRfcFromPeer() throws IOException {
		System.out.println("Enter peer address");
		BufferedReader br = new BufferedReader(new InputStreamReader(System. in ));
		String address = br.readLine();
		System.out.println("Enter Peer Port");
		int port = Integer.parseInt(br.readLine());
		System.out.println("Enter RFC ID");
		String rfcId = br.readLine();
		RFCDownloader rfcDownloader= new RFCDownloader(address, port, rfcId);
		new Thread(rfcDownloader).start();
		
	}

	private class PeerListener implements Runnable {
		
		private ServerSocket peerListenerSocket;
		
		public PeerListener(int listenPort) {
			try {
				peerListenerSocket = new ServerSocket(listenPort);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		@Override
		public void run() {
			while (isKeepAlive) {
				System.out.println("P2P Client is now ready to serve other peers");
				try {
					Socket peerClientSocket = peerListenerSocket.accept();
					RFCUploader rfcUploader = new RFCUploader(peerClientSocket);
					(new Thread(rfcUploader)).start();
				} catch(SocketException socketException){
					System.out.println("The client has been disconnected from the network");
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
			//System.out.println("Connection Closed");
		}
	}

	private class RFCUploader implements Runnable {
		private Socket peerClientSocket;
		// private boolean isReadInput = true;
		private BufferedReader uploaderIn = null;
		private PrintWriter uploaderOut = null;
		public RFCUploader(Socket peerClientSocket) {
			this.peerClientSocket = peerClientSocket;
		}

		@Override
		public void run() {

			try { uploaderIn = new BufferedReader(new InputStreamReader(
				peerClientSocket.getInputStream()));
				uploaderOut = new PrintWriter(new OutputStreamWriter(
				peerClientSocket.getOutputStream()));
				String msgLine = null;
				StringBuilder receivedMessage = new StringBuilder();
				while ((msgLine = uploaderIn.readLine()) != null && !msgLine.contains("END MESSAGE")) {
					if (msgLine.length() != 0) receivedMessage.append(msgLine + "__");
				}
				if (receivedMessage.length() > 2) {
					String[] decodedMessage = decodeMessage(receivedMessage.toString());
					if (!decodedMessage[4].equals(VERSION)) {
						messageToClient(UNSUPPORTED_VERSION, "");
					} else {
						if (decodedMessage[0].equalsIgnoreCase("GET")) {
							uploadFileToClient(decodedMessage);
						} else {
							messageToClient(BAD_REQUEST, "");
						}
					}
				}

			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try { uploaderIn .close();
					  uploaderOut.close();
					peerClientSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}

		private void messageToClient(String status, String message) {
			// Calendar c = Calendar.getInstance();
			Date now = new Date();
			message = "P2P-CI/1.0 " + status + END 
					+ "Date: " + now.toString() + END 
					+ "OS: " + System.getProperty("os.name") + END 
					+ message + END 
					+ END_MESSAGE + END;
			uploaderOut.println(message);
			uploaderOut.flush();
		}

		private String[] decodeMessage(String msg) {
			System.out.println(msg);
			String[] message = msg.split("__");
			// System.out.println(Arrays.toString(message));
			String[] decodedMessage = {
				" ", " ", " ", " ", " "
			};
			String line1[] = message[0].split(" ");
			System.out.println(Arrays.toString(line1));
			decodedMessage[0] = line1[0];
			decodedMessage[1] = line1[2];
			decodedMessage[2] = message[1].substring(message[1].indexOf(": ") + 1);
			decodedMessage[3] = message[2].substring(message[2].indexOf(": ") + 1);
			decodedMessage[4] = line1[3];
			return decodedMessage;
		}

		public void uploadFileToClient(String[] decodedMessage)
		throws IOException {
			File requestedFile = new File(rfcPath + "//" + "RFC_" + decodedMessage[1] + ".txt");
			StringBuilder sb = new StringBuilder();
			String status;
			String message = "";
			if (requestedFile.isFile()) {
				sb.append("Last-Modified: " + new Date(requestedFile.lastModified()).toString() + END);
				sb.append("Content-Length: " + requestedFile.length() + END);
				sb.append("Content-Type: Text/Text" + END);
				sb.append("DATA BEGINS HERE" + END);
				status = RESULT_OK;
				Scanner reader = new Scanner(requestedFile);
				while (reader.hasNextLine()) {
					sb.append(reader.nextLine() + END);
				}
				sb.append("DATA ENDS HERE" + END);
				message = sb.toString();
				reader.close();
			} else {
				status = NOT_FOUND;
			}
			messageToClient(status, message);
		}

	}

	private class RFCDownloader implements Runnable {
		private Socket peerClientSocket;
		private BufferedReader downloaderIn = null;
		private PrintWriter downloaderOut = null;
		private String rfcId;
		public RFCDownloader(String address, int port, String rfcId) {
			try {
				this.peerClientSocket = new Socket(address, port);
				this.rfcId = rfcId;
			} catch(ConnectException connectException){
				System.out.println("Connection To Peer Refused. Check if Peer is running");
				System.exit(-2);
			} 
			catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		@Override
		public void run() {
			try{
				downloaderIn = new BufferedReader(new InputStreamReader(peerClientSocket.getInputStream()));
				downloaderOut = new PrintWriter(new OutputStreamWriter(peerClientSocket.getOutputStream()));
				System.out.println("Enter RFC ");
				String request = "GET RFC "+rfcId+SP+VERSION+END
								+"Hostname: "+hostname+END
								+"OS: "+System.getProperty("os.name")+END+END_MESSAGE+END;
				sendRequestToPeer(request);
				
			}catch (IOException e) {
				e.printStackTrace();
			} finally {
				try { downloaderIn .close();
					  downloaderOut.close();
					peerClientSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		private void sendRequestToPeer(String request)
		{
			downloaderOut.println(request);
			downloaderOut.flush();
			try {
				saveRfcToDisk();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		private void saveRfcToDisk() throws IOException {
			String response = null;
			boolean isReadTitle = false;
			String receivedRfcTitle="";
			while ((response = downloaderIn.readLine()) != null && !response.contains(END_MESSAGE)) {
				if (response.length() != 0) System.out.println(response);
				if(response.contains("DATA BEGINS HERE"))
				{
					StringBuilder sb = new StringBuilder();
					while(!(response=downloaderIn.readLine()).contains("DATA ENDS HERE"))
					{
						System.out.println(response);
						if(!isReadTitle&&response.contains("Title: "))
						{
							isReadTitle = true;
							receivedRfcTitle = response.split("Title: ")[1];
						}
						sb.append(response+END);
					}
					FileWriter fw = new FileWriter(new File(rfcPath+"//RFC_"+rfcId+".txt"), false);
					fw.write(sb.toString());
					fw.close();
					
				}
			}
			System.out.println("");
			myRfcList.put(rfcId, receivedRfcTitle);
			addRfcs(SINGLE_RFC, rfcId);
		}
	}
}