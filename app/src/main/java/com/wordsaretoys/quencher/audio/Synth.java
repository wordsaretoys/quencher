package com.wordsaretoys.quencher.audio;

import com.wordsaretoys.quencher.data.Stage;
import com.wordsaretoys.quencher.data.Stage.Type;
import com.wordsaretoys.quencher.data.Voice;

/**
 * implements an audio synthesizer that plays
 * a single note in a single instrument voice
 */
public class Synth {

	// base wave buffers
	private static int SineLength = 8192;
	private static int SineModulus = SineLength - 1;
	private static float[] Sine = new float[SineLength];

	private static int CosineLength = 8192;
	private static int CosineModulus = CosineLength - 1;
	private static float[] Cosine = new float[CosineLength];
	
	private static int SawtoothLength = 8192;
	private static float[] Sawtooth = new float[SawtoothLength];

	private static int NoiseLength = 65536;
	private static float[] Noise = new float[NoiseLength];

	private static float[] Square = { 1, -1 };

	// dummy stage object for mixing silence
	private static Stage silence;
	
	// vibrato level (as percentage of note frequency)
	private static float VibratoLevel = 0.025f;

	// audio sampling period (sec/sample)
	private float SamplePeriod;

	// reference to voice object
	private Voice voice;

	// index of current stage
	private int stage;
	
	// interpolation factor/time in stage (0..1)
	private float time;

	// rate of change for stage time
	private float rate;
	
	// wave buffers to interpolate between at current stage
	private float[] wave0, wave1;
	
	// wave volume levels at current stage
	private float level0, level1;
	
	// base rate for waveform sampling
	private float baseRate;
	
	// rate and time for sampling wave 0 at current stage
	private float waveRate0, waveTime0;
	
	// rate and time for sampling wave 1 at current stage
	private float waveRate1, waveTime1;

	// modulus for waveform sampling at current stage
	private int waveMod0, waveMod1;
	
	// rate and time for tremolo effect
	private float tremoRate, tremoTime;

	// rate and time for vibrato effect
	private float vibraRate, vibraTime;
	
	// maximum loudness of voice in each channel
	private float leftOut, rightOut;

	// time at which synth becomes active
	private float timestamp;
	
	// active state
	private boolean active;

	// normalization factor for level
	private float levelFactor;
	
	/**
	 * generate base wave buffers
	 */
	public static void makeWaves() {
		for (int i = 0; i < SineLength; i++) {
			float t = (float) i / (float) SineLength;
			Sine[i] = (float) Math.sin(2 * Math.PI * t);
		}

		for (int i = 0; i < CosineLength; i++) {
			float t = (float) i / (float) CosineLength;
			Cosine[i] = (float) Math.cos(2 * Math.PI * t);
		}
		
		float r = 0;
		for (int i = 0; i < NoiseLength; i++) {
			Noise[i] = (float) Math.sin(r);
			r += (2 * Math.random() - 1);
		}
		
/*		float m = 1, r = 0.01f, y0 = 0, y1 = 0;
		for (int i = 0; i < NoiseLength; i++) {
			if (m >= 1) {
				y0 = y1;
				y1 = (float)(2f * Math.random() - 1f);
				m = 0;
			}
			float m2 = (float)(1f - Math.cos(m * Math.PI)) * 0.5f;
			Noise[i] = (1 - m2) * y0 + m2 * y1;
			m += r;
		}
*/		
		for (int i = 0; i < SawtoothLength; i++) {
			float t = (float) i / (float) SawtoothLength;
			if (t < 0.25f) {
				Sawtooth[i] = t * 4;
			} else if (t >= 0.25f && t < 0.5f) {
				float mu = (t - 0.25f) * 4;
				Sawtooth[i] = 1 - mu;
			} else if (t >= 0.5f && t < 0.75f) {
				float mu = (t - 0.5f) * 4;
				Sawtooth[i] = -mu;
			} else {
				float mu = (t - 0.75f) * 4;
				Sawtooth[i] = mu - 1;
			}
		}
		
		silence = new Stage(null);
	}

	/**
	 * get sinewave buffer
	 * @return sine wave buffer
	 */
	public static float[] getSineWave() {
		return Sine;
	}
	
	/**
	 * get noise wave buffer
	 * @return noise wave buffer
	 */
	public static float[] getNoiseWave() {
		return Noise;
	}
	
	/**
	 * get square wave buffer
	 * @return square wave buffer
	 */
	public static float[] getSquareWave() {
		return Square;
	}
	
	/**
	 * get sawtooth wave buffer
	 * @return sawtooth wave buffer
	 */
	public static float[] getSawtoothWave() {
		return Sawtooth;
	}
	
	/**
	 * transforms a volume level (0..1) from  
	 * the linear to the polynomial domain.
	 * 
	 * I used to use an exponential function
	 * but didn't like the response at all--
	 * not nearly loud enough on the high end.
	 * 
	 * @param l volume level from UI
	 * @return transformed volume
	 */
	private static float getAdjustedLevel(float l) {
		return l * l * l * l;
	}
	
