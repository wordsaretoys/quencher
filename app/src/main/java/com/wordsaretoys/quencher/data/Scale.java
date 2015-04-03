package com.wordsaretoys.quencher.data;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.RectF;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.common.Catalogable;
import com.wordsaretoys.quencher.common.Storage;

/**
 * represents a collection of pitches
 */

public class Scale extends Catalogable {

	static final String TAG = "Scale";
	
	// base reference frequency of A4
	// can be modified in user preferences
	public static float TuningOctave = 0.75f;
	public static float TuningReference = 440f;
	public static float TuningFrequency = 440f;
	
	// starting octave
	static final int OctaveStart = 1;
	
	// number of octaves represented
	static final int OctaveCount = 8;
	
	// index of "middle" octave
	static final int MiddleOctave = 4;
	
	// label addenda for octaves
	static final String[] OctaveLabels = {
		"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
	};
	
	/*
	 * data labels for database representation
	 */
	public static final String L_TABLE = "scale";
	
	public static final String L_OFFSET = "offset";
	
	public static final String[] L_FIELDS = {
		L_ID, L_UUID, L_NAME, L_DESC, L_CREATED, L_UPDATED,
		L_OFFSET,
	};
	
	public static final String L_ORDER = L_NAME;

	/*
	 * data variables
	 */
	
	// array of tones
	private ArrayList<Tone> tones;
	
	// offset of first tone from octave start
	private float offset;
	
	// generated sum of intervals
	private float sum;
	
	// (generated) note drawing parameters
	private float maxLabelWidth = -1;
	private float maxOctaveWidth = -1;
	
	// trash pile for deleted tones
	private ArrayList<Tone> trash;
	
	/**
	 * default ctor
	 */
	public Scale() {
		super();
		tones = new ArrayList<Tone>();
		trash = new ArrayList<Tone>();
	}
	
	/**
	 * copies data from existing scale
	 * @param s scale to copy
	 */
	public synchronized void copy(Scale s) {
		super.copy(s);
		for (Tone t : s.tones) {
			Tone tone = new Tone(this);
			tone.copy(t);
			tones.add(tone);
		}
		offset = s.offset;
	}

	/**
	 * get the number of tones
	 * @return tone count
	 */
	public int getToneCount() {
		return tones.size();
	}
	
	/**
	 * get tone at specified index 
	 * @param i index
	 * @return tone object
	 */
	public Tone getTone(int i) {
		return tones.get(i);
	}
	
	/**
	 * add tone to scale
	 * @param tone tone object
	 */
	public void addTone(Tone tone) {
		tones.add(tone);
		onToneChange();
	}

	/**
	 * removes a tone from the scale
	 * @param t tone object to remove
	 */
	public void removeTone(Tone t) {
		tones.remove(t);
		// if we removed the last one, slap in a blank tone
		if (tones.size() == 0) {
			tones.add(new Tone(this));
		}
		trash.add(t);
		onToneChange();
	}

	/**
	 * add duplicate tone to scale
	 * @param i index of tone to duplicate
	 */
	public void duplicateTone(int i) {
		Tone tone = new Tone(this);
		tone.copy(tones.get(i));
		tones.add(i, tone);
		onToneChange();
	}
	
	/**
	 * swap two tones in place
	 * used for move up/down operations
	 * @param i0 index of first tone in swap
	 * @param i1 index of second tone in swap
	 */
	public void swapTones(int i0, int i1) {
		// get tone objects at these indexes
		Tone t0 = tones.get(i0);
		Tone t1 = tones.get(i1);
		// swap them
		tones.set(i0, t1);
		tones.set(i1, t0);
		// notify everyone
		onToneChange();
	}
	
	/**
	 * get octave offset
	 * @return octave offset
	 */
	public float getOffset() {
		return offset;
	}
	
	/**
	 * set octave offset
	 * @param o octave offset
	 */
	public synchronized void setOffset(float o) {
		offset = o;
		onChange();
	}
	
	/**
	 * get generated interval sum
	 * @return interval sum
	 */
	public float getIntervalSum() {
		return sum;
	}
	
