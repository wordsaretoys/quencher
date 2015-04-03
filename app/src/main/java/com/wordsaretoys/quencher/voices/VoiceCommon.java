package com.wordsaretoys.quencher.voices;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.wordsaretoys.quencher.common.Catalogable.OnDataChangedListener;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.common.Storage;
import com.wordsaretoys.quencher.common.Waiter;
import com.wordsaretoys.quencher.data.Stage;
import com.wordsaretoys.quencher.data.Voice;

/**
 * state and objects common to all voice editing functions
 */
public class VoiceCommon {

	static float DefaultTestFrequency = 440f;
	
	// voice currently under edit
	private Voice voice;

	// true if current voice is in use
	private boolean inUse;
	
	// true if current voice is the default
	private boolean isDefault;
		
	// index of currently focused stage
	private int stagePos;
	
	// frequency of test playback note
	private float testFrequency;
	
	// listener for data changes
	private OnDataChangedListener dataChangeListener;
	
	// database id of voice undergoing load
	private long loadingId;
	
	// UI wait state handler
	private Waiter waiter;
	
	/**
	 * default ctor
	 */
	public VoiceCommon(Context context) {
		testFrequency = DefaultTestFrequency;

		dataChangeListener = new OnDataChangedListener() {
			@Override
			public void onDataChanged() {
				Notifier.INSTANCE.send(Notifier.VoiceChange);
			}
		};
		
		waiter = new Waiter((Activity)context);
	}
	
	/**
	 * get current voice object
	 * @return voice object
	 */
	public Voice getVoice() {
		return voice;
	}
	
	/**
	 * get writable state of current voice
	 * @return true if voice is writable
	 */
	public boolean isWritable() {
		return !(inUse || isDefault);
	}
	
	/**
	 * get default state of current voice
	 * @return true if voice is the default
	 */
	public boolean isDefault() {
		return isDefault;
	}
	
	/**
	 * get current stage position, with range checking
	 * @return stage position
	 */
	public int getStagePosition() {
		if (stagePos >= voice.getStageCount()) {
			stagePos = voice.getStageCount() - 1;
		}
		return stagePos;
	}
	
	/**
	 * set current stage position
	 * @param p stage position
	 */
	public void setStagePosition(int p) {
		if (p < voice.getStageCount()) {
			stagePos = p;
		}
	}

	/**
	 * get frequency of playback test note
	 * @return frequency in Hz
	 */
	public float getTestFrequency() {
		return testFrequency;
	}
	
	/**
	 * set frequency of playback test note
	 * @param f frequency in Hz
	 */
	public void setTestFrequency(float f) {
		testFrequency = f;
	}
	
	/**
	 * get stage that has the focus
	 * @return stage object
	 */
	public Stage getFocusedStage() {
		return voice.getStage(getStagePosition());
	}
	
	/**
	 * loads all persistent and session settings
	 */
	public void loadState(Bundle b) {
		if (b != null) {
			Bundle c = b.getBundle("common");
			long id = c.getLong("workingVoice", -1);
	    	loadVoiceAsync(id, false);
			stagePos = c.getInt("stagePos", 0);
			testFrequency = c.getFloat("testFrequency", DefaultTestFrequency);
		} else {
			createNewVoice();
		}
	}

	/**
	 * saves all persistent and session settings
	 */
	public void saveState(Bundle b) {
		Bundle c = new Bundle();
		if (loadingId != -1) {
			c.putLong("workingVoice", loadingId);
		} else {
			c.putLong("workingVoice", voice.getId());
		}
		c.putInt("stagePos", stagePos);
		c.putFloat("testFrequency", testFrequency);
		b.putBundle("common", c);
	}

	/**
	 * reset common params on voice load
	 */
	private void reset() {
		stagePos = 0;
		Notifier.INSTANCE.send(Notifier.NewVoice);
	}
	
	/**
	 * returns whether the current voice is a workspace
	 * with data in it. call before New or Open actions
	 * 
	 * @return true if workspace with data in it 
	 */
	public boolean isSaveAsRequired() {
		return (voice.getName().length() == 0) && (voice.getId() != -1);
	}
	
	/**
	 * set the voice object
	 * 
	 * called from "save as" to avoid reading
	 * the whole thing back in; as it's a new
	 * object, we can assume it's not in use
	 */
	public void setVoice(Voice v) {
		closeVoice();
		voice = v;
		inUse = false;
		isDefault = false;
		reset();
		Storage.INSTANCE.setAutosave(voice);
	    voice.setOnDataChangedListener(dataChangeListener);
	}
	
	/**
	 * open a voice object asynchronously
	 * preventing UI thread blocking
	 * 
	 * @param id database id of voice, or -1 for new
	 */
	private void loadVoiceAsync(final long id, final boolean reset) {
		loadingId = id;
		waiter.lock();
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (id != -1) {
					// load voice
					voice = Voice.fromId(id);
					// check whether it's being used in a score
					inUse = voice.inUse();
					// check whether it's the default
					SharedPreferences prefs = Storage.INSTANCE.getSharedPreferences();
					isDefault = id == prefs.getLong("defaultVoice", 0);
				} else {
					// no blocking calls, but might as well
					// travel the same path as open case
					voice = Voice.createNew();
					inUse = false;
					isDefault = false;
				}
				// if we're still attached to a running activity
				if (VoiceCommon.this == VoiceActivity.common) {
					voice.setOnDataChangedListener(dataChangeListener);
					Storage.INSTANCE.setAutosave(voice);
					// notify the activity it can refresh itself
					Notifier.INSTANCE.send(Notifier.VoiceReady);
					// notify all other views/fragments
					if (reset) {
						reset();
					} else {
						Notifier.INSTANCE.send(Notifier.VoiceChange);
					}
					// reset loading states
					loadingId = -1;
				}
			}
		}).start();
	}

	/**
	 * open a voice object asynchronously
	 * preventing UI thread blocking
	 * 
	 * @param id database id of voice
	 */
	public void openVoice(long id) {
		closeVoice();
		loadVoiceAsync(id, true);
	}

	/**
	 * create a new voice
	 */
	public void createNewVoice() {
		closeVoice();
		loadVoiceAsync(-1, true);
	}

	/**
	 * insures unnamed workspaces are purged
	 */
	public void closeVoice() {
		if (voice != null && isSaveAsRequired()) {
			Storage.INSTANCE.submitForDelete(voice);
		}
	}
	
	/**
	 * get waiter object
	 * @return waiter
	 */
	public Waiter getWaiter() {
		return waiter;
	}
}
