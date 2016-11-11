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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.SequenceInputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceGroup;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import nya.miku.wishmaster.api.AbstractKusabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;

public abstract class AbstractInstant0chan extends AbstractKusabaModule {
    public AbstractInstant0chan(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    protected boolean loadOnlyNewPosts() {
        return loadOnlyNewPosts(true);
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addOnlyNewPostsPreference(preferenceGroup, true);
        super.addPreferencesOnScreen(preferenceGroup);
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.timeZoneId = "GMT+3";
        model.defaultUserName = "Anonymous";
        model.requiredFileForNewThread = !shortName.equals("0");
        model.allowReport = BoardModel.REPORT_SIMPLE;
        model.allowNames = !shortName.equals("b");
        model.allowEmails = false;
        return model;
    }
    
    @Override
    protected final DateFormat getDateFormat() {
        return null;
    }
    
    @Override
    protected WakabaReader getKusabaReader(InputStream stream, UrlPageModel urlModel) {
        if (urlModel != null && urlModel.chanName != null && urlModel.chanName.equals("expand")) {
            stream = new SequenceInputStream(new ByteArrayInputStream("<form id=\"delform\">".getBytes()), stream);
        }
        return new Instant0chanReader(stream, canCloudflare());
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        if (loadOnlyNewPosts() && oldList != null && oldList.length > 0) {
            String url = getUsingUrl() + "expand.php?after=" + oldList[oldList.length-1].number + "&board=" + boardName + "&threadid=" + threadNumber;
            UrlPageModel object = new UrlPageModel();
            object.chanName = "expand";
            ThreadModel[] page = readWakabaPage(url, listener, task, true, object);
            if (page != null && page.length > 0) {
                PostModel[] posts = new PostModel[oldList.length + page[0].posts.length];
                for (int i=0; i<oldList.length; ++i) posts[i] = oldList[i];
                for (int i=0; i<page[0].posts.length; ++i) posts[oldList.length + i] = page[0].posts[i];
                return posts;
            } else {
                return oldList;
            }
        }
        return super.getPostsList(boardName, threadNumber, listener, task, oldList);
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() + "captcha.php?" + Math.random();
        CaptchaModel captchaModel = downloadCaptcha(captchaUrl, listener, task);
        captchaModel.type = CaptchaModel.TYPE_NORMAL;
        return captchaModel;
    }
    
    @Override
    protected void setSendPostEntity(SendPostModel model, ExtendedMultipartBuilder postEntityBuilder) throws Exception {
        postEntityBuilder.
                addString("board", model.boardName).
                addString("replythread", model.threadNumber == null ? "0" : model.threadNumber).
                addString("name", model.name);
        if (model.sage) postEntityBuilder.addString("em", "sage");
        postEntityBuilder.
                addString("captcha", model.captchaAnswer).
                addString("subject", model.subject).
                addString("message", model.comment).
                addString("postpassword", model.password);
        setSendPostEntityAttachments(model, postEntityBuilder);
        postEntityBuilder.addString("embed", "");
        
        postEntityBuilder.addString("redirecttothread", "1");
    }
    
    @Override
    protected List<? extends NameValuePair> getReportFormAllValues(DeletePostModel model) {
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("post[]", model.postNumber));
        pairs.add(new BasicNameValuePair("reportpost", "Отправить"));
        return pairs;
    }
    
    @Override
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Удалить";
    }
    
    @SuppressLint("SimpleDateFormat")
    protected static class Instant0chanReader extends KusabaReader {
        private static final Pattern PATTERN_EMBEDDED = Pattern.compile("<div (?:[^>]*)data-id=\"([^\"]*)\"(?:[^>]*)>", Pattern.DOTALL);
        private static final DateFormat DATE_FORMAT;
        static {
            DateFormatSymbols symbols = new DateFormatSymbols();
            symbols.setShortMonths(new String[] { "Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"});
            DATE_FORMAT = new SimpleDateFormat("yyyy MMM dd HH:mm:ss", symbols);
            DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
        }
        
        public Instant0chanReader(InputStream in, boolean canCloudflare) {
            super(in, DATE_FORMAT, canCloudflare, ~FLAG_HANDLE_EMBEDDED_POST_POSTPROCESS);
        }
        
        public Instant0chanReader(Reader reader, boolean canCloudflare) {
            super(reader, DATE_FORMAT, canCloudflare, ~FLAG_HANDLE_EMBEDDED_POST_POSTPROCESS);
        }
        
        @Override
        protected void parseDate(String date) {
            date = date.replace("&#35;", "");
            date = date.replaceAll("(?:[^\\d]*)(\\d(?:.*))", "$1");
            super.parseDate(date);
        }
        
        @Override
        protected void postprocessPost(PostModel post) {
            super.postprocessPost(post);
            
            Matcher matcher = PATTERN_EMBEDDED.matcher(post.comment);
            while (matcher.find()) {
                String id = matcher.group(1);
                String div = matcher.group(0).toLowerCase(Locale.US);
                String url = null;
                if (div.contains("youtube")) {
                    url = "http://www.youtube.com/watch?v=" + id;
                } else if (div.contains("vimeo")) {
                    url = "http://vimeo.com/" + id;
                } else if (div.contains("coub")) {
                    url = "http://coub.com/view/" + id;
                }
                if (url != null) {
                    AttachmentModel attachment = new AttachmentModel();
                    attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                    attachment.size = -1;
                    attachment.path = url;
                    attachment.thumbnail = div.contains("youtube") ? ("http://img.youtube.com/vi/" + id + "/default.jpg") : null;
                    int oldCount = post.attachments != null ? post.attachments.length : 0;
                    AttachmentModel[] attachments = new AttachmentModel[oldCount + 1];
                    for (int i=0; i<oldCount; ++i) attachments[i] = post.attachments[i];
                    attachments[oldCount] = attachment;
                    post.attachments = attachments;
                }
            }
        }
    }
    
}
