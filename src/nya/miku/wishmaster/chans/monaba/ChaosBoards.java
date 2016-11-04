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

package nya.miku.wishmaster.chans.monaba;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;

public class ChaosBoards {
    private static final String[] ATTACHMENT_FILTERS = new String[] { "jpg", "jpeg", "png", "gif",
            "webm", "rar", "zip", "7z", "mp3", "mp4", "ogv", "txt", "pdf"};
    
    private static final List<BoardModel> LIST = new ArrayList<BoardModel>();
    private static final Map<String, BoardModel> MAP = new HashMap<String, BoardModel>();
    private static final SimpleBoardModel[] SIMPLE_ARRAY;
    static {
        addBoard("a", "Anime", "Public", false);
        addBoard("b", "Random", "Public", true);
        addBoard("cyb", "Cyberpunk", "Public", false);
        addBoard("d", "Development", "Public", false);
        addBoard("int", "International", "Public", false);
        addBoard("it", "Technology", "Public", false);
        addBoard("ra", "Electronics", "Public", false);
        addBoard("sec", "Security", "Public", false);
        addBoard("st", "Stalker", "Public", false);
        addBoard("cpu", "News", "Default", false);
        
        SIMPLE_ARRAY = new SimpleBoardModel[LIST.size()];
        for (int i=0; i<LIST.size(); ++i) SIMPLE_ARRAY[i] = new SimpleBoardModel(LIST.get(i));
    }
    static BoardModel getBoard(String boardName) {
        BoardModel board = MAP.get(boardName);
        if (board == null) return createDefaultBoardModel(boardName, boardName, null, false);
        return board;
    }
    
    static SimpleBoardModel[] getBoardsList() {
        return SIMPLE_ARRAY;
    }
    
    private static void addBoard(String name, String description, String category, boolean nsfw) {
        BoardModel model = createDefaultBoardModel(name, description, category, nsfw);
        LIST.add(model);
        MAP.put(name, model);
    }
    
    private static BoardModel createDefaultBoardModel(String name, String description, String category, boolean nsfw) {
        BoardModel model = new BoardModel();
        model.chan = ChaosModule.CHAN_NAME;
        model.boardName = name;
        model.boardDescription = description;
        model.boardCategory = category;
        model.nsfw = nsfw;
        model.uniqueAttachmentNames = false;
        model.timeZoneId = "GMT+3";
        model.defaultUserName = "anonymous";
        model.bumpLimit = 500;
        model.readonlyBoard = false;
        model.requiredFileForNewThread = true;
        model.allowDeletePosts = false;
        model.allowDeleteFiles = false;
        model.allowNames = true;
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = false;
        model.ignoreEmailIfSage = true;
        model.allowCustomMark = false;
        model.allowRandomHash = true;
        model.allowIcons = true;
        model.iconDescriptions = AbstractMonabaChan.RATINGS;
        model.attachmentsMaxCount = 3;
        model.attachmentsFormatFilters = ATTACHMENT_FILTERS;
        model.markType = BoardModel.MARK_BBCODE;
        model.firstPage = 0;
        model.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        model.catalogAllowed = false;
        return model;
    }
}
