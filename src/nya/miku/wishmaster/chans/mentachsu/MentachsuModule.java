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

package nya.miku.wishmaster.chans.mentachsu;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractWakabaModule;
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
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

@SuppressLint("SimpleDateFormat")
public class MentachsuModule extends AbstractWakabaModule {
    
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
        model.readonlyBoard = false;
        model.requiredFileForNewThread = false;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = true;
        model.allowReport = BoardModel.REPORT_WITH_COMMENT;
        model.allowNames = false;
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = false;
        model.allowCustomMark = false;
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = 1;
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
        HttpRequestModel requestModel = HttpRequestModel.builder().setGET().build();
        String html = HttpStreamer.getInstance().getStringFromUrl(getUsingUrl() + "captcha_update.php", requestModel, httpClient, null, task, false);
        Matcher matcher = CAPTCHA_KEY.matcher(html);
        if (matcher.find()) {
            String captchaUrl = getUsingUrl() + "simple-php-captcha.php?_CAPTCHA&t=" + matcher.group(1);
            Bitmap captchaBitmap = null;
            HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(captchaUrl, requestModel, httpClient, listener, task);
            try {
                InputStream imageStream = responseModel.stream;
                captchaBitmap = BitmapFactory.decodeStream(imageStream);
            } finally {
                responseModel.release();
            }
            CaptchaModel captchaModel = new CaptchaModel();
            captchaModel.type = CaptchaModel.TYPE_NORMAL_DIGITS;
            captchaModel.bitmap = captchaBitmap;
            return captchaModel;
        } else throw new Exception("Captcha update epic fail");
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "board.php";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("board", model.boardName).
                addString("replythread", model.threadNumber == null ? "0" : model.threadNumber).
                addString("em", model.sage ? "sage" : model.email).
                addString("subject", model.subject).
                addString("message", model.comment).
                addString("recaptcha_response_field", model.captchaAnswer);
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("imagefile", model.attachments[0], model.randomHash);
        else if (model.threadNumber == null) postEntityBuilder.addString("nofile", "on");
        
        postEntityBuilder.addString("postpassword", model.password);
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 302) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        return fixRelativeUrl(header.getValue());
                    }
                }
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                Matcher errorMatcher = ERROR_POSTING.matcher(htmlResponse);
                if (errorMatcher.find()) throw new Exception(errorMatcher.group(1).trim());
            } else throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "board.php";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("post[]", model.postNumber));
        if (model.onlyFiles) pairs.add(new BasicNameValuePair("fileonly", "on"));
        pairs.add(new BasicNameValuePair("postpassword", model.password));
        pairs.add(new BasicNameValuePair("deletepost", "Удалить"));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setNoRedirect(true).build();
        String result = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        if (result.contains("Неправильный пароль")) throw new Exception("Неправильный пароль");
        Matcher errorMatcher = ERROR_POSTING.matcher(result);
        if (errorMatcher.find()) throw new Exception(errorMatcher.group(1));
        return null;
    }
    
    @Override
    public String reportPost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "board.php";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("post[]", model.postNumber));
        if (model.onlyFiles) pairs.add(new BasicNameValuePair("fileonly", "on"));
        pairs.add(new BasicNameValuePair("reportreason", model.reportReason));
        pairs.add(new BasicNameValuePair("reportpost", "Аминь"));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setNoRedirect(true).build();
        String result = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        if (result.contains("Post successfully reported")) return null;
        throw new Exception(result);
    }
    
}
