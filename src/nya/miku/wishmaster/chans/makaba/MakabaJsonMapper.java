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

package nya.miku.wishmaster.chans.makaba;

import static nya.miku.wishmaster.chans.makaba.MakabaConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

import org.apache.commons.lang3.StringEscapeUtils;

import android.content.res.Resources;

/**
 * Методы преобразования JSON-объектов с 2ch.hk в универсальные модели
 * @author miku-nyan
 *
 */
public class MakabaJsonMapper {
    private static final Pattern ICON_PATTERN = Pattern.compile("<img.+?src=\"(.+?)\".+?(?:title=\"(.+?)\")?.*?/>");
    
    static BoardModel defaultBoardModel(String boardName, Resources resources) {
        BoardModel model = new BoardModel();
        model.chan = CHAN_NAME;
        model.uniqueAttachmentNames = true;
        model.timeZoneId = "GMT+3";
        model.readonlyBoard = false;
        model.allowDeletePosts = false;
        model.allowDeleteFiles = false;
        model.allowReport = BoardModel.REPORT_WITH_COMMENT;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = true;
        model.allowCustomMark = true;
        model.allowRandomHash = true;
        model.searchAllowed = true;
        model.catalogAllowed = true;
        model.catalogTypeDescriptions = new String[] {
                resources.getString(R.string.makaba_catalog_standart),
                //resources.getString(R.string.makaba_catalog_last_reply),
                resources.getString(R.string.makaba_catalog_num),
                //resources.getString(R.string.makaba_catalog_image_size)
        };
        model.firstPage = 0;
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        model.markType = BoardModel.MARK_BBCODE;
        
        model.boardName = boardName;
        model.boardDescription = boardName;
        model.boardCategory = "";
        
        model.defaultUserName = "Аноним";
        model.bumpLimit = 500;
        model.lastPage = 9;
        
        model.nsfw = SFW_BOARDS.indexOf(model.boardName) == -1;
        model.requiredFileForNewThread = NO_IMAGES_BOARDS.indexOf(model.boardName) == -1;
        model.attachmentsMaxCount = NO_IMAGES_BOARDS.indexOf(model.boardName) == -1 ? 4 : 0;
        model.allowSubjects = NO_SUBJECTS_BOARDS.indexOf(model.boardName) == -1;
        model.allowNames = NO_USERNAMES_BOARDS.indexOf(model.boardName) == -1;
        
        return model;
    }
    
    static BoardModel mapBoardModel(JSONObject source, boolean fromMobileBoardsList, Resources resources) throws JSONException {
        BoardModel model = defaultBoardModel(source.getString(fromMobileBoardsList ? "id" : "Board"), resources);
        
        if (fromMobileBoardsList) {
            model.boardDescription = source.getString("name");
            model.boardCategory = source.getString("category");
            
            model.defaultUserName = getStringSafe(source, "default_name", "Аноним");
            model.bumpLimit = getIntSafe(source, "bump_limit", 500);
            model.lastPage = getIntSafe(source, "pages", 10) - 1;
        } else {
            model.boardDescription = source.getString("BoardName");
            model.boardCategory = null;
            
            model.defaultUserName = "Аноним";
            model.bumpLimit = 500;
            try {
                model.lastPage = source.getJSONArray("pages").length() - 1;
            } catch (Exception e) {
                model.lastPage = 9;
            }
        }
        
        try {
            JSONArray iconsArray = source.getJSONArray("icons");
            if (iconsArray.length() > 0) {
                String[] icons = new String[iconsArray.length() + 1];
                icons[0] = resources.getString(R.string.makaba_no_icon);
                for (int i=0; i<iconsArray.length(); ++i) {
                    icons[iconsArray.getJSONObject(i).getInt("num")] = iconsArray.getJSONObject(i).getString("name");
                }
                for (int i=0; i<icons.length; ++i) {
                    if (icons[i] == null) throw new Exception();
                }
                model.allowIcons = true;
                model.iconDescriptions = icons;
            }
        } catch (Exception e) { /* щито поделать, десу, получить список иконок не удалось, или их просто нет */ }
        
        return model;
    }
    
    static ThreadModel mapThreadModel(JSONObject source, String boardName) throws JSONException {
        ThreadModel model = new ThreadModel();
        model.threadNumber = source.getString("thread_num");
        model.postsCount = source.getInt("posts_count");
        model.attachmentsCount = source.getInt("files_count");
        JSONArray postsArray = source.getJSONArray("posts");
        model.postsCount += postsArray.length();
        model.posts = new PostModel[postsArray.length()];
        for (int i=0; i<postsArray.length(); ++i) {
            model.posts[i] = mapPostModel(postsArray.getJSONObject(i), boardName);
            if (postsArray.getJSONObject(i).has("files")) {
                model.attachmentsCount += postsArray.getJSONObject(i).getJSONArray("files").length();
            }
        }
        model.isSticky = getIntSafe(postsArray.getJSONObject(0), "sticky", 0) != 0;
        model.isClosed = getIntSafe(postsArray.getJSONObject(0), "closed", 0) != 0;
        return model;
    }
    
