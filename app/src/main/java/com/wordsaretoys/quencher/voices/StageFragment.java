package com.wordsaretoys.quencher.voices;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.audio.Engine;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.common.Notifier.NotificationListener;
import com.wordsaretoys.quencher.common.Popup;
import com.wordsaretoys.quencher.data.Stage;
import com.wordsaretoys.quencher.data.Stage.Type;
import com.wordsaretoys.quencher.data.Voice;

public class StageFragment extends Fragment implements NotificationListener {

	static final String TAG = "StageFragment";

	// main view
	View mainView;
	
	// seekbar controls
	SeekBar durationBar, levelBar, noiseBar;
	
	// decimal formatter
	DecimalFormat formatter;
	
	// stage type spinner
	Spinner typeSpinner;
	
	// menu bar buttons
	ImageButton deleteButton, duplicateButton, moveUpButton, moveDownButton;
	
	// harmonics display/adjustment view
	HarmonicView harmonicView;
	
	// flags whether a programmatic update is in progress
	// (this prevents refreshes from writing back to stages)
	boolean updating;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		formatter = (DecimalFormat) NumberFormat.getNumberInstance();
		formatter.setMaximumFractionDigits(3);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		mainView = inflater.inflate(R.layout.stage_detail, container);
		
		durationBar = (SeekBar) mainView.findViewById(R.id.stageDuration);
		durationBar.setMax(256);
		durationBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					Stage stage = VoiceActivity.common.getFocusedStage();
					stage.setTime(getProgress(seekBar, Voice.MaxTiming));
				}
			}

			public void onStartTrackingTouch(SeekBar seekBar) {}
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});
		
		levelBar = (SeekBar) mainView.findViewById(R.id.stageLevel);
		levelBar.setMax(256);
		levelBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					Stage stage = VoiceActivity.common.getFocusedStage();
					stage.setLevel(getProgress(seekBar, 1));
				}
			}
			
			public void onStopTrackingTouch(SeekBar seekBar) {}
			public void onStartTrackingTouch(SeekBar seekBar) {}
		});

		typeSpinner = (Spinner) mainView.findViewById(R.id.stageType);

		String[] typeList = new String[Stage.Type.values().length];
		for (int i = 0; i < typeList.length; i++) {
			typeList[i] = Stage.Type.values()[i].toString();
		}
		ArrayAdapter<CharSequence> adapter =
				new ArrayAdapter<CharSequence>(getActivity(), 
						R.layout.large_spinner_item, typeList);
		typeSpinner.setAdapter(adapter);
		typeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			int shittyHack = 0;
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				// shittyHack required to intercept spinner event
				// that's fired on layout creation. Thanks, Android!
				if (!updating && shittyHack != 0) {
					Stage stage = VoiceActivity.common.getFocusedStage();
					Stage.Type t = Stage.Type.values()[position];
					if (t != stage.getType()) {
						stage.setType(t);
						setUiType();
					}
				}
				shittyHack++;
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {}
		});

		noiseBar = (SeekBar) mainView.findViewById(R.id.stageNoise);
		noiseBar.setMax(256);
		noiseBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					Stage stage = VoiceActivity.common.getFocusedStage();
					stage.setNoiseFactor(getProgress(seekBar, 1));
				}
			}

			public void onStopTrackingTouch(SeekBar seekBar) {}
			public void onStartTrackingTouch(SeekBar seekBar) {}
		});
		
		deleteButton = (ImageButton) mainView.findViewById(R.id.delete);
		deleteButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new VoiceDialogs.StageDeleteDialog()
					.show(getFragmentManager(), Popup.Deferred);
			}
		});

		duplicateButton = (ImageButton) mainView.findViewById(R.id.duplicate);
		duplicateButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				VoiceCommon common = VoiceActivity.common;
				Stage stage = common.getFocusedStage();
				Voice voice = stage.getVoice();
				int stagePos = common.getStagePosition();
				voice.duplicateStage(stagePos);
			}
		});

		moveUpButton = (ImageButton) mainView.findViewById(R.id.moveUp);
		moveUpButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				VoiceCommon common = VoiceActivity.common;
				Stage stage = common.getFocusedStage();
				Voice voice = stage.getVoice();
				int stagePos = common.getStagePosition();
				if (stagePos >= 1) {
					common.setStagePosition(stagePos - 1);
					voice.swapStages(stagePos, stagePos - 1);
				}
			}
		});

		moveDownButton = (ImageButton) mainView.findViewById(R.id.moveDown);
		moveDownButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				VoiceCommon common = VoiceActivity.common;
				Stage stage = common.getFocusedStage();
				Voice voice = stage.getVoice();
				int stagePos = common.getStagePosition();
				if (stagePos < voice.getStageCount() - 1) {
					common.setStagePosition(stagePos + 1);
					voice.swapStages(stagePos, stagePos + 1);
				}
			}
		});
		
		harmonicView = (HarmonicView) mainView.findViewById(R.id.harmonicView);
		
		return mainView;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		Notifier.INSTANCE.register(this);
		Notifier.INSTANCE.register(harmonicView);
		updateValues();
		mainView.setVisibility(
			Engine.INSTANCE.isPlaying() ? View.GONE : View.VISIBLE);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		Notifier.INSTANCE.unregister(this);
		Notifier.INSTANCE.unregister(harmonicView);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}
	
	/**
	 * changes control visiblity based on stage type
	 */
	private void setUiType() {
		Stage stage = VoiceActivity.common.getFocusedStage();
		boolean useHarmonics = stage.getType() == Type.Sine;
		boolean useNoise = stage.getType() == Type.Noise;
		
		harmonicView.setVisibility(useHarmonics ? View.VISIBLE : View.GONE);
		noiseBar.setVisibility(useNoise ? View.VISIBLE : View.GONE);
		mainView.findViewById(R.id.lbl3).setVisibility(
				useNoise ? View.VISIBLE : View.GONE);
	}
	
	/**
	 * updates controls
	 */
	private void updateValues() {
		updating = true;
		Stage stage = VoiceActivity.common.getFocusedStage();
		
		setProgress(durationBar, stage.getTime(), Voice.MaxTiming);
		setProgress(levelBar, stage.getLevel(), 1);
		typeSpinner.setSelection(stage.getType().ordinal());
		setProgress(noiseBar, stage.getNoiseFactor(), 1);

		setUiType();
		
		Voice voice = stage.getVoice();
		int count = voice.getStageCount();
		boolean first = (stage.getIndex() == 0);
		boolean last = (stage.getIndex() == count - 1);
		moveUpButton.setVisibility(first ? View.GONE : View.VISIBLE);
		moveDownButton.setVisibility(last ? View.GONE : View.VISIBLE);
		
		updating = false;
	}

	/**
	 * set seek bar progress from voice property
	 * @param bar seek bar object
	 * @param value voice property value
	 * @param maxValue maximum property value
	 */
	private void setProgress(SeekBar bar, float value, float maxValue) {
		bar.setProgress((int)(bar.getMax() * value / maxValue));
	}
	
	/**
	 * get property value from seek bar
	 * @param bar seek bar object
	 * @param maxValue maximum property value
	 * @return voice property voice
	 */
	private float getProgress(SeekBar bar, float maxValue) {
		return maxValue * (float) bar.getProgress() / (float) bar.getMax();
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
		case Notifier.NewVoice:
		case Notifier.VoiceChange:
		case Notifier.StageCursorChange:
			updateValues();
			break;
		}
	}
	
}
