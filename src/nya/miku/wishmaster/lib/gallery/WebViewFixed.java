package nya.miku.wishmaster.lib.gallery;

import nya.miku.wishmaster.common.Logger;
import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;

public class WebViewFixed extends WebView {
    private static final String TAG = "WebView";
    
    public WebViewFixed(Context context) {
        super(context);
    }
    
    public WebViewFixed(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    public WebViewFixed(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    /**
     * Fixes the onWindowFocusChanged bug, by catching NullPointerException.
     * https://groups.google.com/d/topic/android-developers/ktbwY2gtLKQ/discussion
     * 
     * @author Andrew
     * 
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        try {
            super.onWindowFocusChanged(hasWindowFocus);
        } catch (NullPointerException ex) {
            Logger.e(TAG, ex);
        }
    }
    
    /**
     * Метод {@link #canScrollHorizontally(int)} для использования на старых версиях Android
     */
    public boolean canScrollHorizontallyOldAPI(int direction) {
        final int offset = computeHorizontalScrollOffset();
        final int range = computeHorizontalScrollRange() - computeHorizontalScrollExtent();
        if (range == 0) return false;
        if (direction < 0) {
            return offset > 0;
        } else {
            return offset < range - 1;
        }
    }
    
    /**
     * Метод {@link #canScrollVertically(int)} для использования на старых версиях Android
     */
    public boolean canScrollVerticallyOldAPI(int direction) {
        final int offset = computeVerticalScrollOffset();
        final int range = computeVerticalScrollRange() - computeVerticalScrollExtent();
        if (range == 0) return false;
        if (direction < 0) {
            return offset > 0;
        } else {
            return offset < range - 1;
        }
    }
}
