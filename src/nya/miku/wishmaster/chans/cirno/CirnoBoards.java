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
import java.util.List;
import java.util.Map;

import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;

public class CirnoBoards {
    private static final String[] ATTACHMENT_FILTERS = new String[] { "jpg", "jpeg", "png", "gif" };
    
    private static final List<String> IICHAN_BOARDS_410 = Arrays.asList("int", "ts", "cu", "dev", "ci");
    
    private static final List<String> IICHAN_SPOILER_MARK_BOARDS = Arrays.asList("bro", "maid", "med", "tv", "a", "fi", "to", "vn", "vg");
    
    private static final List<String> IICHAN_READONLY_BOARDS = Arrays.asList("o", "w", "abe", "ma", "azu", "me", "hau", "sos", "mo", "sp", "bg");
    
    private static final List<BoardModel> LIST_IICHAN = new ArrayList<BoardModel>();
    private static final Map<String, BoardModel> MAP_IICHAN = new HashMap<String, BoardModel>();
    private static final SimpleBoardModel[] SIMPLE_ARRAY_IICHAN;
    
    static {
        addBoard("d", "Работа сайта", "Обсуждения", "Мод-тян", false);
        addBoard("b", "Бред", "Общее", "Сырно", true);
        addBoard("hr", "Высокое разрешение", "Общее", "Аноним", false);
        addBoard("gf", "gif- и flash-анимация", "Общее", "Аноним", true); //???
        addBoard("ci", "Городская жизнь", "Общее", "Аноним", false);
        addBoard("an", "Живопись", "Общее", "Кот Синкая", false);
        addBoard("ne", "Животные", "Общее", "Пушок", false);
        addBoard("tran", "Иностранные языки", "Общее", "Е. Д. Поливанов", false);
        addBoard("int", "International", "Общее", "Anonymous", false);
        addBoard("tv", "Кино, ТВ и мультфильмы", "Общее", "К. С. Станиславский", false);
        addBoard("cu", "Кулинария", "Общее", "Аноним", false);
        addBoard("l", "Литература", "Общее", "Ф. М. Достоевский", false);
        addBoard("bro", "My Little Pony", "Общее", "Эпплджек", false);
        addBoard("m", "Картинки-макросы и копипаста", "Общее", "Копипаста-гей", false);
        addBoard("med", "Медицина", "Общее", "Антон Буслов", false);
        addBoard("mu", "Музыка", "Общее", "Виктор Цой", false);
        addBoard("sci", "Наука", "Общее", "Гриша Перельман", false);
        addBoard("mi", "Оружие", "Общее", "Й. Швейк", false);
        addBoard("o", "Оэкаки", "Общее", "Аноним", false);
        addBoard("x", "Паранормальные явления", "Общее", "Эмма Ай", false);
        addBoard("r", "Просьбы", "Общее", "Аноним", false);
        addBoard("dev", "Разработка", "Общее", "Стив Балмер", false);
        addBoard("maid", "Служанки", "Общее", "Госюдзин-сама", false);
        addBoard("tu", "Туризм", "Общее", "Аноним", false);
        addBoard("ph", "Фото", "Общее", "Аноним", false);
        addBoard("fr", "Фурри", "Общее", "Аноним", false);
        addBoard("s", "Электроника и ПО", "Общее", "Чии", false);
        addBoard("es", "Бесконечное лето", "Игры", "Пионер", false);
        addBoard("vg", "Видеоигры", "Игры", "Марио", false);
        addBoard("au", "Автомобили", "Транспорт", "Джереми Кларксон", false);
        addBoard("tr", "Транспорт", "Транспорт", "Аноним", false);
        addBoard("a", "Аниме и манга", "Японская культура", "Мокона", false);
        addBoard("aa", "Аниме-арт", "Японская культура", "Ракка", false);
        addBoard("vn", "Визуальные новеллы", "Японская культура", "Сэйбер", false);
        addBoard("vo", "Vocaloid", "Японская культура", "", false); //hatsune
        addBoard("abe", "ёситоси абэ", "Японская культура", "Chada", false);
        addBoard("c", "Косплей", "Японская культура", "Аноним", false);
        addBoard("ls", "Lucky☆Star", "Японская культура", "Цукаса", false);
        addBoard("rm", "Rozen Maiden", "Японская культура", "Суйгинто", false);
        addBoard("tan", "Сетевые персонажи", "Японская культура", "Уныл-тян", false);
        addBoard("to", "Touhou", "Японская культура", "Нитори", false);
        addBoard("fi", "Фигурки", "Японская культура", "Фигурка анонима", false);
        addBoard("ts", "Цундере", "Японская культура", "Baka Inu", false);
        addBoard("jp", "Япония", "Японская культура", "名無しさん", false);
        
        SIMPLE_ARRAY_IICHAN = new SimpleBoardModel[LIST_IICHAN.size()];
        for (int i=0; i<LIST_IICHAN.size(); ++i) SIMPLE_ARRAY_IICHAN[i] = new SimpleBoardModel(LIST_IICHAN.get(i));
    }
    
    static BoardModel getBoard(String boardName) {
        BoardModel board = MAP_IICHAN.get(boardName);
        if (board == null) return createDefaultBoardModel(boardName, boardName, null, "Аноним", false);
        return board;
    }
    
    static SimpleBoardModel[] getBoardsList() {
        return SIMPLE_ARRAY_IICHAN;
    }
    
    static boolean is410Board(String boardName) {
        return IICHAN_BOARDS_410.indexOf(boardName) != -1;
    }
    
    private static void addBoard(String name, String description, String category, String defaultPosterName, boolean nsfw) {
        BoardModel model = createDefaultBoardModel(name, description, category, defaultPosterName, nsfw);
        LIST_IICHAN.add(model);
        MAP_IICHAN.put(name, model);
    }
    
    private static BoardModel createDefaultBoardModel(String name, String description, String category, String defaultPosterName, boolean nsfw) {
        BoardModel model = new BoardModel();
        model.chan = CirnoModule.IICHAN_NAME;
        model.boardName = name;
        model.boardDescription = description;
        model.boardCategory = category;
        model.nsfw = nsfw;
        model.uniqueAttachmentNames = true;
        model.timeZoneId = "GMT+3";
        model.defaultUserName = defaultPosterName;
        model.bumpLimit = 500;
        
        model.readonlyBoard = IICHAN_READONLY_BOARDS.indexOf(name) != -1;
        model.requiredFileForNewThread = !name.equals("d");
        model.allowDeletePosts = true;
        model.allowDeleteFiles = true;
        model.allowReport = BoardModel.REPORT_WITH_COMMENT;
        model.allowNames = !name.equals("b") && !name.equals("bro");
        model.allowSubjects = true;
        model.allowSage = false;
        model.allowEmails = false;
        model.ignoreEmailIfSage = false;
        model.allowCustomMark = IICHAN_SPOILER_MARK_BOARDS.indexOf(name) != -1;
        model.customMarkDescription = "Spoiler";
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = name.equals("d") ? 0 : 1;
        model.attachmentsFormatFilters = ATTACHMENT_FILTERS;
        model.markType = BoardModel.MARK_WAKABAMARK;
        
        model.firstPage = 0;
        model.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        
        model.catalogAllowed = !name.equals("d");
        return model;
    }
    
}
