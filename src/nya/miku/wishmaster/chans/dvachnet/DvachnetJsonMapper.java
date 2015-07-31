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

package nya.miku.wishmaster.chans.dvachnet;

import java.util.Locale;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class DvachnetJsonMapper {
    
    static BoardModel getDefaultBoardModel(String shortName, String boardDescription, String boardCategory, boolean nsfw) {
        BoardModel model = new BoardModel();
        model.chan = DvachnetModule.CHAN_NAME;
        model.boardName = shortName;
        model.boardDescription = boardDescription;
        model.boardCategory = boardCategory;
        model.nsfw = nsfw;
        model.uniqueAttachmentNames = true;
        model.timeZoneId = "GMT+3";
        model.defaultUserName = "Anon";
        model.bumpLimit = 500;
        model.readonlyBoard = false;
        model.requiredFileForNewThread = shortName.equals("d") ? false : true;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = false;
        model.allowReport = BoardModel.REPORT_NOT_ALLOWED;
        model.allowNames = !shortName.equals("b");
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = true;
        model.allowCustomMark = false;
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = shortName.equals("d") ? 0 : 1;
        model.attachmentsFormatFilters = null;
        model.markType = BoardModel.MARK_WAKABAMARK;
        model.firstPage = 0;
        model.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        model.searchAllowed = false;
        model.catalogAllowed = false;
        return model;
    }
    
    static BoardModel getDefaultBoardModel(SimpleBoardModel simpleModel) {
        return getDefaultBoardModel(simpleModel.boardName, simpleModel.boardDescription, simpleModel.boardCategory, simpleModel.nsfw);
    }
    
    static BoardModel mapBoardModel(JSONObject json, SimpleBoardModel simpleModel) throws Exception {
        BoardModel model = getDefaultBoardModel(simpleModel);
        String boardName = json.getString("board");
        if (!boardName.equals(simpleModel.boardName)) throw new Exception("wrong board name");
        String description = json.optString("board_name", "");
        if (!description.equals("")) model.boardDescription = description;
        model.requiredFileForNewThread = json.optInt("enable_images") == 1;
        model.attachmentsMaxCount = model.requiredFileForNewThread ? 1 : 0;
        model.allowNames = json.optInt("enable_names") == 1;
        model.allowSubjects = json.optInt("enable_subjects") == 1;
        try {
            int pages = json.getJSONArray("pages").length();
            if (pages > 0) model.lastPage = pages - 1;
        } catch (Exception e) {}
        return model;
    }
    
    static PostModel mapPostModel(JSONObject json) {
        PostModel model = new PostModel();
        try {
            model.number = json.getString("num");
        } catch (Exception e) {
            model.number = Long.toString(json.getLong("num"));
        }
        model.name = json.optString("name", "");
        model.subject = json.optString("subject", "");
        model.comment = json.optString("comment", "");
        model.email = json.optString("email", "");
        if (model.email.startsWith("mailto:")) model.email = model.email.substring(7);
        model.trip = json.optString("trip", "");
        model.sage = model.email.toLowerCase(Locale.US).contains("sage");
        model.timestamp = json.optLong("timestamp") * 1000;
        model.parentThread = json.optString("parent", "0");
        if (model.parentThread.equals("0")) model.parentThread = model.number;
        
        String path = json.optString("file_path", "");
        if (path.equals("")) {
            model.attachments = new AttachmentModel[0];
        } else {
            model.attachments = new AttachmentModel[] { new AttachmentModel() };
            AttachmentModel attachment = model.attachments[0];
            attachment.path = path;
            attachment.thumbnail = json.optString("thumbnail", null);
            attachment.width = json.optInt("file_width", -1);
            attachment.height = json.optInt("file_height", -1);
            attachment.size = json.optInt("file_size", -1);
            attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
            String pathLower = path.toLowerCase(Locale.US);
            if (pathLower.endsWith(".gif")) attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
            else if (pathLower.endsWith(".webm")) attachment.type = AttachmentModel.TYPE_VIDEO;
        }
        
        int banned = json.optInt("banned", 0);
        switch (banned) {
            case 1:
                model.comment += "<br/><em>(Автор этого поста был забанен. Помянем.)</em>";
                break;
            case 2:
                model.comment += "<br/><em>(Автор этого поста был предупрежден.)</em>";
                break;
        }
        return model;
    }
}
