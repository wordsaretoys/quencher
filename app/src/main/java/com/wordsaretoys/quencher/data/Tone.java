package com.wordsaretoys.quencher.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.wordsaretoys.quencher.common.Storable;
import com.wordsaretoys.quencher.common.Storage;

public class Tone extends Storable {

	// displayable substitute for blank labels
	public static final String BlankLabel = "(...)";
	
	/*
	 * data labels for database representation
	 */
	public static final String L_TABLE = "tone";

	public static final String L_SCALE = "scale";
	public static final String L_INDEX = "xedni";
	public static final String L_LABEL = "label";
	public static final String L_INTERVAL = "interval";
	
	public static final String[] L_FIELDS = {
		L_ID, L_SCALE, L_INDEX, L_LABEL, L_INTERVAL
	};

	public static final String L_ORDER = L_INDEX;

	/*
	 * data variables
	 */

	// index within scale
	private int index;
	
	// tone label
	private String label;

	// interval between this tone and next
	private float interval;
	
	// scale containing this tone
	private Scale scale;
	
	// calculated pitch number
	private float pitch;
	
	/**
	 * ctor, sets default values
	 * @param s scale object
	 */
	public Tone(Scale s) {
		super();
		scale = s;
		index = s.getToneCount();
		label = "";
		interval = 1;
	}
	
	/**
	 * copy data from existing tone
	 * @param t tone to copy from
	 */
	public synchronized void copy(Tone t) {
		index = t.index;
		interval = t.interval;
		label = new String(t.label.toCharArray());
	}
	
	/**
	 * get index within scale
	 * @return index
	 */
	public int getIndex() {
		return index;
	}
	
	/**
	 * set index within scale
	 * @param i index
	 */
	public synchronized void setIndex(int i) {
		index = i;
		onChange();
	}
	
	/**
	 * get interval from next tone
	 * @return interval
	 */
	public float getInterval() {
		return interval;
	}
	
	/**
	 * set interval from next tone
	 * @param interval
	 */
	public synchronized void setInterval(float t) {
		interval = t;
		onChange();
	}
	
	/**
	 * get tone label
	 * @return label string
	 */
	public String getLabel() {
		return label;
	}
	
	/**
	 * set tone label
	 * @param l label string
	 */
	public synchronized void setLabel(String l) {
		label = l;
		onChange();
	}
	
	/**
	 * get scale containing tone
	 * @return scale object
	 */
	public Scale getScale() {
		return scale;
	}
	
	/**
	 * get the pitch number
	 * @return pitch number
	 */
	public float getPitch() {
		return pitch;
	}
	
	/**
	 * set the pitch number
	 * 
	 * this quantity is calculated, 
	 * and not stored with the tone
	 *  
	 * @param p pitch number
	 */
	public void setPitch(float p) {
		pitch = p;
	}
	
	@Override
	public String getTableName() {
		return L_TABLE;
	}

	@Override
	public String[] getFieldNames() {
		return L_FIELDS;
	}

	@Override
	public String getOrderingField() {
		return L_ORDER;
	}

	@Override
	public void readFields(Cursor tc) {
		super.readFields(tc);
		index = tc.getInt(tc.getColumnIndex(L_INDEX));
		interval = tc.getFloat(tc.getColumnIndex(L_INTERVAL));
		label = tc.getString(tc.getColumnIndex(L_LABEL));
	}

	@Override
	public synchronized void writeFields(ContentValues values) {
		super.writeFields(values);
		values.put(L_SCALE, scale.getId());
		values.put(L_INDEX, index);
		values.put(L_INTERVAL, interval);
		values.put(L_LABEL, label);
	}
	
	@Override
	protected void onChange() {
		super.onChange();
		scale.updatePitches();
		// parent onChange fires ScaleChange event
		scale.onChange();
	}

	/**
	 * select all tones for a given scale
	 * @param id database id to filter by
	 * @return cursor containing selected rows
	 */
	public static Cursor selectByScale(long id) {
		return Storage.INSTANCE.select(
				L_TABLE, L_FIELDS, L_ORDER, L_SCALE, id);
	}
	
	/**
	 * deletes all tone records from the table for a given scale
	 * @param db writeable database in transaction
	 * @param id id database id
	 */
	public static void deleteByScale(SQLiteDatabase db, long id) {
		Storage.INSTANCE.delete(db, L_TABLE, L_SCALE, id);
	}
	
}
