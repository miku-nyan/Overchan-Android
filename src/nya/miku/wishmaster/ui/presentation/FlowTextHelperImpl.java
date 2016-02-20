/*
 * Overchan Android (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
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

import android.annotation.TargetApi;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.LeadingMarginSpan.LeadingMarginSpan2;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Реализация методов для установки обтекания текстом. В целях совместимости с Android < 2.2
 * @author miku-nyan
 *
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class FlowTextHelperImpl {
    public static boolean flowText(SpannableStringBuilder ss, int width, int height, int textFullWidth, TextPaint textPaint) {
        StaticLayout l = new StaticLayout(ss, 0, ss.length(), textPaint, textFullWidth - width, Layout.Alignment.ALIGN_NORMAL, 1.0F, 0, false);
        int lines = 0;
        while (lines < l.getLineCount() && l.getLineBottom(lines) < height) ++lines;
        ++lines;
        
        int endPos;
        if (lines < l.getLineCount()) {
            endPos = l.getLineStart(lines);
        } else {
            return false;
        }
        if (ss.charAt(endPos-1) != '\n' && ss.charAt(endPos-1) != '\r') {
            if (ss.charAt(endPos-1) == ' ') {
                ss.replace(endPos-1, endPos, "\n");
            } else {
                ss.insert(endPos, "\n");
            }
        }
        
        ss.setSpan(new FloatingMarginSpan(lines, width), 0, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return true;
    }
    
    public static void setFloatLayoutPosition(View thumbnailView, TextView messageView) {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)messageView.getLayoutParams();
        params.addRule(RelativeLayout.RIGHT_OF, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            removeEndOfRule(params);
        }
        thumbnailView.bringToFront();
    }
    
    public static void setDefaultLayoutPosition(View thumbnailView, TextView messageView) {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)messageView.getLayoutParams();
        params.addRule(RelativeLayout.RIGHT_OF, thumbnailView.getId());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            addEndOfRule(params, thumbnailView.getId());
        }
    }
    
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private static void addEndOfRule(RelativeLayout.LayoutParams params, int anchor) {
        params.addRule(RelativeLayout.END_OF, anchor);
    }
    
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private static void removeEndOfRule(RelativeLayout.LayoutParams params) {
        params.removeRule(RelativeLayout.END_OF);
    }
    
    private static class FloatingMarginSpan implements LeadingMarginSpan2 {
        private int margin;
        private int lines;

        private FloatingMarginSpan(int lines, int margin) {
            this.margin = margin;
            this.lines = lines;
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return first ? margin : 0;
        }

        @Override
        public int getLeadingMarginLineCount() {
            return lines;
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, 
                CharSequence text, int start, int end, boolean first, Layout layout) {}

    }
    
    public static int getFloatingPosition(Spanned spanned) {
        for (FloatingMarginSpan span : spanned.getSpans(0, spanned.length(), FloatingMarginSpan.class)) {
            return spanned.getSpanEnd(span);
        }
        return -1;
    }
}
