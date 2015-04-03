package com.wordsaretoys.quencher.voices;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.wordsaretoys.quencher.R;
import com.wordsaretoys.quencher.common.Notifier;
import com.wordsaretoys.quencher.common.Notifier.NotificationListener;
import com.wordsaretoys.quencher.data.Stage;

public class HarmonicView extends View implements NotificationListener {

	static final String TAG = "HarmonicView";

	// visible gap between bars
	static final int BarGap = 2;
	
	// header and footer space
	static final int HeaderSize = 32;
	static final int FooterSize = 32;
	
	// colors can be obtained from resources
	final Resources Res = getResources();
	// background color
	final int BackingColor = Res.getColor(R.color.clouds);
	// bar color
	final int BarColor = Res.getColor(R.color.mdgray1);
	// text color
	final int TextColor = Res.getColor(R.color.black);
	// gap line color
	final int GapColor = Res.getColor(R.color.ltgray1);

	// string resources
	String title = Res.getString(R.string.stageHarmonics);
	
	// array of harmonic labels
	String[] labels;
	
	// label text size ratio
	float labelRatio;
	
	// paint objects for all drawing
	Paint fillBrush, lineBrush, textBrush;
	
	// current set of harmonics
	float[] harmonics;

	// width of a harmonic bar
	float barWidth;

	// maximum height of a harmonic bar
	float barHeight;
	
	// bottom of a harmonic bar
	float barBottom;
	
	/**
	 * default ctor, required by layout inflator
	 * @param context
	 * @param attrs
	 */
	public HarmonicView(Context context, AttributeSet attrs) {
		super(context, attrs);

		setBackgroundColor(BackingColor);
		
		fillBrush = new Paint();
		fillBrush.setStyle(Style.FILL);
		
		lineBrush = new Paint();
		lineBrush.setStyle(Style.STROKE);
		
		textBrush = new Paint();
		textBrush.setTypeface(Typeface.SANS_SERIF);
		textBrush.setTextSize(64f);
		textBrush.setAntiAlias(true);
		
		labels = new String[Stage.Harmonics];
		for (int i = 0; i < Stage.Harmonics; i++) {
			labels[i] = String.valueOf(i + 1);
		}
		harmonics = new float[Stage.Harmonics];
	}

	/**
	 * get harmonic number from screen x coordinate
	 * @param x screen x coordinate
	 * @return harmonic number (0..Harmonics - 1)
	 */
	private int screenToHarmonic(float x) {
		x = Math.max(0, Math.min(getWidth() - 1, x));
		return (int)(x * Stage.Harmonics / getWidth());
	}
	
	/**
	 * set left edge screen coordinate from harmonic number
	 * @param h harmonic number (0..Harmonics - 1)
	 * @return left edge x coordinate
	 */
	private float harmonicToScreen(int h) {
		return (float) h * getWidth() / (float) Stage.Harmonics;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		barWidth = (float) w / (float) Stage.Harmonics - BarGap;

		float dd = Math.min(barWidth - BarGap, HeaderSize);
		textBrush.setTextSize(dd);
		
		barHeight = getHeight() - HeaderSize - FooterSize;
		barBottom = getHeight() - FooterSize;
		
		getHarmonics();
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		
		fillBrush.setColor(BarColor);
		textBrush.setColor(TextColor);
		textBrush.setTextAlign(Paint.Align.CENTER);
		lineBrush.setColor(GapColor);
		lineBrush.setStrokeWidth(BarGap);
	
		canvas.drawLine(0, HeaderSize, getWidth(), HeaderSize, lineBrush);
		canvas.drawLine(0, barBottom, getWidth(), barBottom, lineBrush);
		
		float tvc = textBrush.getTextSize() - textBrush.descent() * 0.5f;
		
		for (int h = 0; h < Stage.Harmonics; h++) {
			float left = harmonicToScreen(h);
			float right = left + barWidth;
			float top = barBottom - barHeight * harmonics[h];
			canvas.drawRect(left + BarGap, top, right, barBottom, fillBrush);
			
			canvas.drawLine(left, 0, left, barBottom, lineBrush);
			
			float c = (left + right) * 0.5f;
			canvas.drawText(labels[h], c, tvc, textBrush);
		}
		
		canvas.drawText(title, getWidth() * 0.5f, barBottom + tvc, textBrush);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		switch(e.getActionMasked()) {
		case MotionEvent.ACTION_DOWN:
		case MotionEvent.ACTION_MOVE:
			int h = screenToHarmonic(e.getX());
			float y = e.getY();
			if (y <= HeaderSize) {
				harmonics[h] = 1;
			} else if (y >= barBottom) {
				harmonics[h] = 0;
			} else {
				harmonics[h] = 1 - (y - HeaderSize) / barHeight;
			}
			postInvalidate();
			break;
			
		case MotionEvent.ACTION_UP:
			setHarmonics();
			break;
			
		default:
			return super.onTouchEvent(e);
		}
		return true;
	}
	
	/**
	 * retrieve harmonic data from the current stage
	 */
	private void getHarmonics() {
		Stage stage = VoiceActivity.common.getFocusedStage();
		for (int i = 0; i < Stage.Harmonics; i++) {
			harmonics[i] = stage.getHarmonic(i);
		}
		postInvalidate();
	}

	/**
	 * write harmonic data to the current stage
	 */
	private void setHarmonics() {
		Stage stage = VoiceActivity.common.getFocusedStage();
		stage.setHarmonics(harmonics);
	}
	
	@Override
	public void handleMessage(Message msg) {
		switch(msg.what) {

		case Notifier.NewVoice:
		case Notifier.VoiceChange:
		case Notifier.StageCursorChange:
			getHarmonics();
			break;
		}
	}

}
