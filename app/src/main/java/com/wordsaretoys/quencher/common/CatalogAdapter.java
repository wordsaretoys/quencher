package com.wordsaretoys.quencher.common;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.data.Scale;
import com.wordsaretoys.quencher.data.Score;
import com.wordsaretoys.quencher.data.Voice;
import com.wordsaretoys.quencher.scales.ScaleActivity;
import com.wordsaretoys.quencher.scores.ScoreActivity;
import com.wordsaretoys.quencher.voices.VoiceActivity;


/**
 * list adapter for displaying a catalog cursor
 */
public class CatalogAdapter extends BaseAdapter implements ListAdapter {

	// callback for async completion
	public static interface OnListCompleteListener {
		public void onListComplete(int count);
	}
	
	// activity context
	Context context;
	
	// catalog cursor
	Cursor catalog;
	
	// listener for async completion
	OnListCompleteListener listener;
	
	// column indexes of catalog fields
	int colId, colName, colDesc, colCreated, colUpdated;
	
	// objects for formatting date/time information
	Date date = new Date();
	DateFormat df = 
			DateFormat.getDateTimeInstance(
					SimpleDateFormat.SHORT, SimpleDateFormat.SHORT);
	
	/**
	 * ctor, creates async loading thread
	 * @param context activity context
	 */
	public CatalogAdapter(Context context) {
		super();
		this.context = context;
		final Class<? extends Context> clazz = context.getClass();
		new Thread(new Runnable() {
			public void run() {
				if (clazz == ScoreActivity.class) {
					catalog = Score.getCatalog();
				} else if (clazz == VoiceActivity.class) {
					catalog = Voice.getCatalog();
				} else if (clazz == ScaleActivity.class) {
					catalog = Scale.getCatalog();
				}
				refresh();
			}
		}).start();
	}
	
	/**
	 * set callback listener 
	 * @param l listener
	 */
	public void setOnListCompleteListener(OnListCompleteListener l) {
		listener = l;
	}
	
	/**
	 * once async loading complete, tells UI to display itself
	 */
	public void refresh() {
		((Activity)context).runOnUiThread(new Runnable() {
			public void run() {
				try {
					colId = catalog.getColumnIndex(Storable.L_ID);
					colName = catalog.getColumnIndex(Catalogable.L_NAME);
					colDesc = catalog.getColumnIndex(Catalogable.L_DESC);
					colCreated = catalog.getColumnIndex(Catalogable.L_CREATED);
					colUpdated = catalog.getColumnIndex(Catalogable.L_UPDATED);
					if (listener != null) {
						listener.onListComplete(catalog.getCount());
					}
					notifyDataSetChanged();
				} catch (Exception e) {
					// possible to have exceptions on long-running
					// or blocked queries if activity is discarded
					// while the query is running
				}
			}
		});
	}
	
	/**
	 * closes catalog cursor completely
	 */
	public void close() {
		if (catalog != null) {
			catalog.close();
		}
	}
	
	@Override
	public int getCount() {
		return catalog != null ? catalog.getCount() : 0;
	}

	@Override
	public Object getItem(int position) {
		catalog.moveToPosition(position);
		return catalog.getString(colName);
	}

	@Override
	public long getItemId(int position) {
		catalog.moveToPosition(position);
		return catalog.getLong(colId);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			LayoutInflater inflater = LayoutInflater.from(context);
			convertView = (View) inflater.inflate(R.layout.catalog_item, null);
		}

		catalog.moveToPosition(position);

		TextView nameBox = (TextView) convertView.findViewById(R.id.name);
		String name = catalog.getString(colName);
		nameBox.setText(name);
		
		TextView descBox = (TextView) convertView.findViewById(R.id.description);
		descBox.setText(catalog.getString(colDesc));
		
		TextView timeBox = (TextView) convertView.findViewById(R.id.time);
		date.setTime(catalog.getLong(colUpdated));
		timeBox.setText(df.format(date));
		
		return convertView;
	}
	
}
