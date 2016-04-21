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

import android.os.Parcel;
import android.os.Parcelable;
import nya.miku.wishmaster.http.interactive.InteractiveException;

public class GalleryInteractiveExceptionHolder implements Parcelable {
    public final InteractiveException e;
    
    public GalleryInteractiveExceptionHolder(InteractiveException e) {
        this.e = e;
    }
    
    public GalleryInteractiveExceptionHolder(Parcel parcel) {
        e = (InteractiveException) parcel.readSerializable();
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(e);
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public static final Parcelable.Creator<GalleryInteractiveExceptionHolder> CREATOR = new Parcelable.Creator<GalleryInteractiveExceptionHolder>() {
        @Override
        public GalleryInteractiveExceptionHolder createFromParcel(Parcel source) {
            return new GalleryInteractiveExceptionHolder(source);
        }
        
        @Override
        public GalleryInteractiveExceptionHolder[] newArray(int size) {
            return new GalleryInteractiveExceptionHolder[size];
        }
    };
    
}
