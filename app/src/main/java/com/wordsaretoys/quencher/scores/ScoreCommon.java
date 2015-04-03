package com.wordsaretoys.quencher.scores;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.common.Catalogable.OnDataChangedListener;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.common.Storage;
import com.wordsaretoys.quencher.common.Waiter;
import com.wordsaretoys.quencher.data.Note;
import com.wordsaretoys.quencher.data.Scale;
import com.wordsaretoys.quencher.data.Score;
import com.wordsaretoys.quencher.data.Tone;
import com.wordsaretoys.quencher.data.Track;
import com.wordsaretoys.quencher.data.Voice;

/**
 * state and objects common to all score editing functions/views
 */
public class ScoreCommon {

	// activity context
	private Context context;
	
	// score currently under edit
	private Score score;
	
	// position of track with focus
	private int trackPos;
	
	// position of note with focus
	private int notePos;

	// position of beat marker
	private int beatMarker;
	
	// true if a track/note selection is in progress
	private boolean selecting;
	
	// index of track where note selection is in progress
	private int selectionTrackIndex;
	
	// starting and ending selection index in track
	private int selectionStart, selectionEnd;
		
	// hash map of clipped notes
	private int[] clipboard;
	
	// pitch count of clipped scale (for compatibility test)
	private int clipboardPitchCount;
	
	// listener for data changes
	private OnDataChangedListener dataChangeListener;
	
	// score undergoing load
	private long loadingId;
	
	// UI wait state handler
	private Waiter waiter;
	
	/**
	 * ctor, creates objects
	 */
	public ScoreCommon(Context context) {
		this.context = context;
		clipboard = new int[0];
		dataChangeListener = new OnDataChangedListener() {
			@Override
			public void onDataChanged() {
				Notifier.INSTANCE.send(Notifier.ScoreChange);
			}
		};
		score = getMockScore();
		waiter = new Waiter((Activity)context);
	}

	/**
	 * loads common persistent and session settings
	 */
	public void loadState(Bundle b) {
		if (b != null) {
			Bundle c = b.getBundle("common");
			
			long id = c.getLong("workingScore", -1);
			loadScoreAsync(id, false);
			
			trackPos = c.getInt("trackPos", 0);
			notePos = c.getInt("notePos", 0);
			beatMarker = c.getInt("beatMarker", 0);
			selecting = c.getBoolean("selecting", false);
		
			if (selecting) {
				selectionTrackIndex = c.getInt("selectionTrack");
				selectionStart = c.getInt("selectionStart");
				selectionEnd = c.getInt("selectionEnd");
			}				
				
			clipboard = c.getIntArray("clipboard");
			clipboardPitchCount = c.getInt("clipboardPitchCount");
		} else {
			// new instance of application, so initiate license check
			//checkLicense();
			
			// retrieve the last thing user was working on
			SharedPreferences prefs = Storage.INSTANCE.getSharedPreferences();
			long id = prefs.getLong("lastScore", -1);
			if (id != -1) {
				// FIRST remove the id from permanent storage
				// if the score is corrupt, it will not crash 
				// any /subsequent/ instance of the program
				SharedPreferences.Editor editor = prefs.edit();
				editor.remove("lastScore");
				editor.apply();
				// open the score
				openScore(id);
			} else {
				createNewScore();
			}
		}
	}

	/**
	 * saves all session settings
	 */
	public void saveState(Bundle b) {
		Bundle c = new Bundle();
		if (loadingId != -1) {
			c.putLong("workingScore", loadingId);
		} else {
			c.putLong("workingScore", score.getId());
		}
		c.putInt("trackPos", trackPos);
		c.putInt("notePos", notePos);
		c.putInt("beatMarker", beatMarker);
		c.putBoolean("selecting", selecting);

		if (selecting) {
			c.putInt("selectionTrack", selectionTrackIndex);
			c.putInt("selectionStart", selectionStart);
			c.putInt("selectionEnd", selectionEnd);
		}

		c.putIntArray("clipboard", clipboard);
		c.putInt("clipboardPitchCount", clipboardPitchCount);

		b.putBundle("common", c);
		
		if (score != null) {
			// put reference to the score in persistent storage
			SharedPreferences prefs = Storage.INSTANCE.getSharedPreferences();
			SharedPreferences.Editor editor = prefs.edit();
			editor.putLong("lastScore", score.getId());
			editor.apply();
		}		
	}

