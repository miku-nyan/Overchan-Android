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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractKusabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;

public class OwlchanModule extends AbstractKusabaModule {
    
    private static final String CHAN_NAME = "owlchan.ru";
    private static final String DOMAIN = "owlchan.ru";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "Работа сайта", "Работа сайта", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Клу/b/ы", "Общее", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "es", "Бесконечное лето", "Общее", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "izd", "Графомания", "Общее", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mod", "Уголок мододела", "Общее", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "art", "Рисовач", "Общее", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "o", "Оэкаки", "Общее", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ussr", "СССР", "Общее", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "Видеоигры", "Общее", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "rf", "Убежище", "Общее", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Аниме", "Японская культура", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vn", "Визуальные новеллы", "Японская культура", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ra", "OwlChan Radio", "Радио", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ph", "Философия", "На пробу", false) //?
    };
    private static final String[] ATTACHMENT_FORMATS = new String[] { "jpg", "jpeg", "png", "gif" };
    
    private static final DateFormat DATE_FORMAT;
    static {
        DATE_FORMAT = new SimpleDateFormat("dd/MM/yy | HH:mm:ss", Locale.US);
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
    }
    
    public OwlchanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return CHAN_NAME;
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_owlchan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected boolean useHttpsDefaultValue() {
        return false;
    }
    
    @Override
    protected boolean wakabaNoRedirect() {
        return true;
    }
    
    @Override
    protected DateFormat getDateFormat() {
        return DATE_FORMAT;
    }
    
    @Override
    protected int getKusabaFlags() {
        return ~(KusabaReader.FLAG_HANDLE_EMBEDDED_POST_POSTPROCESS|KusabaReader.FLAG_OMITTED_STRING_REMOVE_HREF);
    }
    
    @Override
    protected ThreadModel[] readWakabaPage(String url, ProgressListener listener, CancellableTask task, boolean checkModified, UrlPageModel urlModel)
            throws Exception {
        try {
            return super.readWakabaPage(url, listener, task, checkModified, urlModel);
        } catch (HttpWrongStatusCodeException e) {
            if (e.getStatusCode() == 302) throw new HttpWrongStatusCodeException(404, "404 - Not found");
            throw e;
        }
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel board = super.getBoard(shortName, listener, task);
        switch (shortName) {
            case "a": board.defaultUserName = "竜宮 レナ"; break;
            case "b": board.defaultUserName = "Семён"; break;
            case "es": board.defaultUserName = "Пионер"; break;
            case "vg": board.defaultUserName = "Bridget"; break;
            case "rf": board.defaultUserName = "Тацухиро Сато"; break;
            case "izd": board.defaultUserName = "И. С. Тургенев"; break;
            case "art": board.defaultUserName = "Художник-кун"; break;
            case "ussr": board.defaultUserName = "Товарищ"; break;
            default: board.defaultUserName = "Аноним"; break;
        }
        board.timeZoneId = "GMT+3";
        if (shortName.equals("es")) board.bumpLimit = 1000;
        board.readonlyBoard = shortName.equals("o");
        board.requiredFileForNewThread = !shortName.equals("d");
        board.allowNames = !shortName.equals("b");
        board.allowCustomMark = true;
        board.customMarkDescription = "Спойлер";
        board.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        board.markType = BoardModel.MARK_BBCODE;
        return board;
    }
    
    @Override
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Удалить";
    }
    
    @Override
    protected String getReportFormValue(DeletePostModel model) {
        return "Отчет";
    }
}
