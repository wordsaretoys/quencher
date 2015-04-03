package com.wordsaretoys.quencher.common;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

/**
 * objects that can be used by the storage object implement this
 */
public abstract class Storable {
	
	public final static String L_ID = "id";
	
	// database id
	protected long id;

	// dirty data flag
	protected boolean dirty;
	
	/**
	 * ctor, sets defaults
	 */
	public Storable() {
		id = -1;
		dirty = true;
	}
	
	/**
	 * get database id
	 * @return database id
	 */
	public long getId() {
		return id;
	}
	
	/**
	 * set database id
	 * @param nid database id
	 */
	public void setId(long nid) {
		id = nid;
	}
	
	/**
	 * get dirty data status
	 * @return true if data is dirty
	 */
	public boolean isDirty() {
		return dirty;
	}
	
	/**
	 * call when data changes
	 */
	protected void onChange() {
		dirty = true;
	}
	
	/**
	 * clear the dirty flag
	 * 
	 * use carefully. the dirty flag determines whether
	 * objects will be written to database. interfering
	 * with the natural handling of this flag may cause
	 * data loss
	 */
	public void clearDirty() {
		dirty = false;
	}
	
	/**
	 * read object from table
	 * @param id database id
	 * @return true if object was read
	 */
	public boolean read(long id) {
		boolean ok = Storage.INSTANCE.read(id, this);
		dirty = !ok;
		return ok;
	}
	
	/**
	 * write object to table
	 * @param db writeable database object
	 */
	public void write(SQLiteDatabase db) {
		if (dirty) {
			Storage.INSTANCE.write(db, this);
			dirty = false;
		}
	}
	
	/**
	 * write object to table within own transaction
	 * @return true if object was written
	 */
	public boolean write() {
		SQLiteDatabase db = Storage.INSTANCE.getWritableDatabase();
		boolean ok = false;
		db.beginTransaction();
		try {
			write(db);
			db.setTransactionSuccessful();
			ok = true;
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			db.endTransaction();
		}
		return ok;
	}
	
	/**
	 * delete object from table
	 * @param db writeable database object
	 */
	public void delete(SQLiteDatabase db) {
		Storage.INSTANCE.delete(db, getTableName(), id);
	}
	
	/**
	 * delete object from table
	 */
	public boolean delete() {
		SQLiteDatabase db = Storage.INSTANCE.getWritableDatabase();
		boolean deleted = false;
		db.beginTransaction();
		try {
			delete(db);
			db.setTransactionSuccessful();
			deleted = true;
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			db.endTransaction();
		}
		return deleted;
	}

	public void readFields(Cursor c) {
		id = c.getLong(c.getColumnIndex(L_ID));
		dirty = false;
	}
	
	public void writeFields(ContentValues values) {
		dirty = false;
	}
	
	public abstract String getTableName();
	public abstract String[] getFieldNames();
	public abstract String getOrderingField();
}
