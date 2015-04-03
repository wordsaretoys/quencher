package com.wordsaretoys.quencher.common;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.view.View;
import android.widget.Toast;

import com.wordsaretoys.quencher.R;

public class SettingsActivity extends Activity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    View view = getLayoutInflater().inflate(R.layout.settings_main, null);
	    setContentView(view);
	    getActionBar().setTitle(R.string.prefsTitle);
	}
	
	public static class SettingsFragment extends PreferenceFragment {

		public SettingsFragment() {}
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences);
			
			Resources res = getResources();
			// set up range checks for preferences
			Preference tuningReference = findPreference("pref_tuning_reference");
			tuningReference.setOnPreferenceChangeListener(
					new RangeCheckListener(
							261.63f, 523.25f, 
							res.getString(R.string.prefsTuningReferenceRange)));

			Preference tuningFrequency = findPreference("pref_tuning_frequency");
			tuningFrequency.setOnPreferenceChangeListener(
					new RangeCheckListener(
							220, 880, 
							res.getString(R.string.prefsTuningFrequencyRange)));
			
			
			Preference audioLatency = findPreference("pref_audio_latency");
			audioLatency.setOnPreferenceChangeListener(
					new RangeCheckListener(
							1, 1000, 
							res.getString(R.string.prefsAudioLatencyRange)));
		}
		
		class RangeCheckListener implements OnPreferenceChangeListener {

			String errMsg;
			float lower;
			float upper;
			
			/**
			 * ctor
			 * 
			 * @param l lower bound
			 * @param h upper bound
			 * @param msg error message if range check fails
			 */
			public RangeCheckListener(float l, float h, String msg) {
				lower = l;
				upper = h;
				errMsg = msg;
			}
			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				float f = -1;
				try {
					f = Float.valueOf( (String) newValue);
				} catch (Exception e) {
					f = -1;
				}
				if (f < lower || f > upper) {
					Toast.makeText(getActivity(), errMsg, Toast.LENGTH_LONG).show();
					return false;
				}
				return true;
			}
			
		}
		
	}
	
}
