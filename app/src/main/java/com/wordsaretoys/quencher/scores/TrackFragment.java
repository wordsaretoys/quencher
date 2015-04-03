package com.wordsaretoys.quencher.scores;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.audio.Engine;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.common.Notifier.NotificationListener;
import com.wordsaretoys.quencher.common.Popup;
import com.wordsaretoys.quencher.data.Score;
import com.wordsaretoys.quencher.data.Track;

/**
 * displays track property editing controls
 */
public class TrackFragment extends Fragment implements NotificationListener {

	static final String TAG = "TrackFragment";
	
	// main layout view
	View mainView;
	
	// note chooser view
	NoteChooserView noteChooser;
	
	// image buttons
	ImageButton scaleButton, voiceButton, timingButton, audioButton;
	ImageButton lockButton, moveUpButton, moveDownButton;
	ImageButton duplicateButton, deleteButton;
	ImageButton noteBackButton, noteNextButton;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		mainView = inflater.inflate(R.layout.track_detail, container);
		
		scaleButton = (ImageButton) mainView.findViewById(R.id.scale);
		scaleButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new ScoreDialogs.ScalePopup().show(
						getFragmentManager(), Popup.Deferred);
			}
		});

		voiceButton = (ImageButton) mainView.findViewById(R.id.voice);
		voiceButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new ScoreDialogs.VoicePopup().show(
						getFragmentManager(), Popup.Deferred);
			}
		});
		
		timingButton = (ImageButton) mainView.findViewById(R.id.timing);
		timingButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new ScoreDialogs.TimingPopup().show(
						getFragmentManager(), Popup.Deferred);
			}
		});

		audioButton = (ImageButton) mainView.findViewById(R.id.audio);
		audioButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new ScoreDialogs.AudioPopup().show(
						getFragmentManager(), Popup.Deferred);
			}
		});
		
		lockButton = (ImageButton) mainView.findViewById(R.id.toggleLock);
		lockButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				toggleLock();
			}
		});

		moveUpButton = (ImageButton) mainView.findViewById(R.id.moveUp);
		moveUpButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				moveTrackUp();
			}
		});

		moveDownButton = (ImageButton) mainView.findViewById(R.id.moveDown);
		moveDownButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				moveTrackDown();
			}
		});
		
		duplicateButton = (ImageButton) mainView.findViewById(R.id.duplicate);
		duplicateButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				duplicateTrack();
			}
		});

		deleteButton = (ImageButton) mainView.findViewById(R.id.delete);
		deleteButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				deleteTrack();
			}
		});
		
		noteChooser = (NoteChooserView) mainView.findViewById(R.id.noteChooserView);

		noteBackButton = (ImageButton) mainView.findViewById(R.id.noteBackspace);
		noteBackButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				ScoreCommon common = ScoreActivity.common;
				common.setNotePosition(common.getNotePosition() - 1);
				common.getFocusedTrack().clearNote(common.getNotePosition());
				Notifier.INSTANCE.send(Notifier.CursorChange);
			}
		});

		noteNextButton = (ImageButton) mainView.findViewById(R.id.noteNext);
		noteNextButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				ScoreCommon common = ScoreActivity.common;
				common.setNotePosition(common.getNotePosition() + 1);
				Notifier.INSTANCE.send(Notifier.CursorChange);
			}
		});
		
		return mainView;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		if (savedInstanceState != null) {
			noteChooser.loadState(savedInstanceState);
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		Notifier.INSTANCE.register(this);
		Notifier.INSTANCE.register(noteChooser);
		updateValues();
		mainView.setVisibility(
			Engine.INSTANCE.isPlaying() ? View.GONE : View.VISIBLE);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		Notifier.INSTANCE.unregister(this);
		Notifier.INSTANCE.unregister(noteChooser);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		noteChooser.saveState(outState);
	}
	
	/**
	 * set the values/states of track controls
	 */
	private void updateValues() {
		Track track = ScoreActivity.common.getFocusedTrack();
		Score score = track.getScore();
		int count = score.getTrackCount();
		boolean first = (track.getIndex() == 0);
		boolean last = (track.getIndex() == count - 1);
		moveUpButton.setVisibility(first ? View.GONE : View.VISIBLE);
		moveDownButton.setVisibility(last ? View.GONE : View.VISIBLE);
		
		audioButton.setImageResource(
				track.isMuted() ? 
						R.drawable.ic_button_muted : R.drawable.ic_button_volume);
		
		lockButton.setImageResource(
				track.isLocked() ? 
						R.drawable.ic_status_locked : R.drawable.ic_status_unlocked);
		showNavigationButtons(!track.isLocked());
	}

	/**
	 * toggle the track editing lock
	 */
	private void toggleLock() {
		Track track = ScoreActivity.common.getFocusedTrack();
		track.setLocked(!track.isLocked());
	}
	
	/**
	 * swap the focused track with the one above it
	 */
	private void moveTrackUp() {
		Score score = ScoreActivity.common.getScore();
		Track track = ScoreActivity.common.getFocusedTrack();
		int rank = track.getIndex();
		// never seen it in production, but monkey can
		// produce a situation where this isn't true
		if (rank >= 1) {
			score.swapTracks(rank, rank - 1);
			ScoreActivity.common.setTrackPosition(rank - 1);
			Notifier.INSTANCE.send(Notifier.CursorChange);
		}
	}

	/**
	 * swap the focused track with the one below it
	 */
	private void moveTrackDown() {
		Score score = ScoreActivity.common.getScore();
		Track track = ScoreActivity.common.getFocusedTrack();
		int rank = track.getIndex();
		// never seen it in production, but monkey can
		// produce a situation where this isn't true
		if (rank < score.getTrackCount() - 1) {
			score.swapTracks(rank, rank + 1);
			ScoreActivity.common.setTrackPosition(rank + 1);
			Notifier.INSTANCE.send(Notifier.CursorChange);
		}
	}

	/**
	 * duplicate the focused track
	 */
	private void duplicateTrack() {
		if (!Engine.INSTANCE.isPlaying()) {
			Score score = ScoreActivity.common.getScore();
			Track track = ScoreActivity.common.getFocusedTrack();
			int rank = track.getIndex();
			score.duplicateTrack(rank);
		}
	}
	
	/**
	 * delete the focused track
	 */
	private void deleteTrack() {
		if (!Engine.INSTANCE.isPlaying()) {
			new ScoreDialogs.TrackDeleteDialog().show(
					getFragmentManager(), Popup.Deferred);
		}
	}
	
	/**
	 * handle set note message according to note handling mode
	 * @param note index of note
	 */
	private void setNote(int note) {
		if (!Engine.INSTANCE.isPlaying()) {
			ScoreCommon common = ScoreActivity.common;
			Track track = common.getFocusedTrack();
			if (!track.isLocked()) {
				int notePos = common.getNotePosition();
				track.setNote(notePos, note);
				notePos++;
				common.setNotePosition(notePos);
				Notifier.INSTANCE.send(Notifier.CursorChange);
			}
		}
	}

	/**
	 * shows or hides navigation button controls
	 * @param show true if controls are shown
	 */
	private void showNavigationButtons(boolean show) {
		int v = show ? View.VISIBLE : View.GONE;
		noteBackButton.setVisibility(v);
		noteNextButton.setVisibility(v);
	}
	
	@Override
	public void handleMessage(Message msg) {
		switch(msg.what) {
		case Notifier.AudioPlaying:
			mainView.setVisibility(View.GONE);
			break;
		
		case Notifier.AudioStopped:
			mainView.setVisibility(View.VISIBLE);
			break;

		case Notifier.NewScore:
		case Notifier.ScoreChange:
		case Notifier.CursorChange:
			updateValues();
			break;
			
		case Notifier.SetNote:
			setNote(msg.arg1);
			break;
			
		}
	}
	
}
