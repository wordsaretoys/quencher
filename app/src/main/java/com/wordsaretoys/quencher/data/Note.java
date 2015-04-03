package com.wordsaretoys.quencher.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.wordsaretoys.quencher.common.Storable;
import com.wordsaretoys.quencher.common.Storage;

/**
 * represents a single note within a track
 * a note is an "time" index into a rhythm
 * plus a "pitch" index into a scale
 */

public class Note extends Storable {

	/*
	 * default values and constants
	 */

	static final String TAG = "Note";
	
	/*
	 * data labels for database representation
	 */
	public static final String L_TABLE = "note";
	
	public static final String L_TRACK = "track";
	public static final String L_INDEX = "xedni";
	public static final String L_PITCH = "pitch";

	public static final String[] L_FIELDS = {
		L_ID, L_TRACK, L_INDEX, L_PITCH
	};
	
	public static final String L_ORDER = L_INDEX;
	
	/*
	 * data variables
	 */
	
	// time index within track
	private int index;

	// pitch index within score
	private int pitch;

	// parent track
	private Track track;
	
	/**
	 * default ctor
	 */
	public Note(Track t) {
		super();
		track = t;
		index = -1;
		pitch = -1;
	}
	
	/**
	 * copy data from existing note
	 * @param n note to copy from
	 */
	public synchronized void copy(Note n) {
		index = n.index;
		pitch = n.pitch;
	}
	
	/**
	 * get note index in track
	 * @return note index
	 */
	public int getIndex() {
		return index;
	}
	
	/**
	 * set note index in track
	 * @param i note index
	 */
	public synchronized void setIndex(int i) {
		index = i;
		onChange();
	}
	
	/**
	 * get the pitch number
	 * @return pitch number
	 */
	public int getPitchNumber() {
		return pitch;
	}
	
	/**
	 * set pitch number
	 * @param p pitch number
	 */
	public synchronized void setPitchNumber(int p) {
		pitch = p;
		onChange();
	}
	
	/**
	 * get the scale-defined frequency of the note
	 * @return frequency in Hz
	 */
	public float getFrequency() {
		return track.getScale().getFrequency(pitch);
	}
	
	/**
	 * get the scale-defined name of the note
	 * @return name string
	 */
	public String getName() {
		return track.getScale().getLabel(pitch);
	}
	
	/**
	 * get note volume
	 * @return note volume
	 */
	public float getVolume() {
		return track.getVolume();
	}
	
	/**
	 * get time that note occurs in score
	 * @return time stamp (in decimal seconds)
	 */
	public float getTime() {
		return 60f * (float) index * track.getTiming() / 
				(float) track.getScore().getTempo();
	}
	
	/**
	 * get the label of the octave this pitch is in
	 * @return label string
	 */
	public String getOctaveLabel() {
		return track.getScale().getOctaveLabel(pitch);
	}
	
	/**
	 * get channel pan
	 * @return pan level
	 */
	public float getPan() {
		return track.getPan();
	}
	
	/**
	 * get track the note belongs to
	 * @return track object
	 */
	public Track getTrack() {
		return track;
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
	public void readFields(Cursor nc) {
		super.readFields(nc);
		index = nc.getInt(nc.getColumnIndex(L_INDEX));
		pitch = nc.getInt(nc.getColumnIndex(L_PITCH));
	}

	@Override
	public synchronized void writeFields(ContentValues values) {
		super.writeFields(values);
		values.put(L_TRACK, track.getId());
		values.put(L_INDEX, index);
		values.put(L_PITCH, pitch);
	}

	/**
	 * select all notes for a given track
	 * @param id database id to filter by
	 * @return cursor containing selected rows
	 */
	public static Cursor selectByTrack(long id) {
		return Storage.INSTANCE.select(
				L_TABLE, L_FIELDS, L_ORDER, L_TRACK, id);
	}
	
	/**
	 * deletes all note records from the table for a given track
	 * @param db writeable database in transaction
	 * @param id track database id
	 */
	public static void deleteByTrack(SQLiteDatabase db, long id) {
		Storage.INSTANCE.delete(db, L_TABLE, L_TRACK, id);
	}
	
}
