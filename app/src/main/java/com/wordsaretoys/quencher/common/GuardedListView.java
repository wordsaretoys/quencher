package com.wordsaretoys.quencher.common;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.ListView;

/**
 * kind of a pointless class, but it prevents HeaderViewListAdapter exceptions
 * when running under Monkey. these errors are an Android bug and not anything
 * I need to worry about.
 */

public class GuardedListView extends ListView {

	public GuardedListView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	@Override
	protected void layoutChildren() {
		try {
			super.layoutChildren();
		} catch (IllegalStateException e) {
			// silently eat this 
		}
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		try {
			super.dispatchDraw(canvas);
		} catch (IndexOutOfBoundsException e) {
			// silently eat this
		}
	}
	
}
