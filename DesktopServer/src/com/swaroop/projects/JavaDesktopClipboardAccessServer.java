//package com.swaroop.projects;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Server Java Clipboard Access Server (This is not a standalone tool and will require the Mobile Client)
 * @author swaroop
 *
 */
public class JavaDesktopClipboardAccessServer {

	static ServerSocket serverSocket;

	static String cachedDesktopClipboard = "";
	static String cachedMobileClipboard = "";

	/**
	 * @param args
	 * @throws IOException 
	 * @throws UnsupportedFlavorException 
	 */
	public static void main(String[] args) throws UnsupportedFlavorException, IOException {

		Thread serverThread = new Thread(new ServerThread());
		serverThread.start();
	}

	/**
	 * Sets the clipboard to the server cache
	 * @param toUpdateDesktopClipboard
	 */
	private static void setDesktopClipBoard(String toUpdateDesktopClipboard) {
		// Set's a text into the clipboard. 
		StringSelection stringSelection = new StringSelection(toUpdateDesktopClipboard);
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents( stringSelection, null);
		cachedDesktopClipboard = toUpdateDesktopClipboard;
	}

	/**
	 * Reads the clipboard and sets it to the cache if changed.
	 * @return - the clipboard information that's read.
	 * @throws UnsupportedFlavorException
	 * @throws IOException
	 */
	private static String readClipBoard() throws UnsupportedFlavorException, IOException {

		// Reads what's in the clipboard. 
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		Transferable contents = clipboard.getContents(null);
		String clipBoardContent = ((String) contents.getTransferData(DataFlavor.stringFlavor));
		
		System.out.println("Text in the Desktop clipboard is " + clipBoardContent);
		return clipBoardContent;
	}

	public static boolean isDesktopClipboardLatest() {
		try {
			String latestDesktopClipboard = readClipBoard();
			boolean isDesktopClipboardAlreadyInSync = cachedDesktopClipboard.equals(latestDesktopClipboard);
			if(!isDesktopClipboardAlreadyInSync) {
				cachedDesktopClipboard = latestDesktopClipboard;
			}
			System.out.println("Server clipboard is " + (isDesktopClipboardAlreadyInSync  ? "not" : "") + " modified" );
			return isDesktopClipboardAlreadyInSync;
		} catch (UnsupportedFlavorException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public static boolean isMobileClipboardLatest(String mobileClipboardLatest) {
		boolean isMobileClipboardAlreadyInSync = cachedMobileClipboard.equals(mobileClipboardLatest);
		System.out.println("Mobile clipboard is " + (isMobileClipboardAlreadyInSync ? "not" : "") + " modified" );
		return isMobileClipboardAlreadyInSync;
	}

	private static class ServerThread implements Runnable {

		@Override
		public void run() {
			try {
				serverSocket = new ServerSocket(8686);
				System.out.println("Listening on port  " + serverSocket.getLocalPort());
				while(true) {
					Socket client = serverSocket.accept();

					// Check if data has been sent by the device and receive it
					ObjectInputStream ois  = readDataFromDevice(client);
					
					// Check if the data has to be sent to the device.
					ObjectOutputStream oos = sendDataToDevice(client);

					ois.close();
					oos.close();
				}

			} catch (IOException e) {
				e.printStackTrace();
			} catch (UnsupportedFlavorException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} finally {
				try {
					serverSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}

		/**
		 * Sends the server clipboard if changed
		 * @param client
		 * @throws IOException
		 * @throws UnsupportedFlavorException
		 */
		private ObjectOutputStream sendDataToDevice(Socket socket)
		throws IOException, UnsupportedFlavorException {

			// Check if server clipboard has been updated, then send it back to device
			ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
			
			if(!isDesktopClipboardLatest()) {
				System.out.println("Appears desktop clipboard has been updated, sending it to device");
				String latestDesktopClipboard = readClipBoard();
				oos.writeObject(latestDesktopClipboard);
			} else {
				oos.writeObject("");
			}
			
			return oos;
		}

		/**
		 * Reads the data that was sent by the Android device
		 * @param socket
		 * @throws IOException
		 * @throws ClassNotFoundException 
		 */
		public static ObjectInputStream readDataFromDevice(Socket socket) throws IOException, ClassNotFoundException {

			System.out.println("Synching information");
			ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
			
			String message = (String) ois.readObject();
						
			// Mobile clipboard has been sent for updation.
			if(!"".equals(message)) {
				System.out.println("Mobile sent its clipboard " + message);
				setDesktopClipBoard(message);
			} else {
				System.out.println("Mobile clipboard didn't change, received dummy request");
			}
			
			return ois;

		}
	}
}


