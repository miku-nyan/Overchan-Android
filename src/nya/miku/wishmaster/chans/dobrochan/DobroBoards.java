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

package nya.miku.wishmaster.chans.dobrochan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;

public class DobroBoards {
    static final String[] RATINGS = new String[] { "SFW", "R-15", "R-18", "R-18G" };
    
    private static final List<BoardModel> LIST = new ArrayList<BoardModel>();
    private static final Map<String, BoardModel> MAP = new HashMap<String, BoardModel>();
    private static final SimpleBoardModel[] SIMPLE_ARRAY;
    
    static {
        addBoard("b", "Бред", "Общее", true);
        addBoard("u", "Университет", "Общее", false);
        addBoard("rf", "Refuge", "Общее", false);
        addBoard("dt", "Dates and datings", "Общее", false);
        addBoard("vg", "Видеоигры", "Общее", false);
        addBoard("r", "Просьбы", "Общее", false);
        addBoard("cr", "Творчество", "Общее", false);
        addBoard("lor", "LOR", "Общее", false);
        addBoard("mu", "Музыка", "Общее", false);
        addBoard("oe", "Oekaki", "Общее", false);
        addBoard("s", "Li/s/p", "Общее", false);
        addBoard("w", "Обои", "Общее", false);
        addBoard("hr", "Высокое разрешение", "Общее", false);
        addBoard("a", "Аниме", "Аниме", false);
        addBoard("ma", "Манга", "Аниме", false);
        addBoard("sw", "Spice & Wolf", "Аниме", false);
        addBoard("hau", "When They Cry", "Аниме", false);
        addBoard("azu", "Azumanga Daioh", "Аниме", false);
        addBoard("tv", "Кино", "На пробу", false);
        addBoard("cp", "Копипаста", "На пробу", false);
        addBoard("gf", "Gif/Flash-анимация", "На пробу", false);
        addBoard("bo", "Книги", "На пробу", false);
        addBoard("di", "Dining room", "На пробу", false);
        addBoard("vn", "Visual novels", "На пробу", false);
        addBoard("ve", "Vehicles", "На пробу", false);
        addBoard("wh", "Вархаммер", "На пробу", false);
        addBoard("fur", "Фурри", "На пробу", false);
        addBoard("to", "Touhou Project", "На пробу", false);
        addBoard("bg", "Настольные игры", "На пробу", false);
        addBoard("wn", "События в мире", "На пробу", false);
        addBoard("slow", "Слоудоска", "На пробу", false);
        addBoard("mad", "Безумие", "На пробу", false);
        addBoard("d", "Обсуждение", "Доброчан", false);
        addBoard("news", "Новости", "Доброчан", false);
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
        model.chan = DobroModule.CHAN_NAME;
        model.boardName = name;
        model.boardDescription = description;
        model.boardCategory = category;
        model.nsfw = nsfw;
        model.uniqueAttachmentNames = false;
        model.timeZoneId = "GMT+3";
        switch (name) {
            case "s": model.defaultUserName = "Доброкодер"; break;
            case "sw": model.defaultUserName = "Лоуренс"; break;
            case "wn": model.defaultUserName = "Анонимный эксперт"; break;
            case "slow": model.defaultUserName = "Добропок"; break;
            case "mad": model.defaultUserName = "Экспериментатор"; break;
            default: model.defaultUserName = "Анонимус"; break;
        }
        model.bumpLimit = 500;
        
        model.readonlyBoard = false;
        model.requiredFileForNewThread = true;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = false;
        model.allowNames = true;
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = false;
        model.allowCustomMark = false;
        model.allowRandomHash = true;
        model.allowIcons = true;
        model.iconDescriptions = RATINGS;
        model.attachmentsMaxCount = 5;
        model.attachmentsFormatFilters = null;
        model.markType = BoardModel.MARK_WAKABAMARK;
        
        model.firstPage = 0;
        model.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        return model;
    }
}
