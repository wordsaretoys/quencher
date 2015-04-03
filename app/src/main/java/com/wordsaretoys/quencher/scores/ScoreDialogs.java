package com.wordsaretoys.quencher.scores;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.common.CatalogAdapter;
import com.wordsaretoys.quencher.common.NameListAdapter;
import com.wordsaretoys.quencher.common.NameListAdapter.OnListCompleteListener;
import com.wordsaretoys.quencher.common.Popup;
import com.wordsaretoys.quencher.common.Storage;
import com.wordsaretoys.quencher.data.Note;
import com.wordsaretoys.quencher.data.Scale;
import com.wordsaretoys.quencher.data.Score;
import com.wordsaretoys.quencher.data.Track;
import com.wordsaretoys.quencher.data.Voice;

/**
 * wrapper class for dialog and popup classes
 * required by the score activity
 */
public class ScoreDialogs {

	static Score getScore() {
		return ScoreActivity.common.getScore();
	}
	
	static Track getFocusedTrack() {
		return ScoreActivity.common.getFocusedTrack();
	}
	
	/**
	 * tempo slider popup
	 */
	public static class TempoPopup extends Popup.SliderPopup {

		/**
		 * update tempo label
		 */
		protected void update() {
			String bpm = 
					getResources().getString(R.string.scoreTempoBpm);
			rightLabel.setText(String.format(bpm, getScore().getTempo()));
		}
	
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			View view = super.onCreateView(inflater, container, state);
			leftLabel.setText(R.string.scoreTempoLabel);
			slider.setMax(210);
			return view;
		}
		
		@Override
		protected void onSliderMoved(int value, int limit) {
			getScore().setTempo(value + 30);
			update();
		}
		
