package com.wordsaretoys.quencher.scales;

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
import com.wordsaretoys.quencher.data.Scale;
import com.wordsaretoys.quencher.data.Tone;

/**
 * wrapper class for dialog and popup classes
 * required by the scale activity
 */
public class ScaleDialogs {
	
	static Scale getScale() {
		return ScaleActivity.common.getScale();
	}
	
	static Tone getFocusedTone() {
		return ScaleActivity.common.getFocusedTone();
	}
	
	/**
	 * tone delete confirmation dialog
	 */
	public static class ToneDeleteDialog extends Popup.DeferredDialog {
		@Override
		public Dialog onCreateDialog(Bundle state) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(R.string.toneDeleteConfirm);
			builder.setNegativeButton(R.string.labelNo, null);
			builder.setPositiveButton(R.string.labelYes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dismiss();
					getScale().removeTone(getFocusedTone());
					Notifier.INSTANCE.send(Notifier.ToneCursorChange);
				}
			});
			return builder.create();
		}
	}

	/**
	 * confirmation dialog for creating/opening a new scale 
	 */
	public static class NewScaleDialog extends Popup.DeferredDialog {
		
		public static NewScaleDialog newInstance(boolean create) {
			NewScaleDialog dialog = new NewScaleDialog();
			Bundle bundle = new Bundle();
			bundle.putBoolean("create", create);
			dialog.setArguments(bundle);
			return dialog;
		}
		
		@Override
		public Dialog onCreateDialog(Bundle state) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(R.string.scaleCloseConfirm);
			builder.setNegativeButton(R.string.labelNo, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					ScaleActivity sa = (ScaleActivity) getActivity();
					dismiss();
					if (sa != null) {
						if (getArguments().getBoolean("create")) {
							ScaleActivity.common.createNewScale();
						} else {
							sa.openScaleList();
						}
					}
				}
			});
			builder.setPositiveButton(R.string.labelYes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					ScaleActivity sa = (ScaleActivity) getActivity();
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
	 * list of scaless available for opening
	 */
	public static class OpenScaleDialog extends Popup.DeferredDialog {

		CatalogAdapter adapter;
		ListView listView;

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			ScaleActivity.common.getWaiter().start();
			adapter = new CatalogAdapter(getActivity());
			
			View view = inflater.inflate(R.layout.catalog, container, false);
			listView = (ListView) view.findViewById(R.id.listView);
			
			View emptyView = view.findViewById(R.id.empty);
			listView.setEmptyView(emptyView);

			adapter.setOnListCompleteListener(
					new CatalogAdapter.OnListCompleteListener() {
				@Override
				public void onListComplete(int count) {
					ScaleActivity.common.getWaiter().stop();
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
					ScaleActivity.common.openScale(id);
				}
			});
			
			getDialog().setTitle(R.string.scaleOpenTitle);
			
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
			nameBox.setHint(R.string.scaleSaveAsDialogName);

			descBox = (EditText) view.findViewById(R.id.desc);
			if (state == null) {
				descBox.setText(getArguments().getString("desc"));
			}
			descBox.setHint(R.string.scaleSaveAsDialogDesc);
			
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setView(view);
			builder.setNegativeButton(R.string.labelCancel, null);
			builder.setPositiveButton(R.string.labelAccept,
					new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface di, int which) {
					
					String name = nameBox.getText().toString();
					String desc = descBox.getText().toString();
					
					ScaleActivity sa = (ScaleActivity) getActivity();
					ScaleCommon common = ScaleActivity.common;
					
					// if this is an unnamed scale
					if (getScale().getName().length() == 0) {
						// name it
						getScale().setName(name);
						getScale().setDescription(desc);
						
						// if we came here via another action, fire it
						switch (sa.postSaveAction) {
						case R.id.create:
							common.createNewScale();
							break;
						case R.id.open:
							sa.openScaleList();
							break;
						default:
							// otherwise, pretend it's a new voice
							// and refresh it
							common.setScale(getScale());
						}
					} else {
						// create new copy of scale with new name/desc
						Scale scale = new Scale();
						scale.copy(common.getScale());
						scale.setName(name);
						scale.setDescription(desc);
						common.setScale(scale);
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
			
			dialog.setTitle(R.string.scaleSaveAsDialogTitle);
			return dialog;
		}
		
		public void enableAccept() {
			AlertDialog dialog = (AlertDialog) getDialog();
			boolean ok = nameBox.getText().toString().trim().length() > 0;
			// monkey thing
			if (dialog != null) {
				dialog.getButton(Dialog.BUTTON_POSITIVE).setEnabled(ok);
			}
		}

		@Override
		public void refresh() {
			enableAccept();
		}
	}
	
	/**
	 * scale delete confirmation dialog
	 */
	public static class ScaleDeleteDialog extends Popup.DeferredDialog {
		@Override
		public Dialog onCreateDialog(Bundle state) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(R.string.scaleDeleteConfirm);
			builder.setNegativeButton(R.string.labelNo, null);
			builder.setPositiveButton(R.string.labelYes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Storage.INSTANCE.submitForDelete(getScale());
					ScaleActivity.common.createNewScale();
				}
			});
			return builder.create();
		}
	}
	
}
