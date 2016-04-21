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

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.util.ChanModels;

public class GalleryInitData implements Parcelable {
    public AttachmentModel attachment;
    public String attachmentHash;
    public BoardModel boardModel;
    public String pageHash;
    public String localFileName;
    
    public GalleryInitData(Intent intent, Bundle bundle) {
        attachment = (AttachmentModel) intent.getSerializableExtra(GalleryActivity.EXTRA_ATTACHMENT);
        boardModel = (BoardModel) intent.getSerializableExtra(GalleryActivity.EXTRA_BOARDMODEL);
        if (boardModel == null) throw new NullPointerException();
        pageHash = intent.getStringExtra(GalleryActivity.EXTRA_PAGEHASH);
        localFileName = intent.getStringExtra(GalleryActivity.EXTRA_LOCALFILENAME);
        attachmentHash = intent.getStringExtra(GalleryActivity.EXTRA_SAVED_ATTACHMENTHASH);
        if (attachmentHash == null && bundle != null) attachmentHash = bundle.getString(GalleryActivity.EXTRA_SAVED_ATTACHMENTHASH);
        if (attachmentHash == null) attachmentHash = ChanModels.hashAttachmentModel(attachment);
    }
    
    public GalleryInitData(Parcel parcel) {
        attachment = (AttachmentModel) parcel.readSerializable();
        attachmentHash = parcel.readString();
        boardModel = (BoardModel) parcel.readSerializable();
        pageHash = parcel.readString();
        localFileName = parcel.readString();
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(attachment);
        dest.writeString(attachmentHash);
        dest.writeSerializable(boardModel);
        dest.writeString(pageHash);
        dest.writeString(localFileName);
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public static final Parcelable.Creator<GalleryInitData> CREATOR = new Parcelable.Creator<GalleryInitData>() {
        @Override
        public GalleryInitData createFromParcel(Parcel source) {
            return new GalleryInitData(source);
        }
        
        @Override
        public GalleryInitData[] newArray(int size) {
            return new GalleryInitData[size];
        }
    };
    
}
