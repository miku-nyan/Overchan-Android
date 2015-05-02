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

package nya.miku.wishmaster.chans;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookieHC4;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.api.util.WakabaUtils;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.cloudflare.CloudflareException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceGroup;

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

public abstract class AbstractWakabaModule extends AbstractChanModule {
    
    private static final String PREF_KEY_USE_HTTPS = "PREF_KEY_USE_HTTPS";
    private static final String PREF_KEY_CLOUDFLARE_COOKIE = "PREF_KEY_CLOUDFLARE_COOKIE";
    
    private static final String CLOUDFLARE_COOKIE_NAME = "cf_clearance";
    private static final String CLOUDFLARE_RECAPTCHA_KEY = "6LeT6gcAAAAAAAZ_yDmTMqPH57dJQZdQcu6VFqog"; 
    private static final String CLOUDFLARE_RECAPTCHA_CHECK_URL_FMT = "cdn-cgi/l/chk_captcha?recaptcha_challenge_field=%s&recaptcha_response_field=%s";
    
    private Map<String, SimpleBoardModel> boardsMap = null;
    
    public AbstractWakabaModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    protected abstract String getUsingDomain();
    
    protected String[] getAllDomains() {
        return new String[] { getUsingDomain() };
    }
    
    protected boolean canHttps() {
        return false;
    }
    
    protected boolean useHttpsDefaultValue() {
        return true;
    }
    
    protected boolean useHttps() {
        if (!canHttps()) return false;
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS), useHttpsDefaultValue());
    }
    
    protected String getUsingUrl() {
        return (useHttps() ? "https://" : "http://") + getUsingDomain() + "/";
    }
    
    protected boolean canCloudflare() {
        return false;
    }
    
    @Override
    protected void initHttpClient() {
        if (canCloudflare()) {
            String cloudflareCookie = preferences.getString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE), null);
            if (cloudflareCookie != null) {
                BasicClientCookieHC4 c = new BasicClientCookieHC4(CLOUDFLARE_COOKIE_NAME, cloudflareCookie);
                c.setDomain(getUsingDomain());
                httpClient.getCookieStore().addCookie(c);
            }
        }
    }
    
    @Override
    public void saveCookie(Cookie cookie) {
        if (cookie != null) {
            httpClient.getCookieStore().addCookie(cookie);
            if (canCloudflare()) {
                if (cookie.getName().equals(CLOUDFLARE_COOKIE_NAME)) {
                    preferences.edit().putString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE), cookie.getValue()).commit();
                }
            }
        }
    }
    
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new WakabaReader(stream);
    }
    
    protected ThreadModel[] readWakabaPage(String url, ProgressListener listener, CancellableTask task, boolean checkModified, UrlPageModel urlModel)
            throws Exception {
        HttpResponseModel responseModel = null;
        WakabaReader in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkModified).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = getWakabaReader(responseModel.stream, urlModel);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return in.readWakabaPage();
            } else {
                if (responseModel.notModified()) return null;
                
                if (canCloudflare()) {
                    String html = null;
                    try {
                        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
                        IOUtils.copyStream(responseModel.stream, byteStream);
                        html = byteStream.toString("UTF-8");
                    } catch (Exception e) {}
                    if (html != null) {
                        if (responseModel.statusCode == 403 && html.contains("CAPTCHA")) {
                            throw CloudflareException.withRecaptcha(CLOUDFLARE_RECAPTCHA_KEY,
                                    getUsingUrl() + CLOUDFLARE_RECAPTCHA_CHECK_URL_FMT, CLOUDFLARE_COOKIE_NAME, getChanName());
                        } else if (responseModel.statusCode == 503 && html.contains("Just a moment...")) {
                            throw CloudflareException.antiDDOS(url, CLOUDFLARE_COOKIE_NAME, getChanName());
                        }
                    }
                }
                throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode + " - " + responseModel.statusReason);
            }
        } catch (Exception e) {
            if (responseModel != null) HttpStreamer.getInstance().removeFromModifiedMap(url);
            throw e;
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
        }
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        addPasswordPreference(preferenceGroup);
        if (canHttps()) {
            CheckBoxPreference httpsPref = new CheckBoxPreference(context);
            httpsPref.setTitle(R.string.pref_use_https);
            httpsPref.setSummary(R.string.pref_use_https_summary);
            httpsPref.setKey(getSharedKey(PREF_KEY_USE_HTTPS));
            httpsPref.setDefaultValue(useHttpsDefaultValue());
            preferenceGroup.addPreference(httpsPref);
            addUnsafeSslPreference(preferenceGroup, getSharedKey(PREF_KEY_USE_HTTPS));
        }
        addProxyPreferences(preferenceGroup);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        return getBoardsList();
    }
    
    protected abstract SimpleBoardModel[] getBoardsList();
    
    
    protected Map<String, SimpleBoardModel> getBoardsMap() {
        if (boardsMap == null) {
            Map<String, SimpleBoardModel> map = new HashMap<>();
            for (SimpleBoardModel board : getBoardsList()) map.put(board.boardName, board);
            boardsMap = map;
        }
        return boardsMap;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        Map<String, SimpleBoardModel> map = getBoardsMap();
        SimpleBoardModel simpleModel = map.get(shortName);
        BoardModel model = new BoardModel();
        model.chan = getChanName();
        model.boardName = shortName;
        model.boardDescription = shortName;
        model.uniqueAttachmentNames = true;
        model.timeZoneId = "UTC";
        model.defaultUserName = "Anonymous";
        model.bumpLimit = 500;
        model.readonlyBoard = true;
        model.firstPage = 0;
        model.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        model.searchAllowed = false;
        model.catalogAllowed = false;
        if (simpleModel != null) {
            model.boardDescription = simpleModel.boardDescription;
            model.boardCategory = simpleModel.boardCategory;
            model.nsfw = simpleModel.nsfw;
        }
        return model;
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = getChanName();
        urlModel.type = UrlPageModel.TYPE_BOARDPAGE;
        urlModel.boardName = boardName;
        urlModel.boardPage = page;
        String url = buildUrl(urlModel);
        
        ThreadModel[] threads = readWakabaPage(url, listener, task, oldList != null, urlModel);
        if (threads == null) {
            return oldList;
        } else {
            return threads;
        }
    }

    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = getChanName();
        urlModel.type = UrlPageModel.TYPE_THREADPAGE;
        urlModel.boardName = boardName;
        urlModel.threadNumber = threadNumber;
        String url = buildUrl(urlModel);
        
        ThreadModel[] threads = readWakabaPage(url, listener, task, oldList != null, urlModel);
        if (threads == null) {
            return oldList;
        } else {
            if (threads.length == 0) throw new Exception("Unable to parse response");
            return oldList == null ? threads[0].posts : ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(threads[0].posts));
        }
    }

    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(getChanName())) throw new IllegalArgumentException("wrong chan");
        return WakabaUtils.buildUrl(model, getUsingUrl());
    }

    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        return WakabaUtils.parseUrl(url, getChanName(), getAllDomains());
    }

}
