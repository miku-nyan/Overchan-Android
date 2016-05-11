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

package nya.miku.wishmaster.chans.nullchan;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractKusabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;

public class Null_chanModule extends AbstractKusabaModule {
    private static final String TAG = "Null_chanModule";
    
    private static final String CHAN_NAME = "0-chan.ru";
    private static final String DOMAIN = "0-chan.ru";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Бред", "all", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fur", "Мех", "adult", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "e", "Электроника", "geek", false)
        };
    private static final Pattern PATTERN_EMBEDDED =
            Pattern.compile("<object type=\"application/x-shockwave-flash\"(?:[^>]*)data=\"([^\"]*)\"(?:[^>]*)>", Pattern.DOTALL);
    
    public Null_chanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Øчан (0-chan.ru)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_0chan_1, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN;
    }
    
    @Override
    protected boolean canHttps() {
        return false;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.timeZoneId = "GMT+3";
        model.defaultUserName = "Аноним";
        model.requiredFileForNewThread = false;
        model.allowReport = BoardModel.REPORT_SIMPLE;
        model.allowEmails = false;
        return model;
    }
    
    @SuppressLint("SimpleDateFormat")
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new WakabaReader(stream) {
            private final DateFormat dateFormat;
            {
                DateFormatSymbols symbols = new DateFormatSymbols();
                symbols.setMonths(new String[] {
                        "Января", "Февраля", "Марта", "Апреля", "Мая", "Июня", "Июля", "Августа", "Сентября", "Октября", "Ноября", "Декабря"});
                dateFormat = new SimpleDateFormat("dd MMMM yyyy в HH:mm:ss", symbols);
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+3"));
            }
            @Override
            protected void parseDate(String date) {
                if (date.length() > 0) {
                    date = date.replaceAll("(?:[^\\d]*)(\\d(?:.*))", "$1");
                    try {
                        currentPost.timestamp = dateFormat.parse(date).getTime();
                    } catch (Exception e) {
                        Logger.e(TAG, "cannot parse date", e);
                    }
                }
            }
            @Override
            protected void parseOmittedString(String omitted) {
                if (omitted.indexOf('<') != -1) omitted = omitted.substring(0, omitted.indexOf('<'));
                super.parseOmittedString(omitted);
            }
            @Override
            protected void postprocessPost(PostModel post) {
                Matcher matcher = PATTERN_EMBEDDED.matcher(post.comment);
                while (matcher.find()) {
                    String url = matcher.group(1).replace("youtube.com/v/", "youtube.com/watch?v=");
                    String id = null;
                    if (url.contains("youtube") && url.contains("v=")) {
                        id = url.substring(url.indexOf("v=") + 2);
                        if (id.contains("&")) id = id.substring(0, id.indexOf("&"));
                    }
                    AttachmentModel attachment = new AttachmentModel();
                    attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                    attachment.path = url;
                    attachment.thumbnail = id != null ? ("http://img.youtube.com/vi/" + id + "/default.jpg") : null;
                    int oldCount = post.attachments != null ? post.attachments.length : 0;
                    AttachmentModel[] attachments = new AttachmentModel[oldCount + 1];
                    for (int i=0; i<oldCount; ++i) attachments[i] = post.attachments[i];
                    attachments[oldCount] = attachment;
                    post.attachments = attachments;
                }
            }
        };
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() + "captcha/image.php?" + Math.random();
        return downloadCaptcha(captchaUrl, listener, task);
    }
    
    @Override
    protected void setSendPostEntityMain(SendPostModel model, ExtendedMultipartBuilder postEntityBuilder) throws Exception {
        postEntityBuilder.
                addString("board", model.boardName).
                addString("replythread", model.threadNumber == null ? "0" : model.threadNumber);
        if (model.sage) postEntityBuilder.addString("sage", "on");
        postEntityBuilder.
                addString("captcha", model.captchaAnswer).
                addString("name", model.name).
                addString("subject", model.subject).
                addString("message", model.comment);
        setSendPostEntityAttachments(model, postEntityBuilder);
        postEntityBuilder.addString("embed", "");
        
        postEntityBuilder.
                addString("postpassword", model.password).
                addString("gotothread", "checked");
    }
    
    @Override
    protected List<? extends NameValuePair> getReportFormAllValues(DeletePostModel model) {
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("post[]", model.postNumber));
        pairs.add(new BasicNameValuePair("reportpost", "Пожаловаться"));
        return pairs;
    }
    
    @Override
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Удалить";
    }
    
}
