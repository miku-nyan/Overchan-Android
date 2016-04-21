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
import nya.miku.wishmaster.api.models.AttachmentModel;

public class GalleryAttachmentInfo implements Parcelable {
    public AttachmentModel attachment;
    public String hash;
    
    public GalleryAttachmentInfo(AttachmentModel attachment, String hash) {
        this.attachment = attachment;
        this.hash = hash;
    }
    
    public GalleryAttachmentInfo(Parcel parcel) {
        hash = parcel.readString();
        attachment = new AttachmentModel();
        attachment.type = parcel.readInt();
        attachment.size = parcel.readInt();
        attachment.thumbnail = parcel.readString();
        attachment.path = parcel.readString();
        attachment.width = parcel.readInt();
        attachment.height = parcel.readInt();
        attachment.originalName = parcel.readString();
        attachment.isSpoiler = parcel.readInt() == 1;
    }
    
    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(hash);
        parcel.writeInt(attachment.type);
        parcel.writeInt(attachment.size);
        parcel.writeString(attachment.thumbnail);
        parcel.writeString(attachment.path);
        parcel.writeInt(attachment.width);
        parcel.writeInt(attachment.height);
        parcel.writeString(attachment.originalName);
        parcel.writeInt(attachment.isSpoiler ? 1 : 0);
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public static final Parcelable.Creator<GalleryAttachmentInfo> CREATOR = new Parcelable.Creator<GalleryAttachmentInfo>() {
        public GalleryAttachmentInfo createFromParcel(Parcel in) {
            return new GalleryAttachmentInfo(in);
        }
        
        public GalleryAttachmentInfo[] newArray(int size) {
            return new GalleryAttachmentInfo[size];
        }
    };
    
}
