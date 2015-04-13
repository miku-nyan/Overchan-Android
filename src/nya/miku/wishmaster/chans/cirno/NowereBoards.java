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

package nya.miku.wishmaster.chans.cirno;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;

public class NowereBoards {
    private static final String[] ATTACHMENT_FILTERS = new String[] { "jpg", "jpeg", "png", "gif" };
    private static final String[] ATTACHMENT_FILTERS_B = new String[] { "jpg", "jpeg", "png", "gif", "pdf" };
    private static final String[] ATTACHMENT_FILTERS_T = new String[] { "jpg", "jpeg", "png", "gif", "torrent" };
    
    private static final List<BoardModel> LIST = new ArrayList<BoardModel>();
    private static final Map<String, BoardModel> MAP = new HashMap<String, BoardModel>();
    private static final SimpleBoardModel[] SIMPLE_ARRAY;
    
    static {
        addBoard("b", "Бред", "Общие", true);
        addBoard("tu", "Туризм", "Общие", false);
        addBoard("a", "Аниме", "Общие", false);
        addBoard("ph", "Фото", "Общие", false);
        addBoard("wa", "Обои", "Общие", false);
        addBoard("cg", "Игры", "Общие", false);
        addBoard("t", "Торренты", "Общие", false);
        addBoard("p", "Политика", "Общие", false);
        addBoard("d", "Дискуcсии", "Работа сайта", false);
        
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
        model.chan = NowereModule.NOWERE_NAME;
        model.boardName = name;
        model.boardDescription = description;
        model.boardCategory = category;
        model.nsfw = nsfw;
        model.uniqueAttachmentNames = true;
        model.timeZoneId = "GMT+3";
        model.defaultUserName = "anonymous";
        model.bumpLimit = 500;
        
        model.readonlyBoard = false;
        model.requiredFileForNewThread = false;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = true;
        model.allowNames = true;
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = false;
        model.ignoreEmailIfSage = false;
        model.allowWatermark = false;
        model.allowOpMark = false;
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = 1;
        if (name.equals("b")) model.attachmentsFormatFilters = ATTACHMENT_FILTERS_B;
        else if (name.equals("t")) model.attachmentsFormatFilters = ATTACHMENT_FILTERS_T;
        else model.attachmentsFormatFilters = ATTACHMENT_FILTERS;
        model.markType = BoardModel.MARK_WAKABAMARK;
        
        model.firstPage = 0;
        model.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        return model;
    }
}
