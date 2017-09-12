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

package nya.miku.wishmaster.chans.chan420;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.lang3.StringEscapeUtils;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class Chan420JsonMapper {
    private static class OrderComparator implements Comparator<JSONObject> {
        @Override
        public int compare(JSONObject lhs, JSONObject rhs) {
            return lhs.optInt("display_order") - rhs.optInt("display_order");
        }
    }
    
    static List<SimpleBoardModel> mapBoards(JSONObject categories, JSONObject boards) {
        ArrayList<JSONObject> catsArray = new ArrayList<>();
        JSONArray catsJsonArray = categories.getJSONArray("categories");
        for (int i=0; i<catsJsonArray.length(); ++i) catsArray.add(catsJsonArray.getJSONObject(i));
        Collections.sort(catsArray, new OrderComparator());
        
        ArrayList<JSONObject> boardsArray = new ArrayList<>();
        JSONArray boardsJsonArray = boards.getJSONArray("boards");
        for (int i=0; i<boardsJsonArray.length(); ++i) boardsArray.add(boardsJsonArray.getJSONObject(i));
        Collections.sort(boardsArray, new OrderComparator());
        
        List<SimpleBoardModel> list = new ArrayList<SimpleBoardModel>();
        for (JSONObject category : catsArray) {
            int catId = category.optInt("id");
            String catName = category.optString("title", "");
            boolean catNsfw = category.optInt("nws_category") == 1;
            for (JSONObject board : boardsArray) {
                if (board.optBoolean("picked")) continue;
                if (board.optInt("category") == catId) {
                    board.put("picked", true);
                    list.add(ChanModels.obtainSimpleBoardModel(Chan420Module.CHAN_NAME, board.getString("board"),
                            StringEscapeUtils.unescapeHtml4(board.getString("title")), catName, (catNsfw || (board.optInt("nws_board") == 1))));
                }
            }
        }
        for (JSONObject board : boardsArray) {
            if (board.optBoolean("picked")) continue;
            list.add(ChanModels.obtainSimpleBoardModel(Chan420Module.CHAN_NAME, board.getString("board"),
                    StringEscapeUtils.unescapeHtml4(board.getString("title")), "", (board.optInt("nws_board") == 1)));
        }
        return list;
    }
    
    static BoardModel getDefaultBoardModel(String boardName) {
        BoardModel model = new BoardModel();
        model.chan = Chan420Module.CHAN_NAME;
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
        model.allowDeletePosts = false;
        model.allowDeleteFiles = false;
        model.allowReport = BoardModel.REPORT_WITH_COMMENT;
        model.allowNames = !boardName.equals("b") && !boardName.equals("420");
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = false;
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = 1;
        model.markType = BoardModel.MARK_BBCODE;
        model.firstPage = 0;
        model.lastPage = 0;
        model.searchAllowed = false;
        model.catalogAllowed = false;
        return model;
    }

    public static PostModel mapPostModel(JSONObject object, String boardName) {
        PostModel model = new PostModel();
        model.number = Long.toString(object.getLong("no"));
        model.name = StringEscapeUtils.unescapeHtml4(toUtf8(RegexUtils.removeHtmlSpanTags(object.optString("name", "Anonymous"))));
        model.subject = StringEscapeUtils.unescapeHtml4(toUtf8(object.optString("sub", "")));
        model.comment = toUtf8(object.optString("com", ""));
        model.email = null;
        model.trip = object.optString("trip", "");
        model.op = false;
        String id = object.optString("id", "");
        model.sage = id.equalsIgnoreCase("Heaven");
        if (!id.isEmpty()) model.name += (" ID:" + id);
        model.timestamp = object.getLong("time") * 1000;
        model.parentThread = object.optString("resto", "0");
        if (model.parentThread.equals("0")) model.parentThread = model.number;
        model.comment = toHtml(model.comment, boardName, model.parentThread);
        String ext = object.optString("ext", "");
        if (!ext.isEmpty()) {
            AttachmentModel attachment = new AttachmentModel();
            switch (ext) {
                case ".jpg":
                case ".png":
                    attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                    break;
                case ".gif":
                    attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
                    break;
                case ".svg":
                case ".svgz":
                    attachment.type = AttachmentModel.TYPE_IMAGE_SVG;
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
            attachment.originalName = object.optString("filename", "") + ext;
            attachment.isSpoiler = object.optInt("spoiler") == 1;
            long tim = object.optLong("filename");
            if (tim != 0) {
                attachment.path = "/" + boardName + "/src/" + Long.toString(tim) + ext;
                if ((attachment.type == AttachmentModel.TYPE_IMAGE_STATIC || attachment.type == AttachmentModel.TYPE_IMAGE_GIF)
                        && attachment.width == object.optInt("tn_w", 0) && attachment.height == object.optInt("tn_h", 0)) {
                    attachment.thumbnail = attachment.path; // Image equal or smaller than 200x200 pixels
                } else {
                    attachment.thumbnail = "/" + boardName + "/thumb/" + Long.toString(tim) + "s.jpg";
                }
            } else {
                String filename = attachment.originalName;
                try {
                    filename = URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
                } catch (Exception e) {}
                attachment.path = "/" + boardName + "/src/" + filename;
            }
            model.attachments = new AttachmentModel[] { attachment };
        }
        return model;
    }
    
    private static String toHtml(String com, String boardName, String threadNumber) {
        com = StringEscapeUtils.escapeHtml4(com);
        
        String[] lines = com.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("&gt;") && !line.startsWith("&gt;&gt;")) {
                sb.append("<span class=\"unkfunc\">").append(line).append("</span><br/>");
            } else {
                sb.append(line).append("<br/>");
            }
        }
        
        if (sb.length() > 5) sb.setLength(sb.length() - 5);
        com = sb.toString();
        com = com.replaceAll("(^|[\\n ])(https?://[^ ]*)", "$1<a href=\"$2\">$2</a>");
        com = ("\n" + com + "\n").replaceAll("\n&gt;(.*?)\n", "\n<span class=\"unkfunc\">&gt;$1</span>\n");
        com = com.replace("\r\n", "\n").replace("\n", "<br/>");
        com = com.replaceAll("(?i)\\[b\\](.*?)\\[/b\\]", "<b>$1</b>");
        com = com.replaceAll("(?i)\\[i\\](.*?)\\[/i\\]", "<i>$1</i>");
        com = com.replaceAll("(?i)\\[s\\](.*?)\\[/s\\]", "<s>$1</s>");
        com = com.replaceAll("(?i)\\[spoiler\\](.*?)\\[/spoiler\\]", "<span class=\"spoiler\">$1</span>");
        com = com.replaceAll("\\[\\*\\*\\](.*?)\\[/\\*\\*\\]", "<b>$1</b>");
        com = com.replaceAll("\\[\\*\\](.*?)\\[/\\*\\]", "<i>$1</i>");
        com = com.replaceAll("\\[%\\](.*?)\\[/%\\]", "<span class=\"spoiler\">$1</span>");
        com = com.replaceAll("&gt;&gt;(\\d+)", "<a href=\"/" + boardName + "/res/" + threadNumber + ".php#$1\">$0</a>");
        
        return com;
    }
    
    private static String toUtf8(String text) {
        try {
            return new String(text.getBytes("Windows-1252"), "UTF-8");
        } catch (Exception e) {
            return text;
        }
    }
    
}
