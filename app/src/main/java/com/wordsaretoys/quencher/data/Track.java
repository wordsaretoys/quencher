package com.wordsaretoys.quencher.data;

import java.util.ArrayList;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.SparseArray;

import com.wordsaretoys.quencher.common.Storable;
import com.wordsaretoys.quencher.common.Storage;

/**
 * represents a single track
 * a track is a timeline of notes tied to a voice,
 * a tone scale, and a time signature
 */
public class Track extends Storable {

	/*
	 * default values and constants
	 */

	static final String TAG = "Track";
	
	// initial track volume
	static final float DefaultVolume = 0.5f;
	
	// initial timing parameters
	static final int DefaultBeats = 4;
	static final int DefaultSlots = 1;
	
	/*
	 * data labels for database representation
	 */
	public static final String L_TABLE = "track";
	
	public static final String L_SCORE = "score";
	public static final String L_VOICE = "voice";
	public static final String L_SCALE = "scale";
	public static final String L_VOL = "vol";
	public static final String L_PAN = "pan";
	public static final String L_SLOTS = "slots";
	public static final String L_BEATS = "beats";
	public static final String L_INDEX = "xedni";
	public static final String L_MUTED = "muted";
	public static final String L_LOCKED = "locked";

	public static final String[] L_FIELDS = {
		L_ID, L_SCORE, L_VOICE, L_SCALE, L_VOL, L_PAN, 
		L_SLOTS, L_BEATS, L_INDEX, L_MUTED, L_LOCKED
	};
	
	public static final String L_ORDER = L_INDEX;
	
	/*
	 * data variables
	 */
	
	// reference to parent
	private Score score;

	// collection of notes, indexed by positions
	private SparseArray<Note> notes;
	
	// voice assigned to this track
	private Voice voice;
	
	// scale/key assigned to this track
	private Scale scale;

	// beats per bar
	private int beats;
	
	// note slots per beat
	private int slots;
	
	// relative volume
	private float volume;
	
	// pan l/r setting
	private float pan;
	
	// track ordering within score
	private int index;

	// muting state
	private boolean muted;
	
	// locked state
	private boolean locked;
	
	// trash pile for deleted notes
	private ArrayList<Note> trash;
	
	
	/**
	 * ctor, creates new track
	 * @param s scale to join
	 * @param k scale assigned to track
	 * @param v voice assigned to track
	 */
	public Track(Score s, Scale k, Voice v) {
		super();
		score = s;
		notes = new SparseArray<Note>();
		voice = v;
		scale = k;
		beats = DefaultBeats;
		slots = DefaultSlots;
		volume = DefaultVolume;
		pan = 0;
		index = s.getTrackCount();
		trash = new ArrayList<Note>();
	}
	
	/**
	 * default ctor
	 * @param s score to join
	 */
	public Track(Score s) {
		this(s, Scale.getDefault(), Voice.getDefault());
	}
	
	/**
	 * copies data from existing track
	 * @param t track to copy from
	 */
	public synchronized void copy(Track t) {
		voice = t.voice;
		scale = t.scale;
		volume = t.volume;
		pan = t.pan;
		slots = t.slots;
		beats = t.beats;
		notes = new SparseArray<Note>();
		for (int i = 0, il = t.notes.size(); i < il; i++) {
			Note note = new Note(this);
			note.copy(t.notes.valueAt(i));
			notes.put(note.getIndex(), note);
		}
		index = t.index;
		muted = t.muted;
		locked = t.locked;
	}
	
	/**
	 * return the note at a given position
	 * @param i position within track
	 * @return note at that position, or null
	 */
	public Note getNote(int i) {
		return notes.get(i);
	}

	/**
	 * add a note to a given position
	 * pitch MUST correspond to current scale
	 * 
	 * @param i position to add to
	 * @param p pitch number
	 */
	public void setNote(int i, int p) {
		Note note = notes.get(i);
		if (note == null) {
			note = new Note(this);
			notes.put(i, note);
		}
		note.setIndex(i);
		note.setPitchNumber(p);
		onChange();
	}
	
