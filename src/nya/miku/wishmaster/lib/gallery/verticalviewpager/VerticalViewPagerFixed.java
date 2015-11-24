package nya.miku.wishmaster.lib.gallery.verticalviewpager;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.lib.gallery.FixedSubsamplingScaleImageView;
import nya.miku.wishmaster.lib.gallery.TouchGifView;
import nya.miku.wishmaster.lib.gallery.WebViewFixed;

public class VerticalViewPagerFixed extends VerticalViewPager {
    public VerticalViewPagerFixed(Context context) {
        super(context);
    }
    
    public VerticalViewPagerFixed(Context context, AttributeSet attrs) {
        super(context, attrs);
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
            if (isImmersiveSwipe(ev)) return false;
            return super.onInterceptTouchEvent(ev);
        } catch (Exception e) {
            Logger.e("VerticalViewPager", e);
            return false;
        }
    }
    
    @Override
    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (v instanceof FixedSubsamplingScaleImageView) {
                return ((FixedSubsamplingScaleImageView) v).canScrollVerticallyOldAPI(-dx);
            } else if (v instanceof WebViewFixed) {
                return ((WebViewFixed) v).canScrollVerticallyOldAPI(-dx);
            } else if (v instanceof TouchGifView) {
                return ((TouchGifView) v).canScrollVerticallyOldAPI(-dx); 
            }
        }
        return super.canScroll(v, checkV, dx, x, y);
    }
    
    private boolean helpImmersiveSwipe = false;
    private int immersiveSwipeHeight;
    private boolean isImmersiveSwipe(MotionEvent ev) {
        if (!helpImmersiveSwipe || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return false;
        return Math.min(ev.getY(), getHeight() - ev.getY()) < immersiveSwipeHeight;
    }
    
    public void setHelpImmersiveSwipe(int pxHeight) {
        helpImmersiveSwipe = pxHeight > 0;
        immersiveSwipeHeight = pxHeight;
    }
    
    public static View wrap(final View view, final Runnable callback) {
        return wrap(view, callback, false);
    }
    
    public static View wrap(final View view, final Runnable callback, boolean helpImmersiveSwipe) {
        VerticalViewPagerFixed viewPager = new VerticalViewPagerFixed(view.getContext());
        viewPager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return 3;
            }
            @Override
            public boolean isViewFromObject(View view, Object object) {
                return view == object;
            }
            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                View v = position == 1 ? view : new View(container.getContext());
                container.addView(v);
                return v;
            }
            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object); 
            }
        });
        viewPager.setOnPageChangeListener(new VerticalViewPagerFixed.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (position != 1) callback.run();
            }
        });
        viewPager.setCurrentItem(1);
        viewPager.setTag(view);
        if (helpImmersiveSwipe) viewPager.setHelpImmersiveSwipe((int) (20 * view.getResources().getDisplayMetrics().density + 0.5f));
        return viewPager;
    }
    
}
