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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Triple;

import android.os.Parcel;
import android.os.Parcelable;
import nya.miku.wishmaster.api.models.AttachmentModel;

public class GalleryInitResult implements Parcelable {
    public int contextId;
    public List<Triple<AttachmentModel, String, String>> attachments;
    public int initPosition;
    public boolean shouldWaitForPageLoaded;
    
    public GalleryInitResult() {
    }
    
    public GalleryInitResult(Parcel parcel) {
        contextId = parcel.readInt();
        initPosition = parcel.readInt();
        shouldWaitForPageLoaded = parcel.readInt() == 1;
        int n = parcel.readInt();
        attachments = new ArrayList<>(n);
        for (int i=0; i<n; ++i) {
            AttachmentModel attachment = new AttachmentModel();
            attachment.type = parcel.readInt();
            attachment.size = parcel.readInt();
            attachment.thumbnail = parcel.readString();
            attachment.path = parcel.readString();
            attachment.width = parcel.readInt();
            attachment.height = parcel.readInt();
            attachment.originalName = parcel.readString();
            attachment.isSpoiler = parcel.readInt() == 1;
            String hash = parcel.readString();
            String post = parcel.readString();
            attachments.add(Triple.of(attachment, hash, post));
        }
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(contextId);
        dest.writeInt(initPosition);
        dest.writeInt(shouldWaitForPageLoaded ? 1 : 0);
        dest.writeInt(attachments.size());
        for (Triple<AttachmentModel, String, String> tuple : attachments) {
            AttachmentModel attachment = tuple.getLeft();
            dest.writeInt(attachment.type);
            dest.writeInt(attachment.size);
            dest.writeString(attachment.thumbnail);
            dest.writeString(attachment.path);
            dest.writeInt(attachment.width);
            dest.writeInt(attachment.height);
            dest.writeString(attachment.originalName);
            dest.writeInt(attachment.isSpoiler ? 1 : 0);
            dest.writeString(tuple.getMiddle());
            dest.writeString(tuple.getRight());
        }
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public static final Parcelable.Creator<GalleryInitResult> CREATOR = new Parcelable.Creator<GalleryInitResult>() {
        @Override
        public GalleryInitResult createFromParcel(Parcel source) {
            return new GalleryInitResult(source);
        }
        
        @Override
        public GalleryInitResult[] newArray(int size) {
            return new GalleryInitResult[size];
        }
    };
    
}