	/**
	 * get count of pitches in scale
	 * @return pitch count
	 */
	public int getCount() {
		return getToneCount() * OctaveCount;
	}
	
	/**
	 * get frequency at a specified index 
	 * @param i pitch index
	 * @return frequency in Hz
	 */
	public float getFrequency(int i) {
		// multiplying factor between each tone
		float root = (float) Math.pow(2.0, 1.0 / sum);
		// reference pitch
		float refr = sum * TuningOctave;
		// note pitch, offset from start of octave
		float pitch = tones.get(i % tones.size()).getPitch() + offset;
		// pitch denoting start of octave
		float start = ((int)(i / tones.size()) - MiddleOctave + OctaveStart) * sum;
		// finally, the actual frequency in Hz
		return (float)(TuningFrequency * Math.pow(root, pitch + start - refr)); 
	}
	
	/**
	 * get the label at a specified index
	 * @param i pitch index
	 * @return label
	 */
	public String getLabel(int i) {
		return tones.get(i % tones.size()).getLabel();
	}
	
	/**
	 * get the octave number at a specified index
	 * @param i pitch index
	 * @return octave number
	 */
	public int getOctave(int i) {
		// transform octave offset into tone index
		// TODO: crash bug from below--may not work for all pitch indexes
		i += (int)(offset * tones.size() / sum);
		return OctaveStart + (int)(i / tones.size());
	}
	
	/**
	 * get the octave label at a specified index
	 * @param i pitch index
	 * @return octave label
	 */
	public String getOctaveLabel(int i) {
		return OctaveLabels[getOctave(i)];
	}
	
	/**
	 * get index of middle pitch
	 * @return pitch index
	 */
	public int getMiddle() {
		return (MiddleOctave - OctaveStart) * tones.size();
	}
	
	/**
	 * updates pitch table based on intervals
	 */
	public void updatePitches() {
		// update interval sum
		sum = 0;
		for (int i = 0; i < tones.size(); i++) {
			sum += tones.get(i).getInterval();
		}
		// calculate new pitches for each tone
		float pitch = 0;
		for (Tone tone : tones) {
			tone.setPitch(pitch);
			pitch += tone.getInterval();
		}
		// invalidate drawing params
		maxLabelWidth = -1;
		maxOctaveWidth = -1;
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
		offset = sc.getFloat(sc.getColumnIndex(L_OFFSET));
		
		// load tones from tone cursor
		Cursor tc = Tone.selectByScale(id);
		if (tc.moveToFirst()) {
			do {
				Tone tone = new Tone(this);
				tone.readFields(tc);
				tones.add(tone);
			} while (tc.moveToNext());
		} else {
			// the UI can't represent an empty scale
			// so we have to add a default tone
			Tone tone = new Tone(this);
			tones.add(tone);
		}
		tc.close();
		
		// generate the pitch array
		updatePitches();
	}

	@Override
	public synchronized void writeFields(ContentValues v) {
		super.writeFields(v);
		v.put(L_OFFSET, offset);
	}
	
	@Override
	public void write(SQLiteDatabase db) {
		super.write(db);
		for (Tone tone : tones) {
			tone.write(db);
		}
		// take out the trash as well
		for (Tone tone : trash) {
			tone.delete(db);
		}
		trash.clear();
	}
	
	@Override
	public void onChange() {
		super.onChange();
		updatePitches();
	}
	
	/**
	 * call when tone configuration changes
	 */
	void onToneChange() {
		for (int i = 0, il = tones.size(); i < il; i++) {
			Tone tone = tones.get(i);
			// reset indexes to their array positions
			tone.setIndex(i);
		}
		updatePitches();
		super.onChange();
	}
	
	@Override
	public void delete(SQLiteDatabase db) {
		super.delete(db);
		// a mass delete by key is much faster
		// than calling delete() for each tone
		Tone.deleteByScale(db, id);
	}
	
	/**
	 * retrieves all scale records in the database
	 * @return cursor containing properties for all scales
	 */
	public static Cursor getCatalog() {
		return getCatalog(L_TABLE);		
	}
	
	/**
	 * get a scale object from a database id
	 * @param id database id
	 * @return scale object or null if not found
	 */
	public static Scale fromId(long id) {
		Scale scale = new Scale();
		return scale.read(id) ? scale : null;
	}
	
