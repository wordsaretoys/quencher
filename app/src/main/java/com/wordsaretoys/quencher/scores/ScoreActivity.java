package com.wordsaretoys.quencher.scores;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.audio.Engine;
import com.wordsaretoys.quencher.audio.Mp4;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.common.Notifier.NotificationListener;
import com.wordsaretoys.quencher.common.Popup;
import com.wordsaretoys.quencher.common.Popup.DeferredDialog;
import com.wordsaretoys.quencher.common.QuencherApp;
import com.wordsaretoys.quencher.common.SettingsActivity;
import com.wordsaretoys.quencher.common.Storage;
import com.wordsaretoys.quencher.data.Note;
import com.wordsaretoys.quencher.data.Scale;
import com.wordsaretoys.quencher.data.Score;
import com.wordsaretoys.quencher.data.Track;
import com.wordsaretoys.quencher.scales.ScaleActivity;
import com.wordsaretoys.quencher.scores.ComposerView.Goto;
import com.wordsaretoys.quencher.voices.VoiceActivity;

/**
 * provides a score composition view plus menus/toolbars
 * serves as the main launching activity
 * maintains storage/notifier objects
 */
public class ScoreActivity extends Activity implements NotificationListener {

	// activity request codes
	static final int OpenScoreList = 0;
	static final int ManageVoices = 1;
	static final int ManageScales = 2;
	static final int Settings = 3;
	
	// data and methods useful for multiple score objects
	static ScoreCommon common;
	
	// track property fragment
	TrackFragment trackFragment;

	// composer view
	ComposerView composerView;
	
	// action mode for note selection
	ActionMode actionMode;
	
	// listeners
	ActionModeListener actionListener;

	// menu id of action that triggered a save as dialog
	int postSaveAction;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    
	    // insure that storage started up correctly
	    if (Storage.INSTANCE.getStartupException() != null) {
	    	// it didn't, so let user know and bug out.
		    Toast.makeText(this, R.string.snafuDatabase, Toast.LENGTH_LONG).show();
		    finish();
		    return;
	    }
	    
	    // warn if developer mode is set
	    if (QuencherApp.DEVELOPER_MODE) {
	    	Toast.makeText(this, 
	    			"Warning: Developer Mode is currently enabled!!!", 
	    			Toast.LENGTH_LONG).show();
	    }

	    // insures that preferences will have default values on first-time use
	    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
	    
	    common = new ScoreCommon(this);
    	common.loadState(savedInstanceState);
    	
	    View view = getLayoutInflater().inflate(R.layout.score_main, null);
	    setContentView(view);

	    trackFragment = (TrackFragment) getFragmentManager().findFragmentById(R.id.trackFragment);
		composerView = (ComposerView) view.findViewById(R.id.composerView);

