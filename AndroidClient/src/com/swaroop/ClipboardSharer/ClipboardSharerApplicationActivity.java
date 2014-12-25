package com.swaroop.ClipboardSharer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

public class ClipboardSharerApplicationActivity extends Activity {

	private static final String LAST_USED_IP_ADDRESS_PREF = "LAST-USED-IP-ADDRESS";
	static String cachedDeviceClipBoard = "";
	static String cachedDesktopClipBoard = "";

	static boolean clientThreadAliveToggle = true;
	private static Button startSyncButton;
	private static EditText editText;

	private static StringBuffer clipboardContent = new StringBuffer("");

	static String SHARED_PREF_NAME = "CLIPBOARD-SYNC-UTILITY";

	static ClipboardManager clipboardManager;
	static ClipboardSharerApplicationActivity currentActivity;

	public static ClipboardSharerApplicationActivity getInstance() {
		return currentActivity;
	}

	public void writeToConsole(final String currentDeviceClipboard) {

		currentActivity.runOnUiThread(new Thread("Write To Console") {

			public void run() {
				TextView consoleView = (TextView) findViewById(R.id.Console);

				clipboardContent.append(currentDeviceClipboard + "\n");

				consoleView.setText(clipboardContent);

				final ScrollView scrollView = (ScrollView) findViewById(R.id.SCROLLER_ID);

				scrollView.post(new Runnable() {            
					public void run() {
						scrollView.fullScroll(View.FOCUS_DOWN);              
					}
				});
			}

		});

	}

	/**
	 * Starts the sync process after the user presses the 'Sync button'
	 */
	private final Button.OnClickListener startSyncButtonOnClick = new Button.OnClickListener() {
		public void onClick(View v) {
			Thread clientThread = new Thread(new ClientThread());
			clientThread.start();
		}
	};

	/**
	 * Is triggered when the Exit button is clicked. This finishes the activity
	 */
	private final Button.OnClickListener exitButtonOnClick = new Button.OnClickListener() {
		public void onClick(View v) {
			clientThreadAliveToggle = false;
			currentActivity.finish();
		}
	};


	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		currentActivity = this;
		clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

		editText = (EditText) findViewById(R.id.IPAddressInputField);
		startSyncButton = (Button) findViewById(R.id.StartSyncButton);

		startSyncButton.setOnClickListener(startSyncButtonOnClick);
		Button exitAppButton = (Button) findViewById(R.id.ExitSyncClientButton);
		exitAppButton.setOnClickListener(exitButtonOnClick);

		// Restore from the Shared Preference if its available before. 
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		String lastUsedIPAddress = settings.getString(LAST_USED_IP_ADDRESS_PREF, "");
		editText.setText(lastUsedIPAddress);

		TextView consoleView = (TextView) findViewById(R.id.Console);
		consoleView.setMovementMethod(new ScrollingMovementMethod());
		consoleView.setCursorVisible(true);


	}

	public void storeIPAddressToPreference(String ipAddress) {
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(LAST_USED_IP_ADDRESS_PREF, ipAddress);
		editor.commit();
	}

	/**
	 * Listens to the clipboard changes in the background. 
	 * @author swaroop
	 *
	 */
	private static class ClientThread implements Runnable {

		private static final String CLIPBOARD_SHARER_LOG_TAG = "ClipboardSharer";

		private void log(String tag, String message, boolean logToConsole) {
			Log.d(CLIPBOARD_SHARER_LOG_TAG, message);
			if(logToConsole) 
				ClipboardSharerApplicationActivity.getInstance().writeToConsole(message);
		}

		public void run() {
			String inputIP = editText.getText().toString();
			log(CLIPBOARD_SHARER_LOG_TAG, "User entered IP Address = " + inputIP, true);
			ClipboardSharerApplicationActivity.getInstance().storeIPAddressToPreference(inputIP);
			InetAddress serverAddress;
			try {
				while(clientThreadAliveToggle) {
					serverAddress = InetAddress.getByName(inputIP);
					Socket socket = new Socket(serverAddress, 8686);

					log(CLIPBOARD_SHARER_LOG_TAG, "Successfully connected to " + inputIP, false);

					// Check if the device has to send any data to server
					ObjectOutputStream oos = sendDataToServer(socket);

					log(CLIPBOARD_SHARER_LOG_TAG, "Send data to server = " + inputIP, false);

					// Check if the server has sent some data.
					ObjectInputStream ois = receiveDataFromServer(socket);

					log(CLIPBOARD_SHARER_LOG_TAG, "Fetched data from server = " + inputIP, false);
					oos.close();
					ois.close();
					socket.close();

					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}					
				}
			} catch(Exception e) {
				log(CLIPBOARD_SHARER_LOG_TAG, "Exception Occured while connecting to Clipboard server" + e.getMessage(), true);
				Log.e(CLIPBOARD_SHARER_LOG_TAG, "Exception Occured while connecting to Clipboard server", e);
			}
		}

		/**
		 * Checks if any data has to be sent to the server
		 * @param socket
		 * @return
		 * @throws IOException
		 */
		private ObjectOutputStream sendDataToServer(Socket socket) throws IOException {

			ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());

			if(isDeviceClipBoardChanged()) {
				log(CLIPBOARD_SHARER_LOG_TAG, "Device clipboard changed, sending update to server " + cachedDeviceClipBoard, true);
				oos.writeObject(cachedDeviceClipBoard);
			} else {
				log(CLIPBOARD_SHARER_LOG_TAG, "Device clipboard not-changed, sending dummy request", false);
				oos.writeObject("");
			}

			return oos;
		}

		/**
		 * Checks if the server has some data
		 * @param socket
		 * @return
		 * @throws IOException
		 * @throws ClassNotFoundException
		 */
		public ObjectInputStream receiveDataFromServer(Socket socket) throws IOException, ClassNotFoundException {
			ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

			String receivedStringFromServer = (String) ois.readObject();

			if(!"".equals(receivedStringFromServer)) {
				log(CLIPBOARD_SHARER_LOG_TAG, "Received a string from server, Desktop clipboard updated " + receivedStringFromServer, true);
				setDeviceClipBoard(receivedStringFromServer.toString());
				cachedDesktopClipBoard = receivedStringFromServer.toString();
			}	
			return ois;
		}

		/**
		 * What's there in the device clipboard
		 * @return
		 */
		public static String getDeviceClipBoard() {
			String deviceClipboard = "";
			if(clipboardManager.getText() != null) // If not initialized 
				deviceClipboard = clipboardManager.getText().toString();

			return deviceClipboard;
		}

		/**
		 * Set any changes observed into the device clipboard. 
		 * @param updatedClipboard
		 */
		public static void setDeviceClipBoard(String updatedClipboard) {
			cachedDeviceClipBoard = updatedClipboard;
			clipboardManager.setText(updatedClipboard);

			ClipboardSharerApplicationActivity.getInstance().writeToConsole(updatedClipboard);
		}

		public static boolean isDeviceClipBoardChanged() {
			String currentDeviceClipBoard = getDeviceClipBoard();
			if(currentDeviceClipBoard.equals(cachedDeviceClipBoard)) {
				return false;
			}
			cachedDeviceClipBoard = currentDeviceClipBoard;

			return true;
		}

	}


}