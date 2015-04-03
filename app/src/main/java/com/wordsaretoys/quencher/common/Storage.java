package com.wordsaretoys.quencher.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import com.wordsaretoys.quencher.R;


/**
 * provides helper methods for data storage
 * 
 * uses enum-based singleton pattern
 */
public enum Storage {
	
	INSTANCE;

	final String DbName = "quencher";
	final int SchemaVersion = 1;
	
	static int AutosaveId = 0;
	
	/**
	 * database helper class
	 * assists with database creation and opening
	 */
	class DatabaseHelper extends SQLiteOpenHelper {
		
		public DatabaseHelper(String name) {
			super(context, name, null, SchemaVersion);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			// database creation handled elsewhere
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// no upgrades defined at this point 
		}
		
	}
	
	/**
	 * autosave thread class
	 */
	class Autosave extends Needle {

		Catalogable saveObject;
		Autosave lastAutosave;
		boolean completed;
		
		public Autosave(Catalogable o, Autosave l) {
			super("autosave " + AutosaveId, 16);
			AutosaveId++;
			saveObject = o;
			if (l != null) {
				l.stop();
			}
			lastAutosave = l;
		}

		@Override
		public void run() {
			while (inPump()) {
				// write unsaved changes
				if (saveObject.isDirty()) {
					if (!saveObject.write()) {
						Notifier.INSTANCE.send(Notifier.StorageSaveFailed);
					}
				}
				// if the last autosave is complete, release it for gc
				if (lastAutosave != null && lastAutosave.completed) {
					lastAutosave = null;
				}
			}
			completed = true;
		}
		
	}
	
	// application context
	Context context;
	
	// sqlite database helper instance
	DatabaseHelper databaseHelper;

	// values collection for writing
	ContentValues values = new ContentValues();
	
	// manages background writes to database
	Autosave autosave;
	
	// hopefully null, unless database was unavailable
	Exception startupException;
	