	/**
	 * remove note from a given position
	 * @param i position to remove from
	 */
	public void clearNote(int i) {
		Note note = notes.get(i);
		if (note != null) {
			trash.add(note);
			notes.delete(i);
		}
		onChange();
	}
	
	/**
	 * get number of notes in track
	 * @return note count
	 */
	public int getNoteCount() {
		return notes.size();
	}
	
	/**
	 * get note at array index
	 * this is the collection version of getNote()
	 * 
	 * @param i array index of note
	 * @return note object
	 */
	public Note getNoteAt(int i) {
		return notes.valueAt(i);
	}
	
	/**
	 * get the voice assigned to the track
	 * @return voice object
	 */
	public Voice getVoice() {
		return voice;
	}
	
	/**
	 * assign a voice to this track
	 * @param v voice object
	 */
	public synchronized void setVoice(Voice v) {
		voice = v;
		onChange();
	}
	
	/**
	 * get the scale assigned to this track
	 * @return scale object
	 */
	public Scale getScale() {
		return scale;
	}
	
	/**
	 * assign a scale to this track
	 * 
	 * if notes are assigned to the track, the
	 * new scale MUST have the same tone count
	 * 
	 * @param s scale object
	 */
	public synchronized void setScale(Scale s) {
		scale = s;
		onChange();
	}
	
	/**
	 * get relative volume
	 * @return volume
	 */
	public float getVolume() { 
		return volume; 
	}
	
	/**
	 * get pan l/r (-1..1)
	 * @return pan setting
	 */
	public float getPan() { 
		return pan; 
	}
	
	/**
	 * set relative volume
	 * @param v volume
	 */
	public synchronized void setVolume(float v) {
		volume = v;
		onChange();
	}

	/**
	 * set pan l/r (-1..1)
	 * @param p pan setting
	 */
	public synchronized void setPan(float p) {
		pan = p;
		onChange();
	}
	
	/**
	 * get timing slots
	 * @return slots count
	 */
	public int getSlots() {
		return slots;
	}

	/**
	 * set timing slots
	 * @param s slots count
	 */
	public synchronized void setSlots(int s) {
		slots = s;
		onChange();
	}
	
	/**
	 * get timing beats
	 * @return beat count
	 */
	public int getBeats() {
		return beats;
	}

	/**
	 * set timing beats
	 * @param b beat count
	 */
	public synchronized void setBeats(int b) {
		beats = b;
		onChange();
	}
	
	/**
	 * get the timing ratio for the track
	 * @return track timing ratio
	 */
	public float getTiming() {
		return (float)beats / (float)(beats * slots);
	}
	
	/**
	 * get track index within score
	 * @return track index
	 */
	public int getIndex() {
		return index;
	}
	
	/**
	 * set track index within score
	 * @param i track index
	 */
	public synchronized void setIndex(int i) {
		index = i;
		onChange();
	}
	
	/**
	 * get mute status
	 * @return true if track is muted
	 */
	public boolean isMuted() {
		return muted;
	}
	
	/**
	 * set muted state
	 * @param m true if track is to be muted
	 */
	public synchronized void setMuted(boolean m) {
		muted = m;
		onChange();
	}
	
	/**
	 * get lock status
	 * @return true if track is locked against edits
	 */
	public boolean isLocked() {
		return locked;
	}
	
	/**
	 * set lock status
	 * @param l true if track is locked against edits
	 */
	public synchronized void setLocked(boolean l) {
		locked = l;
		onChange();
	}
	
	/**
	 * get the score containing this track
	 * @return parent object
	 */
	public Score getScore() { return score; }
	
	/**
	 * get time offset in decimal seconds for a given note position
	 * @param i note position
	 * @return decimal seconds
	 */
	public float positionToTime(int i) {
		return 60f * (float) i * getTiming() / (float) score.getTempo();
	}