    static PostModel mapPostModel(JSONObject source, String boardName) throws JSONException {
        PostModel model = new PostModel();
        
        try {
            model.number = source.getString("num");
        } catch (JSONException e) {
            model.number = Long.toString(source.getLong("num"));
        }
        model.name = StringEscapeUtils.unescapeHtml4(RegexUtils.removeHtmlSpanTags(getStringSafe(source, "name", "")));
        model.subject = StringEscapeUtils.unescapeHtml4(getStringSafe(source, "subject", ""));
        model.comment = getStringSafe(source, "comment", "");
        model.email = getStringSafe(source, "email", "");
        if (model.email.startsWith("mailto:")) model.email = model.email.substring(7);
        model.trip = getStringSafe(source, "trip", "");
        if (model.trip != null) {
            if (model.trip.equals("!!%adm%!!")) model.trip = "## Abu ##";
            else if (model.trip.equals("!!%mod%!!")) model.trip = "## Mod ##";
            else if (model.trip.equals("!!%Inquisitor%!!")) model.trip = "## Applejack ##";
            else if (model.trip.equals("!!%coder%!!")) model.trip = "## Кодер ##";
        }
        model.icons = parseIcons(getStringSafe(source, "icon", ""));
        model.op = getIntSafe(source, "op", 0) == 1;
        model.sage = model.email.toLowerCase(Locale.US).contains("sage") || model.name.contains("ID:\u00A0Heaven");
        model.timestamp = source.getLong("timestamp") * 1000;
        model.parentThread = getStringSafe(source, "parent", model.number);
        if (model.parentThread.equals("0")) model.parentThread = model.number;
        
        if (source.has("files")) {
            JSONArray filesArray = source.getJSONArray("files");
            model.attachments = new AttachmentModel[filesArray.length()];
            for (int i=0; i<filesArray.length(); ++i) {
                model.attachments[i] = mapAttachmentModel(filesArray.getJSONObject(i), boardName);
            }
        } else model.attachments = null;
        
        int banned = getIntSafe(source, "banned", 0);
        switch (banned) {
            case 1:
                model.comment = model.comment + "<br/><em><font color=\"red\">(Автор этого поста был забанен. Помянем.)</font></em>";
                break;
            case 2:
                model.comment = model.comment + "<br/><em><font color=\"red\">(Автор этого поста был предупрежден.)</font></em>";
                break;
        }
        if (NO_SUBJECTS_BOARDS.indexOf(boardName) >= 0) model.subject = "";
        return model;
    }
    
    static AttachmentModel mapAttachmentModel(JSONObject source, String boardName) throws JSONException {
        AttachmentModel model = new AttachmentModel();
        try {
            model.size = source.getInt("size");
            model.width = source.getInt("width");
            model.height = source.getInt("height");
            model.thumbnail = fixAttachmentPath(source.getString("thumbnail"), boardName);
            model.path = fixAttachmentPath(source.getString("path"), boardName);
            String originalName = source.optString("fullname");
            if (originalName.length() > 0) model.originalName = originalName;
            model.type = AttachmentModel.TYPE_IMAGE_STATIC;
            String pathLower = model.path.toLowerCase(Locale.US);
            if (pathLower.endsWith(".gif")) model.type = AttachmentModel.TYPE_IMAGE_GIF;
            else if (pathLower.endsWith(".webm")) model.type = AttachmentModel.TYPE_VIDEO;
        } catch (Exception e) {
            if (source.has("path")) {
                model.type = AttachmentModel.TYPE_OTHER_FILE;
                model.path = fixAttachmentPath(source.getString("path"), boardName);
            } else {
                model.type = AttachmentModel.TYPE_OTHER_NOTFILE;
            }
        }
        return model;
    }
    
    static BadgeIconModel[] parseIcons(String html) {
        if (html == null || html.length() == 0) return null;
        Matcher m = ICON_PATTERN.matcher(html);
        List<BadgeIconModel> list = new ArrayList<BadgeIconModel>();
        while (m.find()) {
            BadgeIconModel icon = new BadgeIconModel();
            icon.source = m.group(1);
            icon.description = m.group(2);
            list.add(icon);
        }
        return list.toArray(new BadgeIconModel[list.size()]);
    }
    
    private static String getStringSafe(JSONObject object, String key, String defaultValue) {
        try {
            return object.getString(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private static int getIntSafe(JSONObject object, String key, int defaultValue) {
        try {
            return object.getInt(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private static String fixAttachmentPath(String url, String boardName) {
        if (url.startsWith("://")) return "http" + url;
        if (url.startsWith("/") || url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "/".concat(boardName).concat("/").concat(url);
    }
}
