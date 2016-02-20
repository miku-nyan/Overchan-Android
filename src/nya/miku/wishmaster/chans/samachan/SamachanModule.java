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

package nya.miku.wishmaster.chans.samachan;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class SamachanModule extends AbstractVichanModule {
    private static final String CHAN_NAME = "samachan.org";

    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Anime/Japan", " ", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "z", "Everything", " ", true)
    };

    private static final String[] ATTACHMENT_FORMATS = new String[] {
            "jpg", "png", "gif", "mp3", "mp4", "webm"
    };

    public SamachanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }

    @Override
    public String getChanName() {
        return CHAN_NAME;
    }

    @Override
    public String getDisplayingName() {
        return "Samachan";
    }

    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_samachan, null);
    }

    @Override
    protected String getUsingDomain() {
        return CHAN_NAME;
    }

    @Override
    protected boolean canHttps() {
        return false;
    }

    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }

    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        return model;
    }

    @Override
    protected AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        String ext = object.optString("ext", "");
        if (!ext.equals("")) {
            AttachmentModel attachment = new AttachmentModel();
            switch (ext) {
                case ".jpeg":
                case ".jpg":
                case ".png":
                    attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                    break;
                case ".gif":
                    attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
                    break;
                case ".mp3":
                    attachment.type = AttachmentModel.TYPE_AUDIO;
                    break;
                case ".webm":
                case ".mp4":
                    attachment.type = AttachmentModel.TYPE_VIDEO;
                    break;
                default:
                    attachment.type = AttachmentModel.TYPE_OTHER_FILE;
            }
            attachment.size = object.optInt("fsize", -1);
            if (attachment.size > 0) attachment.size = Math.round(attachment.size / 1024f);
            attachment.width = object.optInt("w", -1);
            attachment.height = object.optInt("h", -1);
            attachment.originalName = object.optString("filename", "") + ext;
            attachment.isSpoiler = isSpoiler;
            String tim = object.optString("tim", "");
            if (tim.length() > 0 && !ext.equals(".mp3") && !ext.equals(".webm")) {
                attachment.thumbnail = isSpoiler ? null : ("/" + boardName + "/thumb/" + tim + ".jpg");
            }
            attachment.path = "/" + boardName + "/src/" + tim + ext;
            return attachment;
        }
        return null;
    }

    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        super.sendPost(model, listener, task);
        return null;
    }

    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        return super.parseUrl(url.replaceAll("-\\w+.*html", ".html"));
    }
}