	/**
	 * transforms a noise pitch (0..1) from the
	 * linear to the exponential domain.
	 * 
	 * @param n noise pitch from UI
	 * @return transformed noise rate factor
	 */
	private static float getAdjustedNoise(float n) {
		return  (float) Math.pow(10, 3 * n - 4.5);
	}
	
	/**
	 * ctor
	 * @param s sample rate in Hz
	 */
	public Synth(int s) {
		SamplePeriod = 1f / (float) s;
	}
	
	/**
	 * prepare the synth for use by the audio engine
	 * @param voice object with voice data/samples
	 * @param start time in decimal seconds of synth start
	 * @param freq frequency in Hz
	 * @param loud relative loudness (0..1)
	 * @param chan channel pan (-1..1)
	 */
	public void prepare(Voice voice, float start, float freq, float loud, float chan) {
		
		this.voice = voice;

		baseRate = SamplePeriod * freq;
		waveTime0 = waveTime1 = 0;

		tremoRate = CosineLength * SamplePeriod * voice.getTremolo();
		tremoTime = 0;
		
		vibraRate = SineLength * SamplePeriod * voice.getVibrato();
		vibraTime = 0;
		
		time = 1;
		stage = 0;
		
		timestamp = start;

		leftOut = getAdjustedLevel(loud) * (1 - chan) * 0.5f;
		rightOut = getAdjustedLevel(loud) * (1 + chan) * 0.5f;
		
		float lf = 0;
		for (int i = 0; i < voice.getStageCount(); i++) {
			lf = Math.max(lf, voice.getStage(i).getLevel());
		}
		levelFactor = 1f / lf;
	
		active = true;
	}
	
	/**
	 * get activity state of synthesizer
	 * @return true if synth isn't playing
	 */
	public boolean isActive() {
		return active;
	}
	
	/**
	 * get start time of synthesizer event
	 * @return start time in decimal seconds
	 */
	public float getStartTime() {
		return timestamp;
	}
	
	/**
	 * generate the next section of the voice to a buffer
	 * buffer will be summed, not overwritten, so wipe it
	 * 
	 * @param buffer staging buffer
	 * @param index starting index within staging buffer
	 * @param length size of staging buffer
	 */
	public void sample(float[] buffer, int index, int length) {
		// index must be even so we can interleave stereo samples
		index = (index / 2) * 2;
		// while we're not at the end of the buffer, and still making noise
		while (index < length && active) {

			// if we're at the end of the current stage
			if (time >= 1) {

				int toStage = stage + 1;
				
				// select the stages to mix between
				Stage stage0 = stage < voice.getStageCount() ? 
						voice.getStage(stage) : silence;
				Stage stage1 = toStage < voice.getStageCount() ? 
						voice.getStage(toStage) : silence;
				
				// reset time and rate
				rate = SamplePeriod / stage0.getTime();
				time = 0;
				
				// set waveform/level to interpolate from
				wave0 = stage0.getWaveBuffer();
				level0 = getAdjustedLevel(levelFactor * stage0.getLevel());
				waveRate0 = wave0.length * baseRate;
				if (stage0.getType() == Type.Noise) {
					waveRate0 *= getAdjustedNoise(stage0.getNoiseFactor());
				}
				waveMod0 = wave0.length - 1;
				waveTime0 = waveTime1;
				
				// set waveform/level to interpolate to
				wave1 = stage1.getWaveBuffer();
				level1 = getAdjustedLevel(levelFactor * stage1.getLevel());
				waveRate1 = wave1.length * baseRate;
				if (stage1.getType() == Type.Noise) {
					waveRate1 *= getAdjustedNoise(stage1.getNoiseFactor());
				}
				waveMod1 = wave1.length - 1;
				waveTime1 = 0;
				
				// advance to next stage
				stage = toStage;
				// if both stages are equal, they're both silence, and we're done
				active = (stage0 != stage1);
			}

			// get the vibrato function
			float v = VibratoLevel * Sine[(int)(vibraTime) & SineModulus];
			vibraTime += vibraRate;
			
			// interpolate the waveform value
			float s0 = level0 * wave0[(int)(waveTime0) & waveMod0];
			float s1 = level1 * wave1[(int)(waveTime1) & waveMod1];
			float s = (1 - time) * s0 + time * s1;
			waveTime0 += (waveRate0 + v * waveRate0);
			waveTime1 += (waveRate1 + v * waveRate1);

			// get the tremolo function
			float t = Cosine[(int)(tremoTime) & CosineModulus];
			tremoTime += tremoRate;
			
			// final value
			float w = t * s;
			time += rate;

			buffer[index++] += w * leftOut;
			buffer[index++] += w * rightOut;
		}
	}
}
