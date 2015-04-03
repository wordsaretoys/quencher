package com.wordsaretoys.quencher.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.preference.PreferenceManager;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.common.Needle;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.data.Scale;
import com.wordsaretoys.quencher.data.Score;
import com.wordsaretoys.quencher.data.Voice;

/**
 * streams output of audio generator to audio tracks
 */

public enum Engine {
	
	// singleton instance
	INSTANCE;

	// log tag
	final String TAG = "Engine";

	// application context
	Context context;
	
	// sample rate
	int sampleRate;
	
	// notification interval
	float interval;
	
	// time marker
	float lastTime;
	
	// audio generator object
	Audio audio;

	// audio pump thread object
	AudioPump audioPump;
	
	/**
	 * audio pump class
	 */
	class AudioPump extends Needle {

		public AudioPump() {
			super("audio pump", 1);
		}

		@Override
		public void run() {
			AudioTrack track = null;
			
			// load any audio/synth preferences
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			Resources res = context.getResources();
			Scale.loadTuning(prefs, res);
			
			// create audio buffers based on preferred latency
			try {
				String s = prefs.getString(
						"pref_audio_latency", 
						res.getString(R.string.prefsAudioLatencyDefault));
				float latency = Float.valueOf(s) * 0.001f;
				audio.setLatency(latency);
			} catch (Exception e) {
				e.printStackTrace();
				Notifier.INSTANCE.send(Notifier.AudioInitFailed);
				return;
			}
			
			// create and start streaming audio track
			try {
				track = new AudioTrack(
						AudioManager.STREAM_MUSIC,
						sampleRate,
						AudioFormat.CHANNEL_OUT_STEREO,
						AudioFormat.ENCODING_PCM_16BIT,
						audio.getBufferLength() * 2, // length in bytes
						AudioTrack.MODE_STREAM);
				
				if (track.getState() != AudioTrack.STATE_INITIALIZED) {
					throw new RuntimeException("Couldn't initialize AudioTrack object");
				}
				
				track.setStereoVolume(1, 1);
				track.play();
				
			} catch (Exception e) {
				e.printStackTrace();
				Notifier.INSTANCE.send(Notifier.AudioInitFailed);
				return;
			}
			
			while (inPump()) {
				
				short[] buffer = null;
				
				// buffer generation synchronized to play/stop methods
				synchronized(INSTANCE) {
					buffer = audio.generateNextBuffer();
				}
				// always pass the audio buffer to the track
				track.write(buffer, 0, buffer.length);
				// send out periodic notifications if in playback
				if (audio.isPlaying() || audio.isCalling()) {
					if (audio.getElapsedTime() - lastTime >= interval) {
						int ms = (int)(audio.getElapsedTime() * 1000);
						Notifier.INSTANCE.send(Notifier.AudioMarker, ms);
						lastTime = audio.getElapsedTime();
					}
				}
			}
		}
		
	}
	
	/**
	 * ctor, creates audio object
	 */
	private Engine() {
		// create audio generation object
		// operate at native sampling rate for max performance
		sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
		audio = new Audio(sampleRate) {
			protected void onPlay() {
				Notifier.INSTANCE.send(Notifier.AudioPlaying);
			}
			protected void onStop() {
				Notifier.INSTANCE.send(Notifier.AudioStopped);
			}
			protected void onVoicesOff() {
				Notifier.INSTANCE.send(Notifier.AudioOff);
			}
		};
	}
	
	/**
	 * create the engine instance
	 * @param context application/activity context
	 */
	public void onCreate(Context context) {
		this.context = context;
		restart();
	}
	
	/**
	 * creates a new audio pump
	 */
	public synchronized void restart() {
		// tear down old audio pump, if any
		if (audioPump != null) {
			audioPump.stop();
			audioPump = null;
		}
		// create audio pump with new settings
		audioPump = new AudioPump();
		// and get it running
		audioPump.start();
		audioPump.resume();
	}
	
	/**
	 * play a single tone
	 * @param voice voice object to synthesize
	 * @param freq tone frequency in Hz
	 * @param loud loudness (0..1)
	 * @param chan channel pan (-1..1)
	 */
	public synchronized void play(Voice voice, float freq, float loud, float chan) {
		audio.play(voice, freq, loud, chan);
	}	
	
	/**
	 * play a score from a given starting point
	 * @param score score to play
	 * @param start starting beat index
	 */
	public synchronized void play(Score score, int start) {
		audio.play(score, start);
		lastTime = audio.getElapsedTime();
		interval = (1f / 8f) * 60f / (float) score.getTempo();
		Notifier.INSTANCE.send(Notifier.AudioPlaying);
	}

	/**
	 * play a score
	 */
	public void play(Score score) {
		play(score, 0);
	}
	
	/**
	 * stop processing the score
	 * active voices will be allowed to play out
	 */
	public synchronized void stop() {
		audio.stop();
	}

	/**
	 * return score playing status
	 * @return true if a score is playing 
	 */
	public boolean isPlaying() {
		return audio.isPlaying();
	}

	/**
	 * return voice activity status
	 * @return true if a voice is active 
	 */
	public boolean isCalling() {
		return audio.isCalling();
	}
}
