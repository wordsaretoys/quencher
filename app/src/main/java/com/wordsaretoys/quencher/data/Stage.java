package com.wordsaretoys.quencher.data;

import java.util.Arrays;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.wordsaretoys.quencher.audio.Synth;
import com.wordsaretoys.quencher.common.Storable;
import com.wordsaretoys.quencher.common.Storage;

public class Stage extends Storable {

	public static final int Harmonics = 16;
	public static final int BufferSize = 4096;
	public static final int BufferModulus = BufferSize - 1;
	
	public enum Type {
		Sine, Square, Sawtooth, Noise
	}
	
	/*
	 * data labels for database representation
	 */
	public static final String L_TABLE = "stage";

	public static final String L_VOICE = "voice";
	public static final String L_INDEX = "xedni";
	public static final String L_TIME = "time";
	public static final String L_LEVEL = "level";
	public static final String L_TYPE = "type";
	public static final String L_FREQ = "freq";		// base field name
	public static final String L_NOISE = "noise";
	
	public static final String[] L_FIELDS = {
		L_ID, L_VOICE, L_INDEX, 
		L_TIME, L_LEVEL, L_TYPE,
		L_FREQ + "1", L_FREQ + "2", L_FREQ + "3", L_FREQ + "4",
		L_FREQ + "5", L_FREQ + "6", L_FREQ + "7", L_FREQ + "8",
		L_FREQ + "9", L_FREQ + "10", L_FREQ + "11", L_FREQ + "12",
		L_FREQ + "13", L_FREQ + "14", L_FREQ + "15", L_FREQ + "16",
		L_NOISE
	};

	public static final String L_ORDER = L_INDEX;

	/*
	 * data variables
	 */

	// index within voice
	private int index;
	
	// voice containing this stage
	private Voice voice;

	// duration in decimal seconds
	private float time;
	
	// ending amplitude level
	private float level;
	
	// base wave type
	private Type type;
	
	// harmonic source data
	private float[] source = new float[Harmonics];
	
	// noise factor, determines noise frequency/smoothness
	private float noise;
	
	// generated waveform buffer
	private float[] buffer;
	
	/**
	 * ctor, sets default values
	 * @param v voice object
	 */
	public Stage(Voice v) {
		super();
		voice = v;
		type = Type.Sine;
	}
	
