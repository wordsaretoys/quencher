package com.wordsaretoys.quencher.audio;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;

import android.os.Environment;

import com.wordsaretoys.quencher.data.Note;
import com.wordsaretoys.quencher.data.Score;
import com.wordsaretoys.quencher.data.Track;
import com.wordsaretoys.quencher.data.Voice;

/**
 * maintains methods and state for generating
 * a stream of raw stereo PCM data from a score
 */

public class Audio {

	// log tag
	final String TAG = "Audio";

	// length of buffer in frames
	private int bufferLength;
	
	// synthesizer pool
	private ArrayList<Synth> synths;
	
	// playback position within each track
	private int[] notePosition;
	
	// position of last note within each track
	private int[] noteEnding;
	
	// elapsed time during playback
	private float time;
	
	// total score time
	// NOTE: will NOT include fade time for voices
	private float scoreTime;

	// sampling rate in Hz
	private int sampleRate;
	
	// time period covered by staging buffer
	private float stagePeriod;
	
	// audio staging buffer
	private float[] stager;
	
	// audio hardware buffer
	private short[] buffer;
	
	// indicates that score playback is in progress
	private boolean playing;
	
	// indicates that voices are currently active
	private boolean calling;
	
	// score currently undergoing playback
	private Score score;
	
	// signal dumping stuff
	private final boolean DUMP = false;
	private StringBuilder sampleData;
	private boolean dumping = false;
	
	/**
	 * ctor
	 * @param s sampling rate in Hz
	 */
	public Audio(int s) {
		sampleRate = s; 
		synths = new ArrayList<Synth>();
		
		// insure static waveform data exists
		Synth.makeWaves();

		// if we're running a signal dump, allocate a buffer
		if (DUMP) {
			sampleData = new StringBuilder();			
		}
	}

	/**
	 * generate buffers from specified audio latency
	 * @param s audio latency in decimal seconds
	 */
	public void setLatency(float l) {
		// buffer length must be even for sample interleaving
		bufferLength = 2 * ((int)(sampleRate * l) / 2);
		// create staging and audio buffers
		buffer = new short[bufferLength];
		stager = new float[bufferLength];
		// v1.02 fixed incorrect buffer timing
		// must account for 2 channels in buffer size
		stagePeriod = 0.5f * (float) bufferLength / (float) sampleRate;
	}
	
	/**
	 * play a single note
	 */
	public void play(Voice voice, float freq, float loud, float chan) {
		addSynth(voice, 0, freq, loud, chan);
	}
	
	/**
	 * play a score from a given starting point
	 * @param score score to play
	 * @param start starting beat index
	 */
	public void play(Score score, int start) {
		playing = true;
		this.score = score;
		notePosition = new int[score.getTrackCount()];
		for (int i = 0; i < notePosition.length; i++) {
			Track track = score.getTrack(i);
			notePosition[i] = (int)((float) start / (float) track.getTiming());
		}
		noteEnding = null;
		time = 60f * (float) start / (float) score.getTempo();
		onPlay();
		if (DUMP) {
			sampleData.setLength(0);
		}
	}
	
	/**
	 * stop processing the score
	 * active voices will be allowed to play out
	 */
	public void stop() {
		playing = false;
		onStop();
		// if no synths were ever active
		// or none are currently active
		if (synths.size() == 0 || !calling) {
			cleanup();
		}
	}

	/**
	 * return score playing status
	 * @return true if a score is playing 
	 */
	public boolean isPlaying() {
		return playing;
	}

	/**
	 * return voice activity status
	 * @return true if a voice is active 
	 */
	public boolean isCalling() {
		return calling;
	}

	/**
	 * get size of audio buffer
	 * @return audio buffer size in frames
	 */
	public int getBufferLength() {
		return bufferLength;
	}
	
	/**
	 * get elapsed playback time
	 * @return time in decimal seconds
	 */
	public float getElapsedTime() {
		return time;
	}

	/**
	 * get total score time 
	 * (does not include voice fade time)
	 * 
	 * @return total score time
	 */
	public float getScoreTime() {
		return scoreTime;
	}
	
	/**
	 * generate the next hardware audio buffer
	 */
	public short[] generateNextBuffer() {
		if (DUMP) {
			// if we're not dumping and voices are active
			if (!dumping && (playing || calling)) {
				// start dumping
				startDumping();
			}
			
			// if we are dumping are voices aren't active
			if (dumping && !(playing || calling)) {
				// dump and stop
				stopDumping();
			}
		}

		if (playing) {
			if (noteEnding == null) {
				readScore();
			}
			processScore();
		}
		stageActiveVoices();
		if (playing || calling) {
			time += stagePeriod;
		}

		return buffer;
	}
	
	/**
	 * called when a score starts playing
	 */
	protected void onPlay() {}
	
	/**
	 * called when a note is added to the active voices
	 * @param track index of track
	 * @param note index of note
	 */
	protected void onNote(int track, int note) {}

	/**
	 * called when a score stops playing
	 */
	protected void onStop() {}
	
