/*
 * Отличие от ImageView - не подсвечивается при нажатии на родительский View.
 * Оригинальный класс из 2ch Browser (com.vortexwolf.chan.common.controls.NonParentFocusableImageView)
 * На Android >= 4.1 этот фикс не используется (используется стандартное поведение).
 * 
 */
package nya.miku.wishmaster.lib;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

public class NonParentFocusableImageView extends ImageView {
    
    private final boolean needHack = Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN;
    
    public NonParentFocusableImageView(Context context) {
        super(context);
    }
    
    public NonParentFocusableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public NonParentFocusableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    
    @Override
    public void setPressed(boolean pressed) {
        if (needHack && pressed && ((View) this.getParent()).isPressed()) return;
        super.setPressed(pressed);
    }
    
}
