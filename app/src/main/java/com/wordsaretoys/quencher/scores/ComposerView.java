package com.wordsaretoys.quencher.scores;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Region.Op;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Message;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.OverScroller;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.audio.Engine;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.common.Notifier.NotificationListener;
import com.wordsaretoys.quencher.data.Note;
import com.wordsaretoys.quencher.data.Scale;
import com.wordsaretoys.quencher.data.Score;
import com.wordsaretoys.quencher.data.Track;

public class ComposerView extends View implements
	OnGestureListener, OnDoubleTapListener, NotificationListener {

	static final String TAG = "ComposerView";

	// goto position
	public enum Goto {
		Start, End, Cursor, Marker
	}
	
	// basic drawing parameters
	static final int TimeBarHeight = 64;

	static final int GridSize = 1;
	static final int GapSize = 4;
	static final int CursorSize = 8;
	static final int AnnotationSize = 24;
	static final int NoteButtonSize = 64;
	static final int NoteButtonPad = 4;
	
	// derived drawing parameters
	static final int TimeLineTop = TimeBarHeight / 2;
	
	static final int AnnotationTop = GapSize;
	static final int NoteTop = AnnotationTop + AnnotationSize + GapSize;
	static final int CursorTop = NoteTop + NoteButtonSize + 2 * GapSize;
	static final int TrackHeight = CursorTop + CursorSize + GapSize + GridSize;
	
	static final int NoteWidth = NoteButtonSize + 2 * GapSize + GridSize;
	static final int BeatWidth = NoteWidth;
	
	static final int NoteCenter = NoteButtonSize / 2;
	static final int NoteTextArea = NoteButtonSize - 2 * NoteButtonPad;
	
	final Resources Res = getResources();
	
	// color resources
	int colorWhite = Res.getColor(R.color.white);
	int colorBlack = Res.getColor(R.color.black);
	int colorClouds = Res.getColor(R.color.clouds);
	int colorEmerald = Res.getColor(R.color.emerald);
	int colorCarrot = Res.getColor(R.color.carrot);
	int colorSunflower = Res.getColor(R.color.sunflower);
	int colorLtGray0 = Res.getColor(R.color.ltgray0);
	int colorLtGray1 = Res.getColor(R.color.ltgray1);
	int colorLtGray2 = Res.getColor(R.color.ltgray2);
	int colorLtGray3 = Res.getColor(R.color.ltgray3);
	int colorMdGray1 = Res.getColor(R.color.mdgray1);
	int colorFaintBlu = Res.getColor(R.color.faintblu);
	int colorFaintYello = Res.getColor(R.color.faintyello);
	int colorLtBlue1 = Res.getColor(R.color.ltblue1);
	
	// score editor background color
	final int BackingColor = colorClouds;
	// note button background color
	final int ButtonBackingColor = colorLtGray1;
	// note button text color
	final int NoteButtonTextColor = colorBlack;
	// time bar background color
	final int TimeBarBackingColor = colorFaintYello;
	// time bar text color
	final int TimeBarTextColor = colorBlack;
	// note focus highlight color
	final int NoteFocusColor = colorBlack;
	// focused track color
	final int TrackFocusColor = colorFaintBlu;
	// play marker color
	final int PlayMarkerColor = colorBlack;
	// annotation text color
	final int AnnotationColor = colorBlack;
	// grid line color
	final int GridLineColor = colorLtGray2;
	// note selection color
	final int SelectionColor = colorCarrot;
	// bar note backing color
	final int BarBackingColor = colorLtGray3;
	// note playing color
	final int NotePlayingColor = colorEmerald;
	// note button pressed color
	final int NotePressedColor = colorLtBlue1;

	// string resources
	String stringMuted = Res.getString(R.string.trackMuted);
	
	// used to generate strings for display without heap allocations
	StringBuilder stringer = new StringBuilder();
	char[] charBuffer = new char[256];
	
	// gesture detectors
	GestureDetector gestureDetector;
	
	// overscroller handles fling animations and overscroll notifications
	OverScroller scroller;
	
	// paint objects for all drawing
	Paint fillBrush, lineBrush, textBrush;
	
	// beat marker bitmap
	Bitmap beatMarkerBmp;
	
	// scroll offsets
	float scrollX, scrollY;

	// last track and slot touched
	int trackTouched, slotTouched;
	
	// rect object for drawing roundrects
	RectF rect = new RectF();
	
	// indicates audio is playing back
	boolean playback;
	
	// elapsed playback time in decimal seconds
	float playbackTime;
	
	// beat scaling factor across tracks 
	float beatScale;
	
	// note undergoing press indicator flag
	boolean pressing;

	/**
	 * default ctor, required by layout inflator
	 * @param context
	 * @param attrs
	 */
	public ComposerView(Context context, AttributeSet attrs) {
		super(context, attrs);

		setBackgroundColor(BackingColor);
		
		// set up the paint objects
		fillBrush = new Paint();
		fillBrush.setStyle(Style.FILL);
		
		lineBrush = new Paint();
		lineBrush.setStyle(Style.STROKE);
		
		textBrush = new Paint();
		textBrush.setTypeface(Typeface.SANS_SERIF);
		textBrush.setAntiAlias(true);

		gestureDetector = new GestureDetector(context, this);
		gestureDetector.setOnDoubleTapListener(this);
		scroller = new OverScroller(context);

		beatMarkerBmp = BitmapFactory.decodeResource(
				getResources(), R.drawable.ic_beat_marker);
		
		playback = Engine.INSTANCE.isPlaying();
		onSettingsChanged();
	}

	/**
	 * write state to a bundle
	 */
	public void saveState(Bundle b) {
		Bundle c = new Bundle();
		c.putFloat("scrollX", scrollX);
		c.putFloat("scrollY", scrollY);
		b.putBundle("composerView", c);
	}
	
	/**
	 * load saved state from a bundle
	 */
	public void loadState(Bundle b) {
		Bundle c = b.getBundle("composerView");
		scrollX = c.getFloat("scrollX");
		scrollY = c.getFloat("scrollY");
		enforceScrollLimits();
	}

	/**
	 * react to a change in the persistent settings
	 */
	private void onSettingsChanged() {
		enforceScrollLimits();
		postInvalidate();
	}
	
	/**
	 * adjust/check drawing parameters post-rotation
	 */
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		enforceScrollLimits();
		updateBeatScale();
	}
	
	/**
	 * generates a time string in the format HH:MM:SS.SSS
	 * places result in the stringer object and the char buffer
	 * 
	 * @param t decimal time in seconds
	 */
	void generateTimeString(float t) {
		ScoreCommon.buildTimeString(t, stringer);
		if (stringer.length() > charBuffer.length) {
			charBuffer = new char[stringer.length()];
		}
		stringer.getChars(0, stringer.length(), charBuffer, 0);
	}
	
	/**
	 * generates a track annotation (scale, voice, volume)
	 * places result in stringer object and char buffer
	 * 
	 * @param track annotating track
	 */
	void buildLeftAnnotation(Track track) {
		stringer.setLength(0);
		stringer.append(track.getScale().getName());
		if (stringer.length() > 0) {
			stringer.append(" / ");
		}
		stringer.append(track.getVoice().getName());
		if (stringer.length() > charBuffer.length) {
			charBuffer = new char[stringer.length()];
		}
		stringer.getChars(0, stringer.length(), charBuffer, 0);
	}
	
	/**
	 * generates a track annotation (scale, voice, volume)
	 * places result in stringer object and char buffer
	 * 
	 * @param track annotating track
	 */
	void buildRightAnnotation(Track track) {
		stringer.setLength(0);
		if (!track.isMuted()) {
			// goofy but simple way to create a "meter"
			int vol = (int)(track.getVolume() * 10);
			for (int i = 0; i < vol; i++) {
				stringer.append("I");
			}
		} else {
			stringer.append(stringMuted);
		}
		if (stringer.length() > charBuffer.length) {
			charBuffer = new char[stringer.length()];
		}
		stringer.getChars(0, stringer.length(), charBuffer, 0);
	}

	/**
	 * check current scroll settings and clip when necessary
	 * 
	 * @return true if scroll was clipped
	 */
	private boolean enforceScrollLimits() {
		ScoreCommon common = ScoreActivity.common;
		boolean clipped = false;
		if (scrollX < 0) {
			scrollX = 0;
			clipped = true;
		}
		if (scrollY < 0) {
			scrollY = 0;
			clipped = true;
		}
		int tracks = common.getScore().getTrackCount();
		int ylimit =  (int)(tracks * TrackHeight) - getHeight() + TimeBarHeight;
		ylimit = Math.max(ylimit, 0);
		if (scrollY > ylimit) {
			scrollY = ylimit;
			clipped = true;
		}
		return clipped;
	}

	/**
	 * find the beat corresponding to the screen position
	 * corrects for scroll and track listing
	 * 
	 * @param x screen x coordinate
	 * @return decimal position of left edge of beat
	 */
	private float screenToBeat(float x) {
		return (float)(x + scrollX) / (float) (BeatWidth * beatScale);
	}
	
	/**
	 * find the screen coordinate of the specified beat
	 * corrects for scroll and track listing
	 * 
	 * @param b decimal beat
	 * @return screen x coordinate of left edge
	 */
	private float beatToScreen(float b) {
		return b * BeatWidth * beatScale - scrollX;
	}
	
	/**
	 * find the track index corresponding to the screen position
	 * corrects for scroll and time bar
	 * 
	 * @param y screen y coordinate
	 * @return track index
	 */
	private int screenToTrack(float y) {
		return (int)((y + scrollY - TimeBarHeight) / TrackHeight);
	}
	
	/**
	 * find the screen coordinate of the specified track index
	 * corrects for scroll and time bar
	 * 
	 * @param t track index
	 * @return y coordinate of top edge of track
	 */
	private float trackToScreen(int t) {
		return t * TrackHeight - scrollY + TimeBarHeight;
	}
	
	/**
	 * finds the note index corresponding to the screen position
	 * 
	 * @param x screen x coordinate
	 * @param scale track scale
	 * @return note index
	 */
	private int screenToNote(float x, float scale) {
		return (int)((x + scrollX) / (scale * beatScale * NoteWidth));
	}
	
	/**
	 * find the note index corresponding to the screen position
	 * corrects for scroll and track list
	 * 
	 * @param n note index
	 * @param scale track scale
	 * @return screen x coordinate
	 */
	private float noteToScreen(int n, float scale) {
		return n * scale * beatScale * NoteWidth - scrollX;
	}
	
	/**
	 * get the vertical center for drawing a line of text
	 * @param p paint object with valid text settings
	 * @param y0 top of drawing region
	 * @param y1 bottom of drawing region
	 * @return y-coordinate of text center
	 */
	private float midText(Paint p, float y0, float y1) {
		return (y0 + y1 - p.ascent() - p.descent()) * 0.5f;
	}

	/**
	 * get editable state of composer
	 * @return true if notes should be editable
	 */
	public boolean isEditable() {
		return !(playback || ScoreActivity.common.isSelecting());
	}

	/**
	 * scroll or jump to the specified position
	 * 
	 * @param where code for standard position
	 */
	public void goTo(Goto where) {
		scroller.forceFinished(true);
		ScoreCommon common = ScoreActivity.common;
		float x;
		switch(where) {
		case Start:
			scrollX = 0;
			break;
		case End:
			Note note = common.getLastNote();
			if (note != null) {
				x = noteToScreen(
						note.getIndex() + 1, 
						note.getTrack().getTiming());
				scrollX = Math.max(0, scrollX + x - getWidth() * 0.5f);
			} else {
				// no notes in scroll, go to start
				scrollX = 0;
			}
			break;
		case Cursor:
			x = noteToScreen(
					common.getNotePosition(), 
					common.getFocusedTrack().getTiming());
			scrollX = Math.max(0, scrollX + x - getWidth() * 0.5f);
			break;
		case Marker:
			x = beatToScreen(common.getBeatMarker());
			scrollX = Math.max(0, scrollX + x - getWidth() * 0.5f);
			break;
		}
		postInvalidate();
	}

	/**
	 * scroll or jump to specified position
	 * @param time decimal seconds to move to
	 */
	public void goTo(float time) {
		scroller.forceFinished(true);
		float b = time * (float) ScoreActivity.common.getScore().getTempo() / 60f;
		float x = beatToScreen(b);
		scrollX = Math.max(0, scrollX + x - getWidth() * 0.5f);
		postInvalidate();
	}

	/**
	 * get current X scroll as time value
	 * @return scrollX in decimal seconds
	 */
	public float getScrollTime() {
		float b = screenToBeat(0);
		return 60f * (float) b / (float) ScoreActivity.common.getScore().getTempo();
	}
	
	/**
	 * draw the custom view
	 */
	protected void onDraw(Canvas canvas) {
		
		ScoreCommon common = ScoreActivity.common;
		Score score = common.getScore();

		// find beat/track drawing limits
		int firstBeat = (int) screenToBeat(0);
		int lastBeat = (int) screenToBeat(getWidth());
		int firstTrack = screenToTrack(0);
		int lastTrack = screenToTrack(getHeight());
		int trackCount = score.getTrackCount();

		// find focused elements
		int editTrackPos = common.getTrackPosition();
		int editNotePos = common.getNotePosition();

		// draw the track highlight under everything else
		if (!playback) {
			fillBrush.setColor(TrackFocusColor);
			float focusTop = trackToScreen(editTrackPos);
			canvas.drawRect(0, focusTop, getWidth(), focusTop + TrackHeight, fillBrush);
		}
		
		// fill in the time bar 
		canvas.clipRect(0, 0, getWidth(), TimeBarHeight, Op.REPLACE);
		canvas.drawColor(TimeBarBackingColor);

		canvas.clipRect(0, 0, getWidth(), getHeight(), Op.REPLACE);
		lineBrush.setStrokeWidth(GridSize);
		textBrush.setColor(TimeBarTextColor);
		
		// for each beat
		for (int b = firstBeat - 1; b <= lastBeat; b++) {
			float x = beatToScreen(b) - GridSize;
			lineBrush.setColor(NoteFocusColor);

			// every four beats
			if (b % 4 == 0) {
				// draw the time line up to the top
				canvas.drawLine(x, 0, x, TimeBarHeight, lineBrush);
				// generate and draw the timestamp
				textBrush.setTextSize(TimeLineTop * 0.5f);
				textBrush.setTextAlign(Paint.Align.LEFT);
				float time = 60f * (float) b / (float) score.getTempo();
				generateTimeString(time);
				canvas.drawText(charBuffer, 0, stringer.length(), x, 
						midText(textBrush, 0, TimeLineTop), textBrush);
			} else {
				// only draw a time line and only half-height
				// to keep it from screwing up the timestamps
				canvas.drawLine(x, TimeLineTop, x, TimeBarHeight, lineBrush);
			}
			
			// draw beat lines
			lineBrush.setColor(GridLineColor);
			canvas.drawLine(x, TimeBarHeight, x, getHeight(), lineBrush);

			// draw the beat marker if it's present
			if (!playback && b == common.getBeatMarker()) {
				fillBrush.setColor(PlayMarkerColor);
				canvas.drawBitmap(beatMarkerBmp, 
						x - beatMarkerBmp.getWidth() * 0.5f, 
						TimeBarHeight - beatMarkerBmp.getHeight(), fillBrush);
			}
		}

		canvas.clipRect(0, TimeBarHeight, getWidth(), getHeight(), Op.REPLACE);
		textBrush.setColor(NoteButtonTextColor);
		
		// for each potential track 
		for (int t = firstTrack; t <= lastTrack; t++) {
			float top = trackToScreen(t);
			float noteTop = top + NoteTop;
			
			// draw grid line
			lineBrush.setStrokeWidth(GridSize);
			lineBrush.setColor(GridLineColor);
			float y = top + TrackHeight - GridSize;
			canvas.drawLine(0, y, getWidth(), y, lineBrush);

			// if there's actually a track here
			if (t < trackCount) {

				Track track = score.getTrack(t);
				Scale scale = track.getScale();

				// draw annotations
				textBrush.setTextSize(AnnotationSize);
				textBrush.setColor(AnnotationColor);
				if (track != null) {
					float uy = midText(textBrush, 
							AnnotationTop, AnnotationTop + AnnotationSize);
					
					buildLeftAnnotation(track);
					textBrush.setTextAlign(Align.LEFT);
					canvas.drawText(charBuffer, 0, stringer.length(), 
							GapSize, top + uy, textBrush);
					
					buildRightAnnotation(track);
					textBrush.setTextAlign(Align.RIGHT);
					canvas.drawText(charBuffer, 0, stringer.length(), 
							getWidth() - GapSize, top + uy, textBrush);
				}
	
				// find the visible notes
				float timing = track.getTiming();
				int firstNote = screenToNote(0, timing);
				int lastNote = screenToNote(getWidth(), timing);
	
				int playbackIndex = track.timeToPosition(playbackTime);
	
				// for each note
				for (int n = firstNote; n <= lastNote; n++) {
	
					float left = noteToScreen(n, timing);
					float x0 = left + GapSize;
					float y0 = noteTop;
					float x1 = x0 + NoteButtonSize;
					float y1 = y0 + NoteButtonSize;
					rect.set(x0, y0, x1, y1);
	
					Note note = track.getNote(n);
					
					// draw the cursor highlight if it points here
					// and the score is currently editable
					if (isEditable() && t == editTrackPos && n == editNotePos) {
						lineBrush.setStrokeWidth(CursorSize);
						lineBrush.setColor(NoteFocusColor);
						y = top + CursorTop;
						canvas.drawLine(x0, y, x1, y, lineBrush);
					}
	
					// choose button back color based on playback,
					// selection state and position within the bar
					boolean selected = common.isSelecting() &&
							track == common.getSelectionTrack() && 
							common.isNoteSelected(n);
					boolean playnote = playback && 
							playbackIndex == n && 
							note != null;
					boolean barstart = (n % (track.getSlots() * track.getBeats())) == 0;
					boolean pressed = pressing && 
							t == trackTouched &&
							n == slotTouched;
					if (selected) {
						fillBrush.setColor(SelectionColor);
					} else if (pressed) {
						fillBrush.setColor(NotePressedColor);
					} else if (playnote) {
						fillBrush.setColor(NotePlayingColor);
					} else if (barstart) {
						fillBrush.setColor(BarBackingColor);
					} else {
						fillBrush.setColor(ButtonBackingColor);
					}
					canvas.drawRoundRect(rect, 8, 8, fillBrush);
	
					if (note != null) {
						scale.drawNote(canvas, textBrush, rect, NoteButtonPad, note.getPitchNumber());
					}
				}
			}			
		}
		
		// draw marker and scroll to current position during playback
		if (playback) {
			canvas.clipRect(0, 0, getWidth(), TimeBarHeight, Op.REPLACE);
			float beat = playbackTime * (float) common.getScore().getTempo() / 60f;
			int x = (int) beatToScreen(beat);
			canvas.drawBitmap(beatMarkerBmp, 
					x - beatMarkerBmp.getWidth() * 0.5f, 
					TimeBarHeight - beatMarkerBmp.getHeight(), fillBrush);
			if (scroller.isFinished()) {
				scrollToPosition(x);
			}
		}
		
		// handle any fling/scrolling animation in progress
		if (scroller.computeScrollOffset()) {
			scrollX = scroller.getCurrX();
			scrollY = scroller.getCurrY();
			// if we've reached scrolling limits, stop the fling
			if (enforceScrollLimits()) {
				scroller.forceFinished(true);
			}
			// to keep animation going, we need another draw (later)
			postInvalidate();
		}
		
	}

	/**
	 * initiate scroll to the specified position
	 * @param x position to scroll toward
	 */
	private void scrollToPosition(int x) {
		scroller.forceFinished(true);
		int w = getWidth();
		if (x < 0) {
			if (x > -w) {
				scroller.startScroll((int)scrollX, (int)scrollY, -w, 0);
			} else {
				scrollX += x;
				enforceScrollLimits();
			}
		}
		if (x > w) {
			if (x < 2 * w) {
				scroller.startScroll((int)scrollX, (int)scrollY, w, 0);
			} else {
				scrollX += x;
				enforceScrollLimits();
			}
		}
		postInvalidate();
	}
	
	/**
	 * initiate scroll to the specified note position
	 * @param trackPos index of track containing note
	 * @param notePos index of note
	 */
	private void scrollToPosition(int trackPos, int notePos) {
		ScoreCommon common = ScoreActivity.common;
		Track track = common.getScore().getTrack(trackPos);
		scrollToPosition((int)noteToScreen(notePos, track.getTiming()));
	}
	
	/**
	 * handle a cursor position change
	 */
	private void onCursorChange() {
		ScoreCommon common = ScoreActivity.common;
		scrollToPosition(common.getTrackPosition(), common.getNotePosition());
	}

	/**
	 * update beat scaling factor
	 */
	private void updateBeatScale() {
		Score score = ScoreActivity.common.getScore();
		float t = 1;
		for (int i = 0, il = score.getTrackCount(); i < il; i++) {
			Track track = score.getTrack(i);
			t = Math.min(t, track.getTiming());
		}
		beatScale = 1f / t;
	}
	
	/**
	 * handle a touch as a potential selection
	 */
	private void attemptSelection() {
		ScoreCommon common = ScoreActivity.common;
		common.handleSelection(trackTouched, slotTouched);
		Notifier.INSTANCE.send(Notifier.SelectionChange);
		postInvalidate();
	}

	/**
	 * handle start of audio playback
	 */
	private void onAudioPlaying() {
		playback = true;
		postInvalidate();
	}
	
	/**
	 * handle audio marker event
	 * @param ms elapsed time in ms
	 */
	private void onAudioMarker(int ms) {
		playbackTime = (float) ms / 1000f;
		postInvalidate();
	}
	
	/**
	 * handle end of score playback
	 */
	private void onAudioStopped() {
		playback = false;
		postInvalidate();
	}
	
	/**
	 * handle end of audio tail
	 */
	private void onAudioOff() {
	}
	
	/**
	 * pass motion events to the gesture detectors
	 */
	public boolean onTouchEvent(MotionEvent e) {
		int action = e.getActionMasked();
		// first hook the pressed state
		if (action == MotionEvent.ACTION_DOWN) {
			pressing = true;
			postInvalidate();
		}
		if (action == MotionEvent.ACTION_UP){
			pressing = false;
			postInvalidate();
		}
		// then evaluate with gesture detectors
		boolean r = gestureDetector.onTouchEvent(e);
		// only pass to superclass if gestures won't take it 
		return r || super.onTouchEvent(e);
	}
	
	@Override
	public boolean onDown(MotionEvent e) {
		if (!playback) {
			ScoreCommon common = ScoreActivity.common;
			scroller.forceFinished(true);
	
			float x = e.getX();
			float y = e.getY();
		
			// clicked on the time bar?
			if (y <= TimeBarHeight) {
				// indicate we didn't touch a track
				trackTouched = -1;
				// set the beat marker
				common.setBeatMarker((int)(screenToBeat(x) + 0.5f));
				postInvalidate();
			} else {
				// clicked on a (possible) track
				Score score = common.getScore();
				trackTouched = screenToTrack(y);
				if (trackTouched >= 0 && trackTouched < score.getTrackCount()) {
					Track track = score.getTrack(trackTouched);
					slotTouched = screenToNote(x, track.getTiming());
				}
			}
		}
		return true;
	}

	@Override
	public boolean onScroll(MotionEvent e0, MotionEvent e1, float dx, float dy) {
		scrollX += dx;
		scrollY += dy;
		enforceScrollLimits();
		postInvalidate();
		return true;
	}

	@Override
	public boolean onFling(MotionEvent e0, MotionEvent e1, float vx, float vy) {
		scroller.fling(
				(int)scrollX, (int)scrollY, 
				(int)(-vx), (int)(-vy), 
				0, Integer.MAX_VALUE, 
				0, Integer.MAX_VALUE);
		postInvalidate();
		return true;
	}

	@Override
	public void onLongPress(MotionEvent e) {
		ScoreCommon common = ScoreActivity.common;
		Score score = common.getScore();
		int tc = score.getTrackCount();
		if (!playback && trackTouched >= 0 && trackTouched < tc) {
			attemptSelection();
		}
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		ScoreCommon common = ScoreActivity.common;
		int tc = common.getScore().getTrackCount();
		if (!playback && trackTouched >= 0 && trackTouched < tc) {
			// sound a click
			playSoundEffect(SoundEffectConstants.CLICK);
			// if we're in a selection
			if (common.isSelecting()) {
				attemptSelection();
			} else {
				// store touch point as the new cursor
				common.setTrackPosition(trackTouched);
				common.setNotePosition(slotTouched);
				Notifier.INSTANCE.send(Notifier.CursorChange);
			}
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void handleMessage(Message msg) {
		switch(msg.what) {
		case Notifier.NewScore:
			updateBeatScale();
			onCursorChange();
			postInvalidate();
			break;
		case Notifier.ScoreChange:
			enforceScrollLimits();
			updateBeatScale();
			postInvalidate();
			break;
		case Notifier.CursorChange:
			onCursorChange();
			break;
		case Notifier.AudioPlaying:
			onAudioPlaying();
			break;
		case Notifier.AudioMarker:
			onAudioMarker(msg.arg1);
			break;
		case Notifier.AudioStopped:
			onAudioStopped();
			break;
		case Notifier.AudioOff:
			onAudioOff();
			break;
		case Notifier.SettingChange:
			onSettingsChanged();
			break;
		}
	}

	@Override
	public void onShowPress(MotionEvent e) {
	}

	@Override
	public boolean onDoubleTap(MotionEvent e) {
		return false;
	}

	@Override
	public boolean onDoubleTapEvent(MotionEvent e) {
		return false;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		return false;
	}
	
}
