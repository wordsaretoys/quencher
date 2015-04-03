package com.wordsaretoys.quencher.common;

import android.annotation.TargetApi;
import android.app.Application;
import android.os.Build;
import android.os.StrictMode;

import com.wordsaretoys.quencher.audio.Engine;

/**
 * manages object lifetime/startup consistently
 */
public class QuencherApp extends Application {

	final static String TAG = "QuencherApp";

	public static boolean DEVELOPER_MODE = false;
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	public void onCreate() {
		super.onCreate();

		if (DEVELOPER_MODE) {
	        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
	        .detectAll()
	        .penaltyLog()
	        .penaltyDeath()
	        .build());
	        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
	        .detectLeakedClosableObjects()
	        .detectFileUriExposure()
	        .detectLeakedRegistrationObjects()
	        .detectLeakedSqlLiteObjects()
	        .penaltyLog()
	        .penaltyDeath()
	        .build());
		}		
		
		// insure these objects are created
		// in the UI thread context
		Notifier.INSTANCE.onCreate();
		Storage.INSTANCE.onCreate(this);
		Engine.INSTANCE.onCreate(this);
	}
	
}
