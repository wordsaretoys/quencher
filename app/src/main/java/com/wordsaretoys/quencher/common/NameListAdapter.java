package com.wordsaretoys.quencher.common;

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
import com.wordsaretoys.quencher.data.Voice;


/**
 * list adapter for displaying names from a catalog cursor
 */
public class NameListAdapter extends BaseAdapter implements ListAdapter {

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
	int colId, colName;
	
	// id of selected item
	long selectedId;
	
	/**
	 * ctor, creates async loading thread
	 * @param context activity context
	 * @param clazz class reference to indicate what to load
	 */
	public NameListAdapter(Context context, final Class<? extends Catalogable> clazz) {
		super();
		this.context = context;
		new Thread(new Runnable() {
			public void run() {
				if (clazz == Voice.class) {
					catalog = Voice.getCatalog();
				} else if (clazz == Scale.class) {
					catalog = Scale.getCatalog();
				}
				refresh();
			}
		}).start();
		selectedId = -1;
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
	
	public void setSelection(long id) {
		selectedId = id;
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
			convertView = (View) inflater.inflate(R.layout.list_popup_item, null);
		}
		catalog.moveToPosition(position);
		TextView item = (TextView) convertView;
		item.setText(catalog.getString(colName));
		boolean isCurrent = catalog.getLong(colId) == selectedId; 
		// a miserable hack, because View.setActivated() DOES NOTHING
		// and I am completly out of ideas. Hate, hate, hate this shit. 
		item.setBackgroundResource(isCurrent ? R.color.faintblu : R.color.listview_selector);
		return convertView;
	}
	
}
