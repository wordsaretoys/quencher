package com.wordsaretoys.quencher.common;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.wordsaretoys.quencher.R;

/**
 * wrapper class for the basic popups we use
 */
public class Popup {

	public static String Deferred = "deferred";
	
	/**
	 * dialog fragment class that can be hidden/shown/refreshed
	 * used in situations where data is background loaded
	 */
	public static class DeferredDialog extends DialogFragment {
		
		/**
		 * set width of dialog window
		 * @param width width in pixels
		 */
		protected void setWidth(int width) {
			Window window = getDialog().getWindow();
			WindowManager.LayoutParams wlp = window.getAttributes();
			wlp.width = width;
			window.setAttributes(wlp);
		}
		
		/**
		 * set height of dialog window
		 * @param height height in pixels
		 */
		protected void setHeight(int height) {
			Window window = getDialog().getWindow();
			WindowManager.LayoutParams wlp = window.getAttributes();
			wlp.height = height;
			window.setAttributes(wlp);
		}

		/**
		 * override in subclass to repopulate dialog
		 */
		public void refresh() {}
		
		
		/**
		 * call to refresh dialog data and show dialog
		 */
		public void show() {
			// the null pointer guard is necessary as some dialogs will
			// trigger an async load in their button handler before the
			// dismiss() has been fully processed. for short load times
			// the ScoreReady message may be processed as the dialog is
			// being destroyed, thus calling show(). WHAMMO!
			if (getDialog() != null) {
				refresh();
				getDialog().show();
			}
		}
		
		/**
		 * call to hide dialog
		 */
		public void hide() {
			getDialog().hide();
		}
		
		@Override
		public void onResume() {
			refresh();
			super.onResume();
		}
		
		/**
		 * convenience method for dialogs that contain listviews
		 * locks maximum height based on count / item height
		 */
		protected void lockMaximumHeight(ListAdapter adapter) {
			View item = adapter.getView(0, null, null);
			// fix for relativelayout...measurement requires
			// layout params, which for some reason aren't there
			if (item.getClass() == RelativeLayout.class) {
				item.setLayoutParams(
						new RelativeLayout.LayoutParams(
								LayoutParams.MATCH_PARENT, 
								LayoutParams.WRAP_CONTENT));
			}
			item.measure(
					View.MeasureSpec.UNSPECIFIED, 
					View.MeasureSpec.UNSPECIFIED);
			int height = item.getMeasuredHeight() * adapter.getCount();
			int maxHeight = getResources().getDisplayMetrics().heightPixels / 2;
			if (height > maxHeight) {
				setHeight(maxHeight);
			}
		}		
	}
	
	/**
	 * dialog fragment class with methods to support floating "popups"
	 * subclasses can call methods to position/size themselves
	 */
	public static class BasePopup extends DeferredDialog {

		@Override
		public Dialog onCreateDialog(Bundle state) {
			Dialog dialog = super.onCreateDialog(state);
			dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
			return dialog;
		}
		
		/**
		 * anchors the dialog to the specified position,
		 * using the specified gravity to interpret it
		 * 
		 * @param gravity combination of gravity flags
		 * @param x left/right of position to anchor to
		 * @param y top/bottom of position to anchor to
		 */
		protected void anchorTo(int gravity, int x, int y) {
			Window window = getDialog().getWindow();
			WindowManager.LayoutParams wlp = window.getAttributes();
			wlp.gravity = gravity;
			wlp.x = x;
			wlp.y = y;
			window.setAttributes(wlp);
		}
		
		@Override
		public void onResume() {
			super.onResume();
			// popups shouldn't dim the background
			Window window = getDialog().getWindow();
			WindowManager.LayoutParams wlp = window.getAttributes();
			wlp.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			window.setAttributes(wlp);
		}
		
	}
	
	/**
	 * popup with a slider and two labels
	 */
	public static class SliderPopup extends BasePopup {
		
		protected TextView leftLabel, rightLabel;
		protected SeekBar slider;
		
		/**
		 * set slider value as % of maximum
		 * @param r value / limit
		 */
		public void setSliderValue(float r) {
			slider.setProgress((int)(r * slider.getMax()));
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			View view = inflater.inflate(R.layout.slider_popup, container, false);
			
			leftLabel = (TextView) view.findViewById(R.id.labelLeft);
			rightLabel = (TextView) view.findViewById(R.id.labelRight);
			slider = (SeekBar) view.findViewById(R.id.slider);
			slider.setOnSeekBarChangeListener( new OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if (fromUser) {
						onSliderMoved(progress, seekBar.getMax());
					}
				}
				public void onStartTrackingTouch(SeekBar seekBar) {}
				public void onStopTrackingTouch(SeekBar seekBar) {}
			} );
			return view;
		}
		
		/**
		 * called when slider thumb is moved
		 * override in subclass
		 */
		protected void onSliderMoved(int value, int limit) {}
	}
	
	/**
	 * popup with a list view
	 */
	public static class ListPopup extends BasePopup {
		
		protected ListView listView;

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
			View view = inflater.inflate(R.layout.list_popup, container, false);
			listView = (ListView)view.findViewById(R.id.listView);
			listView.setEmptyView(view.findViewById(R.id.empty));
			listView.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					onItemSelected(id, position);
					dismiss();
				}
			});
			return view;
		}

		/**
		 * called when a list item is selected
		 * override in subclass
		 * 
		 * @param id database id of item (if applicable)
		 * @param position list position of item
		 */
		protected void onItemSelected(long id, int position) {}

		/**
		 * standard handling for list popups
		 * @param adapter
		 */
		public void onListReady(ListAdapter adapter) {
			int count = adapter.getCount();
			if (count == 0) {
				((TextView)listView.getEmptyView())
					.setText(R.string.noEntries);
			} else {
				lockMaximumHeight(adapter);
			}
		}
		
	}
	
	/**
	 * general alert dialog, takes no action
	 */
	public static class WarningDialog extends DeferredDialog {
		
		public static WarningDialog newInstance(int msgId) {
			WarningDialog dialog = new WarningDialog();
			Bundle args = new Bundle();
			args.putInt("message", msgId);
			dialog.setArguments(args);
			return dialog;
		}
		
		@Override
		public Dialog onCreateDialog(Bundle state) {
			AlertDialog.Builder builder = 
					new AlertDialog.Builder(getActivity());
			builder.setMessage(getArguments().getInt("message"));
			builder.setPositiveButton(R.string.labelClose, null);
			AlertDialog dialog = builder.create();
			dialog.setCanceledOnTouchOutside(false);
			return dialog;
		}
	}
	
	/**
	 * create a warning dialog from a notification message
	 * @param message alert message object
	 * @return dialog with alert message
	 */
	public static WarningDialog createWarning(Message msg) {
		int resId = 0;
		switch(msg.what) {
		case Notifier.StorageSaveFailed:
			resId = R.string.storageAutoSaveFailed;
			break;
		case Notifier.StorageDeleteFailed:
			resId = R.string.storageDeleteFailed;
			break;
		case Notifier.AudioInitFailed:
			resId = R.string.audioInitFailed;
			break;
		case Notifier.Mp4WriteFailed:
			resId = R.string.mp4WriteFailed;
			break;
		}
		return WarningDialog.newInstance(resId);
	}
}