	/**
	 * called when all voices have shut off
	 */
	protected void onVoicesOff() {}
	
	/**
	 * add a synth to the active list
	 * @param voice instrument/voice to play in
	 * @param time event start time in decimal seconds
	 * @param freq frequency in Hz
	 * @param loud relative loudness (0..1)
	 * @param chan channel panning (-1..1)
	 */
	private void addSynth(Voice voice, float time, float freq, float loud, float chan) {
		Synth synth = null;

		// look for an inactive synth object in the pool
		for (int i = 0, il = synths.size(); i < il; i++) {
			if (!synths.get(i).isActive()) {
				synth = synths.get(i);
				break;
			}
		}
		
		if (synth == null) {
			synth = new Synth(sampleRate);
			synths.add(synth);
		}
		
		// prepare the synth object and make active
		synth.prepare(voice, time, freq, loud, chan);
	}
	
	/**
	 * scan the score to find the ending note of each track
	 * need to do this once before playing any score
	 */
	private void readScore() {
		int tl = score.getTrackCount();
		noteEnding = new int[tl];
		for (int t = 0; t < tl; t++) {
			Track track = score.getTrack(t);
			int lastN = 0;
			int nl = track.getNoteCount();
			for (int n = 0; n < nl; n++) {
				Note note = track.getNoteAt(n);
				lastN = Math.max(lastN, note.getIndex());
			}
			noteEnding[t] = lastN;
		}
		// find the longest timed track
		float scoreBeats = 0;
		for (int t = 0; t < tl; t++) {
			float b = noteEnding[t] * score.getTrack(t).getTiming();
			scoreBeats = Math.max(scoreBeats, b);
		}
		scoreTime = 60f * scoreBeats / (float) score.getTempo();
	}
	
	/**
	 * copy the next set of notes to the active list
	 */
	private void processScore() {
		// we're processing notes that occur between 
		// current time and next staging time
		float nextTime = time + stagePeriod;
		boolean scoreComplete = true;

		// for each track in the score
		for (int t = 0, tl = score.getTrackCount(); t < tl; t++) {
			
			// if any notes remain in the track
			if (notePosition[t] <= noteEnding[t]) {
				Track track = score.getTrack(t);
				
				// look ahead to see if we can activate any notes
				while (track.positionToTime(notePosition[t]) < nextTime) {
					Note note = track.getNote(notePosition[t]);
					if (note != null && !track.isMuted()) {
						addSynth(track.getVoice(), 
								note.getTime(), 
								note.getFrequency(), 
								note.getVolume(), 
								note.getPan());
						onNote(t, note.getIndex());
					}
					notePosition[t]++;
				}
				scoreComplete = false;
			}
		}

		// if we're all done with the score
		if (scoreComplete) {
			stop();
		}
	}

	private void cleanup() {
		// dispose of all synths
		synths.clear();
		// signal the event
		onVoicesOff();
	}
	
	/**
	 * generate samples from the active list
	 */
	private void stageActiveVoices() {
		boolean active = false;
		// process the list
		for (int i = synths.size() - 1; i >= 0; i--) {
			Synth s = synths.get(i);
			// if the voice is active
			if (s.isActive()) {
				// if this is the first use of the buffer, wipe it
				if (!active) {
					Arrays.fill(stager, 0);
				}
				// index is how far into the buffer the voice starts up
				float dt = s.getStartTime() - time;
				int index = dt > 0 ? (int)(sampleRate * dt) : 0;
				// add the generated sample to the staging buffer
				s.sample(stager, index, stager.length);
				// flag it
				active = true;
			}
		}
		
		// if there's anything to mix
		if (active) {
			// headroom mix from staging buffer to audio buffer
			for (int i = 0, il = buffer.length; i < il; i++) {
				float b = stager[i];
				if (b <= -1.25f)
				{
				    b = -0.987654f;
				}
				else if (b >= 1.25f)
				{
				    b = 0.987654f;
				}
				else
				{
				    b = 1.1f * b - 0.2f * b * b * b;
				}
				buffer[i] = (short)(32767 * b);
				// only dump one channel
				if (dumping && ((i & 1) == 0)) {
					sampleData.append(buffer[i]).append("\n");
				}
			}
		}
		
		// if all voices have flipped to off
		if (calling && !active) {
			// zero out the buffer
			Arrays.fill(buffer, (short) 0);
			// if we're no longer playing a score
			if (!playing) {
				cleanup();
			}
		}
		
		calling = active;
	}
	
	/**
	 * start writing audio frames to log
	 */
	private void startDumping() {
		dumping = true;
		sampleData.setLength(0);
	}
	
	/**
	 * stop writing audio frames to log
	 * and generate external log file
	 */
	private void stopDumping() {
		dumping = false;

		File sharedDir = Environment.getExternalStorageDirectory();
		File dir = new File(sharedDir, "Android/data/com.wordsaretoys.quencher");
		if (!dir.isDirectory()) {
			dir.mkdir();
		}
		File file = new File(dir, "samples.txt");
		try {
			FileWriter fw = new FileWriter(file);
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(sampleData.toString());
			bw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
