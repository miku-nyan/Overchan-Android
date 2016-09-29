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

package nya.miku.wishmaster.chans.nullchdvach;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractWakabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
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

public class NullchdvachModule extends AbstractWakabaModule {
    private static final String CHAN_NAME = "0ch2ch.org";
    private static final String DOMAIN_NAME = "0ch2ch.org";
    
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Random", null, true)
    };
    private static final String[] ATTACHMENT_FORMATS = new String[] {
        "gif", "jpg", "png", "pdf", "odf", "zip", "rar", "tar", "bz2", "7z", "doc", "odt", "mp3", "mp4", "mpeg", "flv", "swf", "avi"
    };
    
    private static final DateFormat DATE_FORMAT;
    static {
        DateFormatSymbols chanSymbols = new DateFormatSymbols();
        chanSymbols.setShortWeekdays(new String[] { "", "Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб" });
        DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy (EEE) HH:mm:ss", chanSymbols);
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+4"));
    }
    
    private static final Pattern ERROR_PATTERN = Pattern.compile("<h1 style=\"text-align: center\">(.*?)<br[^>]*>");
    private static final char[] EMBED_FILTER = "<div id=\"video_".toCharArray();
    private int embedFilterCurPos = 0;
    
    public NullchdvachModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN_NAME;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    public String getDisplayingName() {
        return "0ch2ch.org";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_inach, null);
    }
    
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new WakabaReader(stream, DATE_FORMAT, canCloudflare()) {
            @Override
            protected void customFilters(int ch) throws IOException {
                if (ch == EMBED_FILTER[embedFilterCurPos]) {
                    ++embedFilterCurPos;
                    if (embedFilterCurPos == EMBED_FILTER.length) {
                        parseVideoAttachment(readUntilSequence("\"".toCharArray()));
                        embedFilterCurPos = 0;
                    }
                } else {
                    if (embedFilterCurPos != 0) embedFilterCurPos = ch == EMBED_FILTER[0] ? 1 : 0;
                }
            }
            
            private void parseVideoAttachment(String html) {
                if (!html.startsWith("image") && (html.length() > 0)) {
                    String id = html;
                    AttachmentModel attachment = new AttachmentModel();
                    attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                    attachment.size = -1;
                    attachment.path = "http://youtube.com/watch?v=" + id;
                    attachment.thumbnail = "http://img.youtube.com/vi/" + id + "/default.jpg";
                    ++currentThread.attachmentsCount;
                    currentAttachments.add(attachment);
                }
            }
            
            @Override
            protected void postprocessPost(PostModel post) {
                //TODO: Remove zero-width posts anywhere (critical for API 23)
                post.comment = post.comment.replaceAll("(\\u00AD)+","");
            }
        };
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel board = super.getBoard(shortName, listener, task);
        board.defaultUserName = "Аноним";
        board.timeZoneId = "GMT+4";
        board.readonlyBoard = false;
        board.requiredFileForNewThread = true;
        board.allowDeletePosts = false;
        board.allowNames = false;
        board.allowSubjects = true;
        board.allowSage = true;
        board.allowEmails = true;
        board.ignoreEmailIfSage = true;
        board.allowCustomMark = false;
        board.allowRandomHash = true;
        board.allowIcons = false;
        board.attachmentsMaxCount = 1;
        board.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        board.markType = BoardModel.MARK_WAKABAMARK;
        return board;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() + boardName + "/captcha.pl?key=" + (threadNumber == null ? "mainpage" : ("res" + threadNumber)) +
                "&dummy=" + Long.toString(Math.round(Math.random()*1000000)) + "&update=1";
        return downloadCaptcha(captchaUrl, listener, task);
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/wakaba.pl";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("task", "post");
        if (model.threadNumber != null) postEntityBuilder.addString("parent", model.threadNumber);
        if (model.sage) postEntityBuilder.addString("fieldsage", "on");
        postEntityBuilder.
                addString("fieldnoko", "on").
                addString("field2", model.sage ? "sage" : model.email).
                addString("field3", model.subject).
                addString("field4", model.comment).
                addString("captcha", model.captchaAnswer);
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("file", model.attachments[0], model.randomHash);
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 303) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        return fixRelativeUrl(header.getValue());
                    }
                }
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (!htmlResponse.contains("<blockquote")) {
                    Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                    if (errorMatcher.find()) {
                        throw new Exception(errorMatcher.group(1).trim());
                    }
                }
            } else throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
}
