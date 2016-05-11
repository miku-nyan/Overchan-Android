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

package nya.miku.wishmaster.chans.dfwk;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

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
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;

public class DFWKModule extends AbstractKusabaModule {
    
    private static final String CHAN_NAME = "chuck.dfwk.ru";
    private static final String DOMAIN = "chuck.dfwk.ru";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "df", "ДФач - подземелье суровых дварфоводов", null, false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hh", "Haven & Hearth", null, false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "rl", "Rogue-like games - Рогалики и все, все, все.", null, false)
    };
    private static final String[] ATTACHMENT_FORMATS_DF = new String[] { "jpg", "jpeg", "png", "gif", "7z", "mp3", "rar", "zip" };
    private static final String[] ATTACHMENT_FORMATS = new String[] { "jpg", "jpeg", "png", "gif" };
    
    private static final DateFormat DATEFORMAT;
    static {
        DATEFORMAT = new SimpleDateFormat("EEE yy/MM/dd HH:mm", Locale.US);
        DATEFORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
    }
    
    private static final Pattern A_HREF = Pattern.compile("<a href[^>]*>");
    private static final Pattern LINK_DATE = Pattern.compile("<a href=\"([^\"]*)\">(.*?)</a>", Pattern.DOTALL);
    private static final Pattern EMBED_PATTERN = Pattern.compile("<object (?:[^>]*)data=\"(.*?)\"", Pattern.DOTALL);
    
    public DFWKModule(SharedPreferences preferences, Resources resources) {
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
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_dfwk, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel board = super.getBoard(shortName, listener, task);
        board.defaultUserName = "";
        board.timeZoneId = "GMT+3";
        board.allowReport = BoardModel.REPORT_NOT_ALLOWED;
        board.allowNames = shortName.equals("hh");
        board.attachmentsFormatFilters = shortName.equals("df") ? ATTACHMENT_FORMATS_DF : ATTACHMENT_FORMATS;
        return board;
    }
    
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new WakabaReader(stream, DATEFORMAT) {
            private StringBuilder omittedDigitsBuffer = new StringBuilder();
            @Override
            protected void parseOmittedString(String omitted) {
                int postsOmitted = -1;
                int filesOmitted = -1;
                try {
                    omitted = RegexUtils.replaceAll(omitted, A_HREF, "");
                    int len = omitted.length();
                    for (int i=0; i<=len; ++i) {
                        char ch = i == len ? ' ' : omitted.charAt(i);
                        if (ch >= '0' && ch <= '9') {
                            omittedDigitsBuffer.append(ch);
                        } else {
                            if (omittedDigitsBuffer.length() > 0) {
                                int parsedValue = Integer.parseInt(omittedDigitsBuffer.toString());
                                omittedDigitsBuffer.setLength(0);
                                if (postsOmitted == -1) postsOmitted = parsedValue;
                                else if (filesOmitted == -1) filesOmitted = parsedValue;
                            }
                        }
                    }
                } catch (NumberFormatException e) {}
                if (postsOmitted > 0) currentThread.postsCount += postsOmitted;
                if (filesOmitted > 0) currentThread.attachmentsCount += filesOmitted;
            }
            @Override
            protected void parseDate(String date) {
                Matcher linkMatcher = LINK_DATE.matcher(date);
                if (linkMatcher.find()) {
                    currentPost.email = linkMatcher.group(1);
                    if (currentPost.email.toLowerCase(Locale.US).startsWith("mailto:")) currentPost.email = currentPost.email.substring(7);
                    super.parseDate(linkMatcher.group(2));
                } else super.parseDate(date);
            }
            @Override
            protected void postprocessPost(PostModel post) {
                Matcher embedMatcher = EMBED_PATTERN.matcher(post.comment);
                if (embedMatcher.find()) {
                    AttachmentModel embedAttachment = new AttachmentModel();
                    embedAttachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                    embedAttachment.size = -1;
                    embedAttachment.path = embedMatcher.group(1);
                    if (embedAttachment.path.contains("youtube.com/v/")) {
                        String ytId = embedAttachment.path.substring(embedAttachment.path.indexOf("youtube.com/v/") + 14);
                        embedAttachment.path = "http://youtube.com/watch?v=" + ytId;
                        embedAttachment.thumbnail = "http://img.youtube.com/vi/" + ytId + "/default.jpg";
                    }
                    if (post.attachments != null && post.attachments.length > 0) {
                        post.attachments = new AttachmentModel[] { post.attachments[0], embedAttachment };
                    } else {
                        post.attachments = new AttachmentModel[] { embedAttachment };
                    }
                }
            }
        };
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() + "captcha.php?" + Double.toString(Math.random());
        return downloadCaptcha(captchaUrl, listener, task);
    }
    
    @Override
    protected String getBoardScriptUrl(Object tag) {
        return getUsingUrl() + "board45.php";
    }
    
    @Override
    protected void setSendPostEntityMain(SendPostModel model, ExtendedMultipartBuilder postEntityBuilder) {
        postEntityBuilder.
                addString("board", model.boardName).
                addString("replythread", model.threadNumber == null ? "0" : model.threadNumber).
                addString("name", model.name).
                addString("em", model.sage ? "sage" : model.email).
                addString("captcha", model.captchaAnswer).
                addString("subject", model.subject).
                addString("message", model.comment);
    }
    
    @Override
    protected List<? extends NameValuePair> getDeleteFormAllValues(DeletePostModel model) {
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("del_" + model.postNumber, model.postNumber));
        if (model.onlyFiles) pairs.add(new BasicNameValuePair("fileonly", "on"));
        pairs.add(new BasicNameValuePair("postpassword", model.password));
        pairs.add(new BasicNameValuePair("deletepost", "Удалить"));
        return pairs;
    }
    
}
