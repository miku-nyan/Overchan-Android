package nya.miku.wishmaster.lib.gallery;

import nya.miku.wishmaster.common.Logger;
import android.content.Context;
import android.os.Build;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class ViewPagerFixed extends ViewPager {
    private GestureDetectorCompat gestureDetector = null;

    public ViewPagerFixed(Context context) {
        super(context);
    }

    public ViewPagerFixed(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void SetOnGestureListener(android.content.Context context, GestureDetector.SimpleOnGestureListener eventListener){
        if (context==null) {
            context = this.getContext();
        }
        gestureDetector = new GestureDetectorCompat(context, eventListener);
    }

    /**
     * Hacky fix for Issue #4 and
     * http://code.google.com/p/android/issues/detail?id=18990
     * <p/>
     * ScaleGestureDetector seems to mess up the touch events, which means that
     * ViewGroups which make use of onInterceptTouchEvent throw a lot of
     * IllegalArgumentException: pointerIndex out of range.
     * <p/>
     * There's not much I can do in my code for now, but we can mask the result by
     * just catching the problem and ignoring it.
     *
     * @author Chris Banes
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        try {
            if (gestureDetector!=null)
                if (gestureDetector.onTouchEvent(ev))
                    return true;
            return super.onInterceptTouchEvent(ev);
        } catch (Exception e) {
            Logger.e("ViewPager", e);
            return false;
        }
    }
    
    /**
     * Корректный скроллинг на ранних версиях Android
     */
    @Override
    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (Build.VERSION.SDK_INT < 14 && v instanceof WebViewFixed) {
            return ((WebViewFixed) v).canScrollHorizontallyOldAPI(-dx);
        } else if (Build.VERSION.SDK_INT < 14 && v instanceof TouchGifView) {
            return ((TouchGifView) v).canScrollHorizontallyOldAPI(-dx);
        } else {
            return super.canScroll(v, checkV, dx, x, y);
        }
    }
}