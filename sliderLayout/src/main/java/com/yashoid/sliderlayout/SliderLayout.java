package com.yashoid.sliderlayout;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

@SuppressLint("RtlHardcoded")
public class SliderLayout extends ViewGroup {
	
	private static final long FLY_DURATION = 300;
	
	private static final int MAXIMUM_DARKNESS = 128;
	
	public interface SliderListener {
		
		void onStateChanged(int state, int slider);
		
		void onScrollChanged(float ratio, int slider);
		
		void onSliderOpened(int slider);
		
		void onSliderClosed(int slider);
		
	}
	
	public static final int STATE_IDLE = 0;
	public static final int STATE_SCROLLING = 1;
	public static final int STATE_FLYING = 2;
	
	public static final int SLIDER_NO_SLIDER = Gravity.NO_GRAVITY;
	public static final int SLIDER_LEFT = Gravity.LEFT;
	public static final int SLIDER_RIGHT = Gravity.RIGHT;

	private int mWidth;
	private int mHeight;
	
	private View mContent;
	private View mLeftSlider;
	private View mRightSlider;

	private Paint mDarkenerPaint;
	
	private int mState = STATE_IDLE;
	private int mOpenSlider = Gravity.NO_GRAVITY;
	private int mSlidingSlider = Gravity.NO_GRAVITY;
	
	private int mOverSliders = Gravity.NO_GRAVITY;
	private int mDarkeningSliders = Gravity.NO_GRAVITY;
	
	private int mLockedSliders = Gravity.NO_GRAVITY;
	
	/* This to prevent the cases in which MOVE motion event is received without a prior DOWN. This causes the GestureDetector to
	 * call a jump onScroll value. */
	private boolean mIsTouchDown = false;
	
	private float mSlideAmount;
	
	private float mSensitiveAreaWidth;
	
	private GestureDetector mGestureDetector;
	private GestureDetector mInterceptGestureDetector;
	
	private SliderListener mSliderListener = null;
	
	public SliderLayout(Context context) {
		super(context);
		initialize(context, null, 0, 0);
	}

	public SliderLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialize(context, attrs, 0, 0);
	}

	public SliderLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		initialize(context, attrs, defStyleAttr, 0);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public SliderLayout(Context context, AttributeSet attrs, int defStyleAttr,int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		initialize(context, attrs, defStyleAttr, defStyleRes);
	}
	
	private void initialize(Context context, AttributeSet attrs, int defStyleAttr,int defStyleRes) {
		mGestureDetector = new GestureDetector(context, mOnSlideListener);
		mInterceptGestureDetector = new GestureDetector(getContext(), mInterceptGestureListener);
		
		mSensitiveAreaWidth = context.getResources().getDimension(R.dimen._sliderlayout_default_sensitiveareawidth);
		
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SliderLayout, defStyleAttr, defStyleRes);
		
		mSensitiveAreaWidth = a.getDimension(R.styleable.SliderLayout_sensitiveAreaWidth, mSensitiveAreaWidth);
		mOverSliders = a.getInt(R.styleable.SliderLayout_overSliders, mOverSliders);
		mDarkeningSliders = a.getInt(R.styleable.SliderLayout_darkeningSliders, mDarkeningSliders);
		mLockedSliders = a.getInt(R.styleable.SliderLayout_lockedSliders, mLockedSliders);
		
		a.recycle();
		
		setWillNotDraw(false);
		
		mDarkenerPaint = new Paint();
		mDarkenerPaint.setColor(0xff000000);
		
