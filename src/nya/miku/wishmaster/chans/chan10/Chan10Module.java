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

package nya.miku.wishmaster.chans.chan10;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntityHC4;
import org.apache.http.message.BasicNameValuePair;

import android.annotation.SuppressLint;
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
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

@SuppressWarnings("deprecation") //https://issues.apache.org/jira/browse/HTTPCLIENT-1632
public class Chan10Module extends AbstractWakabaModule {
    private static final String TAG = "Chan10Module";
    
    private static final String CHAN_NAME = "10ch.ru";
    private static final String DOMAIN = "10ch.ru";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Бред", " ", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Анимация", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "Работа сайта",  " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "conf", "Конференц-зал", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "edit", "Редакция", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "bc", "Обсуждение", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ad", "Links", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "o", "Other", " ", false)
    };
    private static final Pattern ERROR_POSTING = Pattern.compile("<h2(?:[^>]*)>(.*?)</h2>", Pattern.DOTALL);
    
    public Chan10Module(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Десятый канал (10ch.ru)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_10ch, null);
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
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        switch (shortName) {
            case "a": model.defaultUserName = "Ko-tan"; break;
            case "edit": model.defaultUserName = "Unregistered"; break;
            case "bc": model.defaultUserName = "Журналист"; break;
            case "ad": model.defaultUserName = "Spamer"; break;
            case "o": model.defaultUserName = "Чатер-аватарка"; break;
            default: model.defaultUserName = "Саша"; break;
        }
        
        model.timeZoneId = "GMT+3";
        model.readonlyBoard = false;
        model.requiredFileForNewThread = shortName.equals("a") || shortName.equals("b") || shortName.equals("edit");
        model.allowDeletePosts = true;
        model.allowDeleteFiles = true;
        model.allowReport = BoardModel.REPORT_WITH_COMMENT;
        model.allowNames = !shortName.equals("b") && !shortName.equals("d") && !shortName.equals("a");
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = true;
        model.allowCustomMark = false;
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = 1;
        model.attachmentsFormatFilters = null;
        model.markType = BoardModel.MARK_BBCODE;
        return model;
    }
    
    @SuppressLint("SimpleDateFormat")
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new WakabaReader(stream, null, true) {
            private final DateFormat dateFormat;
            {
                DateFormatSymbols symbols = new DateFormatSymbols();
                symbols.setMonths(new String[] {
                        "Января", "Февраля", "Марта", "Апреля", "Мая", "Июня", "Июля", "Августа", "Сентября", "Октября", "Ноября", "Декабря"});
                dateFormat = new SimpleDateFormat("dd MMMM yy HH:mm:ss", symbols);
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+3"));
            }
            int curIframePos = 0;
            int curAdminPos = 0;
            String adminTrip = null;
            final char[] iframeFilter = "<iframe".toCharArray();
            final char[] adminFilter = "<span class=\"admin\">".toCharArray();
            final Pattern iframeSrc = Pattern.compile("src=\"([^\"]*)\"");
            @Override
            protected void postprocessPost(PostModel post) {
                if (adminTrip != null) {
                    post.trip += adminTrip;
                    adminTrip = null;
                }
            }
            @Override
            protected void customFilters(int ch) throws IOException {
                if (ch == adminFilter[curAdminPos]) {
                    ++curAdminPos;
                    if (curAdminPos == adminFilter.length) {
                        adminTrip = StringEscapeUtils.unescapeHtml4(readUntilSequence("</span>".toCharArray())).trim();
                        curAdminPos = 0;
                    }
                } else {
                    if (curAdminPos != 0) curAdminPos = ch == iframeFilter[0] ? 1 : 0;
                }
                
                if (ch == iframeFilter[curIframePos]) {
                    ++curIframePos;
                    if (curIframePos == iframeFilter.length) {
                        Matcher srcMatcher = iframeSrc.matcher(readUntilSequence(">".toCharArray()));
                        if (srcMatcher.find()) {
                            AttachmentModel attachment = new AttachmentModel();
                            attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                            attachment.path = srcMatcher.group(1);
                            attachment.size = -1;
                            if (attachment.path.startsWith("//")) attachment.path = "http:" + attachment.path;
                            if (attachment.path.contains("coub.com/embed/")) {
                                int qIndex = attachment.path.indexOf('?');
                                if (qIndex > 0) attachment.path = attachment.path.substring(0, qIndex);
                                attachment.path = attachment.path.replace("/embed/", "/view/");
                            }
                            currentAttachments.add(attachment);
                        }
                        curIframePos = 0;
                    }
                } else {
                    if (curIframePos != 0) curIframePos = ch == iframeFilter[0] ? 1 : 0;
                }
            }
            @Override
            protected void parseThumbnail(String imgTag) {
                super.parseThumbnail(imgTag);
                
                try {
                    int sIndex = 0;
                    while (imgTag.charAt(sIndex) <= ' ') ++sIndex;
                    if (imgTag.startsWith("id=\"start_video", sIndex)) {
                        String id = imgTag.substring(sIndex + 15, imgTag.indexOf('"', sIndex + 15));
                        AttachmentModel attachment = new AttachmentModel();
                        attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                        attachment.path = "http://youtube.com/watch?v=" + id;
                        attachment.thumbnail = "http://img.youtube.com/vi/" + id + "/default.jpg";
                        attachment.size = -1;
                        currentAttachments.add(attachment);
                    }
                } catch (Exception e) { //string array bounds
                    Logger.e(TAG, e);
                }
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
        };
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "board.php";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("board", model.boardName).
                addString("replythread", model.threadNumber == null ? "0" : model.threadNumber).
                addString("name", model.name).
                addString("em", model.sage ? "sage" : model.email).
                addString("subject", model.subject).
                addString("message", model.comment);
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("imagefile", model.attachments[0], model.randomHash);
        else if (model.threadNumber == null) postEntityBuilder.addString("nofile", "on");
        postEntityBuilder.addString("embed", "").
                addString("postpassword", model.password).
                addString("redirecttothread", "1");
        
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
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setNoRedirect(true).build();
        String result = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        if (result.contains("Неверный пароль")) throw new Exception("Неверный пароль");
        return null;
    }
    
    @Override
    public String reportPost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "board.php";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("post[]", model.postNumber));
        pairs.add(new BasicNameValuePair("reportreason", model.reportReason));
        pairs.add(new BasicNameValuePair("reportpost", "Отправить"));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setNoRedirect(true).build();
        String result = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        if (result.contains("Post successfully reported")) return null;
        throw new Exception(result);
    }
    
}
