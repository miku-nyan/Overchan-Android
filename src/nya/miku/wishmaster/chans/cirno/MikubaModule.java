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

package nya.miku.wishmaster.chans.cirno;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.cookie.Cookie;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.cookie.BasicClientCookieHC4;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.chans.AbstractChanModule;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.cloudflare.CloudflareException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;

/**
 * Модуль для борды hatsune.ru
 * @author miku-nyan
 *
 */

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

public class MikubaModule extends AbstractChanModule {
    private static final String TAG = "MikubaModule";
    
    private static final String MIKUBA_NAME = "hatsune.ru";
    private static final String MIKUBA_DOMAIN = "hatsune.ru";
    
    private static final Pattern YOUTUBE_PATTERN = Pattern.compile("(?:\\s|^)(?:https?://)?(?:www\\.)?youtube.com/watch\\?v=(\\w+)(?:.*?)(?:\\s|$)",
            Pattern.CASE_INSENSITIVE);
    
    private static final String PREF_KEY_USE_HTTPS = "PREF_KEY_USE_HTTPS";
    private static final String PREF_KEY_CLOUDFLARE_COOKIE = "PREF_KEY_CLOUDFLARE_COOKIE";
    private static final String PREF_KEY_SESSION_COOKIE = "PREF_KEY_SESSION_COOKIE";
    
    private static final String SESSION_COOKIE_NAME = "webpy_session_id";
    
    private static final String CLOUDFLARE_COOKIE_NAME = "cf_clearance";
    private static final String CLOUDFLARE_RECAPTCHA_KEY = "6LeT6gcAAAAAAAZ_yDmTMqPH57dJQZdQcu6VFqog"; 
    private static final String CLOUDFLARE_RECAPTCHA_CHECK_URL_FMT = "cdn-cgi/l/chk_captcha?recaptcha_challenge_field=%s&recaptcha_response_field=%s";
    
    private static final BoardModel MIKUBA_BOARD;
    private static final SimpleBoardModel[] MIKUBA_SIMPLE_BOARDS_LIST;
    static {
        MIKUBA_BOARD = new BoardModel();
        MIKUBA_BOARD.chan = MIKUBA_NAME;
        MIKUBA_BOARD.boardName = "vo";
        MIKUBA_BOARD.boardDescription = "Miku-chan";
        MIKUBA_BOARD.uniqueAttachmentNames = true;
        MIKUBA_BOARD.timeZoneId = "GMT+3";
        MIKUBA_BOARD.defaultUserName = "";
        MIKUBA_BOARD.bumpLimit = 500;
        
        MIKUBA_BOARD.readonlyBoard = false;
        MIKUBA_BOARD.requiredFileForNewThread = false;
        MIKUBA_BOARD.allowDeletePosts = true;
        MIKUBA_BOARD.allowDeleteFiles = false;
        MIKUBA_BOARD.allowNames = false;
        MIKUBA_BOARD.allowSubjects = true;
        MIKUBA_BOARD.allowSage = false;
        MIKUBA_BOARD.allowEmails = false;
        MIKUBA_BOARD.allowWatermark = false;
        MIKUBA_BOARD.allowOpMark = false;
        MIKUBA_BOARD.allowRandomHash = true;
        MIKUBA_BOARD.allowIcons = false;
        MIKUBA_BOARD.attachmentsMaxCount = 1;
        MIKUBA_BOARD.attachmentsFormatFilters = new String[] { "jpg", "jpeg", "png", "gif" };
        
        MIKUBA_BOARD.firstPage = 0;
        MIKUBA_BOARD.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        MIKUBA_SIMPLE_BOARDS_LIST = new SimpleBoardModel[] { new SimpleBoardModel(MIKUBA_BOARD) };
    }
    
