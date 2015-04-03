package com.wordsaretoys.quencher.voices;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.common.CatalogAdapter;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.common.Popup;
import com.wordsaretoys.quencher.common.Storage;
import com.wordsaretoys.quencher.data.Stage;
import com.wordsaretoys.quencher.data.Voice;

/**
 * wrapper class for dialog and popup classes
 * required by the voice activity
 */
public class VoiceDialogs {

	static Voice getVoice() {
		return VoiceActivity.common.getVoice();
	}
	
	static Stage getFocusedStage() {
		return VoiceActivity.common.getFocusedStage();
	}
	
	/**
	 * tremolo slider popup
	 */
	public static class TremoloPopup extends Popup.SliderPopup {

		protected void update() {
			String hertz = getResources().getString(R.string.hertz);
			rightLabel.setText(String.format(hertz, slider.getProgress()));
		}
	
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			View view = super.onCreateView(inflater, container, state);
			leftLabel.setText(R.string.voiceTremolo);
			slider.setMax((int)Voice.MaxTremolo);
			return view;
		}
		
		@Override
		protected void onSliderMoved(int value, int limit) {
			getVoice().setTremolo(value);
			update();
		}
		
		@Override
		public void refresh() {
			slider.setProgress((int)getVoice().getTremolo());
			update();
		}
	}

	/**
	 * vibrato slider popup
	 */
	public static class VibratoPopup extends Popup.SliderPopup {

		protected void update() {
			String hertz = getResources().getString(R.string.hertz);
			rightLabel.setText(String.format(hertz, slider.getProgress()));
		}
	
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			View view = super.onCreateView(inflater, container, state);
			leftLabel.setText(R.string.voiceVibrato);
			slider.setMax((int)Voice.MaxVibrato);
			return view;
		}
		
		@Override
		protected void onSliderMoved(int value, int limit) {
			getVoice().setVibrato(value);
			update();
		}
		
		@Override
		public void refresh() {
			slider.setProgress((int)getVoice().getVibrato());
			update();
		}
	}
	
	/**
	 * test frequency slider popup
	 */
	public static class FrequencyPopup extends Popup.SliderPopup {

		protected void update() {
			String hertz = getResources().getString(R.string.hertz);
			int f = slider.getProgress() + 100;
			rightLabel.setText(String.format(hertz, f));
		}
	
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			View view = super.onCreateView(inflater, container, state);
			leftLabel.setText(R.string.voiceTestFrequencyLabel);
			slider.setMax(900);
			return view;
		}
		
		@Override
		protected void onSliderMoved(int value, int limit) {
			VoiceActivity.common.setTestFrequency(value + 100);
			update();
		}
		
		@Override
		public void refresh() {
			slider.setProgress((int)(VoiceActivity.common.getTestFrequency() - 100));
			update();
		}
	}
	
	/**
	 * stage delete confirmation dialog
	 */
	public static class StageDeleteDialog extends Popup.DeferredDialog {
		@Override
		public Dialog onCreateDialog(Bundle state) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(R.string.stageDeleteConfirm);
			builder.setNegativeButton(R.string.labelNo, null);
			builder.setPositiveButton(R.string.labelYes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dismiss();
					getVoice().removeStage(getFocusedStage());
					Notifier.INSTANCE.send(Notifier.StageCursorChange);
				}
			});
			return builder.create();
		}
	}

	/**
	 * confirmation dialog for creating/opening a new voice 
	 */
	public static class NewVoiceDialog extends Popup.DeferredDialog {
		
		public static NewVoiceDialog newInstance(boolean create) {
			NewVoiceDialog dialog = new NewVoiceDialog();
			Bundle bundle = new Bundle();
			bundle.putBoolean("create", create);
			dialog.setArguments(bundle);
			return dialog;
		}
		
		@Override
		public Dialog onCreateDialog(Bundle state) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(R.string.voiceCloseConfirm);
			builder.setNegativeButton(R.string.labelNo, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dismiss();
					VoiceActivity va = (VoiceActivity) getActivity();
					if (getArguments().getBoolean("create")) {
						VoiceActivity.common.createNewVoice();
					} else {
						va.openVoiceList();
					}
				}
			});
			builder.setPositiveButton(R.string.labelYes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					VoiceActivity va = (VoiceActivity) getActivity();
					dismiss();
					// monkey-proofing
					if (va != null) {
						va.launchSaveAsDialog();
					}
				}
			});
			AlertDialog dialog = builder.create();
			dialog.setCanceledOnTouchOutside(false);
			return dialog;
		}
	}
	
	/**
	 * list of voices available for opening
	 */
	public static class OpenVoiceDialog extends Popup.DeferredDialog {

		CatalogAdapter adapter;
		ListView listView;

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			VoiceActivity.common.getWaiter().start();
			adapter = new CatalogAdapter(getActivity());
			
			View view = inflater.inflate(R.layout.catalog, container, false);
			listView = (ListView) view.findViewById(R.id.listView);
			
			View emptyView = view.findViewById(R.id.empty);
			listView.setEmptyView(emptyView);

			adapter.setOnListCompleteListener(
					new CatalogAdapter.OnListCompleteListener() {
				@Override
				public void onListComplete(int count) {
					VoiceActivity.common.getWaiter().stop();
					if (count == 0) {
						((TextView)listView.getEmptyView())
							.setText(R.string.noEntries);
					} else {
						lockMaximumHeight(adapter);
					}
				}
			});
			
			listView.setAdapter(adapter);
			listView.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					dismiss();
					VoiceActivity.common.openVoice(id);
				}
			});
			
			getDialog().setTitle(R.string.voiceOpenTitle);
			
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
			nameBox.setHint(R.string.voiceSaveAsDialogName);

			descBox = (EditText) view.findViewById(R.id.desc);
			if (state == null) {
				descBox.setText(getArguments().getString("desc"));
			}
			descBox.setHint(R.string.voiceSaveAsDialogDesc);
			
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setView(view);
			builder.setNegativeButton(R.string.labelCancel, null);
			builder.setPositiveButton(R.string.labelAccept,
					new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface di, int which) {
					
					String name = nameBox.getText().toString();
					String desc = descBox.getText().toString();
					
					VoiceActivity va = (VoiceActivity) getActivity();
					VoiceCommon common = VoiceActivity.common;
					
					// if this is an unnamed voice
					if (getVoice().getName().length() == 0) {
						// rename it
						getVoice().setName(name);
						getVoice().setDescription(desc);
						
						// if we came here via another action, fire it
						switch (va.postSaveAction) {
						case R.id.create:
							common.createNewVoice();
							break;
						case R.id.open:
							va.openVoiceList();
							break;
						default:
							// if not, pretend it's a new voice
							// and refresh everything
							common.setVoice(getVoice());
						}
					} else {
						// create copy of voice with new name/desc
						Voice voice = new Voice();
						voice.copy(common.getVoice());
						voice.setName(name);
						voice.setDescription(desc);
						common.setVoice(voice);
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
			
			dialog.setTitle(R.string.voiceSaveAsDialogTitle);
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
	 * voice delete confirmation dialog
	 */
	public static class VoiceDeleteDialog extends Popup.DeferredDialog {
		@Override
		public Dialog onCreateDialog(Bundle state) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(R.string.voiceDeleteConfirm);
			builder.setNegativeButton(R.string.labelNo, null);
			builder.setPositiveButton(R.string.labelYes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Storage.INSTANCE.submitForDelete(getVoice());
					VoiceActivity.common.createNewVoice();
				}
			});
			return builder.create();
		}
	}
	
}
