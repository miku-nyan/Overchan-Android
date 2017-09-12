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

package nya.miku.wishmaster.chans.cirno;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;

public class Chan410Boards {
    static final Set<String> ALL_BOARDS_SET = new HashSet<String>(Arrays.asList(new String[] {
            "b", "int", "cu", "dev", "r", "a", "ts", "tm", "gnx", "ci" }));
    
    private static final String[] ATTACHMENT_FILTERS = new String[] { "jpg", "jpeg", "png", "gif" };
    private static final String[] ATTACHMENT_FILTERS_B = new String[] { "jpg", "jpeg", "png", "gif", "ass", "srt", "ssa" };
    private static final String[] ATTACHMENT_FILTERS_A = new String[] { "jpg", "jpeg", "png", "gif", "pdf" };
    private static final String[] ATTACHMENT_FILTERS_DEV = new String[] {
        "jpg", "jpeg", "png", "gif", "7z", "bz", "bz2", "gz", "mo", "mp3", "ogg", "pdf", "psd", "rar", "svg", "swf", "txt", "xcf", "zip" };
    
    private static final List<BoardModel> LIST = new ArrayList<BoardModel>();
    private static final Map<String, BoardModel> MAP = new HashMap<String, BoardModel>();
    private static final SimpleBoardModel[] SIMPLE_ARRAY;
    
    static {
        addBoard("b", "Авто/b/ус", "Общее", "Пассажир", true);
        addBoard("int", "International", "Общее", "Anonymous", false);
        addBoard("cu", "Кулинария", "Общее", "Аноним", false);
        addBoard("dev", "Разработка", "Общее", "Стив Балмер", false);
        addBoard("r", "Радио 410", "Радио", "Аноним", false);
        addBoard("a", "Аниме и манга", "Аниме", "Нінгенъ", false);
        addBoard("ts", "Цундере", "Аниме", "Baka Inu", false);
        addBoard("tm", "Type-Moon", "Аниме", "Шики", false);
        addBoard("ci", "Городская жизнь", "На пробу", "Аноним", false);
        
        SIMPLE_ARRAY = new SimpleBoardModel[LIST.size()];
        for (int i=0; i<LIST.size(); ++i) SIMPLE_ARRAY[i] = new SimpleBoardModel(LIST.get(i));
    }
    
    static BoardModel getBoard(String boardName) {
        BoardModel board = MAP.get(boardName);
        if (board == null) return createDefaultBoardModel(boardName, boardName, null, "Аноним", false);
        return board;
    }
    
    static SimpleBoardModel[] getBoardsList() {
        return SIMPLE_ARRAY;
    }
    
    private static void addBoard(String name, String description, String category, String defaultPosterName, boolean nsfw) {
        BoardModel model = createDefaultBoardModel(name, description, category, defaultPosterName, nsfw);
        LIST.add(model);
        MAP.put(name, model);
    }
    
    private static BoardModel createDefaultBoardModel(String name, String description, String category, String defaultPosterName, boolean nsfw) {
        BoardModel model = new BoardModel();
        model.chan = Chan410Module.CHAN410_NAME;
        model.boardName = name;
        model.boardDescription = description;
        model.boardCategory = category;
        model.nsfw = nsfw;
        model.uniqueAttachmentNames = true;
        model.timeZoneId = "GMT+3";
        model.defaultUserName = defaultPosterName;
        model.bumpLimit = 500;
        
        model.readonlyBoard = false;
        model.requiredFileForNewThread = true;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = true;
        model.allowReport = BoardModel.REPORT_SIMPLE;
        model.allowNames = !name.equals("b");
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = false;
        model.ignoreEmailIfSage = false;
        model.allowCustomMark = false;
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = 1;
        if (name.equals("b")) model.attachmentsFormatFilters = ATTACHMENT_FILTERS_B;
        else if (name.equals("a")) model.attachmentsFormatFilters = ATTACHMENT_FILTERS_A;
        else if (name.equals("dev")) model.attachmentsFormatFilters = ATTACHMENT_FILTERS_DEV;
        else model.attachmentsFormatFilters = ATTACHMENT_FILTERS;
        model.markType = BoardModel.MARK_WAKABAMARK;
        
        model.firstPage = 0;
        model.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        model.catalogAllowed = !name.equals("d");
        return model;
    }
}