	/**
	 * copy data from existing stage
	 * @param s stage to copy from
	 */
	public synchronized void copy(Stage s) {
		index = s.index;
		time = s.time;
		level = s.level;
		type = s.type;
		source = Arrays.copyOf(s.source, s.source.length);
		noise = s.noise;
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
	 * get stage duration in decimal seconds
	 * @return stage duration
	 */
	public float getTime() {
		return time;
	}
	
	/**
	 * set stage duration in decimal seconds
	 * @param t stage duration
	 */
	public synchronized void setTime(float t) {
		time = t;
		onChange();
	}
	
	/**
	 * get peak amplitude level (0..1)
	 * @return peak amplitiude level
	 */
	public float getLevel() {
		return level;
	}
	
	/**
	 * set peak amplitude level (0..1)
	 * @param l peak amplitude level
	 */
	public synchronized void setLevel(float l) {
		level = l;
		onChange();
	}
	
	/**
	 * get the base wave type
	 * @return base wave type
	 */
	public Type getType() {
		return type;
	}
	
	/**
	 * set the base wave type
	 * @param t base wave type
	 */
	public synchronized void setType(Type t) {
		type = t;
		updateWaveBuffer();
		onChange();
	}
	
	/**
	 * get the amplitude of a specified harmonic
	 * @param h harmonic number (0..16)
	 * @return harmonic amplitude (0..1)
	 */
	public float getHarmonic(int h) {
		return source[h];
	}
	
	/**
	 * set the amplitude of a specified harmonic
	 * @param h harmonic number (0..16)
	 * @param a harmonic amplitude (0..1)
	 */
	public synchronized void setHarmonic(int h, float a) {
		source[h] = a;
		updateWaveBuffer();
		onChange();
	}
	
	/**
	 * set amplitudes for all harmonics
	 * @param a float array of harmonics
	 */
	public synchronized void setHarmonics(float[] a) {
		for (int i = 0; i < a.length; i++) {
			source[i] = a[i];
		}
		updateWaveBuffer();
		onChange();
	}
	
	/**
	 * get noise factor
	 * @return noise factor
	 */
	public float getNoiseFactor() {
		return noise;
	}

	/**
	 * set noise factor
	 * @param f noise factor
	 */
	public synchronized void setNoiseFactor(float f) {
		noise = f;
		onChange();
	}
	
	/**
	 * get waveform buffer
	 * @return waveform buffer
	 */
	public float[] getWaveBuffer() {
		// never return a null buffer
		if (buffer == null) {
			updateWaveBuffer();
		}
		return buffer;
	}
	
	/**
	 * assigns or generates wave buffer based on source type
	 */
	public void updateWaveBuffer() {
		switch (type) {
		case Sine:
			generateCustomWaveform();
			break;
		case Square:
			buffer = Synth.getSquareWave();
			break;
		case Sawtooth:
			buffer = Synth.getSawtoothWave();
			break;
		case Noise:
			buffer = Synth.getNoiseWave();
			break;
		}
	}
	
	/**
	 * generates a custom waveform via Fourier synthesis
	 * using the harmonic table data and a sampled sine
	 */
	private void generateCustomWaveform() {
		float[] base = Synth.getSineWave();
		int mod = base.length - 1;
		float max = 0;

		buffer = new float[BufferSize];
		
		for (int h = 0; h < 16; h++) {
			float a = source[h];
			if (a > 0) {
				float f = (float)(1 + h);
				for (int j = 0; j < buffer.length; j++) {
					float t = (float) j / (float) buffer.length;
					int s = (int)((f * t) * base.length);
					buffer[j] += a * base[s & mod];
					max = Math.max(Math.abs(buffer[j]), max);
				}
			}
		}

		float maxover = max > 0 ? 1f / max : 0;
		for (int i = 0; i < buffer.length; i++) {
			buffer[i] = maxover * buffer[i];
		}
		
	}
	
	/**
	 * get voice containing this stage
	 * @return voice object
	 */
	public Voice getVoice() {
		return voice;
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
		time = tc.getFloat(tc.getColumnIndex(L_TIME));
		level = tc.getFloat(tc.getColumnIndex(L_LEVEL));
		int typeOrd = tc.getInt(tc.getColumnIndex(L_TYPE)); 
		type = Type.values()[typeOrd];
		// source data is spread over multiple fields
		int col = tc.getColumnIndex(L_FREQ + "1");
		for (int i = 0; i < Harmonics; i++) {
			source[i] = tc.getFloat(col + i);
		}
		noise = tc.getFloat(tc.getColumnIndex(L_NOISE));
	}

	@Override
	public synchronized void writeFields(ContentValues values) {
		super.writeFields(values);
		values.put(L_VOICE, voice.getId());
		values.put(L_INDEX, index);
		values.put(L_TIME, time);
		values.put(L_LEVEL, level);
		values.put(L_TYPE, type.ordinal());
		// source data spread over multiple fields
		for (int i = 0; i < Harmonics; i++) {
			values.put(L_FREQ + (i + 1), source[i]);
		}
		values.put(L_NOISE, noise);
	}
	
	@Override
	protected void onChange() {
		super.onChange();
		// parent onChange fires VoiceChange event
		voice.onChange();
	}

	/**
	 * select all stages for a given voice
	 * @param id database id to filter by
	 * @return cursor containing selected rows
	 */
	public static Cursor selectByVoice(long id) {
		return Storage.INSTANCE.select(
				L_TABLE, L_FIELDS, L_ORDER, L_VOICE, id);
	}
	
	/**
	 * deletes all stage records from the table for a given voice
	 * @param db writeable database in transaction
	 * @param id id database id
	 */
	public static void deleteByVoice(SQLiteDatabase db, long id) {
		Storage.INSTANCE.delete(db, L_TABLE, L_VOICE, id);
	}
}
