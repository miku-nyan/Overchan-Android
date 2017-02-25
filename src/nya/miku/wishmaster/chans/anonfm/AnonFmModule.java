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

package nya.miku.wishmaster.chans.anonfm;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONArray;

public class AnonFmModule extends AbstractChanModule {
    private static final String CHAN_NAME = "anon.fm";
    private static final String DOMAIN = "anon.fm";
    private static final Pattern CID_PATTERN = Pattern.compile("name=\"cid\" value=\"(\\d+)\"");
    private static final Header[] HTTP_HEADER_FEEDBACK = new Header[] { new BasicHeader(HttpHeaders.REFERER, "http://" + DOMAIN + "/feedback/") };
    private static final DateFormat TIMEFORMAT;
    private static final BoardModel BOARD;
    static {
        TIMEFORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        TIMEFORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
        BOARD = new BoardModel();
        BOARD.chan = CHAN_NAME;
        BOARD.boardName = "anon.fm";
        BOARD.boardDescription = "anon.fm";
        BOARD.nsfw = false;
        BOARD.uniqueAttachmentNames = true;
        BOARD.timeZoneId = "GMT+3";
        BOARD.defaultUserName = "";
        BOARD.bumpLimit = Integer.MAX_VALUE;
        BOARD.readonlyBoard = false;
        BOARD.requiredFileForNewThread = false;
        BOARD.allowDeletePosts = false;
        BOARD.allowDeleteFiles = false;
        BOARD.allowReport = BoardModel.REPORT_NOT_ALLOWED;
        BOARD.allowNames = false;
        BOARD.allowSubjects = false;
        BOARD.allowSage = false;
        BOARD.allowEmails = false;
        BOARD.allowCustomMark = false;
        BOARD.allowRandomHash = false;
        BOARD.allowIcons = false;
        BOARD.attachmentsMaxCount = 0;
        BOARD.markType = BoardModel.MARK_NOMARK;
        BOARD.firstPage = 0;
        BOARD.lastPage = 0;
        BOARD.searchAllowed = false;
        BOARD.catalogAllowed = false;
    }
    
    private String cid = null;
    
