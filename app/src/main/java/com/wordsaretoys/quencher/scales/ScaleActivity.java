package com.wordsaretoys.quencher.scales;

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
import com.wordsaretoys.quencher.data.Scale;
import com.wordsaretoys.quencher.data.Score;
import com.wordsaretoys.quencher.data.Tone;
import com.wordsaretoys.quencher.data.Track;

/**
 * allows scales to be edited
 */
public class ScaleActivity extends Activity implements NotificationListener {

	static final String TAG = "ScaleActivity";
	
	static final int ListingRequest = 0;
	
	// common scale editing data
	static ScaleCommon common;
	
	// list view
	GuardedListView listView;
	
	// scale adapter
	ScaleAdapter scaleAdapter;
	
	// tone fragment
	ToneFragment toneFragment;
	
	// menu id of action that triggered a save as dialog
	int postSaveAction;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    
	    common = new ScaleCommon(this);
	    common.loadState(savedInstanceState);
	    
	    setContentView(R.layout.scale_main);
	    listView = (GuardedListView) findViewById(R.id.scaleList);
	    
	    View header = getLayoutInflater().inflate(R.layout.tone_item, null);
	    ((TextView)header.findViewById(R.id.toneLabel))
	    	.setText(R.string.toneLabel);
	    ((TextView)header.findViewById(R.id.toneInterval))
	    	.setText(R.string.toneInterval);
	    ((TextView)header.findViewById(R.id.toneRatio))
	    	.setText(R.string.toneRatio);
	    ((TextView)header.findViewById(R.id.toneFreq))
	    	.setText(R.string.toneFrequency);
	    header.setBackgroundResource(R.color.faintyello);
	    listView.addHeaderView(header, null, false);
	    
	    scaleAdapter = new ScaleAdapter();
	    listView.setAdapter(scaleAdapter);
	    listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				int tonePos = position - 1;
				
				// monkey and other test harnesses will click
				// the header, so we need to guard against it
				if (tonePos == -1) {
					tonePos = 0;
				}
				
