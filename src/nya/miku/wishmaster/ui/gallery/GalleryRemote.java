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

import java.io.File;

import android.graphics.Bitmap;
import nya.miku.wishmaster.common.Logger;

public class GalleryRemote {
    private static final String TAG = "GalleryContextBinder";
    
    public final GalleryBinder binder;
    public final int contextId;
    
    public GalleryRemote(GalleryBinder binder, int contextId) {
        this.binder = binder;
        this.contextId = contextId;
    }
    
    public GalleryInitResult getInitResult() {
        try {
            GalleryInitResult result = binder.getInitResult(contextId);
            if (result == null || result.attachments == null) {
                Logger.e(TAG, "returned null");
                return null;
            }
            return result;
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public boolean isPageLoaded(String pagehash) {
        try {
            return binder.isPageLoaded(pagehash);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return false;
        }
    }
    
    public Bitmap getBitmapFromMemory(String hash) {
        try {
            return binder.getBitmapFromMemory(contextId, hash);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public Bitmap getBitmap(String hash, String url) {
        try {
            return binder.getBitmap(contextId, hash, url);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public File getAttachment(GalleryAttachmentInfo attachment, GalleryGetterCallback callback) {
        try {
            String path = binder.getAttachment(contextId, attachment, callback);
            if (path == null) return null;
            return new File(path);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public String getAbsoluteUrl(String url) {
        try {
            return binder.getAbsoluteUrl(contextId, url);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public void tryScrollParent(String postNumber) {
        try {
            binder.tryScrollParent(contextId, postNumber);
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
    }
}
