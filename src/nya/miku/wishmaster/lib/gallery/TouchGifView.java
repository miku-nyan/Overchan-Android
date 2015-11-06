/*
 * Класс использовался в 2ch Browser <https://github.com/vortexwolf/2ch-Browser/>
 * 
 */

package nya.miku.wishmaster.lib.gallery;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.OverScroller;
import android.widget.Scroller;

/**
 * View для установки {@link GifDrawable} с поддержкой зума (pinch-to-zoom), инерционного скроллинга.
 * Поведение при двойных тапах такое же, как у {@link FixedSubsamplingScaleImageView}. 
 * @author miku-nyan
 *
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class TouchGifView extends ImageView {

    Matrix matrix = new Matrix();

    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    int mode = NONE;
    
    boolean isZooming = false;

    PointF last = new PointF();
    PointF start = new PointF();
    float minScale = 1f;
    float realScale = 1f;
    float maxScale = 3f;
    float defaultScale = 2f; //maximum default value
    final float cMinScale = 1f;
    final float cMaxScale = 3f;
    final float cDefaultScale = 2f;
    float[] m;

    float redundantXSpace, redundantYSpace;

    float width, height;
    static final int CLICK = 3;
    float saveScale = 1f;
    float right, bottom, origWidth, origHeight, bmWidth, bmHeight;

    ScaleGestureDetector mScaleDetector;
    GestureDetector mDoubleTapDetector;
    Fling fling;

    Context context;


    @SuppressLint("ClickableViewAccessibility")
    public TouchGifView(Context context) {
        super(context);
        super.setClickable(true);
        this.context = context;
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mDoubleTapDetector = new GestureDetector(context, new GestureListener());
        matrix.setTranslate(1f, 1f);
        m = new float[9];
        setImageMatrix(matrix);
        setScaleType(ScaleType.MATRIX);
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (fling != null) {
                    fling.cancelFling();
                }
                if (!isZooming) {
                    mScaleDetector.onTouchEvent(event);
                    mDoubleTapDetector.onTouchEvent(event);
                                
                    matrix.getValues(m);
                    float x = m[Matrix.MTRANS_X];
                    float y = m[Matrix.MTRANS_Y];
                    PointF curr = new PointF(event.getX(), event.getY());
                                
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            last.set(event.getX(), event.getY());
                            start.set(last);
                            mode = DRAG;
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (mode == DRAG) {
                                float deltaX = curr.x - last.x;
                                float deltaY = curr.y - last.y;
                                float scaleWidth = Math.round(origWidth * saveScale);
                                float scaleHeight = Math.round(origHeight * saveScale);
                                if (scaleWidth < width && scaleHeight < height) {
                                    deltaX = 0;
                                    deltaY = 0;
                                } else if (scaleWidth < width) {
                                    deltaX = 0;
                                    if (y + deltaY > 0)
                                        deltaY = -y;
                                    else if (y + deltaY < -bottom)
                                        deltaY = -(y + bottom); 
                                } else if (scaleHeight < height) {
                                    deltaY = 0;
                                    if (x + deltaX > 0)
                                        deltaX = -x;
                                    else if (x + deltaX < -right)
                                        deltaX = -(x + right);
                                } else {
                                    if (x + deltaX > 0)
                                        deltaX = -x;
                                    else if (x + deltaX < -right)
                                        deltaX = -(x + right);
                                    
                                    if (y + deltaY > 0)
                                        deltaY = -y;
                                    else if (y + deltaY < -bottom)
                                        deltaY = -(y + bottom);
                                }
                                matrix.postTranslate(deltaX, deltaY);
                                last.set(curr.x, curr.y);
                            }
                            break;
                            
                        case MotionEvent.ACTION_UP:
                            mode = NONE;
                            int xDiff = (int) Math.abs(curr.x - start.x);
                            int yDiff = (int) Math.abs(curr.y - start.y);
                            if (xDiff < CLICK && yDiff < CLICK)
                                performClick();
                            break;
                            
                        case MotionEvent.ACTION_POINTER_UP:
                            mode = NONE;
                            break;
                    }
                    setImageMatrix(matrix);
                    invalidate();
                }
                return true; // indicate event was handled
            }
        });
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
       super.setImageDrawable(drawable);
       bmWidth = drawable.getIntrinsicWidth();
       bmHeight = drawable.getIntrinsicHeight();
    }

    public void setMaxZoom(float x) {
        maxScale = x;
    }
    
    private void fixTrans() {
        matrix.getValues(m);
        float transX = m[Matrix.MTRANS_X];
        float transY = m[Matrix.MTRANS_Y];
        float fixTransX = getFixTrans(transX, width, bmWidth*saveScale/realScale);
        float fixTransY = getFixTrans(transY, height, bmHeight*saveScale/realScale);
        if (fixTransX != 0 || fixTransY != 0) {
            matrix.postTranslate(fixTransX, fixTransY);
        }
    }
    
    private void fixScaleTrans() {
        fixTrans();
        float imageWidth = bmWidth*saveScale/realScale;
        float imageHeight = bmHeight*saveScale/realScale;
        matrix.getValues(m);
        if (imageWidth < width) {
            m[Matrix.MTRANS_X] = (width - imageWidth) / 2;
        }
        if (imageHeight < height) {
            m[Matrix.MTRANS_Y] = (height - imageHeight) / 2;
        }
        matrix.setValues(m);
    }
    
    private float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;
        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }
        if (trans < minTrans)
            return -trans + minTrans;
        if (trans > maxTrans)
            return -trans + maxTrans;
        return 0;
    }
    
    public void scale(float mScaleFactor, float focusX, float focusY) {
        float origScale = saveScale;
        saveScale *= mScaleFactor;
        if (saveScale > maxScale) {
            saveScale = maxScale;
            mScaleFactor = maxScale / origScale;
        } else if (saveScale < minScale) {
            saveScale = minScale;
            mScaleFactor = minScale / origScale;
        }
        right = width * saveScale - width - (2 * redundantXSpace * saveScale);
        bottom = height * saveScale - height - (2 * redundantYSpace * saveScale);
        if (origWidth * saveScale <= width || origHeight * saveScale <= height) {
            matrix.postScale(mScaleFactor, mScaleFactor, width / 2, height / 2);
            if (mScaleFactor < 1) {
                matrix.getValues(m);
                float x = m[Matrix.MTRANS_X];
                float y = m[Matrix.MTRANS_Y];
                if (mScaleFactor < 1) {
                    if (Math.round(origWidth * saveScale) >= width || Math.round(origHeight * saveScale) >= height) {
                        
                        if (Math.round(origWidth * saveScale) < width) {
                            if (y < -bottom)
                                matrix.postTranslate(0, -(y + bottom));
                            else if (y > 0)
                                matrix.postTranslate(0, -y);
                        } else {
                            if (x < -right) 
                                matrix.postTranslate(-(x + right), 0);
                            else if (x > 0) 
                                matrix.postTranslate(-x, 0);
                        }
                    }
                }
            }
        } else {
            matrix.postScale(mScaleFactor, mScaleFactor, focusX, focusY);
            matrix.getValues(m);
            float x = m[Matrix.MTRANS_X];
            float y = m[Matrix.MTRANS_Y];
            if (mScaleFactor < 1) {
                if (x < -right) 
                    matrix.postTranslate(-(x + right), 0);
                else if (x > 0) 
                    matrix.postTranslate(-x, 0);
                if (y < -bottom)
                    matrix.postTranslate(0, -(y + bottom));
                else if (y > 0)
                    matrix.postTranslate(0, -y);
            }
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = (float)Math.min(Math.max(.95f, detector.getScaleFactor()), 1.05);
            scale(scaleFactor, detector.getFocusX(), detector.getFocusY());
            return true;
        }
    }
    
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            float targetScale;
            if (saveScale > realScale * 0.9 && saveScale * 0.9 < realScale) targetScale = maxScale;
            else if (saveScale > maxScale * 0.99) targetScale = 1f;
            else targetScale = realScale;
            DoubleTapZoom doubleTap = new DoubleTapZoom(targetScale, e.getX(), e.getY());
            compatibilityPostOnAnimation(doubleTap);
            return true;
        }
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (fling != null) {
                fling.cancelFling();
            }
            fling = new Fling((int) velocityX, (int) velocityY);
            compatibilityPostOnAnimation(fling);
            return super.onFling(e1, e2, velocityX, velocityY);
        }
    }
    
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void compatibilityPostOnAnimation(Runnable runnable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            postOnAnimation(runnable);
        } else {
            postDelayed(runnable, 1000/60);
        }
    }
    
    private class DoubleTapZoom implements Runnable {
        private long startTime;
        private static final float ZOOM_TIME = 500;
        private float startZoom, targetZoom, bitmapX, bitmapY;
        private AccelerateDecelerateInterpolator interpolator = new AccelerateDecelerateInterpolator();
        private PointF startTouch, endTouch;
        
        DoubleTapZoom(float targetZoom, float focusX, float focusY) {
            isZooming = true;
            startTime = System.currentTimeMillis();
            this.startZoom = saveScale;
            this.targetZoom = targetZoom;
            PointF bitmapPoint = transformCoordTouchToBitmap(focusX, focusY, false);
            
            this.bitmapX = bitmapPoint.x;
            this.bitmapY = bitmapPoint.y;
            
            startTouch = transformCoordBitmapToTouch(bitmapX, bitmapY);
            endTouch = new PointF(width / 2, height / 2);
        }
        
        @Override
        public void run() {
            float t = interpolate();
            float deltaScale = calculateDeltaScale(t);
            scale(deltaScale, bitmapX, bitmapY);
            translateImageToCenterTouchPosition(t);
            fixScaleTrans();
            setImageMatrix(matrix);
            if (t < 1f) {
                compatibilityPostOnAnimation(this);
            } else {
                if (saveScale != targetZoom) {
                    scale (targetZoom/saveScale, bitmapX, bitmapY);
                    translateImageToCenterTouchPosition(t);
                    fixScaleTrans();
                    setImageMatrix(matrix);
                }
                isZooming = false;
            }
        }
        
        private float interpolate() {
            long currTime = System.currentTimeMillis();
            float elapsed = (currTime - startTime) / ZOOM_TIME;
            elapsed = Math.min(1f, elapsed);
            return interpolator.getInterpolation(elapsed);
        }
        
        private float calculateDeltaScale(float t) {
            float zoom = startZoom + t * (targetZoom - startZoom);
            return zoom / saveScale;
        }
        
        private void translateImageToCenterTouchPosition(float t) {
            float targetX = startTouch.x + t * (endTouch.x - startTouch.x);
            float targetY = startTouch.y + t * (endTouch.y - startTouch.y);
            PointF curr = transformCoordBitmapToTouch(bitmapX, bitmapY);
            matrix.postTranslate(targetX - curr.x, targetY - curr.y);
        }
        
        private PointF transformCoordTouchToBitmap(float x, float y, boolean clipToBitmap) {
            matrix.getValues(m);
            float origW = getDrawable().getIntrinsicWidth();
            float origH = getDrawable().getIntrinsicHeight();
            float transX = m[Matrix.MTRANS_X];
            float transY = m[Matrix.MTRANS_Y];
            float imageWidth = bmWidth*saveScale/realScale;
            float imageHeight = bmHeight*saveScale/realScale;
            float finalX = ((x - transX) * origW) / imageWidth;
            float finalY = ((y - transY) * origH) / imageHeight;
            if (clipToBitmap) {
                finalX = Math.min(Math.max(finalX, 0), origW);
                finalY = Math.min(Math.max(finalY, 0), origH);
            }
            return new PointF(finalX , finalY);
        }
        
        private PointF transformCoordBitmapToTouch(float bx, float by) {
            matrix.getValues(m);
            float origW = getDrawable().getIntrinsicWidth();
            float origH = getDrawable().getIntrinsicHeight();
            float px = bx / origW;
            float py = by / origH;
            float imageWidth = bmWidth*saveScale/realScale;
            float imageHeight = bmHeight*saveScale/realScale;
            float finalX = m[Matrix.MTRANS_X] + imageWidth * px;
            float finalY = m[Matrix.MTRANS_Y] + imageHeight * py;
            return new PointF(finalX , finalY);
        }
        
    }
    
    private interface IScroller {
        public void init(Context context);
        public void fling(int startX, int startY, int velocityX, int velocityY, int minX, int maxX, int minY, int maxY);
        public void forceFinished(boolean finished);
        public boolean isFinished();
        public boolean computeScrollOffset();
        public int getCurrX();
        public int getCurrY();
    }
    
    private static class OldScroller implements IScroller {
        private Scroller scroller;
        public void init(Context context) {
            scroller = new Scroller(context);
        }
        public void fling(int startX, int startY, int velocityX, int velocityY, int minX, int maxX, int minY, int maxY) {
            scroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY);            
        }
        public void forceFinished(boolean finished) {
            scroller.forceFinished(finished);            
        }
        public boolean isFinished() {
            return scroller.isFinished();
        }
        public boolean computeScrollOffset() {
            return scroller.computeScrollOffset();
        }
        public int getCurrX() {
            return scroller.getCurrX();
        }
        public int getCurrY() {
            return scroller.getCurrY();
        }        
    }
    
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private static class NewScroller implements TouchGifView.IScroller {
        private OverScroller overScroller;
        public void init(Context context) {
            overScroller = new OverScroller(context);
        }
        public void fling(int startX, int startY, int velocityX, int velocityY, int minX, int maxX, int minY, int maxY) {
            overScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY);
        }
        public void forceFinished(boolean finished) {
            overScroller.forceFinished(finished);
        }
        public boolean isFinished() {
            return overScroller.isFinished();
        }
        public boolean computeScrollOffset() {
            overScroller.computeScrollOffset();
            return overScroller.computeScrollOffset();
        }
        public int getCurrX() {
            return overScroller.getCurrX();
        }
        public int getCurrY() {
            return overScroller.getCurrY();
        }     
    }
    
    private class Fling implements Runnable {
        IScroller scroller;
        int currX, currY;
        Fling(int velocityX, int velocityY) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
                scroller = new OldScroller();
            } else {
                scroller = new NewScroller();
            }
            scroller.init(context);
            matrix.getValues(m);
            int startX = (int) m[Matrix.MTRANS_X];
            int startY = (int) m[Matrix.MTRANS_Y];
            int minX, maxX, minY, maxY;
            float imageWidth = bmWidth*saveScale/realScale;
            float imageHeight = bmHeight*saveScale/realScale;
            if (imageWidth > width) {
                minX = (int) (width - imageWidth);
                maxX = 0;
            } else {
                minX = maxX = startX;
            }
            if (imageHeight > height) {
                minY = (int) (height - imageHeight);
                maxY = 0;
            } else {
                minY = maxY = startY;
            }
            scroller.fling(startX, startY, (int) velocityX, (int) velocityY, minX, maxX, minY, maxY);
            currX = startX;
            currY = startY;
        }
        
        public void cancelFling() {
            if (scroller != null) {
                scroller.forceFinished(true);
            }
        }
        
        @Override
        public void run() {
            if (scroller.isFinished()) {
                scroller = null;
                return;
            }
            if (scroller.computeScrollOffset()) {
                int newX = scroller.getCurrX();
                int newY = scroller.getCurrY();
                int transX = newX - currX;
                int transY = newY - currY;
                currX = newX;
                currY = newY;
                matrix.postTranslate(transX, transY);
                fixTrans();
                setImageMatrix(matrix);
                compatibilityPostOnAnimation(this);
            }
        }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        width = MeasureSpec.getSize(widthMeasureSpec);
        height = MeasureSpec.getSize(heightMeasureSpec);
        //default scale.
        float scaleX =  (float)width / (float)bmWidth;
        float scaleY = (float)height / (float)bmHeight;
        float scale = Math.min(scaleX, scaleY); //to fit the screen
        defaultScale = Math.min(cDefaultScale, scale);
        minScale = Math.min(cMinScale, scale)/defaultScale;
        maxScale = cMaxScale/defaultScale;
        realScale = 1f/defaultScale;
        matrix.setScale(defaultScale, defaultScale);
        setImageMatrix(matrix);
        saveScale = 1f;
    
        //Center the image
        redundantYSpace = (float)height - (defaultScale * (float)bmHeight) ;
        redundantXSpace = (float)width - (defaultScale * (float)bmWidth);
        redundantYSpace /= (float)2;
        redundantXSpace /= (float)2;
    
        matrix.postTranslate(redundantXSpace, redundantYSpace);
    
        origWidth = width - 2 * redundantXSpace;
        origHeight = height - 2 * redundantYSpace;
        right = width * saveScale - width - (2 * redundantXSpace * saveScale);
        bottom = height * saveScale - height - (2 * redundantYSpace * saveScale);
        setImageMatrix(matrix);
    }
    
    @Override
    public boolean canScrollHorizontally(int direction) {
        matrix.getValues(m);
        float x = m[Matrix.MTRANS_X];
        float imageWidth = bmWidth*saveScale/realScale;
        if (imageWidth < width) {
            return false;
        } else if (x >= -1 && direction < 0) {
            return false;
        } else if (Math.abs(x) + width + 1 >= imageWidth && direction > 0) {
            return false;
        }
        return true;
    }
    
    @Override
    public boolean canScrollVertically(int direction) {
        matrix.getValues(m);
        float y = m[Matrix.MTRANS_Y];
        float imageHeight = bmHeight*saveScale/realScale;
        if (imageHeight < height) {
            return false;
        } else if (y >= -1 && direction < 0) {
            return false;
        } else if (Math.abs(y) + height + 1 >= imageHeight && direction > 0) {
            return false;
        }
        return true;
    }
    
    public boolean canScrollHorizontallyOldAPI(int direction) {
        return canScrollHorizontally(direction);
    }
    
    public boolean canScrollVerticallyOldAPI(int direction) {
        return canScrollVertically(direction);
    }
    
}