	/**
	 * get reference to shared score 
	 * @return score object
	 */
	public Score getScore() {
		return score;
	}
	
	/**
	 * get beat marker position
	 * @return marker position
	 */
	public int getBeatMarker() {
		return beatMarker;
	}
	
	/**
	 * set beat marker position
	 * @param m marker position
	 */
	public void setBeatMarker(int m) {
		beatMarker = m;
	}
	
	/**
	 * get current track position, with range checking
	 * @return range checked track position
	 */
	public int getTrackPosition() {
		if (trackPos >= score.getTrackCount()) {
			trackPos = score.getTrackCount() - 1;
		}
		return trackPos;
	}
	
	/**
	 * set track position
	 * @param p track position
	 */
	public void setTrackPosition(int p) {
		if (p < score.getTrackCount()) {
			trackPos = p;
		}
	}
	
	/**
	 * get the track that currently has focus
	 * @return track object
	 */
	public Track getFocusedTrack() {
		return score.getTrack(getTrackPosition());
	}
	
	/**
	 * get note position with range checking
	 * @return range checked note position
	 */
	public int getNotePosition() {
		if (notePos < 0) {
			notePos = 0;
		}
		return notePos;
	}
	
	/**
	 * set note position
	 * @param p note position
	 */
	public void setNotePosition(int p) {
		if (p >= 0) {
			notePos = p;
		}
	}
	
	/**
	 * gets the last note in the score
	 * @return last note
	 */
	public Note getLastNote() {
		int tl = score.getTrackCount();
		Note lastNote = null;
		float lastTime = 0;
		for (int t = 0; t < tl; t++) {
			Track track = score.getTrack(t);
			float timing = track.getTiming();
			int nl = track.getNoteCount();
			for (int n = 0; n < nl; n++) {
				Note note = track.getNoteAt(n);
				float time = note.getIndex() * timing;
				if (time > lastTime) {
					lastNote = note;
					lastTime = time;
				}
			}
		}
		return lastNote;
	}
	
	/**
	 * append a time string of the form HH:MM:SS.SSS to the stringbuilder
	 * 
	 * @param t time in decimal seconds
	 * @param sb string builder object
	 */
	public static void buildTimeString(float t, StringBuilder sb) {
		int h = (int)(t / 3600);
		t = t - h * 3600;
		int m = (int)(t / 60);
		t = t - m * 60;
		int s = (int)(t);
		t = t - s;
		int ms = (int)(t * 1000);
		
		sb.setLength(0);
		sb.append(' ');
		sb.append(h).append('h').append(' ');
		if (m < 10) {
			sb.append('0');
		}
		sb.append(m).append('m').append(' ');
		if (s < 10) {
			sb.append('0');
		}
		sb.append(s).append('.').append(ms);
		if (ms < 10) {
			sb.append('0');
		}
		if (ms < 100) {
			sb.append('0');
		}
		sb.append('s');
	}
	
	/**
	 * set default editing parameters and inform listeners
	 */
	public void reset() {
		trackPos = notePos = 0;
		beatMarker = 0;
		Notifier.INSTANCE.send(Notifier.NewScore);
	}

	/**
	 * sets a new score
	 * used for making copies of existing scores
	 * doesn't need to go through async handling
	 * as the score is already in memory
	 * 
	 * @param s score object
	 */
	public void setScore(Score s) {
		closeScore();
		score = s;
		reset();
	    score.setOnDataChangedListener(dataChangeListener);
		Storage.INSTANCE.setAutosave(score);
	}
	
	/**
	 * loads a score from the database (or creates one)
	 * in a worker thread, preventing UI thread blocking
	 * 
	 * @param id database id of score, or -1 to create a new score
	 * @param reset true if editing parameters should be reset to zero
	 */
	private void loadScoreAsync(final long id, final boolean reset) {
		loadingId = id;
		waiter.lock();

		new Thread(new Runnable() {
			@Override
			public void run() {
				if (id != -1) {
					score = Score.fromId(id);
				} else {
					// this may block if default scale/voice
					// haven't been loaded yet, so it's here
					score = Score.createNew();
				}
				// if we're still attached to a running activity
				if (ScoreCommon.this == ScoreActivity.common) {
				    score.setOnDataChangedListener(dataChangeListener);
					Storage.INSTANCE.setAutosave(score);
					// notify the activity it can refresh itself
					Notifier.INSTANCE.send(Notifier.ScoreReady);
					// notify all other views/fragments
					if (reset) {
						reset();
					} else {
						Notifier.INSTANCE.send(Notifier.ScoreChange);
					}
					// reset loading states
					loadingId = -1;
				}
			}
		}).start();
	}
	