	/**
	 * create database helper object and autosave thread
	 */
	public void onCreate(Context c) {
		context = c;

		// does the database exist?
		File dbFile = context.getDatabasePath(DbName);
		if (!dbFile.exists()) {

			// (re)set default objects
			SharedPreferences prefs = getSharedPreferences();
			SharedPreferences.Editor editor = prefs.edit();
			editor.putLong("defaultVoice", 0);
			editor.putLong("defaultScale", 0);
			editor.apply();
			
			// attempt to copy the database from assets
			try {
				copyDatabaseFromAssets();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			// now, if THIS fails, we're not going anywhere
			databaseHelper = new DatabaseHelper(DbName);
		} catch(Exception e) {
			e.printStackTrace();
			startupException = e;
			return;
		}
	}

	/**
	 * set new object to be autosaved
	 * @param o catalogable object
	 */
	public synchronized void setAutosave(Catalogable o) {
		autosave = new Autosave(o, autosave);
		autosave.start();
		autosave.resume();
	}

	/**
	 * set object to be deleted in background
	 * this is a relatively rare operation, so
	 * it's cool to spawn a new thread for it
	 * 
	 * @param o catalogable object
	 */
	public void submitForDelete(final Catalogable o) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (!o.delete()) {
					Notifier.INSTANCE.send(Notifier.StorageDeleteFailed);
				}
			}
		}).start();
	}
	
	/**
	 * get any exception encountered during startup
	 * this will be null unless there was a problem
	 * that requires the app to show down right now 
	 * 
	 * @return exception object (hopefully null)
	 */
	public Exception getStartupException() {
		return startupException;
	}
	
	/**
	 * get application shared preferences object
	 * @return shared preferences object
	 */
	public SharedPreferences getSharedPreferences() {
		String appName = context.getResources().getString(R.string.app_name);
		return context.getSharedPreferences(appName, 0);
	}
	
	/**
	 * get a reference to a readable SQLite database
	 * @return database object open for reading
	 */
	public SQLiteDatabase getReadableDatabase() {
		return databaseHelper.getReadableDatabase();
	}
	
	/**
	 * get a reference to a writable SQLite database
	 * @return database object open for writing
	 */
	public SQLiteDatabase getWritableDatabase() {
		return databaseHelper.getWritableDatabase();
	}
	
	/**
	 * retrieve all rows from a table
	 * @param table name of table
	 * @param fields array of field names
	 * @param order name of ordering field
	 * @return cursor
	 */
	public Cursor getAll(String table, String[] fields, String order) {
		SQLiteDatabase db = getReadableDatabase();
		return db.query(table, fields, null, null, null, null, order);
	}
	
	/**
	 * retrieve selected rows from a table
	 * @param table name of table
	 * @param fields array of field names
	 * @param order name of ordering field
	 * @param selectBy name of selecting field
	 * @param id id to select for
	 * @return cursor
	 */
	public Cursor select(String table, String[] fields, String order, String selectBy, long id) {
		SQLiteDatabase db = getReadableDatabase();
		String[] args = { String.valueOf(id) };
		return db.query(
				table, fields, selectBy + " = ?", args, 
				null, null, order);
	}
	
	/**
	 * load a storage object from the database by its id
	 * @param id database id
	 * @param o storage object
	 * @return true if read was sucessful
	 */
	public boolean read(long id, Storable o) {
		SQLiteDatabase db = getReadableDatabase();
		String[] args = { String.valueOf(id) };
		Cursor sc = db.query(
				o.getTableName(), o.getFieldNames(),	
				Storable.L_ID + " = ?", args, 
				null, null, o.getOrderingField());
		if (sc.moveToFirst()) {
			o.readFields(sc);
		}
		sc.close();
		return sc.getCount() > 0;
	}

	/**
	 * write storage object to database
	 * requires open database transaction
	 * 
	 * @param db database open for transaction
	 * @param o storage object
	 */
	public void write(SQLiteDatabase db, Storable o) {
		long id = o.getId();
		values.clear();
		o.writeFields(values);
		// insert or update record
		if (id == -1) {
			o.setId( db.insert(o.getTableName(), null, values) );
		} else {
			String[] args = { String.valueOf(id) };
			db.update(o.getTableName(), values, Storable.L_ID + "= ?", args);
		}
	}
	
	/**
	 * delete a specified object as part of a transaction
	 * @param db database with active transaction
	 * @param table name of table
	 * @param id database id to remove
	 */
	public void delete(SQLiteDatabase db, String table, long id) {
		String[] args = { String.valueOf(id) };
		db.delete(table, Storable.L_ID + "= ?", args);
	}
	
	/**
	 * delete a collection of objects in a transaction
	 * @param db database with active transaction
	 * @param table name of table
	 * @param filter field to filter on
	 * @param id database id of filter
	 */
	public void delete(SQLiteDatabase db, String table, String filter, long id) {
		String[] args = { String.valueOf(id) };
		db.delete(table, filter + "= ?", args);
	}
	
	/**
	 * runs a SQL script from the assets in a transaction
	 * @param db writable database
	 * @param scriptName name of asset
	 */
	@SuppressWarnings("unused")
	private void runScript(SQLiteDatabase db, String scriptName) {
		String sql = "";
		db.beginTransaction();
		try {
			InputStream stream = context.getAssets().open(scriptName + ".sql");
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			while( (sql = reader.readLine()) != null) {
				sql = sql.trim();
				// if it's not a blank line, and not a comment
				if (sql.length() > 0 && !sql.startsWith("--")) {
					db.execSQL(sql);
				}
			}
			db.setTransactionSuccessful();
		} catch(IOException e) {
			throw new RuntimeException(e);
		} catch (SQLiteException e) {
			throw new RuntimeException(e);
		} finally {
			db.endTransaction();
		}
	}
	
	/**
	 * copy from one file stream to another
	 * @param i input file stream
	 * @param o output file stream
	 * @return true if copy succeeded
	 */
	private boolean copyStream(InputStream i, OutputStream o) {
	    byte[] buf = new byte[1024];
	    int len;
	    try {
		    while ((len = i.read(buf)) > 0) {
		        o.write(buf, 0, len);
		    }
	    } catch (IOException e) {
	    	e.printStackTrace();
	    	return false;
	    }
	    return true;
	}
	
	/**
	 * copy app private database to public area
	 * @return true if operation succeeded
	 */
	public boolean copyDatabaseToPublic() {
		FileInputStream ins = null;
		FileOutputStream outs = null;
		boolean ok = true;
		
		try {
			ins = new FileInputStream(
							context.getDatabasePath(databaseHelper.getDatabaseName()));

			File outFile = new File(context.getExternalFilesDir(null), "quencher.db");
			outs = new FileOutputStream(outFile);
			
			ok = copyStream(ins, outs);
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			ok = false;
		} finally {
			
			try {
				ins.close();
				outs.close();
			} catch (IOException e) {
				e.printStackTrace();
				ok = false;
			}
			
		}
		
		return ok;
	}
	
	/**
	 * copy seed database to app private area
	 * @return true if operation succeeded
	 */
	private boolean copyDatabaseFromAssets() {
		boolean ok = true;
		InputStream ins = null;
		FileOutputStream outs = null;
		
		try {
			ins = context.getAssets().open("quencher.db");
			File outFile = context.getDatabasePath(DbName);
			// make sure database directory exists
			File dbDir = new File(outFile.getParent());
			dbDir.mkdirs();
			// now create the file itself
			outs = new FileOutputStream(outFile);
			ok = copyStream(ins, outs);
		} catch(IOException e) {
			e.printStackTrace();
			ok = false;
		} finally {
			
			try {
				ins.close();
				outs.close();
			} catch (IOException e) {
				e.printStackTrace();
				ok = false;
			}
		}
		
		return ok;
	}

}