    public AnonFmModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Радио Анонимус (feedback)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_anonfm, null);
    }
    
    private boolean useHttps() {
        return useHttps(false);
    }
    
    private String getUsingUrl() {
        return (useHttps() ? "https://" : "http://") + DOMAIN + "/";
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addHttpsPreference(preferenceGroup, false);
        addProxyPreferences(preferenceGroup);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        if (shortName.equals(BOARD.boardName)) return BOARD;
        throw new IllegalArgumentException();
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList) {
        throw new UnsupportedOperationException();
    }
    
    private UrlPageModel getPageModel(String threadNumber) {
        UrlPageModel pageModel = new UrlPageModel();
        pageModel.type = UrlPageModel.TYPE_THREADPAGE;
        pageModel.chanName = CHAN_NAME;
        pageModel.boardName = BOARD.boardName;
        pageModel.threadNumber = threadNumber;
        return pageModel;
    }
    
    private PostModel getPostHeader(String threadNumber, boolean newThreadLink) {
        PostModel postHeader = new PostModel();
        postHeader.number = threadNumber;
        postHeader.subject = "Кукареканье со стороны диджейки";
        postHeader.name = "";
        postHeader.comment = !newThreadLink ? "" : ("<a href=\"" + getUsingUrl() + "#_" +
                Integer.toString(Integer.parseInt(getCurrentThreadNumber().substring(1)) + 1) + "\">Перейти на новую страницу</a>");
        return postHeader;
    }
    
    private String getCurrentThreadNumber() {
        return "_" + preferences.getInt(getSharedKey("curthread"), 1);
    }
    
    private void setCurrentThreadNumber(String threadNumber) {
        if (!threadNumber.startsWith("_")) throw new IllegalArgumentException();
        int i = Integer.parseInt(threadNumber.substring(1));
        int currentThread = preferences.getInt(getSharedKey("curthread"), 1);
        if (i == currentThread) return;
        if (i < currentThread) throw new IllegalArgumentException("Перейдите на новую страницу");
        preferences.edit().putInt(getSharedKey("curthread"), i).commit();
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        String url = getUsingUrl();
        if (model.threadNumber != null && model.threadNumber.length() > 0) url += "#" + model.threadNumber;
        return url;
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        if (UrlPathUtils.getUrlPath(url, DOMAIN) == null) throw new IllegalArgumentException("wrong domain");
        String threadNumber = url.substring(url.indexOf('#') + 1);
        if (threadNumber.length() == url.length()) threadNumber = getCurrentThreadNumber();
        return getPageModel(threadNumber);
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        if (!boardName.equals(BOARD.boardName)) throw new IllegalArgumentException();
        setCurrentThreadNumber(threadNumber);
        String url = getUsingUrl() + "answers.js";
        HttpRequestModel request = HttpRequestModel.builder().setGET().setCheckIfModified(oldList != null).build();
        JSONArray json = HttpStreamer.getInstance().getJSONArrayFromUrl(url, request, httpClient, listener, task, false);
        if (json == null) return oldList;
        long oneday = 86400000;
        long now = System.currentTimeMillis();
        long startday = (now / oneday) * oneday - (3 * 3600000) + oneday; //today or tomorrow
        PostModel[] posts = new PostModel[json.length()+1];
        posts[0] = oldList != null && oldList.length > 0 ? oldList[0] : getPostHeader(threadNumber, false);
        for (int i=json.length(); i>=1; --i) {
            JSONArray current = json.getJSONArray(json.length()-i);
            long time = TIMEFORMAT.parse(RegexUtils.removeHtmlTags(current.getString(3))).getTime();
            posts[i] = new PostModel();
            posts[i].number = Long.toString(time); //current.getString(4) ?
            posts[i].name = current.getString(1).equals("!") ? "Объявление" : current.getString(1);
            posts[i].subject = "";
            posts[i].comment = "<blockquote class=\"unkfunc\">" + current.getString(2) + "</blockquote>" + current.getString(5);
            posts[i].timestamp = startday + time;
            while (posts[i].timestamp > (i<json.length() ? posts[i+1].timestamp : now)) posts[i].timestamp -= oneday;
            posts[i].parentThread = threadNumber;
        }
        if (oldList != null) {
            posts = ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(posts));
            posts[0] = getPostHeader(threadNumber, posts.length > 100);
            for (PostModel post : posts) post.deleted = false;
        }
        return posts;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel request = HttpRequestModel.builder().setGET().setCustomHeaders(HTTP_HEADER_FEEDBACK).build();
        String postFormHtml = HttpStreamer.getInstance().getStringFromUrl(getUsingUrl() + "feedback", request, httpClient, null, task, false);
        Matcher matcher = CID_PATTERN.matcher(postFormHtml);
        if (!matcher.find()) throw new Exception("Couldn't get captcha");
        
        String cid = matcher.group(1);
        this.cid = cid;
        String captchaUrl = getUsingUrl() + "feedback/" + cid + ".gif";
        
        Bitmap captchaBitmap = null;
        HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(captchaUrl, request, httpClient, listener, task);
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
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        if (model.comment.length() > 500) throw new Exception("Максимальная длина сообщения - 500 символов");
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("cid", cid));
        pairs.add(new BasicNameValuePair("left", Integer.toString(500 - model.comment.length())));
        pairs.add(new BasicNameValuePair("msg", model.comment));
        pairs.add(new BasicNameValuePair("check", model.captchaAnswer));
        
        HttpRequestModel request =
                HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setCustomHeaders(HTTP_HEADER_FEEDBACK).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(getUsingUrl() + "feedback", request, httpClient, null, task);
            if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (htmlResponse.contains("window.close,10000")) return getUsingUrl() + "#" + getCurrentThreadNumber();
                if (htmlResponse.contains("Неверный код подтверждения")) throw new Exception("Неверный код подтверждения");
                throw new Exception("Ошибка отправки");
            } else {
                throw new Exception(response.statusCode + " - " + response.statusReason);
            }
        } finally {
            if (response != null) response.release();
        }
    }
}
