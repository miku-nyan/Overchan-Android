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

package nya.miku.wishmaster.api;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;

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

public abstract class AbstractWakabaModule extends AbstractChanModule {
    
    protected static final String PREF_KEY_USE_HTTPS = "PREF_KEY_USE_HTTPS";
    protected static final String PREF_KEY_CLOUDFLARE_COOKIE = "PREF_KEY_CLOUDFLARE_COOKIE";
    
    protected static final String CLOUDFLARE_COOKIE_NAME = "cf_clearance";
    protected static final String CLOUDFLARE_RECAPTCHA_KEY = "6LfOYgoTAAAAAInWDVTLSc8Yibqp-c9DaLimzNGM";
    protected static final String CLOUDFLARE_RECAPTCHA_CHECK_URL_FMT = "cdn-cgi/l/chk_captcha?recaptcha_challenge_field=%s&recaptcha_response_field=%s";
    
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
    
    protected boolean wakabaNoRedirect() {
        return false;
    }
    
    @Override
    protected void initHttpClient() {
        if (canCloudflare()) {
            String cloudflareCookie = preferences.getString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE), null);
            if (cloudflareCookie != null) {
                BasicClientCookie c = new BasicClientCookie(CLOUDFLARE_COOKIE_NAME, cloudflareCookie);
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
        return new WakabaReader(stream, null, canCloudflare());
    }
    
    protected ThreadModel[] readWakabaPage(String url, ProgressListener listener, CancellableTask task, boolean checkModified, UrlPageModel urlModel)
            throws Exception {
        HttpResponseModel responseModel = null;
        WakabaReader in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkModified).setNoRedirect(wakabaNoRedirect()).build();
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
                            throw CloudflareException.withRecaptcha(getUsingUrl(), getChanName(), html, true);
                        } else if (responseModel.statusCode == 503 && html.contains("Just a moment...")) {
                            throw CloudflareException.antiDDOS(url, getChanName());
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
    
    protected SimpleBoardModel[] getBoardsList() {
        return new SimpleBoardModel[0];
    }
    
    
    protected Map<String, SimpleBoardModel> getBoardsMap(ProgressListener listener, CancellableTask task) throws Exception {
        if (boardsMap == null) {
            Map<String, SimpleBoardModel> map = new HashMap<>();
            for (SimpleBoardModel board : getBoardsList(listener, task, null)) map.put(board.boardName, board);
            boardsMap = map;
        }
        return boardsMap;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        Map<String, SimpleBoardModel> map = getBoardsMap(listener, task);
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