				if (tonePos != common.getTonePosition()) {
					common.setTonePosition(tonePos);
					Notifier.INSTANCE.send(Notifier.ToneCursorChange);
				}
			}
	    });
	    
	    toneFragment = (ToneFragment) getFragmentManager().findFragmentById(R.id.toneFragment);

		listView.getCheckedItemPositions().put(
				common.getTonePosition() + 1, true);
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
			common.closeScale();
		}
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		common.saveState(outState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.scale, menu);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {

		// if the UI is locked, show no menu
		if (common.getWaiter().isLocked()) {
			return false;
		}

		boolean playing = Engine.INSTANCE.isPlaying();
		// if a scale is playing, hide everything but stop
		for (int i = 0; i < menu.size(); i++) {
			MenuItem item = menu.getItem(i);
			if (item.getItemId() == R.id.stop) {
				item.setVisible(playing);
			} else {
				item.setVisible(!playing);
			}
		}

		if (!playing) {

			boolean isWorkspace = common.getScale().getName().length() == 0;
			boolean isWritable = common.isWritable();
			
			menu.findItem(R.id.delete).setVisible(isWritable && !isWorkspace);
		}
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
	
		case R.id.play:
			playScale();
			break;
		
		case R.id.stop:
			Engine.INSTANCE.stop();
			break;
		
		case R.id.addTone:
			addTone();
			break;
			
		case R.id.create:
			if (common.isSaveAsRequired()) {
				postSaveAction = R.id.create;
				ScaleDialogs.NewScaleDialog
					.newInstance(true)
					.show(getFragmentManager(), Popup.Deferred);
			} else {
				common.createNewScale();
			}
			break;
			
		case R.id.open:
			if (common.isSaveAsRequired()) {
				postSaveAction = R.id.open;
				ScaleDialogs.NewScaleDialog
					.newInstance(false)
					.show(getFragmentManager(), Popup.Deferred);
			} else {
				openScaleList();
			}
			break;
		
		case R.id.saveAs:
			postSaveAction = 0;
			launchSaveAsDialog();
			break;
			
		case R.id.delete:
			deleteScale();
			break;
			
		}
		return true;
	}
	
	/**
	 * show the name (or untitled string) in title bar
	 */
	void setTitle() {
	    String name = common.getScale().getName();
	    if (name.length() == 0) {
	    	name = getResources().getString(R.string.untitledScale);
	    }
		getActionBar().setTitle(name);
	}
	
	/**
	 * handle the load of a new scale
	 */
	void onNewScale() {
		setTitle();
		if (!common.isWritable()) {
			String msg = common.isDefault() ? 
				getResources().getString(R.string.scaleIsDefault) : 
				getResources().getString(R.string.scaleInUse);
				
			Toast.makeText(ScaleActivity.this, 
					String.format(msg, common.getScale().getName()), 
					Toast.LENGTH_SHORT).show();
		}
		invalidateOptionsMenu();
	}
	
	/**
	 * launches the list of scales
	 */
	void openScaleList() {
		new ScaleDialogs.OpenScaleDialog().show(
				getFragmentManager(), Popup.Deferred);
	}
	
	/**
	 * generate a test score and play the scale from it
	 */
	private void playScale() {
		int tc = common.getScale().getToneCount(); 
		if (tc > 0) {
			Score score = new Score();
			score.setTempo(90);
			Track track = new Track(score);
			track.setScale(common.getScale());
			track.setVoice(common.getPlaybackVoice());
			track.setVolume(0.9f);
			score.addTrack(track);
			
			int p = common.getScale().getMiddle();
			for (int i = 0; i <= tc; i++) {
				int note = p + i;
				track.setNote(i, note);
			}
			Engine.INSTANCE.play(score);
		}
	}
	
	/**
	 * add a new tone to the scale
	 */
	private void addTone() {
		Tone tone = new Tone(common.getScale());
		common.getScale().addTone(tone);
		scaleAdapter.notifyDataSetChanged();
		Notifier.INSTANCE.send(Notifier.ScaleChange);
	}

	/**
	 * displays dialog to save scale
	 */
	void launchSaveAsDialog() {
		Scale scale = common.getScale();
		ScaleDialogs.SaveAsDialog
			.newInstance(scale.getName(), scale.getDescription())
			.show(getFragmentManager(), Popup.Deferred);
	}
	
	/**
	 * deletes the current scale
	 */
	private void deleteScale() {
		new ScaleDialogs.ScaleDeleteDialog()
			.show(getFragmentManager(), Popup.Deferred);
	}

	@Override
	public void handleMessage(Message msg) {
		switch(msg.what) {
		
		case Notifier.ScaleReady:
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

		case Notifier.NewScale:
			onNewScale();

		case Notifier.ScaleChange:
			scaleAdapter.notifyDataSetChanged();
			
		case Notifier.ToneCursorChange:
			listView.getCheckedItemPositions().clear();
			listView.getCheckedItemPositions().put(common.getTonePosition() + 1, true);
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
	
	class ScaleAdapter extends BaseAdapter implements ListAdapter {

		@Override
		public int getCount() {
			return common.getScale().getToneCount();
		}

		@Override
		public Object getItem(int position) {
			return common.getScale().getTone(position);
		}

		@Override
		public long getItemId(int position) {
			return common.getScale().getTone(position).getId();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = (View) getLayoutInflater().inflate(R.layout.tone_item, null);
			}
			
			Tone tone = (Tone) getItem(position);
			Scale scale = tone.getScale();
			
			TextView toneLabel = (TextView) convertView.findViewById(R.id.toneLabel);
			String tl = tone.getLabel();
			if (tl.trim().length() == 0) {
				tl = Tone.BlankLabel;
			}
			toneLabel.setText(tl);
			
			DecimalFormat df = (DecimalFormat) NumberFormat.getNumberInstance();
			df.setMaximumFractionDigits(3);
			
			TextView toneInterval = (TextView) convertView.findViewById(R.id.toneInterval);
			String ti = df.format(tone.getInterval());
			toneInterval.setText(ti);
			
			TextView toneRatio = (TextView) convertView.findViewById(R.id.toneRatio);
			String tr = df.format(tone.getPitch()) + 
					" / " + 
					df.format(scale.getIntervalSum());
			toneRatio.setText(tr);
			
			TextView toneFreq = (TextView) convertView.findViewById(R.id.toneFreq);
			float freq = scale.getFrequency(scale.getMiddle() + position);
			String tf = df.format(freq);
			toneFreq.setText(tf);

			return convertView;
		}

	}
}
