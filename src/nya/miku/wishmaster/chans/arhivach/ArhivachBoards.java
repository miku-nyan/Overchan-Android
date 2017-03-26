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

package nya.miku.wishmaster.chans.arhivach;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;

/**
 * Created by Kalaver <Kalaver@users.noreply.github.com> on 23.06.2015.
 */

public class ArhivachBoards {
    private static final List<BoardModel> LIST = new ArrayList<BoardModel>();
    private static final Map<String, BoardModel> MAP = new HashMap<String, BoardModel>();
    private static final SimpleBoardModel[] SIMPLE_ARRAY;
    static {
        addBoard("", "Arhivach", "Arhivach", true);
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
        model.chan = ArhivachModule.CHAN_NAME;
        model.boardName = name;
        model.boardDescription = description;
        model.boardCategory = category;
        model.nsfw = nsfw;
        model.uniqueAttachmentNames = false;
        model.timeZoneId = "GMT+3";
        model.defaultUserName = "Аноним";
        model.bumpLimit = 500;
        model.readonlyBoard = true;
        model.firstPage = 1;
        model.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        model.searchAllowed = true;
        model.searchPagination = true;
        model.catalogAllowed = false;
        return model;
    }
}
