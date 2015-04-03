package com.wordsaretoys.quencher.scales;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.audio.Engine;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.common.Notifier.NotificationListener;
import com.wordsaretoys.quencher.common.Popup;
import com.wordsaretoys.quencher.data.Scale;
import com.wordsaretoys.quencher.data.Tone;

/**
 * displays tone editing controls
 */

public class ToneFragment extends Fragment implements NotificationListener {

	static final String TAG = "ToneFragment";

	// main view
	View mainView;
	
	// edit boxes
	EditText labelBox, intervalBox, offsetBox;

	// menu bar buttons
	ImageButton deleteButton, duplicateButton, moveUpButton, moveDownButton;
	
	// other buttons
	Button sharpButton, flatButton;
	
	// flags whether a programmatic update is in progress
	// (this prevents refreshes from writing back to tones)
	boolean updating;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		mainView = inflater.inflate(R.layout.tone_detail, container);
		
		labelBox = (EditText) mainView.findViewById(R.id.toneLabel);
		labelBox.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {}
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {}
			@Override
			public void afterTextChanged(Editable s) {
				if (!updating) {
					Tone tone = ScaleActivity.common.getFocusedTone();
					String str = s.toString();
					if (!tone.getLabel().equals(str)) {
						tone.setLabel(str);
					}
				}
			}
		});

		intervalBox = (EditText) mainView.findViewById(R.id.toneInterval);
		intervalBox.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {}
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {}
			@Override
			public void afterTextChanged(Editable s) {
				if (!updating) {
					Tone tone = ScaleActivity.common.getFocusedTone();
					float intr = 0;
					try {
						intr = Float.valueOf(s.toString());
					} catch (Exception e) {
						intr = 1;
					}
					if (intr != tone.getInterval()) {
						tone.setInterval(intr);
					}
				}
			}
		});

		offsetBox = (EditText) mainView.findViewById(R.id.toneOffset);
		offsetBox.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {}
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {}
			@Override
			public void afterTextChanged(Editable s) {
				if (!updating) {
					Scale scale = ScaleActivity.common.getScale();
					float offset = 0;
					try {
						offset = Float.valueOf(s.toString());
					} catch (Exception e) {
						offset = 0;
					}
					if (offset != scale.getOffset()) {
						scale.setOffset(offset);
					}
				}
			}
		});

		sharpButton = (Button) mainView.findViewById(R.id.toneSharp);
		sharpButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String sharp = getActivity().getResources().getString(R.string.toneSharp);
				int start = Math.max(labelBox.getSelectionStart(), 0);
				int end = Math.max(labelBox.getSelectionEnd(), 0);
				labelBox.getText().replace(Math.min(start, end), Math.max(start, end), sharp, 0, 1);
			}
		});
		
		flatButton = (Button) mainView.findViewById(R.id.toneFlat);
		flatButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String flat = getActivity().getResources().getString(R.string.toneFlat);
				int start = Math.max(labelBox.getSelectionStart(), 0);
				int end = Math.max(labelBox.getSelectionEnd(), 0);
				labelBox.getText().replace(Math.min(start, end), Math.max(start, end), flat, 0, 1);
			}
		});
		
		deleteButton = (ImageButton) mainView.findViewById(R.id.delete);
		deleteButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new ScaleDialogs.ToneDeleteDialog()
					.show(getFragmentManager(), Popup.Deferred);
			}
		});

		duplicateButton = (ImageButton) mainView.findViewById(R.id.duplicate);
		duplicateButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				ScaleCommon common = ScaleActivity.common;
				Tone tone = common.getFocusedTone();
				Scale scale = tone.getScale();
				scale.duplicateTone(common.getTonePosition());
			}
		});

		moveUpButton = (ImageButton) mainView.findViewById(R.id.moveUp);
		moveUpButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				ScaleCommon common = ScaleActivity.common;
				Tone tone = common.getFocusedTone();
				Scale scale = tone.getScale();
				int tonePos = common.getTonePosition();
				if (tonePos >= 1) {
					common.setTonePosition(tonePos - 1);
					scale.swapTones(tonePos, tonePos - 1);
				}
			}
		});

		moveDownButton = (ImageButton) mainView.findViewById(R.id.moveDown);
		moveDownButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				ScaleCommon common = ScaleActivity.common;
				Tone tone = common.getFocusedTone();
				Scale scale = tone.getScale();
				int tonePos = common.getTonePosition();
				if (tonePos < scale.getToneCount() - 1) {
					common.setTonePosition(tonePos + 1);
					scale.swapTones(tonePos, tonePos + 1);
				}
			}
		});

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
		updateValues();
		mainView.setVisibility(
			Engine.INSTANCE.isPlaying() ? View.GONE : View.VISIBLE);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		Notifier.INSTANCE.unregister(this);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}
	
	/**
	 * updates button bar controls
	 */
	private void updateValues() {
		updating = true;

		Tone tone = ScaleActivity.common.getFocusedTone();
		Scale scale = tone.getScale();

		labelBox.setText(tone.getLabel());
		intervalBox.setText(String.valueOf(tone.getInterval()));
		offsetBox.setText(String.valueOf(scale.getOffset()));
		
		int count = scale.getToneCount();
		boolean first = (tone.getIndex() == 0);
		boolean last = (tone.getIndex() == count - 1);
		
		moveUpButton.setVisibility(first ? View.GONE : View.VISIBLE);
		moveDownButton.setVisibility(last ? View.GONE : View.VISIBLE);

		// octave offset only visible for first tone
		int v = first ? View.VISIBLE : View.GONE;
		offsetBox.setVisibility(v);
		mainView.findViewById(R.id.lbl2).setVisibility(v);
		mainView.findViewById(R.id.spacer).setVisibility(v);

		updating = false;
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

		case Notifier.NewScale:
		case Notifier.ToneCursorChange:
			updateValues();
			break;
		}
	}

}
