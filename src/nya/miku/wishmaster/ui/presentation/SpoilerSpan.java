/*
 * Overchan Android (Meta Imageboard Client)
 * Copyright (C) 2014-2015  miku-nyan <https://github.com/miku-nyan>
 *     
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nya.miku.wishmaster.ui.presentation;

import nya.miku.wishmaster.common.Logger;
import android.graphics.Color;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

public class SpoilerSpan extends ClickableSpan {
    private static final String TAG = "SpoilerSpan";
    
    private final int foregroundColor, backgroundColor;
    private boolean hidden = true;
    
    public SpoilerSpan(int foregroundColor, int backgroundColor) {
        this.foregroundColor = foregroundColor;
        this.backgroundColor = backgroundColor;
    }
    
    @Override
    public void onClick(View widget) {
        hidden = !hidden;
        try {
            TextView tv = (TextView) widget;
            Spannable text = (Spannable) tv.getText();
            Object span = new ForegroundColorSpan(Color.BLACK);
            text.setSpan(span, 0, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.removeSpan(span);
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        widget.invalidate();
    }
    
    @Override
    public void updateDrawState(TextPaint ds) {
        ds.bgColor = backgroundColor;
        ds.setColor(hidden ? backgroundColor : foregroundColor);
    }
    
}
