package com.wordsaretoys.quencher.scores;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Message;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.audio.Engine;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.common.Notifier.NotificationListener;
import com.wordsaretoys.quencher.data.Scale;
import com.wordsaretoys.quencher.data.Score;
import com.wordsaretoys.quencher.data.Track;

public class NoteChooserView extends View implements
	OnGestureListener, NotificationListener {

	static final String TAG = "NoteChooserView";

	// basic drawing parameters
	static final int NoteSize = 72;
	static final int NoteMargin = 8;
	static final int NoteTextPad = 2;
	
	// derived drawing parameters
	static final int Height = NoteSize + 2 * NoteMargin; 
	static final int NoteWidth = NoteSize + 2 * NoteMargin;
	static final int NoteTop = Height - NoteSize - NoteMargin;

	final Resources Res = getResources();

	// color resources
	int colorBlack = Res.getColor(R.color.black);
	int colorClouds = Res.getColor(R.color.clouds);
	int colorLtGray1 = Res.getColor(R.color.ltgray1);
	int colorLtGray3 = Res.getColor(R.color.ltgray3);
	int colorMdGray1 = Res.getColor(R.color.mdgray1);
	int colorLtBlue1 = Res.getColor(R.color.ltblue1);
	
	// background color
	final int BackingColor = colorClouds;
	// note button backing color
	final int NoteBackingColor = colorLtGray1;
	// root note backing color
	final int RootBackingColor = colorLtGray3;
	// note button text color
	final int NoteTextColor = colorBlack;
	// note button pressed color
	final int NotePressedColor = colorLtBlue1;

	// gesture detectors
	GestureDetector gestureDetector;
	
	// overscroller handles fling animations and overscroll notifications
	OverScroller scroller;
	
	// paint objects for all drawing
	Paint fillBrush, lineBrush, textBrush;
	
	// scroll offsets
	float scrollX;

	// last note touched
	int noteTouched;
	
	// reference to scale being used
	Scale scale;
	
	// track hash code-based array of scale positions
	SparseArray<Float> trackPos;
	
	// scale positions from bundle, required for async load
	float[] storedPos;
	
	// rect object for drawing roundrects
	RectF rect = new RectF();
	
	// note undergoing press indicator flag
	boolean pressing;

	/**
	 * default ctor, required by layout inflator
	 * @param context
	 * @param attrs
	 */
	public NoteChooserView(Context context, AttributeSet attrs) {
		super(context, attrs);

		setBackgroundColor(BackingColor);
		
		// set up the paint objects
		fillBrush = new Paint();
		fillBrush.setStyle(Style.FILL);
		
		lineBrush = new Paint();
		lineBrush.setStyle(Style.STROKE);
		
		textBrush = new Paint();
		textBrush.setTypeface(Typeface.SANS_SERIF);
		textBrush.setColor(NoteTextColor);
		textBrush.setAntiAlias(true);

		gestureDetector = new GestureDetector(context, this);
		scroller = new OverScroller(context);
		trackPos = new SparseArray<Float>();
	}

	/**
	 * write state to a bundle
	 */
	public void saveState(Bundle b) {
		Bundle c = new Bundle();
		c.putFloat("scrollX", scrollX);
		Score score = ScoreActivity.common.getScore();
		int sz = score.getTrackCount();
		float[] positions = new float[sz];
		for (int i = 0; i < sz; i++) {
			int hash = score.getTrack(i).hashCode();
			if (trackPos.get(hash) != null) {
				positions[i] = trackPos.get(hash);
			} else {
				positions[i] = -1;
			}
		}
		c.putFloatArray("positions", positions);
		b.putBundle("noteChooser", c);
	}
	
	/**
	 * load saved state from a bundle
	 */
	public void loadState(Bundle b) {
		Bundle c = b.getBundle("noteChooser");
		scrollX = c.getFloat("scrollX");
		storedPos = c.getFloatArray("positions");
	}

	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(widthMeasureSpec, Height);
	}
	
	/**
	 * adjust/check drawing parameters
	 */
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		enforceScrollLimits();
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
		Track track = common.getFocusedTrack();
		int pitches = track.getScale().getCount();
		int xlimit =  (int)(pitches * NoteWidth) - getWidth() + NoteMargin;
		xlimit = Math.max(xlimit, 0);
		if (scrollX > xlimit) {
			scrollX = xlimit;
			clipped = true;
		}
		trackPos.put(track.hashCode(), scrollX);
		return clipped;
	}

	/**
	 * finds the note index corresponding to the screen position
	 * 
	 * @param x screen x coordinate
	 * @return note index
	 */
	private int screenToNote(float x) {
		return (int)((x + scrollX) / NoteWidth);
	}
	
	/**
	 * find the note index corresponding to the screen position
	 * 
	 * @param n note index
	 * @return screen x coordinate
	 */
	private float noteToScreen(int n) {
		return n * NoteWidth - scrollX;
	}
	
	/**
	 * draw the custom view
	 */
	protected void onDraw(Canvas canvas) {
		ScoreCommon common = ScoreActivity.common;

		Track track = common.getFocusedTrack();
		Scale scale = track.getScale();
		// a mock scale has no name and no real notes to draw
		boolean drawNotes = scale.getName().length() > 0;
		int toneCount = scale.getToneCount();
		
		// find the visible notes
		int firstNote = screenToNote(0);
		int lastNote = screenToNote(getWidth());
		
		// for each note
		for (int n = firstNote; n <= lastNote; n++) {
			float left = noteToScreen(n);
			float x0 = left + NoteMargin;
			float y0 = NoteTop;
			float x1 = x0 + NoteSize;
			float y1 = y0 + NoteSize;

			if (n < scale.getCount()) {
				
				if (pressing && n == noteTouched) {
					fillBrush.setColor(NotePressedColor);
				} else if (n % toneCount == 0) {
					fillBrush.setColor(RootBackingColor);
				} else {
					fillBrush.setColor(NoteBackingColor);
				}
				
				rect.set(x0, y0, x1, y1);
				canvas.drawRoundRect(rect, 8, 8, fillBrush);

				if (drawNotes) {
					scale.drawNote(canvas, textBrush, rect, NoteTextPad, n);
				}
			}
		}

		// handle any fling/scrolling animation in progress
		if (scroller.computeScrollOffset()) {
			scrollX = scroller.getCurrX();
			// if we've reached scrolling limits, stop the fling
			if (enforceScrollLimits()) {
				scroller.forceFinished(true);
			}
			// to keep animation going, we need another draw (later)
			postInvalidate();
		}
		
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
		scroller.forceFinished(true);
		float x = e.getX();
		noteTouched = screenToNote(x);
		return true;
	}

	@Override
	public boolean onScroll(MotionEvent e0, MotionEvent e1, float dx, float dy) {
		scroller.forceFinished(true);
		scrollX += dx;
		enforceScrollLimits();
		postInvalidate();
		return true;
	}

	@Override
	public boolean onFling(MotionEvent e0, MotionEvent e1, float vx, float vy) {
		scroller.forceFinished(true);
		scroller.fling(
				(int)scrollX, 0, 
				(int)(-vx), 0, 
				0, Integer.MAX_VALUE, 
				0, Integer.MAX_VALUE);
		postInvalidate();
		return true;
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		ScoreCommon common = ScoreActivity.common;
		Track track = common.getFocusedTrack();
		// play the touched note, if audible
		if (!track.isMuted()) {
			Engine.INSTANCE.play(track.getVoice(), 
					track.getScale().getFrequency(noteTouched), 
					track.getVolume(), track.getPan());
		}
		// post note touched message
		Notifier.INSTANCE.send(Notifier.SetNote, noteTouched);
		return true;
	}

	/**
	 * handles changes to score editing cursor
	 * scrolls scale window to stored or default position
	 */
	private void onCursorChange() {
		ScoreCommon common = ScoreActivity.common;
		Track track = common.getFocusedTrack();
		scale = track.getScale();
		float sx = trackPos.get(track.hashCode(), -1f);
		scrollX = (sx != -1f) ? sx : scale.getMiddle() * NoteWidth;
		enforceScrollLimits();
		postInvalidate();
	}
	
	@Override
	public void handleMessage(Message msg) {
		switch(msg.what) {
		
		case Notifier.NewScore:
			// invalidate track positions
			trackPos.clear();
			// insure focused track is refreshed
			onCursorChange();
			break;

		case Notifier.ScoreReady:
			// restore bundled positions, if any
			if (storedPos != null) {
				Score score = ScoreActivity.common.getScore();
				// required to prevent monkey crash; unlikely in production
				int count = Math.min(storedPos.length, score.getTrackCount());
				for (int i = 0; i < count; i++) {
					if (storedPos[i] != -1) {
						int hash = score.getTrack(i).hashCode();
						trackPos.put(hash, storedPos[i]);
					}
				}
				storedPos = null;
			}
			break;
			
		case Notifier.CursorChange:
			onCursorChange();
			break;
			
		case Notifier.ScoreChange:
			// sent if current track changes scales
			enforceScrollLimits();
			postInvalidate();
			break;
		}
	}

	@Override
	public void onLongPress(MotionEvent e) {
	}

	@Override
	public void onShowPress(MotionEvent e) {
	}
}