	/**
	 * open a score and reset params to default
	 */
	public void openScore(long id) {
		closeScore();
		loadScoreAsync(id, true);
	}

	/**
	 * create a new score and reset params to default
	 */
	public void createNewScore() {
		closeScore();
		loadScoreAsync(-1, true);
	}
	
	/**
	 * insures unnamed workspaces are purged
	 */
	public void closeScore() {
		if (score != null && isSaveAsRequired()) {
			Storage.INSTANCE.submitForDelete(score);
		}
	}

	/**
	 * returns whether the current score is a workspace
	 * with data in it. call before New or Open actions
	 * 
	 * @return true if workspace with data in it 
	 */
	public boolean isSaveAsRequired() {
		return (score.getName().length() == 0) && (score.getId() != -1);
	}
	
	/**
	 * get selection status
	 * @return true if user is selecting track/note
	 */
	public boolean isSelecting() {
		return selecting;
	}

	/**
	 * get reference to track under selection
	 * @return selection track
	 */
	public Track getSelectionTrack() {
		return score.getTrack(selectionTrackIndex);
	}

	/**
	 * get starting position of selection
	 * @return selection start
	 */
	public int getSelectionStart() {
		return selectionStart;
	}
	
	/**
	 * get ending position of selection
	 * @return selection end
	 */
	public int getSelectionEnd() {
		return selectionEnd;
	}
	
	/**
	 * get selection status for a note
	 * @param n index within track of note
	 * @return true if note is selected
	 */
	public boolean isNoteSelected(int n) {
		return n >= selectionStart && n <= selectionEnd; 
	}
	
	/**
	 * get count of selected notes
	 * @return selection count
	 */
	public int getNoteSelectionCount() {
		int count = 0;
		for (int i = selectionStart; i <= selectionEnd; i++) {
			if (getSelectionTrack().getNote(i) != null) {
				count++;
			}
		}
		return count;
	}
	
	public String getNoteSelectionString() {
		int dn = selectionEnd - selectionStart + 1;
		float dt = 60f * (float) dn * getSelectionTrack().getTiming() 
				/ (float) score.getTempo();
		StringBuilder sb = new StringBuilder();
		buildTimeString(dt, sb);
		return sb.toString();
	}

	/**
	 * handle the attempted selection of a note
	 * @param t index of pressed track
	 * @param n index of pressed note
	 */
	public void handleSelection(int t, int n) {
		// if we're already inside a selection
		if (selecting) {
			// is this the right track?
			if (t == selectionTrackIndex) {
				// reassign the start or end of selection
				// based the relative position of the new index
				if (n < selectionStart) {
					selectionStart = n;
				} else {
					selectionEnd = n;
				}
			}
		} else {
			// begin a new selection
			selecting = true;
			selectionTrackIndex = score.getTrack(t).getIndex();
			selectionStart = selectionEnd = n;
		}
	}
	
	/**
	 * cleanup note selections
	 */
	public void clearSelection() {
		selecting = false;
		selectionStart = selectionEnd = -1;
	}

	/**
	 * copies selected notes to clipboard
	 * @param cut true if existing notes are to be removed
	 */
	public void copyToClipboard(boolean cut) {
		int count = selectionEnd - selectionStart + 1;
		clipboard = new int[count];
		for (int i = 0; i < count; i++) {
			int ni = i + selectionStart;
			Note note = getSelectionTrack().getNote(ni);
			if (note != null) {
				clipboard[i] = note.getPitchNumber();
				if (cut) {
					getSelectionTrack().clearNote(ni);
				}
			} else {
				clipboard[i] = -1;
			}
		}
		// make note of the pitch count for later
		clipboardPitchCount = getSelectionTrack().getScale().getCount();
	}
	
