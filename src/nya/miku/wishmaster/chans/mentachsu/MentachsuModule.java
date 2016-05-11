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

package nya.miku.wishmaster.chans.mentachsu;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractKusabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

@SuppressLint("SimpleDateFormat")
public class MentachsuModule extends AbstractKusabaModule {
    
    private static final String CHAN_NAME = "02ch.su";
    private static final String DOMAIN = "02ch.su";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Биомусор", "Поехавшие", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "e", "ЕОТ, которой нет", "Поехавшие", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "k", "А почему вы спрашиваете?", "Поехавшие", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "l", "(смех)-картинки с подписями и без", "Поехавшие", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "q", "Вопросы и ответы", "Поехавшие", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r", "Посоветуй! Помоги!", "Поехавшие", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "v", "V + Ctrl и разговор с ней", "Поехавшие", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "y", "Ты. Да, я. Я, Я, Я. Я тоже. Убежище", "Поехавшие", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "z", "ЗАШКВАР. Школьники и ньюфаги", "Поехавшие", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Аниме и админы", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "s", "Софт", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "g", "Игры", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "h", "Железо", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "w", "Политика и военная техника", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "t", "Техника и наука", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "u", "Университет и образование", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "j", "Журналистика и новости", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "i", "Иностранцы", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c", "Кино", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "x", "Художники и творцы", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "o", "Отдых и спорт", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "m", "Музыка", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "f", "Фриланс и барахолка", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "n", "Ностальгия", "Тематические", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "02", "Большой брат бдит", "Мета", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "Дежурная часть", "Мета", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "p", "Платиновые треды", "Мета", false)
    };
    private static final String[] ATTACHMENT_FORMATS = new String[] { "gif", "jpg", "png" };
    
    private static final Pattern CAPTCHA_KEY = Pattern.compile("src=\"/simple-php-captcha.php\\?_CAPTCHA&amp;t=(.*?)\"", Pattern.DOTALL);
    private static final Pattern ERROR_POSTING = Pattern.compile("<h2(?:[^>]*)>(.*?)</h2>", Pattern.DOTALL);
    
    private static final DateFormat DATE_FORMAT;
    static {
        DateFormatSymbols symbols = new DateFormatSymbols();
        symbols.setShortWeekdays(new String[] { "", "Вск", "Пон", "Втр", "Срд", "Чтв", "Птн", "Сбт" });
        DATE_FORMAT = new SimpleDateFormat("dd.MM.yy(EEE) HH:mm:ss", symbols);
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
    }
    
    public MentachsuModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "МилицаЧ";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_02ch, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
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
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new WakabaReader(stream, DATE_FORMAT, true);
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.timeZoneId = "GMT+3";
        model.defaultUserName = "Анонимус";
        model.requiredFileForNewThread = false;
        model.allowNames = false;
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        model.markType = BoardModel.MARK_BBCODE;
        return model;
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList) throws Exception {
        PostModel[] result = super.getPostsList(boardName, threadNumber, listener, task, oldList);
        if (result != null && result.length > 0) result[0].number = threadNumber;
        return result;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel requestModel = HttpRequestModel.DEFAULT_GET;
        String html = HttpStreamer.getInstance().getStringFromUrl(getUsingUrl() + "captcha_update.php", requestModel, httpClient, null, task, false);
        Matcher matcher = CAPTCHA_KEY.matcher(html);
        if (matcher.find()) {
            String captchaUrl = getUsingUrl() + "simple-php-captcha.php?_CAPTCHA&t=" + matcher.group(1);
            CaptchaModel captchaModel = downloadCaptcha(captchaUrl, listener, task);
            captchaModel.type = CaptchaModel.TYPE_NORMAL_DIGITS;
            return captchaModel;
        } else throw new Exception("Captcha update epic fail");
    }
    
    @Override
    protected void setSendPostEntityMain(SendPostModel model, ExtendedMultipartBuilder postEntityBuilder) throws Exception {
        super.setSendPostEntityMain(model, postEntityBuilder);
        postEntityBuilder.addString("recaptcha_response_field", model.captchaAnswer);
    }
    
    @Override
    protected void checkDeletePostResult(DeletePostModel model, String result) throws Exception {
        if (result.contains("Неправильный пароль")) throw new Exception("Неправильный пароль");
        Matcher errorMatcher = ERROR_POSTING.matcher(result);
        if (errorMatcher.find()) throw new Exception(errorMatcher.group(1));
    }
    
    @Override
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Удалить";
    }
    
    @Override
    protected String getReportFormValue(DeletePostModel model) {
        return "Аминь";
    }
    
}
