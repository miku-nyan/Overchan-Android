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

package nya.miku.wishmaster.chans.makaba;

import static nya.miku.wishmaster.chans.makaba.MakabaConstants.*;
import static nya.miku.wishmaster.chans.makaba.MakabaJsonMapper.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractChanModule;
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
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.cloudflare.CloudflareException;
import nya.miku.wishmaster.http.recaptcha.Recaptcha;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2solved;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookieHC4;
import org.apache.http.message.BasicHeader;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputType;

/**
 * Класс, осуществляющий взаимодействия с АИБ 2ch.hk (движок makaba)
 * @author miku-nyan
 *
 */

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

public class MakabaModule extends AbstractChanModule {
    private static final String TAG = "MakabaModule"; 
    
    /** что-то типа '2ch.hk' */
    private String domain;
    /** что-то типа 'https://2ch.hk/' */
    private String domainUrl;
    
    private static final int CAPTCHA_YANDEX = 1;
    private static final int CAPTCHA_RECAPTCHA = 2;
    private static final int CAPTCHA_MAILRU = 3;
    private static final int CAPTCHA_RECAPTCHAV1 = 4;
    
    private static final String HASHTAG_PREFIX = "\u00A0#";
    
    /** тип текущей капчи*/
    private int captchaType;
    /** ключ текущей яндекс капчи*/
    private String captchaKey;
    /** id текущей mail.ru капчи*/
    private String captchaMailRuId;
    /** объект текущей рекапчи-v1 */
    private Recaptcha recaptchaV1;
    
    /** карта досок из списка mobile.fcgi */
    private Map<String, BoardModel> boardsMap = null;
    /** дополнительная карта досок (для досок, которые отсутствуют в карте из mobile.fcgi) */
    private Map<String, BoardModel> customBoardsMap = new HashMap<String, BoardModel>();
    
