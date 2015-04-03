package com.wordsaretoys.quencher.scales;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.wordsaretoys.quencher.common.Catalogable.OnDataChangedListener;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.common.Storage;
import com.wordsaretoys.quencher.common.Waiter;
import com.wordsaretoys.quencher.data.Scale;
import com.wordsaretoys.quencher.data.Stage;
import com.wordsaretoys.quencher.data.Tone;
import com.wordsaretoys.quencher.data.Voice;

/**
 * state and objects common to all scale editing functions
 */
public class ScaleCommon {

	// scale currently under edit
	private Scale scale;
	
	// true if current scale is in use
	private boolean inUse;
	
	// true if current scale is the default
	private boolean isDefault;
		
	// index of currently focused tone
	private int tonePos;
	
	// pure tone voice for playing back scales
	Voice playbackVoice;

	// listener for data changes
	private OnDataChangedListener dataChangeListener;
	
	// database id of scale undergoing load
	private long loadingId;

	// UI wait state handler
	private Waiter waiter;
	
	/**
	 * ctor
	 * @param c context object
	 */
	public ScaleCommon(Context context) {
		playbackVoice = new Voice();
		Stage stage = new Stage(playbackVoice);
		stage.setHarmonic(0, 1);
		stage.setLevel(1);
		stage.setTime(0.25f);
		playbackVoice.addStage(stage);

		dataChangeListener = new OnDataChangedListener() {
			@Override
			public void onDataChanged() {
				Notifier.INSTANCE.send(Notifier.ScaleChange);
			}
		};
		
		waiter = new Waiter((Activity)context);
	}
		
	/**
	 * get current scale object
	 * @return scale object
	 */
	public Scale getScale() {
		return scale;
	}
	
	/**
	 * get writable state of current scale
	 * @return true if scale is writable
	 */
	public boolean isWritable() {
		return !(inUse || isDefault);
	}
	
	/**
	 * get default state of current scale
	 * @return true if scale is the default
	 */
	public boolean isDefault() {
		return isDefault;
	}
	
	/**
	 * get current tone position, with range checking
	 * @return tone position
	 */
	public int getTonePosition() {
		if (tonePos >= scale.getToneCount()) {
			tonePos = scale.getToneCount() - 1;
		}
		return tonePos;
	}
	
	/**
	 * set current tone position
	 * @param p tone position
	 */
	public void setTonePosition(int p) {
		if (p < scale.getToneCount()) {
			tonePos = p;
		}
	}
	
	/**
	 * get tone that has the focus
	 * @return tone object
	 */
	public Tone getFocusedTone() {
		return scale.getTone(getTonePosition());
	}

	/**
	 * get pure tone voice for scale playback
	 * @return pure tone voice
	 */
	public Voice getPlaybackVoice() {
		return playbackVoice;
	}
	
	/**
	 * loads all persistent and session settings
	 */
	public void loadState(Bundle b) {
		if (b != null) {
			Bundle c = b.getBundle("common");
			long id = c.getLong("workingScale", -1);
	    	loadScaleAsync(id, false);
			tonePos = c.getInt("tonePos", 0);
		} else {
			createNewScale();
		}
	}

	/**
	 * saves all persistent and session settings
	 */
	public void saveState(Bundle b) {
		Bundle c = new Bundle();
		if (loadingId != -1) {
			c.putLong("workingScale", loadingId);
		} else {
			c.putLong("workingScale", scale.getId());
		}
		c.putInt("tonePos", tonePos);
		b.putBundle("common", c);
	}
	
	/**
	 * reset common params on scale load
	 */
	private void reset() {
		tonePos = 0;
		Notifier.INSTANCE.send(Notifier.NewScale);
	}
	
	/**
	 * returns whether the current scale is a workspace
	 * with data in it. call before New or Open actions
	 * 
	 * @return true if workspace with data in it 
	 */
	public boolean isSaveAsRequired() {
		return (scale.getName().length() == 0) && (scale.getId() != -1);
	}
	
	/**
	 * set current scale object
	 * 
	 * called from "save as" to avoid reading
	 * the whole thing back in; as it's a new
	 * object, we can assume it's not in use
	 */
	public void setScale(Scale s) {
		closeScale();
		scale = s;
		inUse = false;
		isDefault = false;
		reset();
		Storage.INSTANCE.setAutosave(scale);
	    scale.setOnDataChangedListener(dataChangeListener);
	}

	/**
	 * open a scale object asynchronously
	 * preventing UI thread blocking
	 * 
	 * @param id database id of scale, or -1 for new 
	 */
	private void loadScaleAsync(final long id, final boolean reset) {
		loadingId = id;
		waiter.lock();

		new Thread(new Runnable() {
			@Override
			public void run() {
				if (id != -1) {
					// load scale
					scale = Scale.fromId(id);
					// check whether it's being used in a score
					inUse = scale.inUse();
					// check whether it's the default
					SharedPreferences prefs = Storage.INSTANCE.getSharedPreferences();
					isDefault = id == prefs.getLong("defaultScale", 0);
				} else {
					// no blocking calls, but might as well
					// travel the same path as open case
					scale = Scale.createNew();
					inUse = false;
					isDefault = false;
				}
				// if we're still attached to a running activity 
				if (ScaleCommon.this == ScaleActivity.common) {
					scale.setOnDataChangedListener(dataChangeListener);
					Storage.INSTANCE.setAutosave(scale);
					// notify the activity it can refresh itself
					Notifier.INSTANCE.send(Notifier.ScaleReady);
					// notify all other views/fragments
					if (reset) {
						reset();
					} else {
						Notifier.INSTANCE.send(Notifier.ScaleChange);
					}
					// reset loading states
					loadingId = -1;
				}
			}
		}).start();
	}
	
	/**
	 * open a scale
	 */
	public void openScale(long id) {
		closeScale();
		loadScaleAsync(id, true);
	}

	/**
	 * create a new scale using default template
	 */
	public void createNewScale() {
		closeScale();
		loadScaleAsync(-1, true);
	}
	
	/**
	 * insures unnamed workspaces are purged
	 */
	public void closeScale() {
		if (scale != null && isSaveAsRequired()) {
			Storage.INSTANCE.submitForDelete(scale);
		}
	}

	public Waiter getWaiter() {
		return waiter;
	}
}
