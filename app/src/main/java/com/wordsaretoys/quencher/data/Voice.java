package com.wordsaretoys.quencher.data;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.wordsaretoys.quencher.common.Catalogable;
import com.wordsaretoys.quencher.common.Storage;

/**
 * represents a unique instrument sound
 */

public class Voice extends Catalogable {

	/*
	 * constants and static data
	 */

	// maximum timing value in seconds
	public static final float MaxTiming = 1f;
	
	// maximum tremolo frequency in Hz
	public static final float MaxTremolo = 25f;
	
	// maximum vibrato frequency in Hz
	public static final float MaxVibrato = 25f;
	
	/*
	 * data labels for database representation
	 */
	public static final String L_TABLE = "voice";

	public static final String L_TREMO = "tremolo";
	public static final String L_VIBRA = "vibrato";
	
	public static final String[] L_FIELDS = {
		L_ID, L_UUID, L_NAME, L_DESC, L_CREATED, L_UPDATED,
		L_TREMO, L_VIBRA
	};

	public static final String L_ORDER = L_NAME;

	/*
	 * data variables
	 */

	// stage array
	private ArrayList<Stage> stages;

	// tremolo frequency in Hz
	private float tremolo;
	
	// vibrato frequency in Hz
	private float vibrato;
	
	// trash pile for deleted stages
	private ArrayList<Stage> trash;

	/**
	 * default ctor
	 */
	public Voice() {
		super();
		stages = new ArrayList<Stage>();
		trash = new ArrayList<Stage>();
	}
	
	/**
	 * copies data from existing voice
	 * @param v voice to copy
	 */
	public synchronized void copy(Voice v) {
		super.copy(v);
		tremolo = v.tremolo;
		vibrato = v.vibrato;
		for (Stage s : v.stages) {
			Stage stage = new Stage(this);
			stage.copy(s);
			stages.add(stage);
		}
	}

	/**
	 * get the number of stages
	 * @return stage count
	 */
	public int getStageCount() {
		return stages.size();
	}
	
	/**
	 * get stage at specified index 
	 * @param i index
	 * @return stage object
	 */
	public Stage getStage(int i) {
		return stages.get(i);
	}
	
	/**
	 * add stage to voice
	 * @param stage stage object
	 */
	public void addStage(Stage stage) {
		stages.add(stage);
		onStageChange();
	}

	/**
	 * removes a stage from the voice
	 * @param s stage object to remove
	 */
	public void removeStage(Stage s) {
		stages.remove(s);
		// if we removed the last one, slap in a blank stage
		if (stages.size() == 0) {
			stages.add(new Stage(this));
		}
		trash.add(s);
		onStageChange();
	}

	/**
	 * add duplicate stage to voice
	 * @param i index of stage to duplicate
	 */
	public void duplicateStage(int i) {
		Stage stage = new Stage(this);
		stage.copy(stages.get(i));
		stages.add(i, stage);
		onStageChange();
	}
	
	/**
	 * swap two stages in place
	 * used for move up/down operations
	 * @param i0 index of first stage in swap
	 * @param i1 index of second stage in swap
	 */
	public void swapStages(int i0, int i1) {
		// get stage objects at these indexes
		Stage s0 = stages.get(i0);
		Stage s1 = stages.get(i1);
		// swap them
		stages.set(i0, s1);
		stages.set(i1, s0);
		// notify everyone
		onStageChange();
	}
	
	/**
	 * get tremolo frequency in Hz
	 * @return tremolo frequency
	 */
	public float getTremolo() {
		return tremolo;
	}
	
	/**
	 * set tremolo frequency in Hz
	 * @param f tremolo frequency
	 */
	public synchronized void setTremolo(float f) {
		tremolo = f;
		onChange();
	}

	/**
	 * get vibrato frequency in Hz
	 * @return vibrato frequency
	 */
	public float getVibrato() {
		return vibrato;
	}
	
	/**
	 * set vibrato frequency in Hz
	 * @param v vibrato frequency
	 */
	public synchronized void setVibrato(float v) {
		vibrato = v;
		onChange();
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
	public void readFields(Cursor sc) {
		super.readFields(sc);
		tremolo = sc.getFloat(sc.getColumnIndex(L_TREMO));
		vibrato = sc.getFloat(sc.getColumnIndex(L_VIBRA));
		
		// load stages from stage cursor
		Cursor tc = Stage.selectByVoice(id);
		if (tc.moveToFirst()) {
			do {
				Stage stage = new Stage(this);
				stage.readFields(tc);
				stages.add(stage);
			} while (tc.moveToNext());
			tc.close();
		} else {
			// the UI can't represent an empty voice
			// so we have to add a default stage
			Stage stage = new Stage(this);
			stages.add(stage);
			
		}
	}

	@Override
	public synchronized void writeFields(ContentValues v) {
		super.writeFields(v);
		v.put(L_TREMO, tremolo);
		v.put(L_VIBRA, vibrato);
	}
	
	@Override
	public void write(SQLiteDatabase db) {
		super.write(db);
		for (Stage stage : stages) {
			stage.write(db);
		}
		// take out the trash as well
		for (Stage stage : trash) {
			stage.delete(db);
		}
		trash.clear();
	}
	
	/**
	 * call when stage configuration changes
	 */
	void onStageChange() {
		for (int i = 0, il = stages.size(); i < il; i++) {
			Stage stage = stages.get(i);
			// reset indexes to their array positions
			stage.setIndex(i);
		}
		super.onChange();
	}
	
	@Override
	public void delete(SQLiteDatabase db) {
		super.delete(db);
		// a mass delete by key is much faster
		// than calling delete() for each stage
		Stage.deleteByVoice(db, id);
	}
	
	/**
	 * retrieves all voice records in the database
	 * @return cursor containing properties for all voices
	 */
	public static Cursor getCatalog() {
		return getCatalog(L_TABLE);		
	}
	
	/**
	 * build a voice object from a database id
	 * @param id database id of voice
	 * @return voice object or null if not found
	 */
	public static Voice fromId(long id) {
		Voice voice = new Voice();
		return voice.read(id) ? voice : null;
	}

	/**
	 * get a voice object configured for UI use
	 * @return voice object
	 */
	public static Voice createNew() {
		Voice voice = new Voice();
		Stage stage = new Stage(voice);
		voice.stages.add(stage);
		// prevent empty objects from being written to db
		voice.clearDirty();
		stage.clearDirty();
		return voice;
	}
	
	/**
	 * get the default voice used for new tracks
	 * @return voice object
	 */
	public static Voice getDefault() {
		SharedPreferences prefs = Storage.INSTANCE.getSharedPreferences();
		long id = prefs.getLong("defaultVoice", 0);
		return fromId(id);
	}
	
	/**
	 * determines if this voice is in use by any tracks
	 * @return true if this voice is in use
	 */
	public boolean inUse() {
		return Track.usesVoice(id);
	}
	
}