		@Override
		public void refresh() {
			slider.setProgress(getScore().getTempo() - 30);
			update();
		}
	}

	/**
	 * goto score time slider popup
	 */
	public static class GotoPopup extends Popup.SliderPopup {
		StringBuilder stringer = new StringBuilder();
		ComposerView composerView;
		
		/**
		 * update tempo label
		 */
		protected void update(float time) {
			ScoreCommon.buildTimeString(time, stringer);
			rightLabel.setText(stringer.toString());
		}
	
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			View view = super.onCreateView(inflater, container, state);
			
			composerView = 
					(ComposerView) getActivity()
						.findViewById(R.id.composerView);
			leftLabel.setText(R.string.scoreGotoTimeLabel);
			
			return view;
		}
		
		@Override
		protected void onSliderMoved(int value, int limit) {
			float time = (float) value / 1000f;
			composerView.goTo(time);
			update(time);
		}
		
		@Override
		public void refresh() {
			Note note = ScoreActivity.common.getLastNote();
			int endTime = (note != null) ? (int)(1000 * note.getTime()) : 0;
			float time = composerView.getScrollTime();
			slider.setMax(endTime);
			slider.setProgress((int)(time * 1000));
			update(time);
		}
	}
	
	/**
	 * track fragment's scale selection popup
	 */
	public static class ScalePopup extends Popup.ListPopup {

		NameListAdapter adapter;
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			ScoreActivity.common.getWaiter().start();
			View view = super.onCreateView(inflater, container, state);
			adapter = new NameListAdapter(getActivity(), Scale.class);
			
			adapter.setOnListCompleteListener(new OnListCompleteListener() {
				@Override
				public void onListComplete(int count) {
					ScoreActivity.common.getWaiter().stop();
					if (getActivity() != null) {
						onListReady(adapter);
					}
				}
			});
			
			listView.setAdapter(adapter);
			return view;
		}
		
		@Override
		protected void onItemSelected(long id, int position) {
			Track track = getFocusedTrack();
			// TODO: this is a blocking call!!!!
			Scale ns = Scale.fromId(id);
			if (track.getNoteCount() == 0 || ns.getCount() == track.getScale().getCount()) {
				track.setScale(ns);
			} else {
				Toast.makeText(getActivity(), 
						R.string.scoreAssignScaleError, Toast.LENGTH_LONG).show();
			}
		}
		
		@Override
		public void onDismiss(DialogInterface di) {
			super.onDismiss(di);
			listView.setAdapter(null);
			adapter.close();
		}
		
		@Override
		public void refresh() {
			adapter.setSelection(getFocusedTrack().getScale().getId());
			
			View view = getActivity().findViewById(R.id.trackFragment);
			view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
			int th = view.getMeasuredHeight();

			anchorTo(Gravity.BOTTOM | Gravity.LEFT, 0, th);
			setWidth(getResources().getDisplayMetrics().widthPixels / 2);
		}
		
	}

	/**
	 * track fragment's voice selection popup
	 */
	public static class VoicePopup extends Popup.ListPopup {

		NameListAdapter adapter;
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			ScoreActivity.common.getWaiter().start();
			View view = super.onCreateView(inflater, container, state);
			adapter = new NameListAdapter(getActivity(), Voice.class);
			adapter.setOnListCompleteListener(new OnListCompleteListener() {
				@Override
				public void onListComplete(int count) {
					ScoreActivity.common.getWaiter().stop();
					if (getActivity() != null) {
						onListReady(adapter);
					}
				}
			});
			listView.setAdapter(adapter);
			return view;
		}

		@Override
		protected void onItemSelected(long id, int position) {
			// TODO: this is a blocking call!!!!
			Voice nv = Voice.fromId(id);
			getFocusedTrack().setVoice(nv);
		}
		
		@Override
		public void onDismiss(DialogInterface di) {
			super.onDismiss(di);
			listView.setAdapter(null);
			adapter.close();
		}
		
		@Override
		public void refresh() {
			adapter.setSelection(getFocusedTrack().getVoice().getId());
			
			View view = getActivity().findViewById(R.id.trackFragment);
			view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
			int th = view.getMeasuredHeight();
			
			anchorTo(Gravity.BOTTOM | Gravity.LEFT, 0, th);
			setWidth(getResources().getDisplayMetrics().widthPixels / 2);
		}
		
	}
	
	/**
	 * track fragment's timing popup
	 */
	public static class TimingPopup extends Popup.BasePopup {
		
		Spinner beatSpinner, noteSpinner;
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			View view = inflater.inflate(R.layout.track_timing, container, false);
			
			// build the beats/bar spinner
			ArrayAdapter<CharSequence> beatArray =
					ArrayAdapter.createFromResource(
							getActivity(), 
							R.array.trackBeatsList, 
							R.layout.large_spinner_item);
			beatSpinner = (Spinner) view.findViewById(R.id.beatsPerBar);
			beatSpinner.setAdapter(beatArray);
			beatSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent,
						View view, int position, long id) {
					getFocusedTrack().setBeats(position + 2);
				}
				@Override
				public void onNothingSelected(AdapterView<?> parent) {}
			});
			
			// build the notes/beat spinner
			ArrayAdapter<CharSequence> noteArray =
					ArrayAdapter.createFromResource(
							getActivity(), 
							R.array.trackNotesList, 
							R.layout.large_spinner_item);
			noteSpinner = (Spinner) view.findViewById(R.id.notesPerBeat);
			noteSpinner.setAdapter(noteArray);
			noteSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent,
						View view, int position, long id) {
					getFocusedTrack().setSlots(position + 1);
				}
				@Override
				public void onNothingSelected(AdapterView<?> parent) {}
			});

			return view;
		}
		
		@Override
		public void refresh() {
			beatSpinner.setSelection(getFocusedTrack().getBeats() - 2);
			noteSpinner.setSelection(getFocusedTrack().getSlots() - 1);

			View view = getActivity().findViewById(R.id.trackFragment);
			view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
			int th = view.getMeasuredHeight();

			anchorTo(Gravity.BOTTOM | Gravity.LEFT, 0, th);
		}
	}

	/**
	 * track fragment's audio properties popup
	 */
	public static class AudioPopup extends Popup.BasePopup {

		Button muteButton;
		SeekBar volumeSlider, panSlider;
		TextView volumePercent;

		protected void update() {
			Track track = getFocusedTrack();
			muteButton.setText(track.isMuted() ? 
					R.string.trackUnmute : R.string.trackMute);
			volumeSlider.setEnabled(!track.isMuted());
			panSlider.setEnabled(!track.isMuted());
			String percent = getResources().getString(R.string.percent);
			volumePercent.setText(String.format(percent, volumeSlider.getProgress()));
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			
			View view = inflater.inflate(R.layout.track_audio, container, false);
			
			muteButton = (Button) view.findViewById(R.id.muteButton);
			muteButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Track track = getFocusedTrack();
					track.setMuted(!track.isMuted());
					update();
				}
			});
			
			volumePercent = (TextView) view.findViewById(R.id.volumePercent);

			volumeSlider = (SeekBar) view.findViewById(R.id.volumeSlider);
			volumeSlider.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar,
						int progress, boolean fromUser) {
					if (fromUser) {
						float vol = 
								(float) volumeSlider.getProgress() / 
								(float) volumeSlider.getMax();
						getFocusedTrack().setVolume(vol);
						update();
					}
				}
				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {}
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {}
			});
			volumeSlider.setMax(100);
			
			panSlider = (SeekBar) view.findViewById(R.id.panSlider);
			panSlider.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar,
						int progress, boolean fromUser) {
					if (fromUser) {
						float pan = 
								(float) panSlider.getProgress() / 
								(float) panSlider.getMax();
						getFocusedTrack().setPan(2f * pan - 1f);
					}
				}
				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {}
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {}
			});
			panSlider.setMax(100);
			
			return view;
		}
		
		@Override
		public void refresh() {
			volumeSlider.setProgress((int)(getFocusedTrack().getVolume() * 100));
			int p = (int)((0.5f * getFocusedTrack().getPan() + 0.5f) * 100);
			panSlider.setProgress(p);
			update();

			View view = getActivity().findViewById(R.id.trackFragment);
			view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
			int th = view.getMeasuredHeight();

			anchorTo(Gravity.BOTTOM | Gravity.LEFT, 0, th);
		}
	}

	/**
	 * track delete confirmation dialog
	 */
	public static class TrackDeleteDialog extends Popup.DeferredDialog {
		@Override
		public Dialog onCreateDialog(Bundle state) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(R.string.trackDeleteConfirm);
			builder.setNegativeButton(R.string.labelNo, null);
			builder.setPositiveButton(R.string.labelYes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Score score = ScoreActivity.common.getScore();
					Track track = ScoreActivity.common.getFocusedTrack();
					score.removeTrack(track);
				}
			});
			return builder.create();
		}
	}

	/**
	 * confirmation dialog for creating/opening a new score 
	 */
	public static class NewScoreDialog extends Popup.DeferredDialog {
		
		public static NewScoreDialog newInstance(boolean create) {
			NewScoreDialog dialog = new NewScoreDialog();
			Bundle bundle = new Bundle();
			bundle.putBoolean("create", create);
			dialog.setArguments(bundle);
			return dialog;
		}
		
		@Override
		public Dialog onCreateDialog(Bundle state) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(R.string.scoreCloseConfirm);
			builder.setNegativeButton(R.string.labelNo, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					ScoreActivity sa = (ScoreActivity) getActivity();
					dismiss();
					if (sa != null) {
						if (getArguments().getBoolean("create")) {
							sa.createNewScore();
						} else {
							sa.openScoreList();
						}
					}
				}
			});
			builder.setPositiveButton(R.string.labelYes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					ScoreActivity sa = (ScoreActivity) getActivity();
					dismiss();
					if (sa != null) {
						sa.launchSaveAsDialog();
					}
				}
			});
			AlertDialog dialog = builder.create();
			dialog.setCanceledOnTouchOutside(false);
			return dialog;
		}
	}
	
	/**
	 * list of scores available for opening
	 */
	public static class OpenScoreDialog extends Popup.DeferredDialog {

		CatalogAdapter adapter;
		ListView listView;

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			ScoreActivity.common.getWaiter().start();

			adapter = new CatalogAdapter(getActivity());
			
			View view = inflater.inflate(R.layout.catalog, container, false);
			listView = (ListView) view.findViewById(R.id.listView);
			
			View emptyView = view.findViewById(R.id.empty);
			listView.setEmptyView(emptyView);

			adapter.setOnListCompleteListener(
					new CatalogAdapter.OnListCompleteListener() {
				@Override
				public void onListComplete(int count) {
					ScoreActivity.common.getWaiter().stop();
					if (count == 0) {
						((TextView)listView.getEmptyView())
							.setText(R.string.noEntries);
					} else {
						if (getActivity() != null) {
							lockMaximumHeight(adapter);
						}
					}
				}
			});
			
			listView.setAdapter(adapter);
			listView.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					dismiss();
					ScoreActivity.common.openScore(id);
				}
			});
			
			getDialog().setTitle(R.string.scoreOpenTitle);
			
			return view;
		}
		
		@Override
		public void onDismiss(DialogInterface dialog) {
			super.onDismiss(dialog);
			listView.setAdapter(null);
			adapter.close();
		}
	}
	
	/**
	 * save as... dialog
	 */
	public static class SaveAsDialog extends Popup.DeferredDialog {

		EditText nameBox, descBox;
		
		public static SaveAsDialog newInstance(String name, String desc) {
			SaveAsDialog dialog = new SaveAsDialog();
			Bundle args = new Bundle();
			args.putString("name", name);
			args.putString("desc", desc);
			dialog.setArguments(args);
			return dialog;
		}
		
		@Override
		public Dialog onCreateDialog(Bundle state) {
			
			LayoutInflater inflater = getActivity().getLayoutInflater();
			View view = inflater.inflate(R.layout.saveas, null);
			
			nameBox = (EditText) view.findViewById(R.id.name);
			if (state == null) {
				nameBox.setText(getArguments().getString("name"));
			}
			nameBox.setHint(R.string.scoreSaveAsDialogName);

			descBox = (EditText) view.findViewById(R.id.desc);
			if (state == null) {
				descBox.setText(getArguments().getString("desc"));
			}
			descBox.setHint(R.string.scoreSaveAsDialogDesc);
			
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setView(view);
			builder.setNegativeButton(R.string.labelCancel, null);
			builder.setPositiveButton(R.string.labelAccept,
					new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface di, int which) {
					
					String name = nameBox.getText().toString();
					String desc = descBox.getText().toString();
					
					ScoreActivity sa = (ScoreActivity) getActivity();
					ScoreCommon common = ScoreActivity.common;
					
					// if this is an unnamed score
					if (common.getScore().getName().length() == 0) {
						// name it
						common.getScore().setName(name);
						common.getScore().setDescription(desc);
						
						// if we came here via another action, fire it
						switch (sa.postSaveAction) {
						case R.id.create:
							sa.createNewScore();
							break;
						case R.id.open:
							sa.openScoreList();
							break;
						default:
							// otherwise, pretends it's a new score
							// and refresh it
							common.setScore(getScore());
						}
					} else {
						// create new copy of score with given name/desc
						Score score = new Score();
						score.copy(common.getScore());
						score.setName(name);
						score.setDescription(desc);
						common.setScore(score);
					}
				}
			});
			
			final AlertDialog dialog = builder.create();
			
			nameBox.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count,
						int after) {}
				@Override
				public void onTextChanged(CharSequence s, int start, int before,
						int count) {}

				@Override
				public void afterTextChanged(Editable s) {
					enableAccept();
				}
			});
			
			dialog.setTitle(R.string.scoreSaveAsDialogTitle);
			return dialog;
		}
		
		public void enableAccept() {
			AlertDialog dialog = (AlertDialog) getDialog();
			boolean ok = nameBox.getText().toString().trim().length() > 0;
			dialog.getButton(Dialog.BUTTON_POSITIVE).setEnabled(ok);
		}

		@Override
		public void refresh() {
			enableAccept();
		}
	}
	
	/**
	 * score delete confirmation dialog
	 */
	public static class ScoreDeleteDialog extends Popup.DeferredDialog {
		@Override
		public Dialog onCreateDialog(Bundle state) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(R.string.scoreDeleteConfirm);
			builder.setNegativeButton(R.string.labelNo, null);
			builder.setPositiveButton(R.string.labelYes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Storage.INSTANCE.submitForDelete(getScore());
					ScoreActivity.common.createNewScore();
				}
			});
			return builder.create();
		}
	}

	/**
	 * license check retry dialog
	 */
	public static class LicenseCheckFailureDialog extends Popup.DeferredDialog {
		@Override
		public Dialog onCreateDialog(Bundle state) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(R.string.licenseCheckFailure);
			builder.setNegativeButton(R.string.labelCancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					getActivity().finish();
				}
			});
			builder.setPositiveButton(R.string.labelRetry, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
//					ScoreActivity.common.checkLicense();
				}
			});
			Dialog dialog = builder.create();
			dialog.setCanceledOnTouchOutside(false);
			return dialog;
		}
	}
	
	/**
	 * license failure handling dialog
	 */
	public static class LicenseCheckNoneDialog extends Popup.DeferredDialog {
		@Override
		public Dialog onCreateDialog(Bundle state) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(R.string.licenseCheckNone);
			builder.setNegativeButton(R.string.labelNo, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					getActivity().finish();
				}
			});
			builder.setPositiveButton(R.string.labelYes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// TODO: launch google play page
					getActivity().finish();
				}
			});
			Dialog dialog = builder.create();
			dialog.setCanceledOnTouchOutside(false);
			return dialog;
		}
	}
}
