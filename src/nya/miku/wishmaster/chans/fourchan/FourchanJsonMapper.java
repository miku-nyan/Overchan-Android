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

package nya.miku.wishmaster.chans.fourchan;

import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class FourchanJsonMapper {
    private static final boolean LINKIFY = true;
    private static final String[] ATTACHMENT_FORMATS = new String[] { "jpg", "jpeg", "png", "gif", "webm" };
    
    private static final Pattern S_TAG = Pattern.compile("<(/?)s>");
    
    static BoardModel mapBoardModel(JSONObject object) {
        BoardModel model = getDefaultBoardModel(object.getString("board"));
        model.boardDescription = object.optString("title", model.boardName);
        model.nsfw = object.optInt("ws_board") == 0;
        model.bumpLimit = object.optInt("bump_limit", 300);
        model.lastPage = object.optInt("pages", 10);
        model.allowCustomMark = object.optInt("spoilers") == 1;
        return model;
    }
    
    static BoardModel getDefaultBoardModel(String boardName) {
        BoardModel model = new BoardModel();
        model.chan = FourchanModule.CHAN_NAME;
        model.boardName = boardName;
        model.boardDescription = boardName;
        model.boardCategory = null;
        model.nsfw = true;
        model.uniqueAttachmentNames = true;
        model.timeZoneId = "US/Eastern";
        model.defaultUserName = "Anonymous";
        model.bumpLimit = 300;
        model.readonlyBoard = false;
        model.requiredFileForNewThread = true;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = true;
        model.allowReport = BoardModel.REPORT_SIMPLE;
        model.allowNames = !model.boardName.equals("b");
        model.allowSubjects = !model.boardName.equals("b");
        model.allowSage = true;
        model.allowEmails = false;
        model.allowCustomMark = false;
        model.customMarkDescription = "Spoiler";
        model.allowRandomHash = false;
        model.allowIcons = false;
        model.attachmentsMaxCount = 1;
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        model.markType = BoardModel.MARK_4CHAN;
        model.firstPage = 1;
        model.lastPage = 10;
        model.searchAllowed = false;
        model.catalogAllowed = true;
        return model;
    }
    
    static PostModel mapPostModel(JSONObject object, String boardName) {
        PostModel model = new PostModel();
        model.number = Long.toString(object.getLong("no"));
        model.name = StringEscapeUtils.unescapeHtml4(RegexUtils.removeHtmlSpanTags(object.optString("name", "Anonymous")));
        model.subject = StringEscapeUtils.unescapeHtml4(object.optString("sub", ""));
        String comment = object.optString("com", "");
        comment = RegexUtils.replaceAll(comment, S_TAG, "<$1aibspoiler>");
        model.comment = LINKIFY ? RegexUtils.linkify(comment) : comment;
        model.email = null;
        model.trip = object.optString("trip", "");
        String capcode = object.optString("capcode", "none");
        if (!capcode.equals("none")) model.trip += "##"+capcode;
        String countryIcon = object.optString("country", "");
        if (!countryIcon.equals("")) {
            BadgeIconModel icon = new BadgeIconModel();
            icon.source = "s.4cdn.org/image/country/" + /*(boardName.equals("pol") ? "troll/" : "") + */countryIcon.toLowerCase(Locale.US) + ".gif";
            icon.description = object.optString("country_name");
            model.icons = new BadgeIconModel[] { icon };
        }
        model.op = false;
        String id = object.optString("id", "");
        model.sage = id.equalsIgnoreCase("Heaven");
        if (!id.equals("")) model.name += (" ID:" + id);
        if (!id.equals("") && !id.equalsIgnoreCase("Heaven")) model.color = CryptoUtils.hashIdColor(id);
        model.timestamp = object.getLong("time") * 1000;
        model.parentThread = object.optString("resto", "0");
        if (model.parentThread.equals("0")) model.parentThread = model.number;
        String ext = object.optString("ext", "");
        if (!ext.equals("")) {
            AttachmentModel attachment = new AttachmentModel();
            switch (ext) {
                case ".jpg":
                case ".png":
                    attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                    break;
                case ".gif":
                    attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
                    break;
                case ".webm":
                    attachment.type = AttachmentModel.TYPE_VIDEO;
                    break;
                default:
                    attachment.type = AttachmentModel.TYPE_OTHER_FILE;
            }
            attachment.size = object.optInt("fsize", -1);
            if (attachment.size > 0) attachment.size = Math.round(attachment.size / 1024f);
            attachment.width = object.optInt("w", -1);
            attachment.height = object.optInt("h", -1);
            attachment.originalName = StringEscapeUtils.unescapeHtml4(object.optString("filename")) + ext;
            attachment.isSpoiler = object.optInt("spoiler") == 1;
            long tim = object.optLong("tim");
            if (tim != 0) {
                attachment.thumbnail = "t.4cdn.org/" + boardName + "/" + Long.toString(tim) + "s.jpg";
                attachment.path = "i.4cdn.org/" + boardName + "/" + Long.toString(tim) + ext;
                model.attachments = new AttachmentModel[] { attachment };
            }
            
        }
        return model;
    }
}
