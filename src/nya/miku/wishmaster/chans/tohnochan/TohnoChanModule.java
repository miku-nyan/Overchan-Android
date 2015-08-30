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

package nya.miku.wishmaster.chans.tohnochan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

@SuppressWarnings("deprecation") //https://issues.apache.org/jira/browse/HTTPCLIENT-1632
public class TohnoChanModule extends AbstractWakabaModule {
    private static final String CHAN_NAME = "tohno-chan.com";
    private static final String CHAN_DOMAIN = "tohno-chan.com";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "an", "Anime", "Media/Entertainment", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ma", "Manga", "Media/Entertainment", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "Video Games", "Media/Entertainment", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "foe", "Touhou", "Media/Entertainment", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mp3", "Music", "Media/Entertainment", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vn", "Visual Novels", "Media/Entertainment", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fig", "Collectibles", "Hobbies/Interests", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "navi", "Science & technology", "Hobbies/Interests", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "cr", "Creativity", "Hobbies/Interests", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "so", "Ronery", "Broad discussion", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mai", "Waifu", "Broad discussion", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ot", "Otaku Tangents", "Broad discussion", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "日本", "日本語", "Broad discussion", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mt", "Academia", "Broad discussion", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ns", "Hentai", "Other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fb", "Feedback", "Other", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pic", "Dump", "Other", false),
    };
    private static final Pattern ERROR_POSTING = Pattern.compile("<h2(?:[^>]*)>(.*?)</h2>", Pattern.DOTALL);
    private static final Pattern YOUTUBE_PATTERN = Pattern.compile("data=\"(?:.*?)/v/([^&\"\\s]*)", Pattern.DOTALL);
    
    @SuppressLint("SimpleDateFormat")
    private static final class DateFormatHolder {
        private static final DateFormat DATE_FORMAT;
        static {
            DateFormatSymbols symbols = new DateFormatSymbols();
            symbols.setShortWeekdays(new String[] { "", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" });
            DATE_FORMAT = new SimpleDateFormat("MM/dd/yy(EEE)HH:mm", symbols);
            DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("US/Pacific"));
        }
    }
    
    public TohnoChanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Tohno chan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_tohnochan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return CHAN_DOMAIN;
    }
    
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new WakabaReader(stream, DateFormatHolder.DATE_FORMAT) {
            private int curEndThreadFilterPos = 0;
            private int curEmbedFilterPos = 0;
            private final char[] endThreadFilter = "<div class=\"Spacer\">".toCharArray();
            private final char[] embedFilter = "<object type=\"application/x-shockwave-flash\"".toCharArray();
            
            private final Method finalizeThread;
            {
                try {
                    finalizeThread = WakabaReader.class.getDeclaredMethod("finalizeThread");
                    finalizeThread.setAccessible(true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            @Override
            protected void customFilters(int ch) throws IOException {
                if (ch == endThreadFilter[curEndThreadFilterPos]) {
                    ++curEndThreadFilterPos;
                    if (curEndThreadFilterPos == endThreadFilter.length) {
                        try {
                            finalizeThread.invoke(this);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        curEndThreadFilterPos = 0;
                    }
                } else {
                    if (curEndThreadFilterPos != 0) curEndThreadFilterPos = ch == endThreadFilter[0] ? 1 : 0;
                }
                
                if (ch == embedFilter[curEmbedFilterPos]) {
                    ++curEmbedFilterPos;
                    if (curEmbedFilterPos == embedFilter.length) {
                        parseEmbedded(readUntilSequence(">".toCharArray()));
                        curEmbedFilterPos = 0;
                    }
                } else {
                    if (curEmbedFilterPos != 0) curEmbedFilterPos = ch == embedFilter[0] ? 1 : 0;
                }
            }
            
            private void parseEmbedded(String tag) {
                Matcher matcher = YOUTUBE_PATTERN.matcher(tag);
                if (matcher.find()) {
                    String id = matcher.group(1);
                    AttachmentModel attachment = new AttachmentModel();
                    attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                    attachment.path = "http://www.youtube.com/watch?v=" + id;
                    attachment.thumbnail = "http://img.youtube.com/vi/" + id + "/default.jpg";
                    currentAttachments.add(attachment);
                }
            }
            
        };
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.timeZoneId = "US/Pacific";
        model.defaultUserName = "Anonymous";
        model.readonlyBoard = false;
        model.requiredFileForNewThread = !shortName.equals("mt") && !shortName.equals("fb");
        model.allowDeletePosts = true;
        model.allowDeleteFiles = true;
        model.allowReport = BoardModel.REPORT_WITH_COMMENT;
        model.allowNames = true;
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = true;
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = 1;
        model.attachmentsFormatFilters = null;
        model.markType = BoardModel.MARK_BBCODE;
        return model;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "board.php";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("board", model.boardName).
                addString("replythread", model.threadNumber == null ? "0" : model.threadNumber).
                addString("editpost", "0").
                addString("name", model.name).
                addString("em", model.sage ? "sage" : ((model.email != null && model.email.length() > 0) ? model.email : "noko" )).
                addString("subj", model.subject).
                addString("message", model.comment).
                addString("postpassword", model.password);
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("imagefile", model.attachments[0], model.randomHash);
        if (model.custommark) postEntityBuilder.addString("spoiler", "on");
        else if (model.threadNumber == null) postEntityBuilder.addString("nofile", "on");
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 302) {
                if (model.threadNumber != null) return null;
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
        pairs.add(new BasicNameValuePair("deletepost", "Delete"));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setNoRedirect(true).build();
        String result = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        if (result.contains("Incorrect password")) throw new Exception("Incorrect password");
        return null;
    }
    
    @Override
    public String reportPost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "board.php";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("post[]", model.postNumber));
        pairs.add(new BasicNameValuePair("reportreason", model.reportReason));
        pairs.add(new BasicNameValuePair("reportpost", "Report"));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setNoRedirect(true).build();
        String result = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        if (result.contains("Post successfully reported")) return null;
        throw new Exception(result);
    }
    
}