	/**
	 * get clipboard status
	 * @return true if clipboard is empty
	 */
	public boolean isClipboardEmpty() {
		return clipboard.length == 0;
	}
	
	/**
	 * pastes contents of clipboard to current cursor position
	 * WILL overwrite existing notes AND scale notes to destination
	 * WILL NOT erase clipboard
	 * 
	 * @return resource ID of error message, or 0 if successful
	 */
	public int pasteClipboard() {
		Track track = getFocusedTrack();
		// test that the destination track isn't locked
		if (track.isLocked()) {
			return R.string.scorePasteLockError;
		}
		// test the the destination track is using a compatible scale
		if (track.getScale().getCount() != clipboardPitchCount) {
			return R.string.scorePasteScaleError;
		}
		// copy each note to the new track
		for (int i = 0; i < clipboard.length; i++) {
			if (clipboard[i] != -1) {
				track.setNote(i + notePos, clipboard[i]);
			} else {
				track.clearNote(i + notePos);
			}
		}
		return 0;
	}

	/**
	 * generate a big score for stress-testing
	 */
	public void createStressTest() {
		Score score = new Score();
		score.setName("Stress Test");
		for (int tr = 0; tr < 16; tr++) {
			Track track = new Track(score);
			
			int sid = (int)(25 * Math.random());
			Scale scale = Scale.fromId(sid);
			if (scale == null) {
				scale = Scale.getDefault();
			}
			track.setScale(scale);

			int vid = (int)(25 * Math.random());
			Voice voice = Voice.fromId(vid);
			if (voice == null) {
				voice = Voice.getDefault();
			}
			track.setVoice(voice);
			score.addTrack(track);
			
			for (int nt = 0; nt < 5000; nt++) {
				int p = (int)(scale.getCount() * Math.random());
				track.setNote(nt, p);
			}
		}
		setScore(score);
	}
	
	/**
	 * generate a simple mock-up of a score
	 * to display when the app starts up
	 * @return score object
	 */
	private Score getMockScore() {
		Score mock = new Score();
		mock.setName(context.getResources().getString(R.string.app_name));
		Scale sc = new Scale();
		for (int i = 0; i < 7; i++) {
			Tone tone = new Tone(sc);
			sc.addTone(tone);
		}
		Voice vo = new Voice();
		for (int i = 0; i < 256; i++) {
			Track tr = new Track(mock, sc, vo);
			mock.addTrack(tr);
		}
		return mock;
	}
	
	/**
	 * get score loading state
	 * @return true if async loading score
	 */
	public boolean isLoading() {
		return loadingId != -1;
	}
	
	/**
	 * get waiter object
	 * @return waiter
	 */
	public Waiter getWaiter() {
		return waiter;
	}
	
	/**
	 * initiate license check
	 */
/*	public void checkLicense() {
		waiter.lock();
		final Activity act = (Activity) context;
		byte[] SALT = {
			-3, 58, 20, 5, -83, 94, 58, 3, -43, 8,
			6, 3, -43, 76, 34, -5, 34, 56, 45, -35
		};
		String deviceId = 
				Secure.getString(
				context.getContentResolver(), Secure.ANDROID_ID);
		String RSAkey = 
				context.getResources().getString(R.string.licenseKey);
		
		LicenseChecker checker = new LicenseChecker(context, 
				new ServerManagedPolicy(context, 
				new AESObfuscator(SALT, act.getPackageName(), deviceId)), RSAkey);
		
		checker.checkAccess(new LicenseCheckerCallback() {
			@Override
			public void allow(int reason) {
				act.runOnUiThread(new Runnable() {
					public void run() {
						waiter.unlock();
					}
				});
			}
			@Override
			public void dontAllow(final int reason) {
				act.runOnUiThread(new Runnable() {
					public void run() {
						waiter.unlock();
						FragmentManager fm = act.getFragmentManager();
						DeferredDialog dd = (reason == Policy.RETRY) ?
							new ScoreDialogs.LicenseCheckFailureDialog() :
							new ScoreDialogs.LicenseCheckNoneDialog();
						dd.show(fm, Popup.Deferred);
					}
				});
			}
			@Override
			public void applicationError(int errorCode) {
				Log.e("", "License check returned error code " + errorCode);
				dontAllow(Policy.NOT_LICENSED);
			}
			
		});
	}
	*/
}
