package com.wordsaretoys.quencher.common;

import android.app.Activity;
import android.view.View;
import android.view.Window;

/**
 * provides reference counted interface to progress/UI locking functions
 */
public class Waiter {

	// controlling activity
	Activity activity;
	
	// reference count for wait requests
	private int waitCount;
	
	// reference count for lock requests
	private int lockCount;
	
	/**
	 * ctor, sets activity and progress appearance
	 * MUST be called before content view is set
	 * 
	 * @param a activity
	 */
	public Waiter(Activity a) {
		activity = a;
		activity
			.getWindow()
			.requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
	}
	
	/**
	 * update UI based on current reference counts
	 */
	public void refresh() {
		activity.setProgressBarIndeterminateVisibility(waitCount > 0);
		activity.findViewById(android.R.id.content)
			.setVisibility(isLocked() ? View.GONE : View.VISIBLE);
		activity.invalidateOptionsMenu();
	}

	/**
	 * get locked state
	 * @return true if UI is locked
	 */
	public boolean isLocked() {
		return lockCount > 0;
	}
	
	/**
	 * start waiting
	 */
	public void start() {
		waitCount++;
		refresh();
	}
	
	/**
	 * stop waiting
	 */
	public void stop() {
		waitCount--;
		refresh();
	}
	
	/**
	 * lock the activity UI
	 */
	public void lock() {
		waitCount++;
		lockCount++;
		refresh();
	}
	
	/**
	 * unlock the activity UI
	 */
	public void unlock() {
		waitCount--;
		lockCount--;
		refresh();
	}
}
