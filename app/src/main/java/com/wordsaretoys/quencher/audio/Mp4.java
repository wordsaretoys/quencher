package com.wordsaretoys.quencher.audio;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaMuxer.OutputFormat;
import android.os.Environment;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.data.Score;


/**
 * encodes a score into an MP4 audio file
 */
public class Mp4 {

	// audio sample rate in Hz
	static final int SampleRate = 44100;

	// monotically increasing notification ID source
	static int NotificationId = 0;
	
	// maximum progress for notifications
	static int MaxProgress = 100;
	
	// file extension 
	static String FileExt = ".mp4";
	
	// audio generator object
	private Audio audio;
	
	// audio encoding pump
	private Thread pump;

	// score to encode
	private Score score;
	
	// activity context
	private Context context;
	
	// notification manager
	private NotificationManager notifyManager;
	
	// notification builder
	private Notification.Builder notifyBuilder;
	
	// notification ID
	private int notifyId = NotificationId++;
	
	// last progress update
	private int lastProgress;
	
	// name of file being generated
	private String fileName;

	// reference to app resources
	private Resources res;
	
	/**
	 * ctor
	 * @param c activity context
	 */
	public Mp4(Context c) {
		context = c;
		res = context.getResources();
	}
	
	/**
	 * start encoding
	 */
	public void create(Score s) {
		score = s;
		audio = new Audio(SampleRate);
		audio.setLatency(1);
		audio.play(score, 0);
		pump = new Thread(new Pump());
		pump.start();

		File path = 
				Environment.getExternalStoragePublicDirectory(
						Environment.DIRECTORY_MUSIC);
		fileName = path.getAbsolutePath() + "/" + score.getName() + FileExt;
		
		notifyManager = (NotificationManager) 
				context.getSystemService(Context.NOTIFICATION_SERVICE);
		notifyBuilder = new Notification.Builder(context);
		
		notifyBuilder.setContentTitle(res.getString(R.string.app_name));
		notifyBuilder.setContentText(
				String.format(
						res.getString(R.string.mp4Encoding), fileName));
		notifyBuilder.setSmallIcon(android.R.drawable.ic_media_play);
		notifyBuilder.setProgress(MaxProgress, 0, false);
		
		notifyManager.notify(notifyId, notifyBuilder.build());
	}
	
	/**
	 * audio encoding thread pump
	 */
	class Pump implements Runnable {

		@Override
		public void run() {

			// create objects to encode mp4 AAC audio
			MediaCodec encoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
			
			MediaFormat format = MediaFormat.createAudioFormat("audio/mp4a-latm", SampleRate, 2);
			format.setInteger(MediaFormat.KEY_BIT_RATE, 64000); // 64Kb/s
			format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE);
			
			encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
			encoder.start();
			
			InputHandler input = new InputHandler(encoder);
			OutputHandler output = new OutputHandler(encoder);
			
			while (!output.isFinished()) {

				if (!input.isFinished()) {
					input.run();
				}
				
				try {
					output.run();
				} catch (Exception e) {
					e.printStackTrace();
					Notifier.INSTANCE.send(Notifier.Mp4WriteFailed);
					notifyManager.cancel(notifyId);
					return;
				}
			}
		}
	}

	/**
	 * input buffer handling class
	 */
	class InputHandler {

		static final int Timeout = 10000;
		static final long Microseconds = 1000 * 1000;
		
		MediaCodec encoder;
		ByteBuffer[] buffers;
		
		long time;
		boolean finished;
		short[] source;
		int sourceIndex;
		
		public InputHandler(MediaCodec c) {
			encoder = c;
			buffers = encoder.getInputBuffers();
			getNextSource();
		}

		public boolean isFinished() {
			return finished;
		}
		
		void getNextSource() {
			time = (long)(audio.getElapsedTime() * Microseconds);
			source = audio.generateNextBuffer();
			sourceIndex = 0;
			finished = !(audio.isPlaying() || audio.isCalling());
			
			if (!finished) {
				int prog = (int)(MaxProgress * audio.getElapsedTime() / audio.getScoreTime());
				if (prog > lastProgress) {
					notifyBuilder.setProgress(MaxProgress, prog, false);
					notifyManager.notify(notifyId, notifyBuilder.build());
					lastProgress = prog;
				}
			} else {
				// switch to indefinite progress as we don't know 
				// how long the muxer will take to write it all
				notifyBuilder.setProgress(0, 0, true);
				notifyBuilder.setContentText(
						String.format(
								res.getString(R.string.mp4Writing), fileName));
				notifyManager.notify(notifyId, notifyBuilder.build());
			}
		}
		
		public void run() {
			int index = encoder.dequeueInputBuffer(Timeout);
			if (index >= 0) {

				ByteBuffer ib = buffers[index];
				ib.clear();
				
				while(ib.position() < ib.limit()) {
					ib.putShort(source[sourceIndex++]);
					if (sourceIndex >= source.length) {
						getNextSource();
					}
				}

				int flags = finished ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0;
				encoder.queueInputBuffer(index, 0, ib.limit(), time, flags);
			}
		}
		
	}
	
	/**
	 * output buffer handling class
	 */
	@TargetApi(18)
	class OutputHandler {
		
		static final int Timeout = 10000;
		
		MediaCodec encoder;
		ByteBuffer[] buffers;
		BufferInfo info;
		
		MediaMuxer muxer;
		int trackIndex;
		
		boolean finished;
		
		public OutputHandler(MediaCodec c) {
			encoder = c;
			buffers = encoder.getOutputBuffers();
			info = new BufferInfo();
		}
		
		private void createMuxer() throws IOException {
			muxer = new MediaMuxer(fileName, OutputFormat.MUXER_OUTPUT_MPEG_4);
			trackIndex = muxer.addTrack(encoder.getOutputFormat());
			muxer.start();
		}
		
		public boolean isFinished() {
			return finished;
		}

		public void run() throws IOException {
			int index = encoder.dequeueOutputBuffer(info, Timeout);
			if (index >= 0) {
				
				if (muxer == null) {
					createMuxer();
				}
				
				ByteBuffer ob = buffers[index];
				muxer.writeSampleData(trackIndex, ob, info);
				ob.clear();
				
				encoder.releaseOutputBuffer(index, false);
				
				if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					finished = true;
					
					encoder.stop();
					encoder.release();
					encoder = null;
					
					muxer.stop();
					muxer.release();
					muxer = null;
					
					notifyBuilder.setProgress(0, 0, false);
					notifyBuilder.setContentText(
							String.format(
									res.getString(R.string.mp4Complete), fileName));
					
					Intent musicIntent = 
							Intent.makeMainSelectorActivity(
									Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC);
					PendingIntent launchPlayerIntent = 
							PendingIntent.getActivity(context, 0, 
									musicIntent, Intent.FLAG_ACTIVITY_NEW_TASK);
					notifyBuilder.setContentIntent(launchPlayerIntent);
					
					notifyManager.notify(notifyId, notifyBuilder.build());
				}
				
			} else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				buffers = encoder.getOutputBuffers();
			} else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				createMuxer();
			}
		}
	}
	
}
