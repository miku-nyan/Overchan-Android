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

package nya.miku.wishmaster.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import android.content.SharedPreferences;
import android.content.res.Resources;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

public abstract class AbstractKusabaModule extends AbstractWakabaModule {
    private static final Pattern ERROR_POSTING = Pattern.compile("<h2(?:[^>]*)>(.*?)</h2>", Pattern.DOTALL);
    
    public AbstractKusabaModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel board = super.getBoard(shortName, listener, task);
        board.timeZoneId = "UTC";
        board.defaultUserName = "Anonymous";
        board.readonlyBoard = false;
        board.requiredFileForNewThread = true;
        board.allowDeletePosts = true;
        board.allowDeleteFiles = true;
        board.allowReport = BoardModel.REPORT_WITH_COMMENT;
        board.allowNames = true;
        board.allowSubjects = true;
        board.allowSage = true;
        board.allowEmails = true;
        board.ignoreEmailIfSage = true;
        board.allowCustomMark = false;
        board.allowRandomHash = true;
        board.allowIcons = false;
        board.attachmentsMaxCount = 1;
        board.attachmentsFormatFilters = null;
        board.markType = BoardModel.MARK_WAKABAMARK;
        return board;
    }
    
    @Override
    protected final WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return getKusabaReader(stream, urlModel);
    }
    
    protected int getKusabaFlags() {
        return ~0;
    }
    
    protected WakabaReader getKusabaReader(InputStream stream, UrlPageModel urlModel) {
        return new KusabaReader(stream, getDateFormat(), canCloudflare(), getKusabaFlags());
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getBoardScriptUrl(model);
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task);
        setSendPostEntity(model, postEntityBuilder);
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
        String url = getBoardScriptUrl(model);
        List<? extends NameValuePair> pairs = getDeleteFormAllValues(model);
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setNoRedirect(true).build();
        String result = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        checkDeletePostResult(model, result);
        return null;
    }
    
    @Override
    public String reportPost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getBoardScriptUrl(model);
        List<? extends NameValuePair> pairs = getReportFormAllValues(model);
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setNoRedirect(true).build();
        String result = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        checkReportPostResult(model, result);
        return null;
    }
    
    protected String getBoardScriptUrl(Object tag) {
        return getUsingUrl() + "board.php";
    }
    
    protected void setSendPostEntity(SendPostModel model, ExtendedMultipartBuilder postEntityBuilder) throws Exception {
        setSendPostEntityMain(model, postEntityBuilder);
        setSendPostEntityAttachments(model, postEntityBuilder);
        setSendPostEntityPassword(model, postEntityBuilder);
    }
    
    protected void setSendPostEntityMain(SendPostModel model, ExtendedMultipartBuilder postEntityBuilder) throws Exception {
        postEntityBuilder.
                addString("board", model.boardName).
                addString("replythread", model.threadNumber == null ? "0" : model.threadNumber).
                addString("name", model.name).
                addString("em", model.sage ? "sage" : model.email).
                addString("subject", model.subject).
                addString("message", model.comment);
    }
    
    protected void setSendPostEntityAttachments(SendPostModel model, ExtendedMultipartBuilder postEntityBuilder) throws Exception {
        if (model.attachments != null && model.attachments.length > 0) {
            postEntityBuilder.addFile("imagefile", model.attachments[0], model.randomHash);
            if (model.custommark) postEntityBuilder.addString("spoiler", "on");
        } else if (model.threadNumber == null) postEntityBuilder.addString("nofile", "on");
    }
    
    protected void setSendPostEntityPassword(SendPostModel model, ExtendedMultipartBuilder postEntityBuilder) throws Exception {
        postEntityBuilder.addString("postpassword", model.password);
    }
    
    protected List<? extends NameValuePair> getDeleteFormAllValues(DeletePostModel model) throws Exception {
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("post[]", model.postNumber));
        if (model.onlyFiles) pairs.add(new BasicNameValuePair("fileonly", "on"));
        pairs.add(new BasicNameValuePair("postpassword", model.password));
        pairs.add(new BasicNameValuePair("deletepost", getDeleteFormValue(model)));
        return pairs;
    }
    
    protected List<? extends NameValuePair> getReportFormAllValues(DeletePostModel model) throws Exception {
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("post[]", model.postNumber));
        pairs.add(new BasicNameValuePair("reportreason", model.reportReason));
        pairs.add(new BasicNameValuePair("reportpost", getReportFormValue(model)));
        return pairs;
    }
    
    protected void checkDeletePostResult(DeletePostModel model, String result) throws Exception {
        if (result.contains("Incorrect password")) throw new Exception("Incorrect password");
        if (result.contains("Неверный пароль")) throw new Exception("Неверный пароль");
        if (result.contains("Неправильный пароль")) throw new Exception("Неправильный пароль");
        if (result.contains("Ошибка при попытке удалить сообщение")) throw new Exception("Ошибка при попытке удалить сообщение");
    }
    
    protected void checkReportPostResult(DeletePostModel model, String result) throws Exception {
        if (result.contains("Post successfully reported")) return;
        throw new Exception(result);
    }
    
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Delete";
    }
    
    protected String getReportFormValue(DeletePostModel model) {
        return "Report";
    }
    
    protected static class KusabaReader extends WakabaReader {
        public static final int FLAG_HANDLE_ADMIN_TAG = 1 << 0;
        public static final int FLAG_HANDLE_MOD_TAG = 1 << 1;
        public static final int FLAG_HANDLE_LOCKED_STICKY = 1 << 2;
        public static final int FLAG_OMITTED_STRING_REMOVE_HREF = 1 << 3;
        public static final int FLAG_HANDLE_EMBEDDED_POST_POSTPROCESS = 1 << 4;
        
        private static final Pattern PATTERN_EMBEDDED =
                Pattern.compile("<object type=\"application/x-shockwave-flash\"(?:[^>]*)data=\"([^\"]*)\"(?:[^>]*)>", Pattern.DOTALL);
        
        private static final Pattern A_HREF = Pattern.compile("<a href[^>]*>");
        
        private static final char[] FILTER_ADMIN = "<span class=\"admin\">".toCharArray();
        private static final char[] FILTER_MOD = "<span class=\"mod\">".toCharArray();
        private static final char[] FILTER_SPAN_CLOSE = "</span>".toCharArray();
        
        private int curAdminPos = 0;
        private int curModPos = 0;
        private String adminTrip = null;
        
        private final int flags;
        
        public KusabaReader(InputStream in, DateFormat dateFormat, boolean canCloudflare, int flags) {
            super(in, dateFormat, canCloudflare);
            this.flags = flags;
        }
        
        public KusabaReader(Reader reader, DateFormat dateFormat, boolean canCloudflare, int flags) {
            super(reader, dateFormat, canCloudflare);
            this.flags = flags;
        }
        
        private boolean hasFlag(int flag) {
            return (flags & flag) != 0;
        }
        
        private void addAdminTrip(String trip) {
            if (adminTrip == null) adminTrip = trip; else adminTrip += trip;
        }
        
        /** переопределяющий метод должен вызвать метод суперкласса */
        @Override
        protected void postprocessPost(PostModel post) {
            if (adminTrip != null) {
                post.trip += adminTrip;
                adminTrip = null;
            }
            
            if (hasFlag(FLAG_HANDLE_EMBEDDED_POST_POSTPROCESS)) {
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
                    attachment.size = -1;
                    attachment.path = url;
                    attachment.thumbnail = id != null ? ("http://img.youtube.com/vi/" + id + "/default.jpg") : null;
                    
                    int oldCount = post.attachments != null ? post.attachments.length : 0;
                    AttachmentModel[] attachments = new AttachmentModel[oldCount + 1];
                    for (int i=0; i<oldCount; ++i) attachments[i] = post.attachments[i];
                    attachments[oldCount] = attachment;
                    post.attachments = attachments;
                }
            }
            
        }
        
        /** переопределяющий метод должен вызвать метод суперкласса */
        @Override
        protected void customFilters(int ch) throws IOException {
            if (ch == FILTER_ADMIN[curAdminPos]) {
                ++curAdminPos;
                if (curAdminPos == FILTER_ADMIN.length) {
                    if (hasFlag(FLAG_HANDLE_ADMIN_TAG)) {
                        addAdminTrip(StringEscapeUtils.unescapeHtml4(readUntilSequence(FILTER_SPAN_CLOSE)).trim());
                    }
                    curAdminPos = 0;
                }
            } else {
                if (curAdminPos != 0) curAdminPos = ch == FILTER_ADMIN[0] ? 1 : 0;
            }
            
            if (ch == FILTER_MOD[curModPos]) {
                ++curModPos;
                if (curModPos == FILTER_MOD.length) {
                    if (hasFlag(FLAG_HANDLE_MOD_TAG)) {
                        addAdminTrip(StringEscapeUtils.unescapeHtml4(readUntilSequence(FILTER_SPAN_CLOSE)).trim());
                    }
                    curModPos = 0;
                }
            } else {
                if (curModPos != 0) curModPos = ch == FILTER_MOD[0] ? 1 : 0;
            }
        }
        
        @Override
        protected void parseOmittedString(String omitted) {
            if (hasFlag(FLAG_OMITTED_STRING_REMOVE_HREF)) omitted = RegexUtils.replaceAll(omitted, A_HREF, "");
            super.parseOmittedString(omitted);
        }
        
        @Override
        protected void parseThumbnail(String imgTag) {
            if (hasFlag(FLAG_HANDLE_LOCKED_STICKY)) {
                if (imgTag.contains("/css/locked.gif")) currentThread.isClosed = true;
                if (imgTag.contains("/css/sticky.gif")) currentThread.isSticky = true;
            }
            super.parseThumbnail(imgTag);
        }
    }
}