	/**
	 * get note position for a given time offset in decimal seconds
	 * @param time decimal seconds
	 * @return note position
	 */
	public int timeToPosition(float time) {
		return (int)((time * (float) score.getTempo()) / (60f * getTiming()));
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
		long voiceId = tc.getLong(tc.getColumnIndex(L_VOICE));
		voice = Voice.fromId(voiceId);
		// crash protection
		if (voice == null) {
			voice = Voice.getDefault();
		}
		long scaleId = tc.getLong(tc.getColumnIndex(L_SCALE));
		scale = Scale.fromId(scaleId);
		// crash protected
		if (scale == null) {
			scale = Scale.getDefault();
		}
		volume = tc.getFloat(tc.getColumnIndex(L_VOL));
		pan = tc.getFloat(tc.getColumnIndex(L_PAN));
		slots = tc.getInt(tc.getColumnIndex(L_SLOTS));
		beats = tc.getInt(tc.getColumnIndex(L_BEATS));
		index = tc.getInt(tc.getColumnIndex(L_INDEX));
		muted = (tc.getInt(tc.getColumnIndex(L_MUTED)) == 1);
		locked = (tc.getInt(tc.getColumnIndex(L_LOCKED)) == 1);

		Cursor nc = Note.selectByTrack(id);
		if (nc.moveToFirst()) {
			do {
				Note note = new Note(this);
				note.readFields(nc);
				notes.put(note.getIndex(), note);
			} while (nc.moveToNext());
		}
		nc.close();
	}

	@Override
	public synchronized void writeFields(ContentValues values) {
		super.writeFields(values);
		values.put(L_SCORE, score.getId());
		values.put(L_VOICE, voice.getId());
		values.put(L_SCALE, scale.getId());
		values.put(L_VOL, volume);
		values.put(L_PAN, pan);
		values.put(L_SLOTS, slots);
		values.put(L_BEATS, beats);
		values.put(L_INDEX, index);
		values.put(L_MUTED, muted ? 1 : 0);
		values.put(L_LOCKED, locked ? 1 : 0);
	}

	@Override
	protected void onChange() {
		super.onChange();
		// parent onChange fires ScoreChange event
		score.onChange();
	}

	@Override
	public void write(SQLiteDatabase db) {
		super.write(db);
		for (int i = 0, il = notes.size(); i < il; i++) {
			Note note = notes.valueAt(i);
			note.write(db);
		}
		// take out the trash as well
		for (Note note : trash) {
			note.delete(db);
		}
		trash.clear();
	}
	
	@Override
	public void delete(SQLiteDatabase db) {
		super.delete(db);
		// a mass delete by key is much faster
		// than calling delete() for each note
		Note.deleteByTrack(db, id);
	}
	
	/**
	 * select all tracks for a given score
	 * @param id database id to filter by
	 * @return cursor containing selected rows
	 */
	public static Cursor selectByScore(long id) {
		return Storage.INSTANCE.select(
				L_TABLE, L_FIELDS, L_ORDER, L_SCORE, id);
	}
	
	/**
	 * determines if an object is used by ANY track
	 * @param id database id of object
	 * @return true if object is in use
	 */
	private static boolean usesObject(String field, long id) {
		SQLiteDatabase db = Storage.INSTANCE.getReadableDatabase();
		String[] args = { String.valueOf(id) };
		String[] cols = { L_ID }; 
		Cursor tc = db.query(
				L_TABLE, cols,	
				field + " = ?", args, 
				null, null, null);
		boolean used = tc.getCount() > 0; 
		tc.close();
		return used;
	}
	
	/**
	 * determines if a scale is used by ANY track
	 * @param id database id of scale
	 * @return true if scale is in use
	 */
	public static boolean usesScale(long id) {
		return usesObject(L_SCALE, id);
	}
	
	/**
	 * determines if a voice is used by ANY track
	 * @param id database id of voice
	 * @return true if voice is in use
	 */
	public static boolean usesVoice(long id) {
		return usesObject(L_VOICE, id);
	}
	
}