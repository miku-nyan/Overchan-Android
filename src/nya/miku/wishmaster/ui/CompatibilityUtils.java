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

package nya.miku.wishmaster.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.os.Environment;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class CompatibilityUtils {
    
    @SuppressWarnings("deprecation")
    public static void getDisplaySize(Display display, Point outSize) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR2) {
            outSize.set(display.getWidth(), display.getHeight());
        } else {
            CompatibilityImpl.getDisplaySize(display, outSize);
        }
    }
    
    @SuppressWarnings("deprecation")
    public static void removeOnGlobalLayoutListener(View view, OnGlobalLayoutListener onGlobalLayoutListener) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            view.getViewTreeObserver().removeGlobalOnLayoutListener(onGlobalLayoutListener);
        } else {
            CompatibilityImpl.removeOnGlobalLayoutListener(view, onGlobalLayoutListener);
        }
    }
    
    @SuppressWarnings("deprecation")
    public static void setImageAlpha(ImageView imageView, int alphaValue) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            imageView.setAlpha((int) alphaValue);
        } else {
            CompatibilityImpl.setImageAlpha(imageView, alphaValue);
        }
    }
    
    @SuppressWarnings("deprecation")
    public static int getColor(Resources resources, int id) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return resources.getColor(id);
        } else {
            return CompatibilityImpl.getColor(resources, id);
        }
    }
    
    @SuppressWarnings("deprecation")
    public static ColorStateList getColorStateList(Resources resources, int id) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return resources.getColorStateList(id);
        } else {
            return CompatibilityImpl.getColorStateList(resources, id);
        }
    }
    
    @SuppressWarnings("deprecation")
    public static void setTextAppearance(TextView textView, int resId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            textView.setTextAppearance(textView.getContext(), resId);
        } else {
            CompatibilityImpl.setTextAppearance(textView, resId);
        }
    }
    
    public static boolean hasAccessStorage(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        } else {
            if (!CompatibilityImpl.hasAccessStorage(activity)) return false;
            
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) && !Environment.getExternalStorageDirectory().canRead()) {
                //https://code.google.com/p/android-developer-preview/issues/detail?id=2982
                Toast.makeText(activity, "You should restart the application", Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        }
    }
}