    public MakabaModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
        updateDomain(
                preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN),
                preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS), true));
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Два.ч (2ch.hk)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_makaba, null);
    }
    
    @Override
    protected void initHttpClient() {
        setCookie(
                preferences.getString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE_DOMAIN), null),
                CLOUDFLARE_COOKIE_NAME,
                preferences.getString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE_VALUE), null));
        setCookie(
                preferences.getString(getSharedKey(PREF_KEY_USERCODE_COOKIE_DOMAIN), null),
                USERCODE_COOKIE_NAME,
                preferences.getString(getSharedKey(PREF_KEY_USERCODE_COOKIE_VALUE), null));
    }
    
    private void updateDomain(String domain, boolean useHttps) {
        if (domain.endsWith("/")) domain = domain.substring(0, domain.length() - 1);
        if (domain.contains("//")) domain = domain.substring(domain.indexOf("//") + 2);
        if (domain.equals("")) domain = DEFAULT_DOMAIN;
        this.domain = domain;
        this.domainUrl = (useHttps ? "https://" : "http://") + domain + "/";
    }
    
    /** Установить cookie к текущему клиенту */
    private void setCookie(String domain, String name, String value) {
        if (value == null || value.equals("")) return;
        BasicClientCookieHC4 c = new BasicClientCookieHC4(name, value);
        c.setDomain(domain == null || domain.equals("") ? ("." + this.domain) : domain);
        c.setPath("/");
        httpClient.getCookieStore().addCookie(c);
    }
    
    @Override
    public void saveCookie(Cookie cookie) {
        if (cookie != null) {
            httpClient.getCookieStore().addCookie(cookie);
            if (cookie.getName().equals(CLOUDFLARE_COOKIE_NAME)) {
                preferences.edit().
                        putString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE_DOMAIN), cookie.getDomain()).
                        putString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE_VALUE), cookie.getValue()).commit();
            }
            if (cookie.getName().equals(USERCODE_COOKIE_NAME)) {
                preferences.edit().
                        putString(getSharedKey(PREF_KEY_USERCODE_COOKIE_DOMAIN), cookie.getDomain()).
                        putString(getSharedKey(PREF_KEY_USERCODE_COOKIE_VALUE), cookie.getValue()).commit();
            }
        }
    }
    
    private void saveUsercodeCookie() {
        for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
            if (cookie.getName().equals(USERCODE_COOKIE_NAME) && cookie.getDomain().contains(domain)) saveCookie(cookie);
        }
    }
    
    /** Загрузить из интернета (по адресу url) JSON объект. Учитывается возможность ошибки CloudFlare. */
    private JSONObject downloadJSONObject(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        JSONObject response = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        try {
            response = HttpStreamer.getInstance().getJSONObjectFromUrl(url, rqModel, httpClient, listener, task, true);
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        }
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return response;
    }
    
    /** Загрузить из интернета (по адресу url) JSON массив. Учитывается возможность ошибки CloudFlare. */
    private JSONArray downloadJSONArray(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        JSONArray response = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        try {
            response = HttpStreamer.getInstance().getJSONArrayFromUrl(url, rqModel, httpClient, listener, task, true);
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        }
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return response;
    }
    
    /** выбрасывает CloudflareException, если исключение была вызвано из-за ошибки с Cloudflare */
    private void checkCloudflareError(HttpWrongStatusCodeException e, String url) throws CloudflareException {
        if (e.getStatusCode() == 403) {
            if (e.getHtmlString() != null && e.getHtmlString().contains("CAPTCHA")) {
                throw CloudflareException.withRecaptcha(CLOUDFLARE_RECAPTCHA_KEY,
                        this.domainUrl + CLOUDFLARE_RECAPTCHA_CHECK_URL_FMT, CLOUDFLARE_COOKIE_NAME, getChanName());
            }
        } else if (e.getStatusCode() == 503) {
            if (e.getHtmlString() != null && e.getHtmlString().contains("Just a moment...")) {
                throw CloudflareException.antiDDOS(url, CLOUDFLARE_COOKIE_NAME, getChanName());
            }
        }
    }
    
    private void addMobileAPIPreference(PreferenceGroup group) {
        final Context context = group.getContext();
        CheckBoxPreference mobileAPIPref = new CheckBoxPreference(context);
        mobileAPIPref.setTitle(R.string.makaba_prefs_mobile_api);
        mobileAPIPref.setSummary(R.string.pref_only_new_posts_summary);
        mobileAPIPref.setKey(getSharedKey(PREF_KEY_MOBILE_API));
        mobileAPIPref.setDefaultValue(true);
        group.addPreference(mobileAPIPref);
    }
    
    private void addRecaptchaPreference(PreferenceGroup group) {
        final Context context = group.getContext();
        PreferenceCategory captchaCategory = new PreferenceCategory(context);
        captchaCategory.setTitle(R.string.makaba_prefs_captcha_category);
        group.addPreference(captchaCategory);
        
        ListPreference captchaPreference = new ListPreference(context);
        captchaPreference.setTitle(R.string.makaba_prefs_captcha_type);
        captchaPreference.setDialogTitle(R.string.makaba_prefs_captcha_type);
        captchaPreference.setKey(getSharedKey(PREF_KEY_CAPTCHA_TYPE));
        captchaPreference.setEntryValues(CAPTCHA_TYPES_KEYS);
        captchaPreference.setEntries(CAPTCHA_TYPES);
        captchaPreference.setDefaultValue(CAPTCHA_TYPE_DEFAULT);
        int i = Arrays.asList(CAPTCHA_TYPES_KEYS).indexOf(preferences.getString(getSharedKey(PREF_KEY_CAPTCHA_TYPE), CAPTCHA_TYPE_DEFAULT));
        if (i >= 0) captchaPreference.setSummary(CAPTCHA_TYPES[i]);
        captchaCategory.addPreference(captchaPreference);
        
        CheckBoxPreference googleFallbackPreference = new CheckBoxPreference(context);
        googleFallbackPreference.setTitle(R.string.makaba_prefs_google_fallback);
        googleFallbackPreference.setSummary(R.string.makaba_prefs_google_fallback_summary);
        googleFallbackPreference.setKey(getSharedKey(PREF_KEY_GOOGLE_FALLBACK));
        googleFallbackPreference.setDefaultValue(false);
        captchaCategory.addPreference(googleFallbackPreference);
    }
    
    /** Добавить категорию настроек домена (в т.ч. https) */
    private void addDomainPreferences(PreferenceGroup group) {
        Context context = group.getContext();
        OnPreferenceChangeListener updateDomainListener = new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (preference.getKey().equals(getSharedKey(PREF_KEY_DOMAIN))) {
                    updateDomain((String) newValue, preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS), true));
                    return true;
                } else if (preference.getKey().equals(getSharedKey(PREF_KEY_USE_HTTPS))) {
                    updateDomain(preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN), (boolean)newValue);
                    return true;
                }
                return false;
            }
        };
        PreferenceCategory domainCat = new PreferenceCategory(context);
        domainCat.setTitle(R.string.makaba_prefs_domain_category);
        group.addPreference(domainCat);
        EditTextPreference domainPref = new EditTextPreference(context); //поле ввода домена
        domainPref.setTitle(R.string.makaba_prefs_domain);
        domainPref.setDialogTitle(R.string.makaba_prefs_domain);
        domainPref.setSummary(R.string.makaba_prefs_domain_summary);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        domainPref.setOnPreferenceChangeListener(updateDomainListener);
        domainCat.addPreference(domainPref);
        CheckBoxPreference httpsPref = new CheckBoxPreference(context); //чекбокс "использовать https"
        httpsPref.setTitle(R.string.pref_use_https);
        httpsPref.setSummary(R.string.pref_use_https_summary);
        httpsPref.setKey(getSharedKey(PREF_KEY_USE_HTTPS));
        httpsPref.setDefaultValue(true);
        httpsPref.setOnPreferenceChangeListener(updateDomainListener);
        domainCat.addPreference(httpsPref);
        addUnsafeSslPreference(domainCat, getSharedKey(PREF_KEY_USE_HTTPS)); //чекбокс "игнорировать ошибки SSL"
    }
    
    @Override
    public void addPreferencesOnScreen(final PreferenceGroup preferenceScreen) {
        addMobileAPIPreference(preferenceScreen);
        addRecaptchaPreference(preferenceScreen);
        addDomainPreferences(preferenceScreen);
        addProxyPreferences(preferenceScreen);
        
        final CheckBoxPreference proxyPreference = (CheckBoxPreference) preferenceScreen.findPreference(getSharedKey(PREF_KEY_USE_PROXY));
        final CheckBoxPreference fallbackPref = (CheckBoxPreference) preferenceScreen.findPreference(getSharedKey(PREF_KEY_GOOGLE_FALLBACK));
        fallbackPref.setEnabled(!proxyPreference.isChecked());
        proxyPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {            
            @Override
            public boolean onPreferenceClick(Preference preference) {
                fallbackPref.setEnabled(!proxyPreference.isChecked());
                return false;
            }
        });
    }
    
    private int getUsingCaptchaType() {
        String key = preferences.getString(getSharedKey(PREF_KEY_CAPTCHA_TYPE), CAPTCHA_TYPE_DEFAULT);
        if (Arrays.asList(CAPTCHA_TYPES_KEYS).indexOf(key) == -1) key = CAPTCHA_TYPE_DEFAULT;
        switch (key) {
            case "mailru":
                return CAPTCHA_MAILRU;
            case "recaptcha":
                return CAPTCHA_RECAPTCHA;
            case "recaptchav1":
                return CAPTCHA_RECAPTCHAV1;
        }
        throw new IllegalStateException();
    }
    
    private boolean fallbackGoogle() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_PROXY), false) ||
                preferences.getBoolean(getSharedKey(PREF_KEY_GOOGLE_FALLBACK), false);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        List<SimpleBoardModel> list = new ArrayList<SimpleBoardModel>();
        Map<String, BoardModel> newMap = new HashMap<String, BoardModel>();
        
        String url = domainUrl + "makaba/mobile.fcgi?task=get_boards";
        
        JSONObject mobileBoardsList = downloadJSONObject(url, (oldBoardsList != null && this.boardsMap != null), listener, task);
        if (mobileBoardsList == null) return oldBoardsList;
        
        Iterator<String> it = mobileBoardsList.keys();
        while (it.hasNext()) {
            JSONArray category = mobileBoardsList.getJSONArray(it.next());
            for (int i=0; i<category.length(); ++i) {
                JSONObject currentBoard = category.getJSONObject(i);
                BoardModel model = mapBoardModel(currentBoard, true, resources);
                newMap.put(model.boardName, model);
                list.add(new SimpleBoardModel(model));
            }
        }
        
        this.boardsMap = newMap;
        
        SimpleBoardModel[] result = new SimpleBoardModel[list.size()];
        boolean[] copied = new boolean[list.size()];
        int curIndex = 0;
        for (String category : CATEGORIES) {
            for (int i=0; i<list.size(); ++i) {
                if (list.get(i).boardCategory.equals(category)) {
                    result[curIndex++] = list.get(i);
                    copied[i] = true;
                }
            }
        }
        for (int i=0; i<list.size(); ++i) {
            if (!copied[i]) {
                result[curIndex++] = list.get(i);
            }
        }
        
        return result;
    }

    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        if (this.boardsMap == null) {
            try {
                getBoardsList(listener, task, null);
            } catch (Exception e) {
                Logger.d(TAG, "cannot update boards list from mobile.fcgi");
            }
        }
        if (this.boardsMap != null) {
            if (this.boardsMap.containsKey(shortName)) {
                return this.boardsMap.get(shortName);
            }
        }
        
        if (this.customBoardsMap.containsKey(shortName)) {
            return this.customBoardsMap.get(shortName);
        }
        
        String url = domainUrl + shortName + "/index.json";
        JSONObject json;
        try {
            json = downloadJSONObject(url, false, listener, task);
        } finally {
            HttpStreamer.getInstance().removeFromModifiedMap(url);
        }
        BoardModel result = mapBoardModel(json, false, resources);
        if (!shortName.equals(result.boardName)) throw new Exception();
        this.customBoardsMap.put(result.boardName, result);
        return result;
    }

    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = domainUrl + boardName + "/" + (page == 0 ? "index" : Integer.toString(page)) + ".json";
        JSONObject index = downloadJSONObject(url, (oldList != null), listener, task);
        if (index == null) return oldList;
        
        try { // кэширование модели BoardModel во время загрузки списка тредов
            BoardModel boardModel = mapBoardModel(index, false, resources);
            if (boardName.equals(boardModel.boardName)) {
                this.customBoardsMap.put(boardModel.boardName, boardModel);
            }
        } catch (Exception e) { /* если не получилось сейчас замапить модель доски, и фиг с ней */ }
        
        JSONArray threads = index.getJSONArray("threads");
        ThreadModel[] result = new ThreadModel[threads.length()];
        for (int i=0; i<threads.length(); ++i) {
            result[i] = mapThreadModel(threads.getJSONObject(i), boardName);
        }
        return result;
    }

    @Override
    public ThreadModel[] getCatalog(String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        Exception last = null;
        for (String url : new String[] {
                domainUrl + "makaba/makaba.fcgi?task=catalog&board=" + boardName + "&filter=" + CATALOG_TYPES[catalogType] + "&json=1",
                domainUrl + boardName + "/catalog.json"
        }) {
            try {
                JSONObject json = downloadJSONObject(url, (oldList != null), listener, task);
                if (json == null) return oldList;
                JSONArray threads = json.getJSONArray("threads");
                ThreadModel[] result = new ThreadModel[threads.length()];
                for (int i=0; i<threads.length(); ++i) {
                    JSONObject curThread = threads.getJSONObject(i);
                    ThreadModel model = new ThreadModel();
                    model.threadNumber = curThread.getString("num");
                    try {
                        model.postsCount = curThread.getInt("posts_count") + 1;
                        model.attachmentsCount = curThread.getInt("files_count");
                        model.attachmentsCount += curThread.getJSONArray("files").length();
                        model.isSticky = curThread.getInt("sticky") != 0;
                        model.isClosed = curThread.getInt("closed") != 0;
                    } catch (Exception e) {
                        Logger.e(TAG, e);
                    } 
                    model.posts = new PostModel[] { mapPostModel(curThread, boardName) };
                    result[i] = model;
                }
                return result;
            } catch (Exception e) {
                last = e;
            }
        }
        throw last == null ? new Exception() : last;
    }

    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        boolean mobileAPI = preferences.getBoolean(getSharedKey(PREF_KEY_MOBILE_API), true);
        if (!mobileAPI) {
            String url = domainUrl + boardName + "/res/" + threadNumber + ".json";
            JSONObject object = downloadJSONObject(url, (oldList != null), listener, task);
            if (object == null) return oldList;
            JSONArray postsArray = object.getJSONArray("threads").getJSONObject(0).getJSONArray("posts");
            PostModel[] posts = new PostModel[postsArray.length()];
            for (int i=0; i<postsArray.length(); ++i) {
                posts[i] = mapPostModel(postsArray.getJSONObject(i), boardName);
            }
            if (oldList != null) {
                posts = ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(posts));
            }
            return posts;
        }
        try {
            String lastPost = threadNumber;
            if (oldList != null && oldList.length > 0) {
                lastPost = oldList[oldList.length-1].number;
            }
            String url = domainUrl + "makaba/mobile.fcgi?task=get_thread&board=" + boardName + "&thread=" + threadNumber + "&num=" + lastPost;
            JSONArray newPostsArray = downloadJSONArray(url, (oldList != null), listener, task);
            if (newPostsArray == null) return oldList;
            PostModel[] newPosts = new PostModel[newPostsArray.length()];
            for (int i=0; i<newPostsArray.length(); ++i) {
                newPosts[i] = mapPostModel(newPostsArray.getJSONObject(i), boardName);
            }
            if (oldList == null || oldList.length == 0) {
                return newPosts;
            } else {
                long lastNum = Long.parseLong(lastPost);
                ArrayList<PostModel> list = new ArrayList<PostModel>(Arrays.asList(oldList));
                for (int i=0; i<newPosts.length; ++i) {
                    if (Long.parseLong(newPosts[i].number) > lastNum) {
                        list.add(newPosts[i]);
                    }
                }
                return list.toArray(new PostModel[list.size()]);
            }
        } catch (JSONException e) {
            String lastPost = threadNumber;
            if (oldList != null && oldList.length > 0) {
                lastPost = oldList[oldList.length-1].number;
            }
            String url = domainUrl + "makaba/mobile.fcgi?task=get_thread&board=" + boardName + "&thread=" + threadNumber + "&num=" + lastPost;
            JSONObject makabaError = downloadJSONObject(url, (oldList != null), listener, task);
            Integer code = makabaError.has("Code") ? makabaError.getInt("Code") : null;
            if (code != null && code.equals(Integer.valueOf(-404))) code = 404;
            String error = code != null ? code.toString() : null;
            String reason = makabaError.has("Error") ? makabaError.getString("Error") : null;
            if (reason != null) {
                if (error != null) {
                    error += ": " + reason;
                } else {
                    error = reason;
                }
            }
            throw error == null ? e : new Exception(error);
        }
    }

    @Override
    public PostModel[] search(String boardName, String searchRequest, ProgressListener listener, CancellableTask task) throws Exception {
        String url;
        HttpRequestModel request;
        if (searchRequest.startsWith(HASHTAG_PREFIX)) {
            url = domainUrl + "makaba/makaba.fcgi?task=hashtags&board=" + boardName + "&tag=" +
                    URLEncoder.encode(searchRequest.substring(HASHTAG_PREFIX.length()), "UTF-8") + "&json=1";
            request = HttpRequestModel.builder().setGET().build();
        } else {
            url = domainUrl + "makaba/makaba.fcgi";
            HttpEntity postEntity = ExtendedMultipartBuilder.create().
                    addString("task", "search").
                    addString("board", boardName).
                    addString("find", searchRequest).
                    addString("json", "1").
                    build();
            request = HttpRequestModel.builder().setPOST(postEntity).build();
        }
        JSONObject response = null;
        try {
            response = HttpStreamer.getInstance().getJSONObjectFromUrl(url, request, httpClient, listener, task, true);
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        }
        if (listener != null) listener.setIndeterminate();
        JSONArray posts = response.getJSONArray("posts");
        PostModel[] result = new PostModel[posts.length()];
        for (int i=0; i<posts.length(); ++i) {
            result[i] = mapPostModel(posts.getJSONObject(i), boardName);
        }
        return result;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        switch (getUsingCaptchaType()) {
            case CAPTCHA_MAILRU:
                UrlPageModel refererPage = new UrlPageModel();
                refererPage.chanName = CHAN_NAME;
                refererPage.boardName = boardName;
                if (threadNumber == null) {
                    refererPage.type = UrlPageModel.TYPE_BOARDPAGE;
                    refererPage.boardPage = 0;
                } else {
                    refererPage.type = UrlPageModel.TYPE_THREADPAGE;
                    refererPage.threadNumber = threadNumber;
                }
                String refererUrl = buildUrl(refererPage);
                Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, refererUrl) };
                
                captchaType = CAPTCHA_MAILRU;
                String jsUrl = MAILRU_JS_URL;
                Bitmap captchaBitmap = null;
                HttpRequestModel requestModel = HttpRequestModel.builder().setGET().setCustomHeaders(customHeaders).build();
                String jsResponse = HttpStreamer.getInstance().getStringFromUrl(jsUrl, requestModel, httpClient, listener, task, true);
                Matcher mailRuIdMatcher = MAILRU_ID_PATTERN.matcher(jsResponse);
                if (!mailRuIdMatcher.find()) throw new Exception("Couldn't get Mail.Ru captcha ID");
                captchaMailRuId = mailRuIdMatcher.group(1);
                
                Matcher mailRuUrlMatcher = MAILRU_URL_PATTERN.matcher(jsResponse);
                String captchaUrl = mailRuUrlMatcher.find() ? mailRuUrlMatcher.group(1) : MAILRU_DEFAULT_CAPTCHA_URL;
                HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(captchaUrl, requestModel, httpClient, listener, task);
                try {
                    InputStream imageStream = responseModel.stream;
                    captchaBitmap = BitmapFactory.decodeStream(imageStream);
                } finally {
                    responseModel.release();
                }
                CaptchaModel captchaModel = new CaptchaModel();
                captchaModel.type = CaptchaModel.TYPE_NORMAL;
                captchaModel.bitmap = captchaBitmap;
                return captchaModel;
            case CAPTCHA_RECAPTCHA:
                captchaType = CAPTCHA_RECAPTCHA;
                return null;
            case CAPTCHA_RECAPTCHAV1:
                captchaType = CAPTCHA_RECAPTCHAV1;
                recaptchaV1 = Recaptcha.obtain(RECAPTCHA_V1_KEY, task, httpClient, "http");
                captchaModel = new CaptchaModel();
                captchaModel.type = CaptchaModel.TYPE_NORMAL;
                captchaModel.bitmap = recaptchaV1.bitmap;
                return captchaModel;
            default:
                throw new Exception("failed to get captcha");
        }
    }

    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = domainUrl + "makaba/posting.fcgi?json=1";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("task", "post").
                addString("board", model.boardName).
                addString("thread", model.threadNumber == null ? "0" : model.threadNumber);
        
        postEntityBuilder.addString("comment", model.comment);
        
        if (model.captchaAnswer != null) {
            if (captchaType == CAPTCHA_YANDEX && captchaKey != null) {
                postEntityBuilder.addString("captcha", captchaKey);
                postEntityBuilder.addString("captcha_value", model.captchaAnswer);
            } else if (captchaType == CAPTCHA_RECAPTCHA) {
                String key = Recaptcha2solved.pop(RECAPTCHA_KEY);
                if (key == null) throw Recaptcha2.obtain(RECAPTCHA_KEY, CHAN_NAME, fallbackGoogle());
                postEntityBuilder.addString("captcha_type", "recaptcha");
                postEntityBuilder.addString("g-recaptcha-response", key);
            } else if (captchaType == CAPTCHA_MAILRU && captchaMailRuId != null) {
                postEntityBuilder.addString("captcha_type", "mailru");
                postEntityBuilder.addString("captcha_id", captchaMailRuId);
                postEntityBuilder.addString("captcha_value", model.captchaAnswer);
            } else if (captchaType == CAPTCHA_RECAPTCHAV1 && recaptchaV1 != null) {
                postEntityBuilder.addString("captcha_type", "recaptchav1");
                postEntityBuilder.addString("recaptcha_challenge_field", recaptchaV1.challenge);
                postEntityBuilder.addString("recaptcha_response_field", model.captchaAnswer);
            }
        }
        if (task != null && task.isCancelled()) throw new InterruptedException("interrupted");
        
        if (model.subject != null) postEntityBuilder.addString("subject", model.subject);
        if (model.name != null) postEntityBuilder.addString("name", model.name);
        if (model.sage) postEntityBuilder.addString("email", "sage");
        else if (model.email != null) postEntityBuilder.addString("email", model.email);
        
        if (model.attachments != null) {
            String[] images = new String[] { "image1", "image2", "image3", "image4" };
            for (int i=0; i<model.attachments.length; ++i) {
                postEntityBuilder.addFile(images[i], model.attachments[i], model.randomHash);
            }
        }
        
        if (model.icon != -1) postEntityBuilder.addString("icon", Integer.toString(model.icon));
        
        //if (model.watermark) postEntityBuilder.addString("water_mark", "on");
        if (model.custommark) postEntityBuilder.addString("op_mark", "1");
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).build();
        String response = null;
        try {
            response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, null, task, true);
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        }
        saveUsercodeCookie();
        JSONObject makabaResult = new JSONObject(response);
        try {
            String statusResult = makabaResult.getString("Status");
            if (statusResult.equals("OK")) return null;
            if (statusResult.equals("Redirect")) {
                UrlPageModel redirect = new UrlPageModel();
                redirect.type = UrlPageModel.TYPE_THREADPAGE;
                redirect.chanName = CHAN_NAME;
                redirect.boardName = model.boardName;
                redirect.threadNumber = Long.toString(makabaResult.getLong("Target"));
                return buildUrl(redirect);
            }
        } catch (Exception e) {}
        throw new Exception(makabaResult.getString("Reason"));
    }
    
    @Override
    public String reportPost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = domainUrl + "makaba/makaba.fcgi?json=1";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("task", "report").
                addString("posts", "").
                addString("board", model.boardName).
                addString("thread", model.threadNumber).
                addString("comment", ">>" + model.postNumber + " " + model.reportReason);
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        String response = null;
        try {
            response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, null, task, true);
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        }
        try {
            JSONObject json = new JSONObject(response);
            if (json.getString("message_title").equals("Ошибок нет")) return null;
            throw new Exception(json.getString("message_title") + " " + json.getString("message"));
        } catch (Exception e) {
            Logger.e(TAG, e);
            throw new Exception(response);
        }
    }
    
    @Override
    public void downloadFile(String url, OutputStream out, ProgressListener listener, CancellableTask task) throws Exception {
        String fixedUrl = fixRelativeUrl(url);
        try {
            HttpRequestModel rqModel = HttpRequestModel.builder().setGET().build();
            HttpStreamer.getInstance().downloadFileFromUrl(fixedUrl, out, rqModel, httpClient, listener, task, true);
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, fixedUrl);
            throw e;
        }
    }

    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(CHAN_NAME)) throw new IllegalArgumentException("wrong chan");
        if (model.boardName != null && !model.boardName.matches("\\w*")) throw new IllegalArgumentException("wrong board name");
        StringBuilder url = new StringBuilder(this.domainUrl);
        switch (model.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                break;
            case UrlPageModel.TYPE_BOARDPAGE:
                url.append(model.boardName).append("/");
                if (model.boardPage == UrlPageModel.DEFAULT_FIRST_PAGE) model.boardPage = 0;
                if (model.boardPage != 0) url.append(model.boardPage).append(".html");
                break;
            case UrlPageModel.TYPE_CATALOGPAGE:
                url.append("makaba.fcgi?task=catalog&board=").append(model.boardName).append("&filter=").append(CATALOG_TYPES[model.catalogType]);
                break;
            case UrlPageModel.TYPE_SEARCHPAGE:
                if (model.searchRequest.startsWith(HASHTAG_PREFIX)) {
                    url.append("makaba/makaba.fcgi?task=hashtags&board=").append(model.boardName).append("&tag=");
                    try {
                        url.append(URLEncoder.encode(model.searchRequest.substring(HASHTAG_PREFIX.length()), "UTF-8"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    break;
                }
                throw new IllegalArgumentException("can't build url for search page");
            case UrlPageModel.TYPE_THREADPAGE:
                url.append(model.boardName).append("/res/").append(model.threadNumber).append(".html");
                if (model.postNumber != null && model.postNumber.length() != 0) url.append("#").append(model.postNumber);
                break;
            case UrlPageModel.TYPE_OTHERPAGE:
                url.append(model.otherPath.startsWith("/") ? model.otherPath.substring(1) : model.otherPath);
        }
        return url.toString();
    }

    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String domain;
        String path = "";
        Matcher parseUrl = Pattern.compile("https?://(?:www\\.)?(.+)").matcher(url);
        if (!parseUrl.find()) throw new IllegalArgumentException("incorrect url");
        Matcher parsePath = Pattern.compile("(.+?)(?:/(.*))").matcher(parseUrl.group(1));
        if (parsePath.find()) {
            domain = parsePath.group(1).toLowerCase(Locale.US);
            path = parsePath.group(2);
        } else {
            domain = parseUrl.group(1).toLowerCase(Locale.US);
        }
        
        //проверка домена
        if ((!this.domain.equals(domain)) && (DOMAINS_LIST.indexOf(domain) == -1)) {
            throw new IllegalArgumentException("wrong domain");
        }
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = CHAN_NAME;
        try {
            if (path.startsWith("makaba/makaba.fcgi?") && path.contains("task=catalog") && path.contains("board=")) {
                model.type = UrlPageModel.TYPE_CATALOGPAGE;
                
                String boardName = path.substring(path.indexOf("board=") + 6);
                if (boardName.indexOf("&") != -1) boardName = boardName.substring(0, boardName.indexOf("&"));
                model.boardName = boardName;
                
                if (path.indexOf("filter=") == -1) {
                    model.catalogType = 0;
                } else {
                    String catalogType = path.substring(path.indexOf("filter=") + 7);
                    if (catalogType.indexOf("&") != -1) catalogType = catalogType.substring(0, catalogType.indexOf("&"));
                    
                    model.catalogType = Arrays.asList(CATALOG_TYPES).indexOf(catalogType);
                    model.catalogType = model.catalogType == -1 ? 0 : model.catalogType;
                }
            } else if (path.startsWith("makaba/makaba.fcgi?") && path.contains("task=hashtags") && path.contains("board=")) {
                model.type = UrlPageModel.TYPE_SEARCHPAGE;
                String boardName = path.substring(path.indexOf("board=") + 6);
                if (boardName.indexOf("&") != -1) boardName = boardName.substring(0, boardName.indexOf("&"));
                model.boardName = boardName;
                if (path.indexOf("tag") == -1) throw new IllegalStateException("cannot parse hashtag");
                String hashtag = path.substring(path.indexOf("tag=") + 4);
                if (hashtag.indexOf("&") != -1) hashtag = hashtag.substring(0, hashtag.indexOf("&"));
                model.searchRequest = HASHTAG_PREFIX + URLDecoder.decode(hashtag, "UTF-8");
            } else if (path.contains("/res/")) {
                model.type = UrlPageModel.TYPE_THREADPAGE;
                Matcher matcher = Pattern.compile("(.+?)/res/([0-9]+?).html(.*)").matcher(path);
                if (!matcher.find()) throw new Exception();
                model.boardName = matcher.group(1);
                model.threadNumber = matcher.group(2);
                if (matcher.group(3).startsWith("#")) {
                    String post = matcher.group(3).substring(1);
                    if (!post.equals("")) model.postNumber = post;
                }
            } else {
                model.type = UrlPageModel.TYPE_BOARDPAGE;
                
                if (path.indexOf("/") == -1) {
                    if (path.equals("")) throw new Exception();
                    model.boardName = path;
                    model.boardPage = 0;
                } else {
                    model.boardName = path.substring(0, path.indexOf("/"));
                    
                    String page = path.substring(path.lastIndexOf("/") + 1);
                    if (!page.equals("")) {
                        String pageNum = page.substring(0, page.indexOf(".html"));
                        model.boardPage = pageNum.equals("index") ? 0 : Integer.parseInt(pageNum);
                    } else model.boardPage = 0;
                }
            }
        } catch (Exception e) {
            if (path == null || path.length() == 0 || path.equals("/")) {
                model.type = UrlPageModel.TYPE_INDEXPAGE;
            } else {
                model.type = UrlPageModel.TYPE_OTHERPAGE;
                model.otherPath = path;
            }
        }
        return model;
    }
}
