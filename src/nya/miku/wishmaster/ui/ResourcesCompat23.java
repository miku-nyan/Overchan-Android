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

package nya.miku.wishmaster.ui;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Build;
import android.widget.TextView;

public class ResourcesCompat23 {
    
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
}
