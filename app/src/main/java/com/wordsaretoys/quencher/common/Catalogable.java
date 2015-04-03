package com.wordsaretoys.quencher.common;

import java.util.Date;
import java.util.UUID;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public abstract class Catalogable extends Storable {

	public interface OnDataChangedListener {
		public void onDataChanged();
	}
	
	public final static String L_NAME = "name";
	public final static String L_DESC = "desc";
	public final static String L_UUID = "uuid";
	public final static String L_CREATED = "created";
	public final static String L_UPDATED = "updated";
	
	
	public final static String[] L_CATALOG = {
		L_ID, L_NAME, L_DESC, L_UUID, L_CREATED, L_UPDATED
	};

	// uuid for resolving imports
	protected UUID uuid;
	
	// human-readable name (optional)
	protected String name;
	
	// human-readable description (optional)
	protected String desc;
	
	// time of object creation
	protected Date created;
	
	// time of last object update
	protected Date updated;

	// data change listener
	protected OnDataChangedListener listener;
	
	/**
	 * default ctor
	 */
	public Catalogable() {
		super();
		name = "";
		desc = "";
		created = new Date(System.currentTimeMillis());
		updated = new Date(System.currentTimeMillis());
	}

	/**
	 * copies data from another catalogable object
	 * @param c catalogable object
	 */
	public void copy(Catalogable c) {
		name = new String(c.name.toCharArray());
		desc = new String(c.desc.toCharArray());
	}
	
	/**
	 * get name
	 * @return name
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * set name
	 * @param n name
	 */
	public void setName(String n) {
		name = n;
		onChange();
	}
	
	/**
	 * get long description
	 * @return description
	 */
	public String getDescription() {
		return desc;
	}
	
	/**
	 * set long description
	 * @param d description
	 */
	public void setDescription(String d) {
		desc = d;
		onChange();
	}
	
	/**
	 * get creation date/time of object
	 * @return date object
	 */
	public Date getCreateTime() {
		return created;
	}
	
	/**
	 * get date/time of last update
	 * @return date object
	 */
	public Date getUpdateTime() {
		return updated;
	}

	/**
	 * set updated time to current time
	 */
	public void setUpdateTime() {
		updated.setTime(System.currentTimeMillis());
	}

	/**
	 * set the data change listener
	 * this listener will be called on any change to object data
	 * @param l listener object
	 */
	public void setOnDataChangedListener(OnDataChangedListener l) {
		listener = l;
	}
	
	@Override
	public void readFields(Cursor c) {
		super.readFields(c);
		String uuidStr = c.getString(c.getColumnIndex(L_UUID));
		uuid = UUID.fromString(uuidStr);
		name = c.getString(c.getColumnIndex(L_NAME));
		desc = c.getString(c.getColumnIndex(L_DESC));
		long ct = c.getLong(c.getColumnIndex(L_CREATED));
		created = new Date(ct);
		long ut = c.getLong(c.getColumnIndex(L_UPDATED));
		updated = new Date(ut);
	}
	
	@Override
	public void writeFields(ContentValues values) {
		super.writeFields(values);
		if (uuid == null) {
			uuid = UUID.randomUUID();
		}
		values.put(L_UUID, uuid.toString());
		values.put(L_NAME, name);
		values.put(L_DESC, desc);
		values.put(L_UPDATED, updated.getTime());
	}

	@Override
	public void onChange() {
		super.onChange();
		setUpdateTime();
		if (listener != null) {
			listener.onDataChanged();
		}
	}
	
	/**
	 * returns cursor containing catalog columns of specified table
	 * 
	 * @param table name of table
	 * @return catalog cursor
	 */
	public static Cursor getCatalog(String table) {
		SQLiteDatabase db = Storage.INSTANCE.getReadableDatabase();
		return db.query(table, L_CATALOG, L_NAME + "<>''", null, null, null, L_NAME);
	}
}
