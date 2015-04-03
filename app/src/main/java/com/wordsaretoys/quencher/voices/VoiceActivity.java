package com.wordsaretoys.quencher.voices;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import android.app.Activity;
import android.os.Bundle;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.audio.Engine;
import com.wordsaretoys.quencher.common.GuardedListView;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.common.Notifier.NotificationListener;
import com.wordsaretoys.quencher.common.Popup;
import com.wordsaretoys.quencher.common.Popup.DeferredDialog;
import com.wordsaretoys.quencher.data.Stage;
import com.wordsaretoys.quencher.data.Voice;

/**
 * allows voices to be edited
 */

public class VoiceActivity extends Activity implements NotificationListener {

	static final String TAG = "VoiceActivity";

	static final int ListingRequest = 0;
	
	// common voice editing data
	static VoiceCommon common;

	// list of stages
	GuardedListView listView;
	
	// stage list adapter
	VoiceAdapter voiceAdapter;
	
	// fragment for editing stages
	StageFragment stageFragment;
	
	// decimal number formatter
	DecimalFormat formatter;
	
	// menu id of action that triggered a save as dialog
	int postSaveAction;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);

	    formatter = (DecimalFormat) NumberFormat.getNumberInstance();	    
		formatter.setMaximumFractionDigits(3);
	    
	    common = new VoiceCommon(this);
	    common.loadState(savedInstanceState);
	    
	    setContentView(R.layout.voice_main);
	    listView = (GuardedListView) findViewById(R.id.voiceList);
	    
	    View header = getLayoutInflater().inflate(R.layout.stage_item, null);
	    ((TextView)header.findViewById(R.id.stageType))
	    	.setText(R.string.stageHeaderType);
	    ((TextView)header.findViewById(R.id.stageDuration))
	    	.setText(R.string.stageHeaderDuration);
	    ((TextView)header.findViewById(R.id.stageLevel))
	    	.setText(R.string.stageHeaderLevel);
	    header.setBackgroundResource(R.color.faintyello);
	    listView.addHeaderView(header, null, false);
	    
	    voiceAdapter = new VoiceAdapter();
	    listView.setAdapter(voiceAdapter);
	    listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				int stagePos = position - 1;

				// monkey and other test harnesses will click
				// the header, so we need to guard against it
				if (stagePos == -1) {
					stagePos = 0;
				}
				
				if (stagePos != common.getStagePosition()) {
					common.setStagePosition(stagePos);
					Notifier.INSTANCE.send(Notifier.StageCursorChange);
				}
			}
	    });
    
	    stageFragment = (StageFragment) getFragmentManager().findFragmentById(R.id.stageFragment);
		
		listView.getCheckedItemPositions().put(
				common.getStagePosition() + 1, true);
	}
	
	@Override
	public void onResume() {
		super.onResume();
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
		if (isFinishing()) {
			common.closeVoice();
		}
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		common.saveState(outState);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.voice, menu);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {

		// if the UI is locked, show no menu
		if (common.getWaiter().isLocked()) {
			return false;
		}

		boolean isWorkspace = common.getVoice().getName().length() == 0;
		boolean isWritable = common.isWritable();
		
		menu.findItem(R.id.delete).setVisible(isWritable && !isWorkspace);

		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		
		case R.id.play:
			playVoice();
			break;
		
		case R.id.addStage:
			addStage();
			break;
			
		case R.id.create:
			if (common.isSaveAsRequired()) {
				postSaveAction = R.id.create;
				VoiceDialogs.NewVoiceDialog
					.newInstance(true)
					.show(getFragmentManager(), Popup.Deferred);
			} else {
				common.createNewVoice();
			}
			break;
			
		case R.id.open:
			if (common.isSaveAsRequired()) {
				postSaveAction = R.id.open;
				VoiceDialogs.NewVoiceDialog
					.newInstance(false)
					.show(getFragmentManager(), Popup.Deferred);
			} else {
				openVoiceList();
			}
			break;
			
		case R.id.saveAs:
			postSaveAction = 0;
			launchSaveAsDialog();
			break;
			
		case R.id.delete:
			deleteVoice();
			break;
			
		case R.id.setTremolo:
			new VoiceDialogs.TremoloPopup()
				.show(getFragmentManager(), Popup.Deferred);
			break;
			
		case R.id.setVibrato:
			new VoiceDialogs.VibratoPopup()
				.show(getFragmentManager(), Popup.Deferred);
			break;
			
		case R.id.testFrequency:
			new VoiceDialogs.FrequencyPopup()
				.show(getFragmentManager(), Popup.Deferred);
			break;
		}
		return true;
	}
	
	/**
	 * show the name (or untitled string) in title bar
	 */
	void setTitle() {
	    String name = common.getVoice().getName();
	    if (name.length() == 0) {
	    	name = getResources().getString(
	    			R.string.untitledVoice);
	    }
		getActionBar().setTitle(name);
	}
	
	/**
	 * handle the load of a new voice
	 */
	void onNewVoice() {
		setTitle();
		if (!common.isWritable()) {
			String msg = common.isDefault() ? 
					getResources().getString(R.string.voiceIsDefault) : 
						getResources().getString(R.string.voiceInUse); 
					
				Toast.makeText(VoiceActivity.this, 
						String.format(msg, common.getVoice().getName()), 
						Toast.LENGTH_SHORT).show();
		}
		invalidateOptionsMenu();
	}

	/**
	 * launches the list of voices
	 */
	void openVoiceList() {
		new VoiceDialogs.OpenVoiceDialog().show(
				getFragmentManager(), Popup.Deferred);
	}
	
	/**
	 * generate a test score and play it
	 */
	private void playVoice() {
		Engine.INSTANCE.play(
				common.getVoice(), common.getTestFrequency(), 0.8f, 0);
	}
	
	/**
	 * add a new stage to the voice
	 */
	private void addStage() {
		Stage stage = new Stage(common.getVoice());
		common.getVoice().addStage(stage);
		voiceAdapter.notifyDataSetChanged();
	}
	
	/**
	 * displays dialog to save score
	 */
	void launchSaveAsDialog() {
		Voice voice = common.getVoice();
		VoiceDialogs.SaveAsDialog
			.newInstance(voice.getName(), voice.getDescription())
			.show(getFragmentManager(), Popup.Deferred);
	}
	
	/**
	 * deletes the current voice
	 */
	private void deleteVoice() {
		new VoiceDialogs.VoiceDeleteDialog()
			.show(getFragmentManager(), Popup.Deferred);
	}

	@Override
	public void handleMessage(Message msg) {
		switch(msg.what) {
		
		case Notifier.VoiceReady:
			common.getWaiter().unlock();
			setTitle();

		    // reveal deferred dialogs
			DeferredDialog dialog = 
					(DeferredDialog) getFragmentManager()
					.findFragmentByTag(Popup.Deferred);
			if (dialog != null) {
				dialog.show();
			}
			break;
			
		case Notifier.AudioPlaying:
		case Notifier.AudioStopped:
			invalidateOptionsMenu();
			break;

		case Notifier.NewVoice:
			onNewVoice();
			
		case Notifier.VoiceChange:
			voiceAdapter.notifyDataSetChanged();
			
		case Notifier.StageCursorChange:
			listView.getCheckedItemPositions().clear();
			listView.getCheckedItemPositions().put(common.getStagePosition() + 1, true);
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
	
	class VoiceAdapter extends BaseAdapter implements ListAdapter {

		final String percent = getResources().getString(
				R.string.percent);

		@Override
		public int getCount() {
			return common.getVoice().getStageCount();
		}

		@Override
		public Object getItem(int position) {
			return common.getVoice().getStage(position);
		}

		@Override
		public long getItemId(int position) {
			return common.getVoice().getStage(position).getId();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = (View) getLayoutInflater().inflate(R.layout.stage_item, null);
			}
			
			Stage stage = (Stage) getItem(position);
			
			TextView stageType = (TextView) convertView.findViewById(R.id.stageType);
			String st = stage.getType().toString();
			stageType.setText(st);
			
			TextView stageDuration = (TextView) convertView.findViewById(R.id.stageDuration);
			String sd = formatter.format(stage.getTime()) + " s";
			stageDuration.setText(sd);
			
			TextView stageLevel = (TextView) convertView.findViewById(R.id.stageLevel);
			String sl = String.format(percent, (int)(100 * stage.getLevel()));
			stageLevel.setText(sl);

			return convertView;
		}

	}
	
}
