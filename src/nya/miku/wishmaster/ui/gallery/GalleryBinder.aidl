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

import android.graphics.Bitmap;
import nya.miku.wishmaster.ui.gallery.GalleryAttachmentInfo;
import nya.miku.wishmaster.ui.gallery.GalleryGetterCallback;
import nya.miku.wishmaster.ui.gallery.GalleryInitData;
import nya.miku.wishmaster.ui.gallery.GalleryInitResult;

interface GalleryBinder {
    int initContext(in GalleryInitData initData);
    GalleryInitResult getInitResult(int contextId);
    Bitmap getBitmapFromMemory(int contextId, String hash);
    Bitmap getBitmap(int contextId, String hash, String url);
    String getAttachment(int contextId, in GalleryAttachmentInfo attachment, GalleryGetterCallback callback);
    String getAbsoluteUrl(int contextId, String url);
    void tryScrollParent(int contextId, String postNumber);
    boolean isPageLoaded(String pagehash);
}