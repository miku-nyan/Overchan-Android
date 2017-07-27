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

package nya.miku.wishmaster.chans.tumbach;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.CloudflareChanModule;
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
import nya.miku.wishmaster.api.util.LazyPreferences;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.api.util.WakabaUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.recaptcha.Recaptcha;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2solved;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class TumbachModule extends CloudflareChanModule {
    private static final String TAG = "TumbachModule";
    private static final String CHAN_NAME = "tumba.ch";
    private static final String DOMAIN = "tumba.ch";
    
    private static final String[] CAPTCHA_TYPES = new String[] {
            "Node captcha", "Google Recaptcha 2", "Google Recaptcha 2 (fallback)", "Google Recaptcha" };
    private static final String[] CAPTCHA_TYPES_KEYS = new String[] {
            "node-captcha", "recaptcha", "recaptcha-fallback", "recaptchav1" };
    private static final String CAPTCHA_TYPE_DEFAULT = "node-captcha";
    
    private static final int CAPTCHA_NODE = 1;
    private static final int CAPTCHA_RECAPTCHA = 2;
    private static final int CAPTCHA_RECAPTCHA_FALLBACK = 3;
    private static final int CAPTCHA_RECAPTCHA_V1 = 4;
    
    private static final String RECAPTCHA_PUBLIC_KEY = "6LdZ3g4TAAAAABiO8nWlxVOwLNx3tR2r2wiZMHuI";
    
    private static final String PREF_KEY_CAPTCHA_TYPE = "PREF_KEY_CAPTCHA_TYPE";
    
    public static final String[] CATALOG_TYPES = new String[] { "date", "recent", "bumps" };
    public static final String[] CATALOG_DESCRIPTIONS = new String[] {
            "Сортировать по дате создания", "Сортировать по дате последнего поста", "Сортировать по количеству бампов" };
    
    private static final List<String> SFW_BOARDS = Arrays.asList("a", "art", "d", "mu", "news", "s", "vg");
    private static final String[] RATINGS = new String[] { "SFW", "R-15", "R-18", "R-18G" };
    
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    static { DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT")); }
    
    private static final Pattern COMMENT_QUOTE = Pattern.compile("<span class=['\"]quotation['\"]>");
    private static final Pattern COMMENT_CODE = Pattern.compile("<div class=['\"]code-block[^>]*>(.*?)</div>");
    
    private HashMap<String, BoardModel> boardsMap;
    
    private int captchaType;
    private String nodeCaptchaKey;
    private Recaptcha recaptchaV1;
    
    public TumbachModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Tumbach";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_tumbach, null);
    }
    
    private boolean useHttps() {
        return useHttps(true);
    }
    
    private String getUsingUrl() {
        return (useHttps() ? "https://" : "http://") + DOMAIN + "/";
    }
    
    @Override
    protected String getCloudflareCookieDomain() {
        return DOMAIN;
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        addOnlyNewPostsPreference(preferenceGroup, true);
        final ListPreference captchaPreference = new LazyPreferences.ListPreference(context); //captcha_type
        captchaPreference.setTitle(R.string.pref_captcha_type);
        captchaPreference.setDialogTitle(R.string.pref_captcha_type);
        captchaPreference.setKey(getSharedKey(PREF_KEY_CAPTCHA_TYPE));
        captchaPreference.setEntryValues(CAPTCHA_TYPES_KEYS);
        captchaPreference.setEntries(CAPTCHA_TYPES);
        captchaPreference.setDefaultValue(CAPTCHA_TYPE_DEFAULT);
        int i = Arrays.asList(CAPTCHA_TYPES_KEYS).indexOf(preferences.getString(getSharedKey(PREF_KEY_CAPTCHA_TYPE), CAPTCHA_TYPE_DEFAULT));
        if (i >= 0) captchaPreference.setSummary(CAPTCHA_TYPES[i]);
        preferenceGroup.addPreference(captchaPreference);
        addPasswordPreference(preferenceGroup);
        addHttpsPreference(preferenceGroup, true);
        addCloudflareRecaptchaFallbackPreference(preferenceGroup);
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
        return loadOnlyNewPosts(true);
    }
    
    private int getUsingCaptchaType() {
        String key = preferences.getString(getSharedKey(PREF_KEY_CAPTCHA_TYPE), CAPTCHA_TYPE_DEFAULT);
        if (Arrays.asList(CAPTCHA_TYPES_KEYS).indexOf(key) == -1) key = CAPTCHA_TYPE_DEFAULT;
        switch (key) {
            case "node-captcha":
                return CAPTCHA_NODE;
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
            throw new Exception(json.getString("message"));
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
        int attachmentsCount = json.optInt("maxFileCount", 2);
        model.requiredFileForNewThread = attachmentsCount > 0;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = true;
        model.allowReport = BoardModel.REPORT_NOT_ALLOWED;
        model.allowNames = true;
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = false;
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
        String url = getUsingUrl() + "misc/board/" + shortName + ".json";
        JSONObject json = downloadJSONObject(url, false, listener, task);
        try {
            BoardModel board = mapBoardModel(json.getJSONObject("board"));
            addToMap(board);
            return board;
        } catch (JSONException e) {
            throw new Exception(json.getString("message"));
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
                BoardModel board = getBoard(boardName, listener, task);
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
            throw new Exception(json.getString("message"));
        }
    }
    
    @Override
    public ThreadModel[] getCatalog(String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = getUsingUrl() + boardName + "/catalog" + (catalogType > 0 ? "-" + CATALOG_TYPES[catalogType] : "") + ".json";
        JSONObject json = downloadJSONObject(url, oldList != null, listener, task);
        if (json == null) return oldList;
        try {
            try {
                getBoard(boardName, listener, task);
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
            throw new Exception(json.getString("message"));
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
        if (oldList != null && oldList.length > 0 && loadOnlyNewPosts()) {
            String lastPostNumberUrl = getUsingUrl() + "api/threadLastPostNumber.json?boardName=" + boardName + "&threadNumber=" + threadNumber;
            try {
                JSONObject lastPostNumber = downloadJSONObject(lastPostNumberUrl, false, listener, task);
                if (lastPostNumber.optString("lastPostNumber").equals(oldList[oldList.length-1].number)) {
                    return oldList;
                }
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
        
        String url = getUsingUrl() + boardName + "/res/" + threadNumber + ".json";
        JSONObject json = downloadJSONObject(url, oldList != null, listener, task);
        if (json == null) return oldList;
        try {
            try {
                getBoard(boardName, listener, task);
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
            PostModel[] newList = mapThreadModel(json.getJSONObject("thread")).posts;
            if (oldList == null) return newList;
            return ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(newList));
        } catch (JSONException e) {
            throw new Exception(json.getString("message"));
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
        String extra = json.optString("extraData");
        if (extra.indexOf(" /") > 0) {
            extra = "(" + extra + ")";
            model.name = model.name.length() > 0 ? (model.name + " " + extra) : extra;
        }
        String subject = json.optString("subject");
        if (!subject.startsWith("<a href")) {
            model.subject = RegexUtils.removeHtmlTags(subject);
        } else {
            model.comment = subject + "<br/>";
            model.subject = "";
        }
        String text = RegexUtils.replaceAll(json.optString("text"), COMMENT_QUOTE, "<span class=\"unkfunc\">");
        text = RegexUtils.replaceAll(text, COMMENT_CODE, "<code>$1</code>");
        model.comment = model.comment != null ? (model.comment + text) : text;
        model.email = json.optString("email");
        model.trip = json.optString("tripCode");
        JSONObject user = json.optJSONObject("user");
        if (user != null) {
            String level = user.optString("level");
            if (level.equals("SUPERUSER")) model.trip += "## Admin";
            else if (level.equals("ADMIN") || level.equals("MODER")) model.trip += "## Mod";    
        }
        model.icons = null;
        model.op = json.optBoolean("isOp");
        JSONObject options = json.optJSONObject("options");
        model.sage = options.optBoolean("sage") || model.email.toLowerCase(Locale.US).equals("sage");
        if (options.optBoolean("bannedFor")) {
            model.comment += "<br/><br/><font color=\"red\">Потребитель был запрещен для этого столба</font>";
        }
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
        try {
            String quotaUrl = getUsingUrl() + "api/captchaQuota.json?boardName=" + boardName;
            int quota = downloadJSONObject(quotaUrl, false, null, task).optInt("quota");
            if (quota > 0) return null;
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        
        CaptchaModel captchaModel;
        int captchaType = getUsingCaptchaType();
        switch (captchaType) {
            case CAPTCHA_NODE:
                String url = getUsingUrl() + "api/nodeCaptchaImage.json";
                JSONObject json = downloadJSONObject(url, false, listener, task);
                String challenge = json.getString("challenge");
                String captchaUrl = getUsingUrl() + "node-captcha/" + json.getString("fileName");
                captchaModel = downloadCaptcha(captchaUrl, listener, task);
                captchaModel.type = CaptchaModel.TYPE_NORMAL_DIGITS;
                this.captchaType = captchaType;
                this.nodeCaptchaKey = challenge;
                return captchaModel;
            case CAPTCHA_RECAPTCHA:
            case CAPTCHA_RECAPTCHA_FALLBACK:
                this.captchaType = captchaType;
                this.nodeCaptchaKey = null;
                this.recaptchaV1 = null;
                return null;
            case CAPTCHA_RECAPTCHA_V1:
                this.captchaType = captchaType;
                this.nodeCaptchaKey = null;
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
            case CAPTCHA_NODE:
                postEntityBuilder.addString("nodeCaptchaChallenge", nodeCaptchaKey).addString("nodeCaptchaResponse", model.captchaAnswer);
                break;
            case CAPTCHA_RECAPTCHA:
            case CAPTCHA_RECAPTCHA_FALLBACK:
                postEntityBuilder.addString("captchaEngine", "google-recaptcha");
                String response = Recaptcha2solved.pop(RECAPTCHA_PUBLIC_KEY);
                if (response == null) {
                    boolean fallback = getUsingCaptchaType() == CAPTCHA_RECAPTCHA_FALLBACK;
                    throw Recaptcha2.obtain(getUsingUrl(), RECAPTCHA_PUBLIC_KEY, null, CHAN_NAME, fallback);
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
                addString("name", model.name).
                addString("subject", model.subject).
                addString("text", model.comment).
                addString("signAsOp", model.custommark ? "true" : "false").
                addString("password", model.password).
                addString("markupMode", "EXTENDED_WAKABA_MARK,BB_CODE");
        if (model.sage) postEntityBuilder.addString("sage", "true");
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
            throw new Exception(result.getString("message"));
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
            String error = result.optString("message");
            if (error.length() > 0) throw new Exception(error);
        }
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
        String urlPath = UrlPathUtils.getUrlPath(url, DOMAIN);
        if (urlPath == null) throw new IllegalArgumentException("wrong domain");
        if (url.contains("/catalog.html")) {
            try {
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
        if (urlPath.contains("#")) urlPath = urlPath.replaceAll("#\\D+", "#");
        return WakabaUtils.parseUrlPath(urlPath, CHAN_NAME);
    }
}