//		setOnKeyListener(mBackKeyListener);
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);
		
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		
		int childWidthMode = widthMode==MeasureSpec.UNSPECIFIED?MeasureSpec.UNSPECIFIED:MeasureSpec.EXACTLY;
		int childHeightMode = heightMode==MeasureSpec.UNSPECIFIED?MeasureSpec.UNSPECIFIED:MeasureSpec.EXACTLY;
		
		final int childCount = getChildCount();
		
		if (childCount>3) {
			throw new IllegalStateException("LayoutSlider can not have more than 3 child views.");
		}
		
		for (int i=0; i<childCount; i++) {
			View child = getChildAt(i);
			
			LayoutParams params = (LayoutParams) child.getLayoutParams();
			
			switch (params.gravity) {
			case Gravity.NO_GRAVITY:
				mContent = child;
				break;
			case Gravity.LEFT:
				mLeftSlider = child;
				break;
			case Gravity.RIGHT:
				mRightSlider = child;
				break;
			}
		}
		
		int widthSpec = MeasureSpec.makeMeasureSpec(widthSize, childWidthMode);
		int heightSpec = MeasureSpec.makeMeasureSpec(heightSize, childHeightMode);
		
		int contentMeasuredWidth = 0;
		int contentMeasuredHeight = 0;
		
		if (mContent!=null) {
			mContent.measure(widthSpec, heightSpec);
			
			contentMeasuredWidth = mContent.getMeasuredWidth();
			contentMeasuredHeight = mContent.getMeasuredHeight();
		}
		
		
		int leftSliderMeasuredWidth = 0;
		int leftSliderMeasuredHeight = 0;
		
		if (mLeftSlider!=null) {
			int leftSliderWidth = mLeftSlider.getLayoutParams().width;
			mLeftSlider.measure(MeasureSpec.makeMeasureSpec(leftSliderWidth, MeasureSpec.EXACTLY), heightSpec);
			
			leftSliderMeasuredWidth = mLeftSlider.getMeasuredWidth();
			leftSliderMeasuredHeight = mLeftSlider.getMeasuredHeight();
		}
		
		
		int rightSliderMeasuredWidth = 0;
		int rightSliderMeasuredHeight = 0;
		
		if (mRightSlider!=null) {
			int rightSliderWidth = mRightSlider.getLayoutParams().width;
			mRightSlider.measure(MeasureSpec.makeMeasureSpec(rightSliderWidth, MeasureSpec.EXACTLY), heightSpec);
			
			rightSliderMeasuredWidth = mRightSlider.getMeasuredWidth();
			rightSliderMeasuredHeight = mRightSlider.getMeasuredHeight();
		}
		
		boolean hasChange = false;
		
		if (widthMode==MeasureSpec.UNSPECIFIED && widthSize<=0) {
			widthSize = Math.max(contentMeasuredWidth, Math.max(leftSliderMeasuredWidth, rightSliderMeasuredWidth));
			
			hasChange = true;
		}
		
		if (heightMode==MeasureSpec.UNSPECIFIED && heightSize<=0) {
			heightSize = Math.max(contentMeasuredHeight, Math.max(leftSliderMeasuredHeight, rightSliderMeasuredHeight));
			
			hasChange = true;
		}
		
		if (hasChange) {
			widthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
			heightSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
			
			if (mContent!=null) {
				mContent.measure(widthSpec, heightSpec);
			}
			
			if (mLeftSlider!=null) {
				int leftSliderWidth = mLeftSlider.getLayoutParams().width;
				mLeftSlider.measure(MeasureSpec.makeMeasureSpec(leftSliderWidth, MeasureSpec.EXACTLY), heightSpec);
			}
			
			if (mRightSlider!=null) {
				int rightSliderWidth = mRightSlider.getLayoutParams().width;
				mRightSlider.measure(MeasureSpec.makeMeasureSpec(rightSliderWidth, MeasureSpec.EXACTLY), heightSpec);
			}
		}
		
		setMeasuredDimension(widthSize, heightSize);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		mWidth = r - l;
		mHeight = b - t;
		
		mLeftSlider = null;
		mRightSlider = null;
		mContent = null;
		
		final int childCount = getChildCount();
		
		if (childCount>3) {
			throw new IllegalStateException("LayoutSlider can not have more than 3 child views.");
		}
		
		for (int i=0; i<childCount; i++) {
			View child = getChildAt(i);
			
			LayoutParams params = (LayoutParams) child.getLayoutParams();
			
			switch (params.gravity) {
			case Gravity.NO_GRAVITY:
				mContent = child;
				break;
			case Gravity.LEFT:
				mLeftSlider = child;
				break;
			case Gravity.RIGHT:
				mRightSlider = child;
				break;
			}
		}
		
		if (mContent!=null) {
			int shift = 0;
			
			if (mSlideAmount>0 && (mOverSliders&Gravity.LEFT)!=Gravity.LEFT) {
				shift = (int) mSlideAmount;
			}
			
			if (mSlideAmount<0 && (mOverSliders&Gravity.RIGHT)!=Gravity.RIGHT) {
				shift = (int) mSlideAmount;
			}
			
			mContent.layout(shift, 0, shift + mWidth, mHeight);
		}
		
		if (mLeftSlider!=null) {
			int leftSliderWidth = mLeftSlider.getLayoutParams().width;
			mLeftSlider.layout((int) mSlideAmount - leftSliderWidth, 0, (int) mSlideAmount, mHeight);
		}
		
		if (mRightSlider!=null) {
			int rightSliderWidth = mRightSlider.getLayoutParams().width;
			mRightSlider.layout((int) mSlideAmount + mWidth, 0, (int) mSlideAmount + mWidth + rightSliderWidth, mHeight);
		}
	}
	
	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		
		float max = 0;
		boolean darken = false;
		
		if (mSlideAmount>0) {
			if ((mDarkeningSliders&Gravity.LEFT)==Gravity.LEFT) {
				darken = true;
			}
			
			max = mLeftSlider.getWidth();
		}
		
		if (mSlideAmount<0) {
			if ((mDarkeningSliders&Gravity.RIGHT)==Gravity.RIGHT) {
				darken = true;
			}
			
			max = mRightSlider.getWidth();
		}
		
		if (darken) {
			int darkeningAmount = max==0?0:(int) (MAXIMUM_DARKNESS*(Math.abs(mSlideAmount)/max));
			mDarkenerPaint.setAlpha(darkeningAmount);
			
			canvas.drawRect(mSlideAmount, 0, mSlideAmount + mWidth, mHeight, mDarkenerPaint);
		}
	}

	@Override
	public boolean dispatchKeyEventPreIme(KeyEvent event) {
		if (event.getKeyCode()==KeyEvent.KEYCODE_BACK && mOpenSlider!=Gravity.NO_GRAVITY) {
			flyClose();
			return true;
		}
		
		return false;
	};
	
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		switch (mState) {
		case STATE_IDLE:
			boolean result = mInterceptGestureDetector.onTouchEvent(ev);
			boolean actionIsDown = ev.getAction()==MotionEvent.ACTION_DOWN;

			switch (mOpenSlider) {
			case Gravity.NO_GRAVITY:
				if ((mLeftSlider!=null && ev.getX()<mSensitiveAreaWidth)
						|| (mRightSlider!=null && ev.getX()>mWidth - mSensitiveAreaWidth)) {
					return actionIsDown?false:result;
				}
				break;
			case Gravity.LEFT:
				if (ev.getX()>mSlideAmount) {
					return true;
				}
				break;
			case Gravity.RIGHT:
				if (ev.getX()<mWidth + mSlideAmount) {
					return true;
				}
				break;
			}
			
			return false;
		case STATE_SCROLLING:
		case STATE_FLYING:
		default:
			return true;
		}
	}
	
	private OnGestureListener mInterceptGestureListener = new OnGestureListener() {
		
		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			return false;
		}
		
		@Override
		public void onShowPress(MotionEvent e) { }
		
		@Override
		public boolean onScroll(MotionEvent ev, MotionEvent e2, float distanceX, float distanceY) {
			if (Math.abs(distanceY)>Math.abs(distanceX)) {
				return false;
			}
			
			switch (mOpenSlider) {
			case Gravity.NO_GRAVITY:
				if ((mLeftSlider!=null && ev.getX()<mSensitiveAreaWidth)
						|| (mRightSlider!=null && ev.getX()>mWidth - mSensitiveAreaWidth)) {
					return true;
				}
				break;
			case Gravity.LEFT:
				if (ev.getX()>mSlideAmount) {
					return true;
				}
				break;
			case Gravity.RIGHT:
				if (ev.getX()<mWidth + mSlideAmount) {
					return true;
				}
				break;
			}
			
			return false;
		}
		
		@Override
		public void onLongPress(MotionEvent e) { }
		
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			return false;
		}
		
		@Override
		public boolean onDown(MotionEvent ev) {
			return true;
		}
		
	};
	
	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (mState==STATE_FLYING) {
			return true;
		}
		
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
		case MotionEvent.ACTION_MOVE:
			if (!mIsTouchDown) {
				event.setAction(MotionEvent.ACTION_DOWN);
			}
			return mGestureDetector.onTouchEvent(event);
		default:
			boolean result = mGestureDetector.onTouchEvent(event);
			onUp(event);
			return result;
		}
	}
	
	private OnGestureListener mOnSlideListener = new OnGestureListener() {
		
		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			return false;
		}
		
		@Override
		public void onShowPress(MotionEvent e) { }
		
		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
			if (e1==null) {
				e1 = e2;
			}
			
			if (!mIsTouchDown) {
				mIsTouchDown = true;
				return true;
			}
			
			requestDisallowInterceptTouchEvent(true);
			
			mState = STATE_SCROLLING;
			
			mSlideAmount += -distanceX;
			
			if (mSlidingSlider==Gravity.NO_GRAVITY) {
				if (mSlideAmount>0) {
					if (mLeftSlider!=null && e1.getX()<mWidth/2 && (mLockedSliders&Gravity.LEFT)!=Gravity.LEFT) {
						mSlidingSlider = Gravity.LEFT;
						
						if (mSliderListener!=null) {
							mSliderListener.onStateChanged(STATE_SCROLLING, mSlidingSlider);
						}
					}
				}
				else {
					if (mRightSlider!=null && e1.getX()>mWidth/2 && (mLockedSliders&Gravity.RIGHT)!=Gravity.RIGHT) {
						mSlidingSlider = Gravity.RIGHT;
						
						if (mSliderListener!=null) {
							mSliderListener.onStateChanged(STATE_SCROLLING, mSlidingSlider);
						}
					}
				}
			}

			switch (mSlidingSlider) {
			case Gravity.NO_GRAVITY:
				mSlideAmount = 0;
				break;
			case Gravity.LEFT:
				if (mSlideAmount>mLeftSlider.getWidth()) {
					mSlideAmount = mLeftSlider.getWidth();
				}
				
				if (mSlideAmount<0) {
					mSlideAmount = 0;
				}
				
				if (mSliderListener!=null) {
					mSliderListener.onScrollChanged(mSlideAmount/mLeftSlider.getWidth(), mSlidingSlider);
				}
				break;
			case Gravity.RIGHT:
				if (mSlideAmount<-mRightSlider.getWidth()) {
					mSlideAmount = -mRightSlider.getWidth();
				}
				
				if (mSlideAmount>0) {
					mSlideAmount = 0;
				}
				
				if (mSliderListener!=null) {
					mSliderListener.onScrollChanged(-mSlideAmount/mRightSlider.getWidth(), mSlidingSlider);
				}
				break;
			}
			
			if (mSlidingSlider!=Gravity.NO_GRAVITY) {
				invalidate();
				requestLayout();
				return true;
			}
			
			return false;
		}
		
		@Override
		public void onLongPress(MotionEvent e) { }
		
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			if (mSlidingSlider!=Gravity.NO_GRAVITY) {
				if (mSlidingSlider==Gravity.LEFT) {
					if (velocityX>0) {
						flyOpen();
					}
					else {
						flyClose();
					}
				}
				else if (mSlidingSlider==Gravity.RIGHT) {
					if (velocityX>0) {
						flyClose();
					}
					else {
						flyOpen();
					}
				}
				return true;
			}
			else {
				return false;
			}
		}
		
		@Override
		public boolean onDown(MotionEvent e) {
			if (mOpenSlider==Gravity.NO_GRAVITY) {
				float minimumX = mLeftSlider==null?0:mSensitiveAreaWidth;
				float maximumX = mRightSlider==null?mWidth:mWidth - mSensitiveAreaWidth;
				float x = e.getX();
				
				if (x>=minimumX && x<=maximumX) {
					return false;
				}
			}

			mIsTouchDown = true;
			
			return true;
		}
		
	};
	
	private void onUp(MotionEvent e) {
		mIsTouchDown = false;
		
		requestDisallowInterceptTouchEvent(false);
		
		switch (mState) {
		case STATE_IDLE:
			switch (mOpenSlider) {
			case Gravity.NO_GRAVITY:
				break;
			case Gravity.LEFT:
				if (e.getX()>mSlideAmount) {
					flyClose();
				}
				break;
			case Gravity.RIGHT:
				if (e.getX()<mWidth + mSlideAmount) {
					flyClose();
				}
				break;
			}
			return;
		case STATE_SCROLLING:
			if (mSlideAmount==0) {
				mState = STATE_IDLE;
				
				if (mSliderListener!=null) {
					mSliderListener.onStateChanged(STATE_IDLE, SLIDER_NO_SLIDER);
					mSliderListener.onSliderClosed(mSlidingSlider);
				}
				
				mSlidingSlider = Gravity.NO_GRAVITY;
				mOpenSlider = Gravity.NO_GRAVITY;
			}
			else if (mLeftSlider!=null && mSlideAmount==mLeftSlider.getWidth()) {
				mState = STATE_IDLE;
				mOpenSlider = Gravity.LEFT;
				
				if (mSliderListener!=null) {
					mSliderListener.onStateChanged(STATE_IDLE, mOpenSlider);
					mSliderListener.onSliderClosed(mOpenSlider);
				}
			}
			else if (mRightSlider!=null && mSlideAmount==-mRightSlider.getWidth()) {
				mState = STATE_IDLE;
				mOpenSlider = Gravity.RIGHT;
				
				if (mSliderListener!=null) {
					mSliderListener.onStateChanged(STATE_IDLE, mOpenSlider);
					mSliderListener.onSliderClosed(mOpenSlider);
				}
			}
			else {
				flyClose();
			}
			return;
		case STATE_FLYING:
			return;
		}
	}
	
	public void setSliderListener(SliderListener sliderListener) {
		mSliderListener = sliderListener;
	}
	
	public void closeSliders() {
		if (mState==STATE_FLYING) {
			return;
		}
		
		if (mOpenSlider!=Gravity.NO_GRAVITY) {
			flyClose();
		}
	}
	
	public void closeSlider(int gravity) {
		if (mState==STATE_FLYING) {
			return;
		}
		
		switch (gravity) {
		case Gravity.LEFT:
			if (mLeftSlider!=null && mOpenSlider==gravity) {
				flyClose();
			}
			break;
		case Gravity.RIGHT:
			if (mRightSlider!=null && mOpenSlider==gravity) {
				flyClose();
			}
			break;
		}
	}
	
	public void closeSlider(int gravity, boolean animate) {
		if (animate) {
			closeSlider(gravity);
			return;
		}
		
		if (mState==STATE_FLYING) {
			return;
		}
		
		switch (gravity) {
		case Gravity.LEFT:
			if (mLeftSlider!=null && mOpenSlider==gravity) {
				closeImmediate();
			}
			break;
		case Gravity.RIGHT:
			if (mRightSlider!=null && mOpenSlider==gravity) {
				closeImmediate();
			}
			break;
		}
	}
	
	public void openSlider(int gravity) {
		switch (gravity) {
		case Gravity.LEFT:
			if (mLeftSlider!=null) {
				mSlidingSlider = gravity;
				flyOpen();
			}
			break;
		case Gravity.RIGHT:
			if (mRightSlider!=null) {
				mSlidingSlider = gravity;
				flyOpen();
			}
			break;
		}
	}
	
	public boolean isSliderOpen(int gravity) {
		return mOpenSlider==gravity;
	}
	
	public void setSliderIsOver(int gravity, boolean isOver) {
		if (isOver) {
			mOverSliders |= gravity;
		}
		else {
			mOverSliders &= ~gravity;
		}
	}
	
	public void setSlidersAreOver(boolean areOver) {
		if (areOver) {
			mOverSliders = Gravity.LEFT | Gravity.RIGHT;
		}
		else {
			mOverSliders = Gravity.NO_GRAVITY;
		}
	}
	
	public void setSliderDarkens(int gravity, boolean darkens) {
		if (darkens) {
			mDarkeningSliders |= gravity;
		}
		else {
			mDarkeningSliders &= ~gravity;
		}
	}
	
	public void setSlidersDarken(boolean darkens) {
		if (darkens) {
			mDarkeningSliders = Gravity.LEFT | Gravity.RIGHT;
		}
		else {
			mDarkeningSliders = Gravity.NO_GRAVITY;
		}
	}
	
	public void lockSlider(int gravity) {
		mLockedSliders |= gravity;
		
		if ((mOpenSlider&mLockedSliders)!=0) {
			flyClose();
		}
	}
	
	public void lockSliders() {
		mLockedSliders = Gravity.LEFT | Gravity.RIGHT;
		
		if ((mOpenSlider&mLockedSliders)!=0) {
			flyClose();
		}
	}
	
	public void unlockSlider(int gravity) {
		mLockedSliders &= ~gravity;
	}
	
	public void unlockSliders() {
		mLockedSliders = Gravity.NO_GRAVITY;
	}
	
	private void closeImmediate() {
		mState = STATE_IDLE;
		
		requestDisallowInterceptTouchEvent(false);
		
		mSlideAmount = 0;
		
		if (mSliderListener!=null) {
			mSliderListener.onStateChanged(STATE_IDLE, mSlidingSlider);
			mSliderListener.onSliderClosed(mSlidingSlider);
		}
		
		mSlidingSlider = Gravity.NO_GRAVITY;
		mOpenSlider = Gravity.NO_GRAVITY;
		
		requestLayout();
	}
	
	private void flyClose() {
		mState = STATE_FLYING;
		
		requestDisallowInterceptTouchEvent(false);
		
		if (mSliderListener!=null) {
			mSliderListener.onStateChanged(STATE_FLYING, mSlidingSlider);
		}
		
		ValueAnimator animator = new ValueAnimator();
		animator.setFloatValues(mSlideAmount, 0);
		
		long duration = 0;
		switch (mSlidingSlider) {
		case Gravity.LEFT:
			duration = (long) (FLY_DURATION*mSlideAmount/mLeftSlider.getWidth());
			break;
		case Gravity.RIGHT:
			duration = (long) (FLY_DURATION*-mSlideAmount/mRightSlider.getWidth());
			break;
		}
		animator.setDuration(duration);
		animator.setInterpolator(new AccelerateDecelerateInterpolator());
		
		animator.addUpdateListener(new AnimatorUpdateListener() {
			
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				mSlideAmount = (float) animation.getAnimatedValue();
				
				requestLayout();
				invalidate();
				
				notifySlideChanged();
			}
			
		});
		animator.addListener(new AnimatorListener() {
			
			@Override
			public void onAnimationStart(Animator animation) { }
			
			@Override
			public void onAnimationRepeat(Animator animation) { }
			
			@Override
			public void onAnimationEnd(Animator animation) {
				mState = STATE_IDLE;
				
				if (mSliderListener!=null) {
					mSliderListener.onStateChanged(STATE_IDLE, mSlidingSlider);
					mSliderListener.onSliderClosed(mSlidingSlider);
				}
				
				mSlidingSlider = Gravity.NO_GRAVITY;
				mOpenSlider = Gravity.NO_GRAVITY;
				mSlideAmount = 0;
				requestLayout();
				invalidate();
			}
			
			@Override
			public void onAnimationCancel(Animator animation) { }
			
		});
		animator.start();
	}
	
	private void flyOpen() {
		mState = STATE_FLYING
		
		requestDisallowInterceptTouchEvent(false);
		
		if (mSliderListener!=null) {
			mSliderListener.onStateChanged(STATE_FLYING, mSlidingSlider);
		}
		
		ValueAnimator animator = new ValueAnimator();
		animator.setFloatValues(mSlideAmount, mSlidingSlider==Gravity.LEFT?mLeftSlider.getWidth():-mRightSlider.getWidth());
		
		long duration = FLY_DURATION;
		switch (mSlidingSlider) {
		case Gravity.LEFT:
			duration = FLY_DURATION - (long) (FLY_DURATION*mSlideAmount/mLeftSlider.getWidth());
			break;
		case Gravity.RIGHT:
			duration = FLY_DURATION - (long) (FLY_DURATION*-mSlideAmount/mRightSlider.getWidth());
			break;
		}
		animator.setDuration(duration);
		
		animator.setInterpolator(new AccelerateDecelerateInterpolator());
		
		animator.addUpdateListener(new AnimatorUpdateListener() {
			
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				mSlideAmount = (float) animation.getAnimatedValue();
				
				requestLayout();
				invalidate();

				notifySlideChanged();
			}
			
		});
		animator.addListener(new AnimatorListener() {
			
			@Override
			public void onAnimationStart(Animator animation) { }
			
			@Override
			public void onAnimationRepeat(Animator animation) { }
			
			@Override
			public void onAnimationEnd(Animator animation) {
				mState = STATE_IDLE;
				
				if (mSliderListener!=null) {
					mSliderListener.onStateChanged(STATE_IDLE, mSlidingSlider);
				}
				
				mOpenSlider = mSlidingSlider;
				
				switch (mOpenSlider) {
				case Gravity.LEFT:
					mSlideAmount = mLeftSlider.getWidth();
					break;
				case Gravity.RIGHT:
					mSlideAmount = -mRightSlider.getWidth();
					break;
				}
				
				if (mSliderListener!=null) {
					mSliderListener.onSliderOpened(mOpenSlider);
				}
				
				requestLayout();
				invalidate();
			}
			
			@Override
			public void onAnimationCancel(Animator animation) { }
			
		});
		animator.start();
	}
	
	private void notifySlideChanged() {
		if (mSliderListener!=null) {
			switch (mSlidingSlider) {
			case Gravity.LEFT:
				mSliderListener.onScrollChanged(mSlideAmount/mLeftSlider.getWidth(), mSlidingSlider);
				break;
			case Gravity.RIGHT:
				mSliderListener.onScrollChanged(-mSlideAmount/mRightSlider.getWidth(), mSlidingSlider);
				break;
			}
		}
	}
	
	@Override
	public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new LayoutParams(getContext(), attrs);
	}
	
	@Override
	protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
		return new LayoutParams(p);
	}
	
	@Override
	protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
	}
	
	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof LayoutParams;
	}
	
	public static class LayoutParams extends ViewGroup.LayoutParams {

		public int gravity = Gravity.NO_GRAVITY;
		
		public LayoutParams(Context c, AttributeSet attrs) {
			super(c, attrs);
			
			TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.SliderLayout_Layout);
			
			for (int i=0; i<a.getIndexCount(); i++) {
				if (a.getIndex(i)==R.styleable.SliderLayout_Layout_android_layout_gravity) {
					gravity = a.getInt(i, Gravity.NO_GRAVITY);
				}
			}
			
			a.recycle();
		}

		public LayoutParams(int width, int height) {
			super(width, height);
		}

		public LayoutParams(android.view.ViewGroup.LayoutParams source) {
			super(source);
		}
		
	}

}
