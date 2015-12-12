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

package nya.miku.wishmaster.chans.allchan;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.WakabaUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.cloudflare.CloudflareException;
import nya.miku.wishmaster.http.recaptcha.Recaptcha;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2fallback;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2js;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2solved;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class AllchanModule extends AbstractChanModule {
    private static final String TAG = "AllchanModule";
    private static final String CHAN_NAME = "allchan.su";
    private static final String DOMAIN = "allchan.su";
    
    private static final String[] CAPTCHA_TYPES = new String[] {
            "Yandex (latin)", "Yandex (digits)", "Yandex (rus)", "Google Recaptcha 2", "Google Recaptcha 2 (fallback)", "Google Recaptcha" };
    private static final String[] CAPTCHA_TYPES_KEYS = new String[] {
            "yandex-elatm", "yandex-estd", "yandex-rus", "recaptcha", "recaptcha-fallback", "recaptchav1" };
    private static final String CAPTCHA_TYPE_DEFAULT = "yandex-estd";
    
    private static final int CAPTCHA_YANDEX_ELATM = 1;
    private static final int CAPTCHA_YANDEX_ESTD = 2;
    private static final int CAPTCHA_YANDEX_RUS = 3;
    private static final int CAPTCHA_RECAPTCHA = 4;
    private static final int CAPTCHA_RECAPTCHA_FALLBACK = 5;
    private static final int CAPTCHA_RECAPTCHA_V1 = 6;
    
    private static final String RECAPTCHA_PUBLIC_KEY = "6LfKRgcTAAAAAIe-bmV_pCbMzvKvBZGbZNRsfmED";
    
    private static final String PREF_KEY_ONLY_NEW_POSTS = "PREF_KEY_ONLY_NEW_POSTS";
    private static final String PREF_KEY_CAPTCHA_TYPE = "PREF_KEY_CAPTCHA_TYPE";
    private static final String PREF_KEY_USE_HTTPS = "PREF_KEY_USE_HTTPS";
    private static final String PREF_KEY_CLOUDFLARE_COOKIE = "PREF_KEY_CLOUDFLARE_COOKIE";
    
    private static final String CLOUDFLARE_COOKIE_NAME = "cf_clearance";
    private static final String CLOUDFLARE_RECAPTCHA_KEY = "6LeT6gcAAAAAAAZ_yDmTMqPH57dJQZdQcu6VFqog"; 
    private static final String CLOUDFLARE_RECAPTCHA_CHECK_URL_FMT = "cdn-cgi/l/chk_captcha?recaptcha_challenge_field=%s&recaptcha_response_field=%s";
    
    public static final String[] CATALOG_TYPES = new String[] { "date", "recent", "bumps" };
    public static final String[] CATALOG_DESCRIPTIONS = new String[] {
            "Сортировать по дате создания", "Сортировать по дате последнего поста", "Сортировать по количеству бампов" };
    
    private static final List<String> SFW_BOARDS = Arrays.asList("a", "cg", "d", "echo", "int", "mlp", "po", "pr", "rf", "rpg", "soc", "vg");
    private static final String[] RATINGS = new String[] { "SFW", "R-15", "R-18", "R-18G" };
    
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    static { DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT")); }
    
    private static final Pattern COMMENT_QUOTE = Pattern.compile("<span class=\"quotation\">", Pattern.LITERAL);
    
    private HashMap<String, BoardModel> boardsMap;
    
    private int captchaType;
    private String yandexCaptchaKey;
    private Recaptcha recaptchaV1;
    
    private String[] lastDeleted; // { boardName, threadNumber }
    
    public AllchanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "AllChan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_allchan, null);
    }
    
    private boolean useHttps() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS), true);
    }
    
    private String getUsingUrl() {
        return (useHttps() ? "https://" : "http://") + DOMAIN + "/";
    }
    
    @Override
    protected void initHttpClient() {
        String cloudflareCookie = preferences.getString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE), null);
        if (cloudflareCookie != null) {
            BasicClientCookie c = new BasicClientCookie(CLOUDFLARE_COOKIE_NAME, cloudflareCookie);
            c.setDomain(DOMAIN);
            httpClient.getCookieStore().addCookie(c);
        }
    }
    
    @Override
    public void saveCookie(Cookie cookie) {
        if (cookie != null) {
            httpClient.getCookieStore().addCookie(cookie);
            if (cookie.getName().equals(CLOUDFLARE_COOKIE_NAME)) {
                preferences.edit().putString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE), cookie.getValue()).commit();
            }
        }
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        CheckBoxPreference onlyNewPostsPreference = new CheckBoxPreference(context); //only_new_posts
        onlyNewPostsPreference.setTitle(R.string.pref_only_new_posts);
        onlyNewPostsPreference.setSummary(R.string.pref_only_new_posts_summary);
        onlyNewPostsPreference.setKey(getSharedKey(PREF_KEY_ONLY_NEW_POSTS));
        onlyNewPostsPreference.setDefaultValue(true);
        preferenceGroup.addPreference(onlyNewPostsPreference);
        final ListPreference captchaPreference = new ListPreference(context); //captcha_type
        captchaPreference.setTitle(R.string.pref_captcha_type);
        captchaPreference.setDialogTitle(R.string.pref_captcha_type);
        captchaPreference.setKey(getSharedKey(PREF_KEY_CAPTCHA_TYPE));
        captchaPreference.setEntryValues(CAPTCHA_TYPES_KEYS);
        captchaPreference.setEntries(CAPTCHA_TYPES);
        captchaPreference.setDefaultValue(CAPTCHA_TYPE_DEFAULT);
        int i = Arrays.asList(CAPTCHA_TYPES_KEYS).indexOf(preferences.getString(getSharedKey(PREF_KEY_CAPTCHA_TYPE), CAPTCHA_TYPE_DEFAULT));
        if (i >= 0) captchaPreference.setSummary(CAPTCHA_TYPES[i]);
        preferenceGroup.addPreference(captchaPreference);
        CheckBoxPreference httpsPref = new CheckBoxPreference(context); //https
        httpsPref.setTitle(R.string.pref_use_https);
        httpsPref.setSummary(R.string.pref_use_https_summary);
        httpsPref.setKey(getSharedKey(PREF_KEY_USE_HTTPS));
        httpsPref.setDefaultValue(true);
        preferenceGroup.addPreference(httpsPref);
        addUnsafeSslPreference(preferenceGroup, getSharedKey(PREF_KEY_USE_HTTPS));
        addProxyPreferences(preferenceGroup);
        
        final CheckBoxPreference proxyPreference = (CheckBoxPreference) preferenceGroup.findPreference(getSharedKey(PREF_KEY_USE_PROXY));
        proxyPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {            
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (proxyPreference.isChecked() && captchaPreference.getValue().equals("recaptcha")) {
                    captchaPreference.setValue("recaptcha-fallback");
                }
                return false;
            }
        });
        captchaPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (proxyPreference.isChecked() && newValue.equals("recaptcha")) {
                    captchaPreference.setValue("recaptcha-fallback");
                    return false;
                }
                return true;
            }
        });
    }
    
    
    private boolean loadOnlyNewPosts() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_ONLY_NEW_POSTS), true);
    }
    
    private int getUsingCaptchaType() {
        String key = preferences.getString(getSharedKey(PREF_KEY_CAPTCHA_TYPE), CAPTCHA_TYPE_DEFAULT);
        if (Arrays.asList(CAPTCHA_TYPES_KEYS).indexOf(key) == -1) key = CAPTCHA_TYPE_DEFAULT;
        switch (key) {
            case "yandex-elatm":
                return CAPTCHA_YANDEX_ELATM;
            case "yandex-estd":
                return CAPTCHA_YANDEX_ESTD;
            case "yandex-rus":
                return CAPTCHA_YANDEX_RUS;
            case "recaptcha":
                return preferences.getBoolean(getSharedKey(PREF_KEY_USE_PROXY), false) ?
                        CAPTCHA_RECAPTCHA_FALLBACK :
                            CAPTCHA_RECAPTCHA;
            case "recaptcha-fallback":
                return CAPTCHA_RECAPTCHA_FALLBACK;
            case "recaptchav1":
                return CAPTCHA_RECAPTCHA_V1;
        }
        throw new IllegalStateException();
    }
    
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
    
    private void checkCloudflareError(HttpWrongStatusCodeException e, String url) throws CloudflareException {
        if (e.getStatusCode() == 403) {
            if (e.getHtmlString() != null && e.getHtmlString().contains("CAPTCHA")) {
                throw CloudflareException.withRecaptcha(CLOUDFLARE_RECAPTCHA_KEY,
                        getUsingUrl() + CLOUDFLARE_RECAPTCHA_CHECK_URL_FMT, CLOUDFLARE_COOKIE_NAME, getChanName());
            }
        } else if (e.getStatusCode() == 503) {
            if (e.getHtmlString() != null && e.getHtmlString().contains("Just a moment...")) {
                throw CloudflareException.antiDDOS(url, CLOUDFLARE_COOKIE_NAME, getChanName());
            }
        }
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        String url = getUsingUrl() + "misc/boards.json";
        JSONObject json = downloadJSONObject(url, oldBoardsList != null, listener, task);
        if (json == null) return oldBoardsList;
        try {
            JSONArray boards = json.getJSONArray("boards");
            SimpleBoardModel[] list = new SimpleBoardModel[boards.length()];
            for (int i=0; i<boards.length(); ++i) {
                BoardModel board = mapBoardModel(boards.getJSONObject(i));
                list[i] = new SimpleBoardModel(board);
            }
            return list;
        } catch (JSONException e) {
            throw new Exception(json.getString("errorDescription"));
        }
    }
    
    private void addToMap(BoardModel model) {
        if (boardsMap == null) boardsMap = new HashMap<>();
        BoardModel previous = boardsMap.put(model.boardName, model);
        if (model.lastPage == BoardModel.LAST_PAGE_UNDEFINED && previous != null) model.lastPage = previous.lastPage;
    }
    
    private BoardModel mapBoardModel(JSONObject json) {
        BoardModel model = new BoardModel();
        model.chan = CHAN_NAME;
        model.boardName = json.getString("name");
        model.boardDescription = json.optString("title", model.boardName);
        model.nsfw = SFW_BOARDS.indexOf(model.boardName) == -1;
        model.uniqueAttachmentNames = true;
        model.timeZoneId = "GMT+3";
        model.defaultUserName = json.optString("defaultUserName", "Аноним");
        model.bumpLimit = json.optInt("bumpLimit", 500);
        model.readonlyBoard = !json.optBoolean("postingEnabled", true);
        int attachmentsCount = json.optInt("maxFileCount", 1);
        model.requiredFileForNewThread = attachmentsCount > 0;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = true;
        model.allowReport = BoardModel.REPORT_NOT_ALLOWED;
        model.allowNames = true;
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = true;
        model.allowCustomMark = true;
        model.allowRandomHash = true;
        model.allowIcons = true;
        model.iconDescriptions = RATINGS;
        model.attachmentsMaxCount = attachmentsCount;
        model.attachmentsFormatFilters = null;
        model.markType = BoardModel.MARK_BBCODE;
        model.firstPage = 0;
        model.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        model.searchAllowed = false;
        model.catalogAllowed = true;
        model.catalogTypeDescriptions = CATALOG_DESCRIPTIONS;
        addToMap(model);
        return model;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        if (boardsMap != null && boardsMap.containsKey(shortName)) return boardsMap.get(shortName);
        String url = getUsingUrl() + "misc/board/b.json";
        JSONObject json = downloadJSONObject(url, false, listener, task);
        try {
            BoardModel board = mapBoardModel(json.getJSONObject("board"));
            addToMap(board);
            return board;
        } catch (JSONException e) {
            throw new Exception(json.getString("errorDescription"));
        }
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = getUsingUrl() + boardName + "/" + Integer.toString(page) + ".json";
        JSONObject json = downloadJSONObject(url, oldList != null, listener, task);
        if (json == null) return oldList;
        try {
            try {
                BoardModel board = mapBoardModel(json.optJSONObject("board"));
                addToMap(board);
                board.lastPage = Math.max(json.getInt("pageCount") - 1, 0);
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
            JSONArray threads = json.getJSONArray("threads");
            ThreadModel[] list = new ThreadModel[threads.length()];
            for (int i=0; i<threads.length(); ++i) {
                list[i] = mapThreadModel(threads.getJSONObject(i));
            }
            return list;
        } catch (JSONException e) {
            Logger.e(TAG, e);
            throw new Exception(json.getString("errorDescription"));
        }
    }
    
    @Override
    public ThreadModel[] getCatalog(String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = getUsingUrl() + boardName + "/catalog.json";
        if (catalogType > 0) url += "?sort=" + CATALOG_TYPES[catalogType];
        JSONObject json = downloadJSONObject(url, oldList != null, listener, task);
        if (json == null) return oldList;
        try {
            try {
                BoardModel board = mapBoardModel(json.getJSONObject("board"));
                addToMap(board);
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
            JSONArray threads = json.getJSONArray("threads");
            ThreadModel[] list = new ThreadModel[threads.length()];
            for (int i=0; i<threads.length(); ++i) {
                list[i] = mapThreadModel(threads.getJSONObject(i));
            }
            return list;
        } catch (JSONException e) {
            throw new Exception(json.getString("errorDescription"));
        }
    }
    
    private ThreadModel mapThreadModel(JSONObject json) {
        ThreadModel model = new ThreadModel();
        model.postsCount = json.optInt("postCount", -1);
        model.attachmentsCount = -1;
        model.isSticky = json.optBoolean("fixed");
        model.isClosed = json.optBoolean("closed");
        JSONArray postsJson = json.optJSONArray("lastPosts");
        PostModel[] posts = new PostModel[postsJson == null ? 1 : 1 + postsJson.length()];
        posts[0] = mapPostModel(json.getJSONObject("opPost"), null);
        for (int i=1; i<posts.length; ++i) {
            posts[i] = mapPostModel(postsJson.getJSONObject(i-1), posts[0].number);
        }
        model.posts = posts;
        model.threadNumber = posts[0].number;
        return model;
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        boolean reloadDeleted = lastDeleted != null && boardName.equals(lastDeleted[0]) && threadNumber.equals(lastDeleted[1]);
        if (reloadDeleted) oldList = null;

        String url = getUsingUrl() + "api/threadLastPostNumber.json?"
            + "boardName=" + boardName + "&threadNumber=" + threadNumber;
        JSONObject json = downloadJSONObject(url, oldList != null, listener, task);
        if (null == json) return oldList;
        int newLastPostNumber = json.getInt("lastPostNumber");
        if (0 == newLastPostNumber) return oldList; //Thread does not exist (maybe it was deleted)

        if (loadOnlyNewPosts()) {
            int lastPostNumber = (null != oldList) ? oldList[oldList.length].number : 0;
            if (lastPostNumber <= newLastPostNumber) return oldList; //Nothing new here
        }

        //Yep, downloading entire thread. This is for the sake of caching
        url = getUsingUrl() + boardName + "/res/" + threadNumber + ".json";
        json = downloadJSONObject(url, oldList != null, listener, task);
        if (json == null) return oldList;
        try {
            PostModel[] newList = mapThreadModel(json.getJSONObject("thread")).posts;
            if (reloadDeleted) lastDeleted = null;
            return newList;
        } catch (JSONException e) {
            throw new Exception(json.getString("errorDescription"));
        }
    }
    
    private PostModel mapPostModel(JSONObject json, String parentThread) {
        PostModel model = new PostModel();
        model.number = json.optString("number", null);
        if (model.number == null) throw new RuntimeException();
        String name = json.optString("name");
        int index = name.indexOf("<font color=\"");
        if (index >= 0) {
            String color = name.substring(index + 13);
            int endIndex = color.indexOf('"');
            if (endIndex >= 0) {
                try {
                    color = color.substring(0, endIndex);
                    model.color = Color.parseColor(color);
                } catch (Exception e) {
                    Logger.e(TAG, "couldn't parse color: " + color, e);
                }
            }
        }
        model.name = RegexUtils.removeHtmlTags(name);
        String subject = json.optString("subject");
        if (!subject.startsWith("<a href")) {
            model.subject = RegexUtils.removeHtmlTags(subject);
        } else {
            model.comment = subject + "<br/>";
            model.subject = "";
        }
        String text = RegexUtils.replaceAll(json.optString("text"), COMMENT_QUOTE, "<span class=\"unkfunc\">");
        model.comment = model.comment != null ? (model.comment + text) : text;
        model.email = json.optString("email");
        model.trip = json.optString("tripCode");
        model.icons = null;
        model.op = json.optBoolean("isOp");
        model.sage = model.email.toLowerCase(Locale.US).contains("sage");
        try {
            model.timestamp = DATE_FORMAT.parse(json.optString("createdAt")).getTime();
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        model.parentThread = json.optString("threadNumber", parentThread != null ? parentThread : model.number);
        JSONArray fileInfos = json.optJSONArray("fileInfos");
        if (fileInfos != null) {
            AttachmentModel[] attachments = new AttachmentModel[fileInfos.length()];
            for (int i=0; i<fileInfos.length(); ++i) {
                AttachmentModel attachment = new AttachmentModel();
                JSONObject attachmentJson = fileInfos.getJSONObject(i);
                String boardName = attachmentJson.optString("boardName");
                attachment.path = boardName + "/src/" + attachmentJson.optString("name");
                int size = attachmentJson.optInt("size", -1);
                if (size > 0) size /= 1024;
                attachment.size = size;
                JSONObject dimensions = attachmentJson.optJSONObject("dimensions");
                if (dimensions != null) {
                    int width = dimensions.optInt("width", -1);
                    int height = dimensions.optInt("height", -1);
                    if (Math.min(width, height) > 0) {
                        attachment.width = width;
                        attachment.height = height;
                    } else {
                        attachment.width = attachment.height = -1;
                    }
                } else {
                    attachment.width = attachment.height = -1;
                }
                JSONObject thumbJson = attachmentJson.optJSONObject("thumb");
                if (thumbJson != null) {
                    String thumbnail = thumbJson.optString("name", null);
                    if (thumbnail != null) thumbnail = boardName + "/thumb/" + thumbnail;
                    attachment.thumbnail = thumbnail;
                }
                String mimeType = attachmentJson.optString("mimeType");
                if (mimeType.equals("image/gif")) {
                    attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
                } else if (mimeType.startsWith("image/")) {
                    attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                } else if (mimeType.startsWith("video/")) {
                    attachment.type = AttachmentModel.TYPE_VIDEO;
                } else if (mimeType.startsWith("audio/")) {
                    attachment.type = AttachmentModel.TYPE_AUDIO;
                } else {
                    attachment.type = AttachmentModel.TYPE_OTHER_FILE;
                }
                attachment.isSpoiler = attachmentJson.optString("rating").toLowerCase(Locale.US).startsWith("r-18");
                attachments[i] = attachment;
            }
            model.attachments = attachments;
        }
        return model;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String quotaUrl = getUsingUrl() + "api/captchaQuota.json?boardName=" + boardName;
        try {
            int quota = downloadJSONObject(quotaUrl, false, listener, task).getInt("quota");
            if (quota > 0) return null;
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, quotaUrl);
        }
        
        CaptchaModel captchaModel;
        int captchaType = getUsingCaptchaType();
        switch (captchaType) {
            case CAPTCHA_YANDEX_ELATM:
            case CAPTCHA_YANDEX_ESTD:
            case CAPTCHA_YANDEX_RUS:
                String url = getUsingUrl() + "api/yandexCaptchaImage.json?type=";
                switch (captchaType) {
                    case CAPTCHA_YANDEX_ELATM: url += "elatm"; break;
                    case CAPTCHA_YANDEX_ESTD: url += "estd"; break;
                    case CAPTCHA_YANDEX_RUS: url += "rus"; break;
                }
                JSONObject json;
                try {
                    json = downloadJSONObject(url, false, listener, task);
                } catch (HttpWrongStatusCodeException e) {
                    checkCloudflareError(e, url);
                    throw e;
                }
                String challenge = json.getString("challenge");
                String captchaUrl = (useHttps() ? "https://" : "http://") + json.optString("url", "i.captcha.yandex.net/image?key=" + challenge);
                Bitmap captchaBitmap = null;
                HttpRequestModel requestModel = HttpRequestModel.builder().setGET().build();
                HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(captchaUrl, requestModel, httpClient, listener, task);
                try {
                    InputStream imageStream = responseModel.stream;
                    captchaBitmap = BitmapFactory.decodeStream(imageStream);
                } finally {
                    responseModel.release();
                }
                this.captchaType = captchaType;
                this.yandexCaptchaKey = challenge;
                this.recaptchaV1 = null;
                captchaModel = new CaptchaModel();
                captchaModel.type = captchaType == CAPTCHA_YANDEX_ESTD ? CaptchaModel.TYPE_NORMAL_DIGITS : CaptchaModel.TYPE_NORMAL;
                captchaModel.bitmap = captchaBitmap;
                return captchaModel;
            case CAPTCHA_RECAPTCHA:
            case CAPTCHA_RECAPTCHA_FALLBACK:
                this.captchaType = captchaType;
                this.yandexCaptchaKey = null;
                this.recaptchaV1 = null;
                return null;
            case CAPTCHA_RECAPTCHA_V1:
                this.captchaType = captchaType;
                this.yandexCaptchaKey = null;
                this.recaptchaV1 = Recaptcha.obtain(RECAPTCHA_PUBLIC_KEY, task, httpClient, useHttps() ? "https" : "http");
                captchaModel = new CaptchaModel();
                captchaModel.type = CaptchaModel.TYPE_NORMAL;
                captchaModel.bitmap = recaptchaV1.bitmap;
                return captchaModel;
            default:
                throw new IllegalStateException();
        }
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "action/create" + (model.threadNumber == null ? "Thread" : "Post");
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("boardName", model.boardName);
        if (model.threadNumber != null) {
            postEntityBuilder.addString("threadNumber", model.threadNumber);
        }
        switch (captchaType) {
            case CAPTCHA_YANDEX_ELATM:
            case CAPTCHA_YANDEX_ESTD:
            case CAPTCHA_YANDEX_RUS:
                switch (captchaType) {
                    case CAPTCHA_YANDEX_ELATM: postEntityBuilder.addString("captchaEngine", "yandex-captcha-elatm"); break;
                    case CAPTCHA_YANDEX_ESTD: postEntityBuilder.addString("captchaEngine", "yandex-captcha-estd"); break;
                    case CAPTCHA_YANDEX_RUS: postEntityBuilder.addString("captchaEngine", "yandex-captcha-rus"); break;
                }
                postEntityBuilder.addString("yandexCaptchaChallenge", yandexCaptchaKey).addString("yandexCaptchaResponse", model.captchaAnswer);
                break;
            case CAPTCHA_RECAPTCHA:
            case CAPTCHA_RECAPTCHA_FALLBACK:
                postEntityBuilder.addString("captchaEngine", "google-recaptcha");
                String response = Recaptcha2solved.pop(RECAPTCHA_PUBLIC_KEY);
                if (response == null) {
                    throw (getUsingCaptchaType() == CAPTCHA_RECAPTCHA_FALLBACK ?
                            new Recaptcha2fallback(RECAPTCHA_PUBLIC_KEY, CHAN_NAME) :
                                new Recaptcha2js(RECAPTCHA_PUBLIC_KEY));
                }
                postEntityBuilder.addString("g-recaptcha-response", response);
                break;
            case CAPTCHA_RECAPTCHA_V1:
                postEntityBuilder.
                        addString("captchaEngine", "google-recaptcha-v1").
                        addString("recaptcha_challenge_field", recaptchaV1.challenge).
                        addString("recaptcha_response_field", model.captchaAnswer);
                break;
        }
        postEntityBuilder.
                addString("email", model.sage ? "sage" : model.email).
                addString("name", model.name).
                addString("subject", model.subject).
                addString("text", model.comment).
                addString("signAsOp", model.custommark ? "true" : "false").
                addString("password", model.password).
                addString("markupMode", "EXTENDED_WAKABA_MARK,BB_CODE");
        String rating = (model.icon >= 0 && model.icon < RATINGS.length) ? RATINGS[model.icon] : "SFW";
        if (model.attachments != null && model.attachments.length > 0) {
            for (int i=0; i<model.attachments.length; ++i) {
                postEntityBuilder.
                        addFile("file_" + Integer.toString(i+1), model.attachments[i], model.randomHash).
                        addString("file_" + Integer.toString(i+1) + "_rating", rating);
            }
        }
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).build();
        JSONObject result = HttpStreamer.getInstance().getJSONObjectFromUrl(url, request, httpClient, null, task, false);
        try {
            if (model.threadNumber != null) {
                return getUsingUrl() + model.boardName + "/res/" + model.threadNumber + ".html#" + result.getInt("postNumber");
            } else {
                return getUsingUrl() + model.boardName + "/res/" + result.getInt("threadNumber") + ".html";
            }
        } catch (JSONException e) {
            throw new Exception(result.getString("errorDescription"));
        }
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        if (model.onlyFiles) {
            String url = getUsingUrl() + "action/deleteFile";
            String postUrl = getUsingUrl() + "api/post.json?boardName=" + model.boardName + "&postNumber=" + model.postNumber;
            PostModel post = mapPostModel(downloadJSONObject(postUrl, false, null, task), null);
            if (post.attachments != null) {
                for (AttachmentModel attachment : post.attachments) {
                    ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                            addString("fileName", attachment.path.substring(attachment.path.lastIndexOf('/') + 1)).
                            addString("password", model.password);
                    HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).build();
                    JSONObject result = HttpStreamer.getInstance().getJSONObjectFromUrl(url, request, httpClient, null, task, false);
                    String error = result.optString("errorDescription");
                    if (error.length() > 0) throw new Exception(error);
                }
            }
        } else {
            String url = getUsingUrl() + "action/deletePost";
            ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                    addString("boardName", model.boardName).
                    addString("postNumber", model.postNumber).
                    addString("password", model.password);
            HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).build();
            JSONObject result = HttpStreamer.getInstance().getJSONObjectFromUrl(url, request, httpClient, null, task, false);
            String error = result.optString("errorDescription");
            if (error.length() > 0) throw new Exception(error);
        }
        lastDeleted = new String[] { model.boardName, model.threadNumber };
        return null;
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(CHAN_NAME)) throw new IllegalArgumentException("wrong chan");
        if (model.type == UrlPageModel.TYPE_CATALOGPAGE) return getUsingUrl() + model.boardName + "/catalog.html";
        return WakabaUtils.buildUrl(model, getUsingUrl());
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        if (url.contains("/catalog.html")) {
            try {
                RegexUtils.getUrlPath(url, DOMAIN);
                int index = url.indexOf("/catalog.html");
                String left = url.substring(0, index);
                UrlPageModel model = new UrlPageModel();
                model.chanName = getChanName();
                model.type = UrlPageModel.TYPE_CATALOGPAGE;
                model.boardName = left.substring(left.lastIndexOf('/') + 1);
                model.catalogType = 0;
                return model;
            } catch (Exception e) {}
        }
        return WakabaUtils.parseUrl(url, CHAN_NAME, DOMAIN);
    }
}
