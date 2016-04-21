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

package nya.miku.wishmaster.ui.gallery;

import nya.miku.wishmaster.ui.gallery.GalleryInteractiveExceptionHolder;

interface GalleryGetterCallback {
    boolean isTaskCancelled();
    
    void showLoading();
    
    void setProgressMaxValue(long value);
    void setProgress(long value);
    void setProgressIndeterminate();
    
    void onException(String message);
    void onInteractiveException(in GalleryInteractiveExceptionHolder holder);
}