		if (savedInstanceState != null) {
			composerView.loadState(savedInstanceState);
		}
		actionListener = new ActionModeListener();
	}

	@Override
	public void onResume() {
		super.onResume();
		Notifier.INSTANCE.register(composerView);
	    Notifier.INSTANCE.register(this);

	    // hide any restored deferred dialogs
	    // until they can be properly refreshed
		DeferredDialog dialog = 
				(DeferredDialog) getFragmentManager()
				.findFragmentByTag(Popup.Deferred);
		if (dialog != null) {
			dialog.hide();
		}
		
		common.getWaiter().refresh();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		Notifier.INSTANCE.unregister(this);
		Notifier.INSTANCE.unregister(composerView);
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		common.saveState(outState);
		composerView.saveState(outState);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.score, menu);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {

		// if the UI is locked, show no menu
		if (common.getWaiter().isLocked()) {
			return false;
		}

		// if a score is playing, hide everything but stop
		boolean playing = Engine.INSTANCE.isPlaying();
		for (int i = 0; i < menu.size(); i++) {
			MenuItem item = menu.getItem(i);
			if (item.getItemId() == R.id.stop) {
				item.setVisible(playing);
			} else {
				item.setVisible(!playing);
			}
		}

		if (!playing) {
			boolean isWorkspace = common.getScore().getName().length() == 0;
			boolean canPaste = !common.isClipboardEmpty();
			boolean mediaMuxerSupport = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
	
			menu.findItem(R.id.paste).setVisible(canPaste);
			menu.findItem(R.id.delete).setVisible(!isWorkspace);
			menu.findItem(R.id.makeMP4).setVisible(!isWorkspace && mediaMuxerSupport);
			
			menu.findItem(R.id.stressTest).setVisible(QuencherApp.DEVELOPER_MODE);
		}
		
		if (QuencherApp.DEVELOPER_MODE) {
			menu.findItem(R.id.stressTest).setVisible(!ActivityManager.isUserAMonkey());
			menu.findItem(R.id.settings).setVisible(!ActivityManager.isUserAMonkey());
//			menu.findItem(R.id.instruments).setVisible(!ActivityManager.isUserAMonkey());
//			menu.findItem(R.id.scales).setVisible(!ActivityManager.isUserAMonkey());
		}
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		// prevents race condition where invalid request
		// sneaks in just after play command is issued
		// (more of a monkey thing than actual use case)
		if (Engine.INSTANCE.isPlaying() && item.getItemId() != R.id.stop) {
			return true;
		}
		
		Score score = common.getScore();
		switch(item.getItemId()) {
		
		case R.id.addTrack:
			Track track = new Track(score);
			score.addTrack(track);
			break;
		
		case R.id.play:
			Engine.INSTANCE.play(score, common.getBeatMarker());
			break;
			
		case R.id.stop:
			Engine.INSTANCE.stop();
			break;
			
		case R.id.create:
			if (common.isSaveAsRequired()) {
				postSaveAction = R.id.create;
				ScoreDialogs.NewScoreDialog
					.newInstance(true)
					.show(getFragmentManager(), Popup.Deferred);
			} else {
				createNewScore();
			}
			break;
		
		case R.id.open:
			if (common.isSaveAsRequired()) {
				postSaveAction = R.id.open;
				ScoreDialogs.NewScoreDialog
					.newInstance(false)
					.show(getFragmentManager(), Popup.Deferred);
			} else {
				openScoreList();
			}
			break;
			
		case R.id.instruments:
			launchVoiceManager();
			break;
			
		case R.id.scales:
			launchScaleManager();
			break;
			
		case R.id.saveAs:
			postSaveAction = 0;
			launchSaveAsDialog();
			break;
			
		case R.id.delete:
			deleteScore();
			break;
			
		case R.id.paste:
			int err;
			if ( (err = common.pasteClipboard()) != 0) {
				Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
			}
			break;
			
		case R.id.makeMP4:
			makeMP4();
			break;
			
		case R.id.settings:
			launchSettingsFragment();
			break;
			
		case R.id.setTempo:
			new ScoreDialogs.TempoPopup().show(
					getFragmentManager(), Popup.Deferred);
			break;
			
		case R.id.gotoStart:
			composerView.goTo(Goto.Start);
			break;
			
		case R.id.gotoEnd:
			composerView.goTo(Goto.End);
			break;
			
		case R.id.gotoCursor:
			composerView.goTo(Goto.Cursor);
			break;
			
		case R.id.gotoMarker:
			composerView.goTo(Goto.Marker);
			break;
			
		case R.id.gotoTime:
			new ScoreDialogs.GotoPopup().show(
					getFragmentManager(), Popup.Deferred);
			break;
			
		case R.id.stressTest:
			common.createStressTest();
			break;

		default:
			return false;
		}
		
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		
		case ManageVoices:
		case ManageScales:
			// if a valid score is available
			if (!common.isLoading()) {
				// restore it as autosave object
				Storage.INSTANCE.setAutosave(common.getScore());
			}
			break;
			
		case Settings:
			// restart audio engine to pick up new audio/synth settings
			Engine.INSTANCE.restart();
			break;
		}
	}
	
	/**
	 * show the name (or untitled string) in title bar
	 */
	void setTitle() {
	    String name = common.getScore().getName();
	    if (name.length() == 0) {
	    	name = getResources().getString(R.string.untitledScore);
	    }
		getActionBar().setTitle(name);
	}
	
	/**
	 * handle a change of score
	 */
	void onNewScore() {
		setTitle();
	}
	
	/**
	 * handles request for a new score
	 */
	void createNewScore() {
		common.createNewScore();
	}
	
	/**
	 * launches the list of scores
	 */
	void openScoreList() {
		new ScoreDialogs.OpenScoreDialog().show(
				getFragmentManager(), Popup.Deferred);
	}
	
	/**
	 * launch the scale manager activity
	 */
	private void launchScaleManager() {
		Intent intent = new Intent();
		intent.setClass(this, ScaleActivity.class);
		startActivityForResult(intent, ManageScales);
	}

	/**
	 * launch the voice manager activity
	 */
	private void launchVoiceManager() {
		Intent intent = new Intent();
		intent.setClass(this, VoiceActivity.class);
		startActivityForResult(intent, ManageVoices);
	}

	/**
	 * export the score to a music file
	 */
	private void makeMP4() {
		new Mp4(this).create(common.getScore());
	}
	
	/**
	 * launch the settings fragment
	 */
	private void launchSettingsFragment() {
		Intent intent = new Intent();
		intent.setClass(this, SettingsActivity.class);
		startActivityForResult(intent, Settings);
	}
	
	/**
	 * displays dialog to save score
	 */
	void launchSaveAsDialog() {
		Score score = common.getScore();
		ScoreDialogs.SaveAsDialog
			.newInstance(score.getName(), score.getDescription())
			.show(getFragmentManager(), Popup.Deferred);
	}
	
	/**
	 * deletes the current score
	 */
	private void deleteScore() {
		new ScoreDialogs.ScoreDeleteDialog()
			.show(getFragmentManager(), Popup.Deferred);
	}
	
	/**
	 * handles setup/controls/teardown for note selection mode
	 */
	class ActionModeListener implements ActionMode.Callback {

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.notes_cab, menu);
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			ScoreCommon common = ScoreActivity.common;
			if (mode != null) {
//				String sc = getResources().getString(R.string.scoreSelectionCount);
//				mode.setTitle(String.format(sc, common.getNoteSelectionCount()));
				String sc = getResources().getString(R.string.scoreSelectionTimes);
				mode.setTitle(String.format(sc, common.getNoteSelectionString()));
			}
			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			ScoreCommon common = ScoreActivity.common;
			switch(item.getItemId()) {
			case R.id.copy:
				common.copyToClipboard(false);
				actionMode.finish();
				break;
			case R.id.cut:
				common.copyToClipboard(true);
				actionMode.finish();
				break;
			case R.id.transposeUp:
				transpose(1);
				break;
			case R.id.transposeDown:
				transpose(-1);
				break;
			default:
				return false;
			}
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			ScoreCommon common = ScoreActivity.common;
			common.clearSelection();
			composerView.postInvalidate();
			actionMode = null;
			Notifier.INSTANCE.send(Notifier.SettingChange);
		}

		/**
		 * transpose notes over a scale
		 * @param offset number of scale degrees to transpose (-1..1)
		 */
		private void transpose(int offset) {
			Track track = common.getSelectionTrack();
			Scale scale = track.getScale();
			int start = common.getSelectionStart();
			int end = common.getSelectionEnd();
			
			for (int i = start; i <= end; i++) {
				Note note = track.getNote(i);
				if (note != null) {
					int pitch = note.getPitchNumber() + offset;
					if (pitch >= 0 && pitch <= scale.getCount()) {
						track.setNote(i, pitch);
					}
				}
			}
			// insure composer view is refreshed
			Notifier.INSTANCE.send(Notifier.ScoreChange);
		}
	}
	
	@Override
	public void handleMessage(Message msg) {
		switch(msg.what) {
		
		case Notifier.ScoreReady:
			common.getWaiter().unlock();
			
			setTitle();
			if (common.isSelecting()) {
				actionMode = startActionMode(actionListener);
			}

		    // reveal deferred dialogs
			DeferredDialog dialog = 
					(DeferredDialog) getFragmentManager()
					.findFragmentByTag(Popup.Deferred);
			if (dialog != null) {
				dialog.show();
			}
			break;

		case Notifier.NewScore:
			onNewScore();
			
		case Notifier.AudioPlaying:
		case Notifier.AudioStopped:
		case Notifier.SettingChange:
		case Notifier.ScoreChange:
			invalidateOptionsMenu();
			break;

		case Notifier.SelectionChange:
			if (actionMode == null) {
				actionMode = startActionMode(actionListener);
			} else {
				if (common.isSelecting()) {
					actionMode.invalidate();
				} else {
					actionMode.finish();
				}
			}
			break;

		case Notifier.SetCursor:
			common.setTrackPosition(msg.arg1);
			common.setNotePosition(msg.arg2);
			Notifier.INSTANCE.send(Notifier.CursorChange);
			break;

		case Notifier.StorageSaveFailed:
		case Notifier.StorageDeleteFailed:
		case Notifier.AudioInitFailed:
		case Notifier.Mp4WriteFailed:
			Popup.createWarning(msg)
				.show(getFragmentManager(), Popup.Deferred);
			break;
			
		}
	}

}
