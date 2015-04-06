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

import android.text.Selection;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.TextView;

/**
 * Исправлено поведение при долгом нажатии на ссылку
 * @author miku-nyan
 *
 */
public class FixedLinkMovementMethod extends LinkMovementMethod {
    
    private boolean skipNextClick = false;
    
    private FixedLinkMovementMethod() {}
    private static LinkMovementMethod sInstance;
    public static MovementMethod getInstance() {
        if (sInstance == null) sInstance = new FixedLinkMovementMethod();
        return sInstance;
    }
    
    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getAction();
        if (skipNextClick && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN)) {
            skipNextClick = false;
            return false;
        }
        return super.onTouchEvent(widget, buffer, event);
    }

    @Override
    public void initialize(TextView widget, final Spannable text) {
        widget.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                skipNextClick = true;
                Selection.removeSelection(text);
                return false;
            }
        });
        super.initialize(widget, text);
    }
}