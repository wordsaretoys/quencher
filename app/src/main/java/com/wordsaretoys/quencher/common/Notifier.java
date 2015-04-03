package com.wordsaretoys.quencher.common;

import java.util.ArrayList;

import android.os.Handler;
import android.os.Message;

public enum Notifier implements Handler.Callback {

	// singleton instance
	INSTANCE;
	
	// score editor messages
	public static final int NewScore = 100;
	public static final int ScoreChange = 101;
	public static final int CursorChange = 102;
	public static final int SettingChange = 103;
	public static final int SelectionChange = 105;
	public static final int SetCursor = 106;
	public static final int SetNote = 107;
	public static final int ScoreReady = 108;
	
	// voice editor messages
	public static final int NewVoice = 200;
	public static final int VoiceChange = 201;
	public static final int StageCursorChange = 202;
	public static final int VoiceReady = 203;
	
	// scale editor messages
	public static final int NewScale = 300;
	public static final int ScaleChange = 301;
	public static final int ToneCursorChange = 302;
	public static final int ScaleReady = 303;
	
	// audio engine messages
	public static final int AudioPlaying = 400;
	public static final int AudioStopped = 401;
	public static final int AudioMarker = 402;
	public static final int AudioOff = 403;
	
	// audio alert messages
	public static final int AudioInitFailed = 500;
	
	// storage alert messages
	public static final int StorageSaveFailed = 600;
	public static final int StorageDeleteFailed = 601;
	
	// mp4 alert messages
	public static final int Mp4WriteFailed = 700;
	
	// log tag
	static final String TAG = "Notifier";
	
	/**
	 * callback listener interface
	 */
	public static interface NotificationListener {
		public void handleMessage(Message msg);
	}
	
	// handler
	private Handler handler;
	
	// listener collection
	private ArrayList<NotificationListener> listeners;
	
	/**
	 * ctor
	 */
	private Notifier() {
		handler = new Handler(this);
		listeners = new ArrayList<NotificationListener>();
	}
	
	/**
	 * does nothing except allow the singleton
	 * to be created in a known thread context 
	 */
	public void onCreate() {}
	
	/**
	 * register for events
	 * @param l listener object
	 */
	public void register(NotificationListener l) {
		listeners.add(l);
	}
	
	/**
	 * unregister from events
	 * @param l listener object
	 */
	public void unregister(NotificationListener l) {
		listeners.remove(l);
	}

	@Override
	public boolean handleMessage(Message msg) {
		for (NotificationListener l : listeners) {
			l.handleMessage(msg);
		}
		return true;
	}

	/**
	 * send a message to all customers
	 * @param what message type
	 * @param arg0 first integer argument
	 * @param arg1 second integer argument
	 * @param obj object reference
	 */
	public void send(int what, int arg0, int arg1, Object obj) {
		Message msg = Message.obtain(handler, what, arg0, arg1, obj);
		handler.sendMessage(msg);
	}
	
	/**
	 * send a message to all customers
	 * @param what message type
	 * @param arg0 first integer argument
	 * @param arg1 second integer argument
	 */
	public void send(int what, int arg0, int arg1) {
		send(what, arg0, arg1, null);
	}
	
	/**
	 * send a message to all customers
	 * @param what message type
	 * @param arg integer argument
	 */
	public void send(int what, int arg) {
		send(what, arg, 0, null);
	}
	
	/**
	 * send a message to all customers
	 * @param what message type
	 */
	public void send(int what) {
		send(what, 0, 0, null);
	}
}