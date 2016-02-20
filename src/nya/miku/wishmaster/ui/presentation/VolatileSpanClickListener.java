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

import java.lang.ref.WeakReference;

import android.view.View;

public class VolatileSpanClickListener implements ClickableURLSpan.URLSpanClickListener {
    
    public interface Listener {
        public void onURLSpanClick(View v, ClickableURLSpan span, String url, String referer);
    }
    
    private volatile WeakReference<Listener> listenerRef;
    
    public VolatileSpanClickListener(Listener listener) {
        this.listenerRef = new WeakReference<>(listener);
    }
    
    public void setListener(Listener listener) {
        this.listenerRef = new WeakReference<>(listener);
    }
    
    @Override
    public void onClick(View v, ClickableURLSpan span, String url, String referer) {
        if (listenerRef != null) {
            Listener listener = listenerRef.get();
            if (listener != null) {
                listener.onURLSpanClick(v, span, url, referer);
            }
        }
    }
}