    public MikubaModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }

    @Override
    public String getChanName() {
        return MIKUBA_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "hatsune.ru (Ычан - Vocaloid)";
    }

    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_mikuba, null);
    }
    
    @Override
    protected void initHttpClient() {
        String cloudflareCookie = preferences.getString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE), null);
        String sessionCookie = preferences.getString(getSharedKey(PREF_KEY_SESSION_COOKIE), null);
        if (cloudflareCookie != null) {
            BasicClientCookieHC4 c = new BasicClientCookieHC4(CLOUDFLARE_COOKIE_NAME, cloudflareCookie);
            c.setDomain(MIKUBA_DOMAIN);
            httpClient.getCookieStore().addCookie(c);
        }
        if (sessionCookie != null) {
            BasicClientCookieHC4 c = new BasicClientCookieHC4(SESSION_COOKIE_NAME, sessionCookie);
            c.setDomain(MIKUBA_DOMAIN);
            httpClient.getCookieStore().addCookie(c);
        }
    }
    
    @Override
    public void saveCookie(Cookie cookie) {
        if (cookie != null) {
            httpClient.getCookieStore().addCookie(cookie);
            saveCookieToPreferences(cookie);
        }
    }
    
    private void saveCookiesToPreferences() {
        for (Cookie cookie : httpClient.getCookieStore().getCookies()) saveCookieToPreferences(cookie);
    }
    
    private void saveCookieToPreferences(Cookie cookie) {
        if (cookie.getName().equals(CLOUDFLARE_COOKIE_NAME)) {
            preferences.edit().putString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE), cookie.getValue()).commit();
        } else if (cookie.getName().equals(SESSION_COOKIE_NAME)) {
            preferences.edit().putString(getSharedKey(PREF_KEY_SESSION_COOKIE), cookie.getValue()).commit();
        }
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        CheckBoxPreference httpsPref = new CheckBoxPreference(context);
        httpsPref.setTitle(R.string.pref_use_https);
        httpsPref.setSummary(R.string.pref_use_https_summary);
        httpsPref.setKey(getSharedKey(PREF_KEY_USE_HTTPS));
        httpsPref.setDefaultValue(false);
        preferenceGroup.addPreference(httpsPref);
        addUnsafeSslPreference(preferenceGroup, getSharedKey(PREF_KEY_USE_HTTPS));
        addProxyPreferences(preferenceGroup);
    }
    
    private boolean useHttps() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS), false);
    }
    
    @Override
    public String getDefaultPassword() {
        return resources.getString(R.string.mikuba_password_ignored);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        return MIKUBA_SIMPLE_BOARDS_LIST;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        return MIKUBA_BOARD;
    }
    
    private ThreadModel[] readPage(String url, ProgressListener listener, CancellableTask task, boolean checkIfModified) throws Exception {
        HttpResponseModel responseModel = null;
        MikubaReader in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModified).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = new MikubaReader(responseModel.stream);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return in.readPage();
            } else {
                if (responseModel.notModified()) return null;
                String html = null;
                try {
                    ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
                    IOUtils.copyStream(responseModel.stream, byteStream);
                    html = byteStream.toString("UTF-8");
                } catch (Exception e) {}
                if (html != null) {
                    if (responseModel.statusCode == 403 && html.contains("CAPTCHA")) {
                        throw CloudflareException.withRecaptcha(CLOUDFLARE_RECAPTCHA_KEY,
                                (useHttps() ? "https://" : "http://") + MIKUBA_DOMAIN + "/" + CLOUDFLARE_RECAPTCHA_CHECK_URL_FMT,
                                CLOUDFLARE_COOKIE_NAME, MIKUBA_NAME);
                    } else if (responseModel.statusCode == 503 && html.contains("Just a moment...")) {
                        throw CloudflareException.antiDDOS(url, CLOUDFLARE_COOKIE_NAME, MIKUBA_NAME);
                    }
                }
                throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode + " - " + responseModel.statusReason);
            }
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
            saveCookiesToPreferences();
        }
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = (useHttps() ? "https://" : "http://") + MIKUBA_DOMAIN + "/b/" + (page > 0 ? String.valueOf(page) : "");
        ThreadModel[] threads = readPage(url, listener, task, oldList != null);
        if (threads == null) {
            return oldList;
        } else {
            return threads;
        }
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        String url = (useHttps() ? "https://" : "http://") + MIKUBA_DOMAIN + "/reply/" + threadNumber;
        
        ThreadModel[] threads = readPage(url, listener, task, oldList != null);
        if (threads == null) {
            return oldList;
        } else {
            if (threads.length == 0) throw new Exception("Unable to parse response");
            return oldList == null ? threads[0].posts : ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(threads[0].posts));
        }
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = (useHttps() ? "https://" : "http://") + MIKUBA_DOMAIN + "/c";
        
        Bitmap captchaBitmap = null;
        HttpRequestModel requestModel = HttpRequestModel.builder().setGET().build();
        HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(captchaUrl, requestModel, httpClient, listener, task);
        try {
            InputStream imageStream = responseModel.stream;
            captchaBitmap = BitmapFactory.decodeStream(imageStream);
        } finally {
            responseModel.release();
            saveCookiesToPreferences();
        }
        CaptchaModel captchaModel = new CaptchaModel();
        captchaModel.type = CaptchaModel.TYPE_NORMAL;
        captchaModel.bitmap = captchaBitmap;
        return captchaModel;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String body, video;
        Matcher ytLink = YOUTUBE_PATTERN.matcher(model.comment);
        if (ytLink.find()) {
            body = new StringBuilder(model.comment).delete(ytLink.start(), ytLink.end()).toString();
            video = "http://www.youtube.com/watch?v=" + ytLink.group(1);
        } else {
            body = model.comment;
            video = "";
        }
        
        String url = (useHttps() ? "https://" : "http://") + MIKUBA_DOMAIN + "/reply/" + (model.threadNumber == null ? "0" : model.threadNumber);
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("title", model.subject).
                addString("captcha", model.captchaAnswer).
                addString("body", body).
                addString("video", video).
                addString("email", "");
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("image", model.attachments[0], model.randomHash);
        else postEntityBuilder.addPart("image", new ByteArrayBody(new byte[0], ""));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            Logger.d(TAG, response.statusCode + " - " + response.statusReason);
            if (response.statusCode == 303) {
                return null;
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                //Logger.d(TAG, htmlResponse);
                if (htmlResponse.contains("/static/captcha.gif")) {
                    throw new Exception("капча неправильная!");
                }
            } else throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
            saveCookiesToPreferences();
        }
        
        return null;
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = (useHttps() ? "https://" : "http://") + MIKUBA_DOMAIN + "/delete/" + model.postNumber;
        HttpRequestModel request = HttpRequestModel.builder().setGET().setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, listener, task);
            Logger.d(TAG, response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
            saveCookiesToPreferences();
        }
        return null;
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(MIKUBA_NAME)) throw new IllegalArgumentException("wrong chan");
        StringBuilder url = new StringBuilder();
        url.append(useHttps() ? "https://" : "http://").append(MIKUBA_DOMAIN).append('/');
        switch (model.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                return url.append("b/").toString();
            case UrlPageModel.TYPE_BOARDPAGE:
                return url.append("b/").append(
                        model.boardPage != UrlPageModel.DEFAULT_FIRST_PAGE && model.boardPage > 0 ? String.valueOf(model.boardPage) : "").toString();
            case UrlPageModel.TYPE_THREADPAGE:
                return url.append("reply/").append(model.threadNumber).
                        append(model.postNumber == null || model.postNumber.length() == 0 ? "" : ("#r" + model.postNumber)).toString();
            case UrlPageModel.TYPE_OTHERPAGE:
                return url.append(model.otherPath.startsWith("/") ? model.otherPath.substring(1) : model.otherPath).toString();
        }
        throw new IllegalArgumentException("wrong page type");
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String domain;
        String path = "";
        Matcher parseUrl = Pattern.compile("https?://(?:www\\.)?(.+)", Pattern.CASE_INSENSITIVE).matcher(url);
        if (!parseUrl.find()) throw new IllegalArgumentException("incorrect url");
        Matcher parsePath = Pattern.compile("(.+?)(?:/(.*))").matcher(parseUrl.group(1));
        if (parsePath.find()) {
            domain = parsePath.group(1).toLowerCase(Locale.US);
            path = parsePath.group(2);
        } else {
            domain = parseUrl.group(1).toLowerCase(Locale.US);
        }
        if (!domain.equals(MIKUBA_DOMAIN)) throw new IllegalArgumentException("wrong chan");
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = MIKUBA_NAME;
        model.boardName = "vo";
        Matcher boardPageMatcher = Pattern.compile("(?:b(?:/(\\d+)?)?)?").matcher(path);
        if (boardPageMatcher.matches()) {
            model.type = UrlPageModel.TYPE_BOARDPAGE;
            String found = boardPageMatcher.group(1);
            model.boardPage = found == null ? 0 : Integer.parseInt(found);
            return model;
        }
        Matcher threadPageMatcher = Pattern.compile("reply/(\\d+)(?:#r(\\d+))?").matcher(path);
        if (threadPageMatcher.matches()) {
            model.type = UrlPageModel.TYPE_THREADPAGE;
            model.threadNumber = threadPageMatcher.group(1);
            model.postNumber = threadPageMatcher.group(2);
            return model;
        }
        
        model.boardName = null;
        model.type = UrlPageModel.TYPE_OTHERPAGE;
        model.otherPath = path;
        return model;
    }
    
}