	/**
	 * get a scale object set up for UI use
	 * @return scale object
	 */
	public static Scale createNew() {
		Scale scale = new Scale();
		Tone tone = new Tone(scale);
		scale.tones.add(tone);
		scale.updatePitches();
		// prevent empty objects from being written to db
		scale.clearDirty();
		tone.clearDirty();
		return scale;
	}

	/**
	 * get the default scale used for new tracks
	 * @return scale object
	 */
	public static Scale getDefault() {
		SharedPreferences prefs = Storage.INSTANCE.getSharedPreferences();
		long id = prefs.getLong("defaultScale", 0);
		return fromId(id);
	}

	/**
	 * determines if this scale is in use by a score
	 * @return true if this scale is in use
	 */
	public boolean inUse() {
		return Track.usesScale(id);
	}

	/**
	 * consistent drawing method for a note
	 * will render as label<sub>octave</sub> within padded box
	 * 
	 * @param canvas destination canvas for note text
	 * @param paint text paint object to use
	 * @param rect rectangle to draw within
	 * @param pad distance from edge of draw box to text
	 * @param note pitch index of note within scale
	 */
	public void drawNote(Canvas canvas, Paint paint, RectF rect, int pad, int note) {

		// find the largest label width, if not known
		if (maxLabelWidth == -1) {
			float tw = 0;
			paint.setTextSize(64f);
			for (Tone tone : tones) {
				String label = tone.getLabel();
				label = label.length() > 0 ? label : Tone.BlankLabel;
				tw = Math.max(tw, paint.measureText(label));
			}
			maxLabelWidth = tw;
		}
		
		// find the largest octave (label) with, if not known
		if (maxOctaveWidth == -1) {
			float tw = 0;
			paint.setTextSize(32f);
			for (int i = 0; i < OctaveLabels.length; i++) {
				tw = Math.max(tw, paint.measureText(OctaveLabels[i]));
			}
			maxOctaveWidth = tw;
		}

		// find optimal text size
		float w = rect.width() - 2 * pad;
		float ts = 64f * w / (float)(maxLabelWidth + maxOctaveWidth);

		// determine note-specific drawing params
		String label = getLabel(note);
		label = label.length() > 0 ? label : Tone.BlankLabel;
		String octave = getOctaveLabel(note);
		
		paint.setTextSize(ts);
		float lw = paint.measureText(label);
		paint.setTextSize(ts * 0.5f);
		float ow = paint.measureText(octave);
		float c = (w - lw - ow) * 0.5f;
		float x = rect.left + pad + c;
		
		paint.setTextSize(ts);
		paint.setTextAlign(Align.LEFT);
		float y = (rect.top + rect.bottom - 
				paint.ascent() - paint.descent()) * 0.5f;
		canvas.drawText(label, x, y, paint);

		paint.setTextSize(ts * 0.5f);
		canvas.drawText(octave, x + lw, y, paint);
	}

	/**
	 * reloads scale tuning settings
	 * @param prefs shared preferences object
	 * @param res application resources object
	 */
	public static void loadTuning(SharedPreferences pref, Resources res) {
		String s;
		float f = -1;

		s = pref.getString(
				"pref_tuning_reference", 
				res.getString(R.string.prefsTuningReferenceDefault));
		try {
			f = Float.valueOf(s);
		} catch (Exception e) {
			f = 440;
		}
		
		// we calculate the octave ratio from the specified frequency
		// and the frequency endpoints of the middle octave
		// our pitch scale is logarithmic; we are actually finding the
		// ratio of a set of arbitrary "pitch numbers" where p = log(f)
		TuningOctave = (float)
				((Math.log(f) - Math.log(261.63)) / 
						(Math.log(523.25) - Math.log(261.63)));
		
		s = pref.getString(
				"pref_tuning_frequency", 
				res.getString(R.string.prefsTuningFrequencyDefault));
		try {
			f = Float.valueOf(s);
		} catch (Exception e) {
			f = 440f;
		} finally {
			TuningFrequency = f;
		}
	}
}
