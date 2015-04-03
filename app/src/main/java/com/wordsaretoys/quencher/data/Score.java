package com.wordsaretoys.quencher.data;

import java.util.ArrayList;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.wordsaretoys.quencher.common.Catalogable;

/**
 * represents a musical score
 * a collection of tracks tied to a tempo
 */
public class Score extends Catalogable {
	
	/*
	 * constants
	 */
	static final int DefaultTempo = 120;

	/*
	 * data labels for database representation
	 */
	public static final String L_TABLE = "score";
	
	public static final String L_TRACK = "track";
	public static final String L_TEMPO = "tempo";

	public static final String[] L_FIELDS = {
		L_ID, L_UUID, L_NAME, L_DESC, L_CREATED, L_UPDATED,
		L_TEMPO
	};
	
	public static final String L_ORDER = L_NAME;
	
	/*
	 * data variables
	 */
	
	// tracks within the score
	private ArrayList<Track> tracks;
	
	// tempo in beats per minute
	private int tempo;
	
	// trash pile for deleted tracks
	private ArrayList<Track> trash;
	
	/**
	 * default ctor
	 */
	public Score() {
		super();
		tracks = new ArrayList<Track>();
		tempo = DefaultTempo;
		trash = new ArrayList<Track>();
	}
	
	/**
	 * copies data from existing score
	 * @param s score to copy from
	 */
	public synchronized void copy(Score s) {
		super.copy(s);
		tempo = s.tempo;
		for (int i = 0, il = s.getTrackCount(); i < il; i++) {
			Track track = new Track(this);
			track.copy(s.getTrack(i));
			tracks.add(track);
		}
	}

	/**
	 * returns the number of tracks in the score
	 * @return track count
	 */
	public int getTrackCount() {
		return tracks.size();
	}
	
	/**
	 * return the track at the specified index
	 * @param i index
	 * @return track object
	 */
	public Track getTrack(int i) {
		return tracks.get(i);
	}
	
	/**
	 * adds a track to the score
	 * @param t new track object
	 */
	public void addTrack(Track t) {
		tracks.add(t);
		onTrackChange();
	}
	
	/**
	 * removes a track from the score
	 * @param t track object to remove
	 */
	public void removeTrack(Track t) {
		tracks.remove(t);
		// if we removed the last one, slap in a blank track
		if (tracks.size() == 0) {
			tracks.add(new Track(this));
		}
		trash.add(t);
		onTrackChange();
	}

	/**
	 * add duplicate track to score
	 * @param i index of track to duplicate
	 */
	public void duplicateTrack(int i) {
		Track track = new Track(this);
		track.copy(tracks.get(i));
		tracks.add(i, track);
		onTrackChange();
	}
	
	/**
	 * swap two tracks in place
	 * used for move up/down operations
	 * @param i0 index of first track in swap
	 * @param i1 index of second track in swap
	 */
	public void swapTracks(int i0, int i1) {
		// get track objects at these indexes
		Track t0 = tracks.get(i0);
		Track t1 = tracks.get(i1);
		// swap them
		tracks.set(i0, t1);
		tracks.set(i1, t0);
		// notify everyone
		onTrackChange();
	}
	
	/**
	 * get the tempo of the score
	 * @return score tempo
	 */
	public int getTempo() {
		return tempo;
	}

	/**
	 * set the tempo of the score
	 * @param t new tempo
	 */
	public synchronized void setTempo(int t) {
		tempo = t;
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
		tempo = sc.getInt(sc.getColumnIndex(L_TEMPO));
		Cursor nc = Track.selectByScore(id);
		if (nc.moveToFirst()) {
			do {
				Track track = new Track(this);
				track.readFields(nc);
				tracks.add(track);
			} while (nc.moveToNext());
		} else {
			// the UI can't represent an empty score
			// so we have to add a default track 
			Track track = new Track(this);
			tracks.add(track);
		}
		nc.close();
	}

	@Override
	public synchronized void writeFields(ContentValues values) {
		super.writeFields(values);
		values.put(L_TEMPO, tempo);
	}

	@Override
	public void write(SQLiteDatabase db) {
		super.write(db);
		for (Track track : tracks) {
			track.write(db);
		}
		// take out the trash as well
		for (Track track : trash) {
			track.delete(db);
		}
		trash.clear();
	}
	
	@Override
	public void delete(SQLiteDatabase db) {
		super.delete(db);
		for (Track track : tracks) {
			track.delete(db);
		}
	}
	
	/**
	 * call when track configuration changes
	 */
	void onTrackChange() {
		for (int i = 0, il = tracks.size(); i < il; i++) {
			Track track = tracks.get(i);
			// reset track indexes to their array positions
			track.setIndex(i);
		}
		super.onChange();
	}
	
	/**
	 * retrieves all score records in the database
	 * @return cursor containing properties for all scores
	 */
	public static Cursor getCatalog() {
		return getCatalog(L_TABLE);		
	}
	
	/**
	 * get a score object from a database id
	 * @param id database id
	 * @return score object or null if not found
	 */
	public static Score fromId(long id) {
		Score score = new Score();
		return score.read(id) ? score : null;
	}
	
	/**
	 * get a score object set up for UI use
	 * @return score object
	 */
	public static Score createNew() {
		Score score = new Score();
		Track track = new Track(score);
		score.tracks.add(track);
		// prevent empty objects from being written to db
		score.clearDirty();
		track.clearDirty();
		return score;
	}
}
