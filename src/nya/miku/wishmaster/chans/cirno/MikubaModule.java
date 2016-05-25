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

package nya.miku.wishmaster.chans.cirno;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.entity.mime.content.ByteArrayBody;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.CloudflareChanModule;
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
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;

/**
 * Модуль для борды hatsune.ru
 * @author miku-nyan
 *
 */

public class MikubaModule extends CloudflareChanModule {
    private static final String TAG = "MikubaModule";
    
    private static final String MIKUBA_NAME = "hatsune.ru";
    private static final String MIKUBA_DOMAIN = "hatsune.ru";
    
    private static final Pattern URL_PATH_BOARDPAGE_PATTERN = Pattern.compile("(?:b(?:/(\\d+)?)?)?");
    private static final Pattern URL_PATH_THREADPAGE_PATTERN = Pattern.compile("reply/(\\d+)(?:#r(\\d+))?");
    
    private static final Pattern YOUTUBE_PATTERN = Pattern.compile("(?:\\s|^)(?:https?://)?(?:www\\.)?youtube.com/watch\\?v=(\\w+)(?:.*?)(?:\\s|$)",
            Pattern.CASE_INSENSITIVE);
    
    private static final String PREF_KEY_USE_HTTPS = "PREF_KEY_USE_HTTPS";
    private static final String PREF_KEY_SESSION_COOKIE = "PREF_KEY_SESSION_COOKIE";
    
    private static final String SESSION_COOKIE_NAME = "webpy_session_id";
    
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
        MIKUBA_BOARD.allowCustomMark = false;
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
        return "hatsune.ru (Miku-chan)";
    }

    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_mikuba, null);
    }
    
    @Override
    protected void initHttpClient() {
        super.initHttpClient();
        String sessionCookie = preferences.getString(getSharedKey(PREF_KEY_SESSION_COOKIE), null);
        if (sessionCookie != null) {
            BasicClientCookie c = new BasicClientCookie(SESSION_COOKIE_NAME, sessionCookie);
            c.setDomain(MIKUBA_DOMAIN);
            httpClient.getCookieStore().addCookie(c);
        }
    }
    
    @Override
    protected String getCloudflareCookieDomain() {
        return MIKUBA_DOMAIN;
    }
    
    @Override
    public void saveCookie(Cookie cookie) {
        super.saveCookie(cookie);
        if (cookie != null) {
            saveCookieToPreferences(cookie);
        }
    }
    
    private void saveCookiesToPreferences() {
        for (Cookie cookie : httpClient.getCookieStore().getCookies()) saveCookieToPreferences(cookie);
    }
    
    private void saveCookieToPreferences(Cookie cookie) {
        if (cookie.getName().equals(SESSION_COOKIE_NAME)) {
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
        httpsPref.setDefaultValue(true);
        preferenceGroup.addPreference(httpsPref);
        addCloudflareRecaptchaFallbackPreference(preferenceGroup);
        addProxyPreferences(preferenceGroup);
    }
    
    private boolean useHttps() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS), true);
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
                byte[] html = null;
                try {
                    ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
                    IOUtils.copyStream(responseModel.stream, byteStream);
                    html = byteStream.toByteArray();
                } catch (Exception e) {}
                if (html != null) {
                    checkCloudflareError(new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusReason, html), url);
                }
                throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode + " - " + responseModel.statusReason);
            }
        } catch (Exception e) {
            if (responseModel != null) HttpStreamer.getInstance().removeFromModifiedMap(url);
            throw e;
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
        try {
            String captchaUrl = (useHttps() ? "https://" : "http://") + MIKUBA_DOMAIN + "/c";
            return downloadCaptcha(captchaUrl, listener, task);
        } finally {
            saveCookiesToPreferences();
        }
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
        String path = UrlPathUtils.getUrlPath(url, MIKUBA_DOMAIN);
        if (path == null) throw new IllegalArgumentException("wrong domain");
        path = path.toLowerCase(Locale.US);
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = MIKUBA_NAME;
        model.boardName = "vo";
        Matcher boardPageMatcher = URL_PATH_BOARDPAGE_PATTERN.matcher(path);
        if (boardPageMatcher.matches()) {
            model.type = UrlPageModel.TYPE_BOARDPAGE;
            String found = boardPageMatcher.group(1);
            model.boardPage = found == null ? 0 : Integer.parseInt(found);
            return model;
        }
        Matcher threadPageMatcher = URL_PATH_THREADPAGE_PATTERN.matcher(path);
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
