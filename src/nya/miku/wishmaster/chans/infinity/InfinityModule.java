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

package nya.miku.wishmaster.chans.infinity;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntityHC4;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookieHC4;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.TextUtils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.FastHtmlTagParser;
import nya.miku.wishmaster.chans.AbstractChanModule;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.cloudflare.CloudflareException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.base64.Base64;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

public class InfinityModule extends AbstractChanModule {
    
    static final String CHAN_NAME = "8chan";
    private static final String DEFAULT_DOMAIN = "8ch.net";
    private static final String ONION_DOMAIN = "fullchan4jtta4sx.onion";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN, ONION_DOMAIN, "8chan.co" };
    
    private static final String[] CATALOG = new String[] { "Catalog" };
    private static final String[] ATTACHMENT_FORMATS = new String[] { "jpg", "jpeg", "gif", "png", "webm", "mp4", "swf" };
    private static final FastHtmlTagParser.TagReplaceHandler QUOTE_REPLACER = new FastHtmlTagParser.TagReplaceHandler() {
        @Override
        public FastHtmlTagParser.TagsPair replace(FastHtmlTagParser.TagsPair source) {
            if (source.openTag.equalsIgnoreCase("<p class=\"body-line ltr quote\">"))
                return new FastHtmlTagParser.TagsPair("<blockquote class=\"unkfunc\">", "</blockquote>");
            return null;
        }
    };
    private static final Pattern ATTACHMENT_EMBEDDED_LINK = Pattern.compile("<a[^>]*href=\"([^\">]*)\"[^>]*>");
    private static final Pattern ATTACHMENT_EMBEDDED_THUMB = Pattern.compile("<img[^>]*src=\"([^\">]*)\"[^>]*>");
    
    private static final Pattern CAPTCHA_BASE64 = Pattern.compile("data:image/png;base64,([^\"]*)\"");
    private static final Pattern CAPTCHA_COOKIE = Pattern.compile("<input[^>]*name='captcha_cookie'[^>]*value='([^']*)'");
    
    private static final Pattern ERROR_PATTERN = Pattern.compile("<h2 [^>]*>(.*?)</h2>");
    
    private static final String PREF_KEY_USE_HTTPS = "PREF_KEY_USE_HTTPS";
    private static final String PREF_KEY_USE_ONION = "PREF_KEY_USE_ONION";
    private static final String PREF_KEY_CLOUDFLARE_COOKIE = "PREF_KEY_CLOUDFLARE_COOKIE";
    private static final String CLOUDFLARE_COOKIE_NAME = "cf_clearance";
    private static final String CLOUDFLARE_RECAPTCHA_KEY = "6LeT6gcAAAAAAAZ_yDmTMqPH57dJQZdQcu6VFqog"; 
    private static final String CLOUDFLARE_RECAPTCHA_CHECK_URL_FMT = "cdn-cgi/l/chk_captcha?recaptcha_challenge_field=%s&recaptcha_response_field=%s";
    private static final String TAG = null;
    
    private Map<String, BoardModel> boardsMap = new HashMap<>();
    private boolean needTorCaptcha = false;
    private String torCaptchaCookie = null;
    
    
    public InfinityModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "\u221Echan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_8chan, null);
    }
    
    @Override
    protected void initHttpClient() {
        String cloudflareCookie = preferences.getString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE), null);
        if (cloudflareCookie != null) {
            BasicClientCookieHC4 c = new BasicClientCookieHC4(CLOUDFLARE_COOKIE_NAME, cloudflareCookie);
            c.setDomain(DEFAULT_DOMAIN);
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
    
    private JSONObject downloadJSONObject(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        JSONObject object = null;
        try {
            object = HttpStreamer.getInstance().getJSONObjectFromUrl(url, rqModel, httpClient, listener, task, true);
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        }
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return object;
    }
    
    private JSONArray downloadJSONArray(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        JSONArray array = null;
        try {
            array = HttpStreamer.getInstance().getJSONArrayFromUrl(url, rqModel, httpClient, listener, task, true);
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        }
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return array;
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        addPasswordPreference(preferenceGroup);
        CheckBoxPreference httpsPref = new CheckBoxPreference(context);
        httpsPref.setTitle(R.string.pref_use_https);
        httpsPref.setSummary(R.string.pref_use_https_summary);
        httpsPref.setKey(getSharedKey(PREF_KEY_USE_HTTPS));
        httpsPref.setDefaultValue(true);
        preferenceGroup.addPreference(httpsPref);
        addUnsafeSslPreference(preferenceGroup, getSharedKey(PREF_KEY_USE_HTTPS));
        CheckBoxPreference onionPref = new CheckBoxPreference(context);
        onionPref.setTitle(R.string.pref_use_onion);
        onionPref.setSummary(R.string.pref_use_onion_summary);
        onionPref.setKey(getSharedKey(PREF_KEY_USE_ONION));
        onionPref.setDefaultValue(false);
        onionPref.setDisableDependentsState(true);
        preferenceGroup.addPreference(onionPref);
        httpsPref.setDependency(getSharedKey(PREF_KEY_USE_ONION));
        addProxyPreferences(preferenceGroup);
    }
    
    private String getUsingUrl() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_ONION), false) ? ("http://" + ONION_DOMAIN + "/") :
            ((preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS), true) ? "https://" : "http://") + DEFAULT_DOMAIN + "/");
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        String url = getUsingUrl() + "boards.json";
        
        HttpResponseModel responseModel = null;
        InfinityBoardsListReader in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(oldBoardsList != null).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = new InfinityBoardsListReader(responseModel.stream);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return in.readBoardsList();
            } else {
                if (responseModel.notModified()) return null;
                byte[] html = null;
                try {
                    ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
                    IOUtils.copyStream(responseModel.stream, byteStream);
                    html = byteStream.toByteArray();
                } catch (Exception e) {}
                throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode + " - " + responseModel.statusReason, html);
            }
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
        }
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
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel fromMap = boardsMap.get(shortName);
        if (fromMap != null) return fromMap;
        String url = getUsingUrl() + "settings.php?board=" + shortName;
        JSONObject json = downloadJSONObject(url, false, listener, task);
        BoardModel model = new BoardModel();
        model.chan = CHAN_NAME;
        model.boardName = shortName;
        model.boardDescription = json.optString("title", shortName);
        model.uniqueAttachmentNames = true;
        model.timeZoneId = "US/Eastern";
        model.defaultUserName = json.optString("anonymous", "Anonymous");
        model.bumpLimit = json.optInt("reply_limit", 500);
        model.readonlyBoard = false;
        model.requiredFileForNewThread = false;
        model.allowDeletePosts = json.optBoolean("allow_delete", false);
        model.allowDeleteFiles = model.allowDeletePosts;
        model.allowNames = true;
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = true;
        model.allowWatermark = false;
        model.allowOpMark = false;
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = json.optBoolean("disable_images", false) ? 0 : 5;
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        model.markType = BoardModel.MARK_NOMARK;
        model.firstPage = 1;
        model.lastPage = json.optInt("max_pages", BoardModel.LAST_PAGE_UNDEFINED);
        model.searchAllowed = false;
        model.catalogAllowed = true;
        model.catalogTypeDescriptions = CATALOG;
        boardsMap.put(shortName, model);
        return model;
    }
    
    @Override
    public ThreadModel[] getCatalog(String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = getUsingUrl() + boardName + "/catalog.json";
        JSONArray response = downloadJSONArray(url, oldList != null, listener, task);
        if (response == null) return oldList;
        List<ThreadModel> threads = new ArrayList<>();
        for (int i=0, len=response.length(); i<len; ++i) {
            JSONArray curArray = response.getJSONObject(i).getJSONArray("threads");
            for (int j=0, clen=curArray.length(); j<clen; ++j) {
                JSONObject curThreadJson = curArray.getJSONObject(j);
                ThreadModel curThread = new ThreadModel();
                curThread.threadNumber = Long.toString(curThreadJson.getLong("no"));
                curThread.postsCount = curThreadJson.optInt("replies", -2) + 1;
                curThread.attachmentsCount = curThreadJson.optInt("images", -2) + 1;
                curThread.isSticky = curThreadJson.optInt("sticky") == 1;
                curThread.isClosed = curThreadJson.optInt("closed") == 1;
                curThread.posts = new PostModel[] { mapPostModel(curThreadJson, boardName) };
                threads.add(curThread);
            }
        }
        return threads.toArray(new ThreadModel[threads.size()]);
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = getUsingUrl() + boardName + "/" + (page-1) + ".json";
        JSONObject response = downloadJSONObject(url, oldList != null, listener, task);
        if (response == null) return oldList;
        JSONArray threads = response.getJSONArray("threads");
        ThreadModel[] result = new ThreadModel[threads.length()];
        for (int i=0, len=threads.length(); i<len; ++i) {
            JSONArray posts = threads.getJSONObject(i).getJSONArray("posts");
            JSONObject op = posts.getJSONObject(0);
            ThreadModel curThread = new ThreadModel();
            curThread.threadNumber = Long.toString(op.getLong("no"));
            curThread.postsCount = op.optInt("replies", -2) + 1;
            curThread.attachmentsCount = op.optInt("images", -2) + 1;
            curThread.isSticky = op.optInt("sticky") == 1;
            curThread.isClosed = op.optInt("closed") == 1;
            curThread.posts = new PostModel[posts.length()];
            for (int j=0, plen=posts.length(); j<plen; ++j) {
                curThread.posts[j] = mapPostModel(posts.getJSONObject(j), boardName);
            }
            result[i] = curThread;
        }
        return result;
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        String url = getUsingUrl() + boardName + "/res/" + threadNumber + ".json";
        JSONObject response = downloadJSONObject(url, oldList != null, listener, task);
        if (response == null) return oldList;
        JSONArray posts = response.getJSONArray("posts");
        PostModel[] result = new PostModel[posts.length()];
        for (int i=0, len=posts.length(); i<len; ++i) {
            result[i] = mapPostModel(posts.getJSONObject(i), boardName);
        }
        if (oldList != null) {
            result = ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(result));
        }
        return result;
    }
    
    private PostModel mapPostModel(JSONObject object, String boardName) {
        PostModel model = new PostModel();
        model.number = Long.toString(object.getLong("no"));
        model.name = StringEscapeUtils.unescapeHtml4(object.optString("name", "Anonymous").replaceAll("</?span[^>]*?>", ""));
        model.subject = StringEscapeUtils.unescapeHtml4(object.optString("sub", ""));
        model.comment = object.optString("com", "");
        try {
            model.comment = FastHtmlTagParser.getPTagParser().replace(model.comment, QUOTE_REPLACER);
        } catch (Exception e) {}
        model.email = object.optString("email", "");
        model.trip = object.optString("trip", "");
        String capcode = object.optString("capcode", "none");
        if (!capcode.equals("none")) model.trip += "##"+capcode;
        String countryIcon = object.optString("country", "");
        if (!countryIcon.equals("")) {
            BadgeIconModel icon = new BadgeIconModel();
            icon.source = "/static/flags/" + countryIcon.toLowerCase(Locale.US) + ".png";
            icon.description = object.optString("country_name");
            model.icons = new BadgeIconModel[] { icon };
        }
        model.op = false;
        String id = object.optString("id", "");
        model.sage = id.equalsIgnoreCase("Heaven") || model.email.toLowerCase(Locale.US).contains("sage");
        if (!id.equals("")) model.name += (" ID:" + id);
        model.timestamp = object.getLong("time") * 1000;
        model.parentThread = object.optString("resto", "0");
        if (model.parentThread.equals("0")) model.parentThread = model.number;
        
        List<AttachmentModel> attachments = null;
        boolean isSpoiler = object.optInt("spoiler") == 1;
        AttachmentModel rootAttachment = mapAttachment(object, boardName, isSpoiler);
        if (rootAttachment != null) {
            attachments = new ArrayList<>();
            attachments.add(rootAttachment);
            JSONArray extraFiles = object.optJSONArray("extra_files");
            if (extraFiles != null && extraFiles.length() != 0) {
                for (int i=0, len=extraFiles.length(); i<len; ++i) {
                    AttachmentModel attachment = mapAttachment(extraFiles.getJSONObject(i), boardName, isSpoiler);
                    if (attachment != null) attachments.add(attachment);
                }
            }
        }
        String embed = object.optString("embed", "");
        if (!embed.equals("")) {
            AttachmentModel embedAttachment = new AttachmentModel();
            embedAttachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
            Matcher linkMatcher = ATTACHMENT_EMBEDDED_LINK.matcher(embed);
            if (linkMatcher.find()) {
                embedAttachment.path = linkMatcher.group(1);
                if (embedAttachment.path.startsWith("//")) embedAttachment.path = "http:" + embedAttachment.path;
                Matcher thumbMatcher = ATTACHMENT_EMBEDDED_THUMB.matcher(embed);
                if (thumbMatcher.find()) {
                    embedAttachment.thumbnail = thumbMatcher.group(1);
                    if (embedAttachment.thumbnail.startsWith("//")) embedAttachment.thumbnail = "http:" + embedAttachment.thumbnail;
                }
                embedAttachment.isSpoiler = isSpoiler;
                if (attachments != null) attachments.add(embedAttachment); else attachments = Collections.singletonList(embedAttachment);
            }
        }
        if (attachments != null) model.attachments = attachments.toArray(new AttachmentModel[attachments.size()]);
        return model;
    }
    
    private AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        String ext = object.optString("ext", "");
        if (!ext.equals("")) {
            AttachmentModel attachment = new AttachmentModel();
            switch (ext) {
                case ".jpeg":
                case ".jpg":
                case ".png":
                    attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                    break;
                case ".gif":
                    attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
                    break;
                case ".webm":
                case ".mp4":
                    attachment.type = AttachmentModel.TYPE_VIDEO;
                    break;
                default:
                    attachment.type = AttachmentModel.TYPE_OTHER_FILE;
            }
            attachment.size = object.optInt("fsize", -1);
            if (attachment.size > 0) attachment.size = Math.round(attachment.size / 1024f);
            attachment.width = object.optInt("w", -1);
            attachment.height = object.optInt("h", -1);
            attachment.originalName = object.optString("filename", "") + ext;
            attachment.isSpoiler = isSpoiler;
            String tim = object.optString("tim", "");
            if (tim.length() > 0) {
                attachment.thumbnail = isSpoiler ? null : ("/" + boardName + "/thumb/" + tim + ".jpg");
                attachment.path = "/" + boardName + "/src/" + tim + ext;
                return attachment;
            }
        }
        return null;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        if (needTorCaptcha) {
            String url = getUsingUrl() + "dnsbls_bypass.php";
            String response =
                    HttpStreamer.getInstance().getStringFromUrl(url, HttpRequestModel.builder().setGET().build(), httpClient, listener, task, false);
            Matcher base64Matcher = CAPTCHA_BASE64.matcher(response);
            Matcher cookieMatcher = CAPTCHA_COOKIE.matcher(response);
            if (base64Matcher.find() && cookieMatcher.find()) {
                byte[] bitmap = Base64.decode(base64Matcher.group(1), Base64.DEFAULT);
                torCaptchaCookie = cookieMatcher.group(1);
                CaptchaModel captcha = new CaptchaModel();
                captcha.type = CaptchaModel.TYPE_NORMAL;
                captcha.bitmap = BitmapFactory.decodeByteArray(bitmap, 0, bitmap.length);
                return captcha;
            }
        }
        return null;
    }
    
    private void checkCaptcha(String answer, CancellableTask task) throws Exception {
        try {
            if (torCaptchaCookie == null) throw new Exception("Invalid captcha");
            String url = getUsingUrl() + "dnsbls_bypass.php";
            List<NameValuePair> pairs = new ArrayList<NameValuePair>();
            pairs.add(new BasicNameValuePair("captcha_text", answer));
            pairs.add(new BasicNameValuePair("captcha_cookie", torCaptchaCookie));
            HttpRequestModel rqModel = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setTimeout(30000).build();
            String response = HttpStreamer.getInstance().getStringFromUrl(url, rqModel, httpClient, null, task, true);
            if (response.contains("Error") && !response.contains("Success")) throw new HttpWrongStatusCodeException(400, "400");
            needTorCaptcha = false;
        } catch (HttpWrongStatusCodeException e) {
            if (task != null && task.isCancelled()) throw new InterruptedException("interrupted");
            if (e.getStatusCode() == 400) throw new Exception("You failed the CAPTCHA");
            throw e;
        }
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        if (needTorCaptcha) checkCaptcha(model.captchaAnswer, task);
        if (task != null && task.isCancelled()) throw new InterruptedException("interrupted");
        String url = getUsingUrl() + "post.php";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("name", model.name).
                addString("email", model.sage ? "sage" : model.email).
                addString("subject", model.subject).
                addString("body", model.comment).
                addString("post", model.threadNumber == null ? "New Topic" : "New Reply").
                addString("board", model.boardName);
        if (model.threadNumber != null) postEntityBuilder.addString("thread", model.threadNumber);
        postEntityBuilder.addString("password", TextUtils.isEmpty(model.password) ? getDefaultPassword() : model.password);
        if (model.attachments != null) {
            String[] images = new String[] { "file", "file2", "file3", "file4", "file5" };
            for (int i=0; i<model.attachments.length; ++i) {
                postEntityBuilder.addFile(images[i], model.attachments[i]);
            }
        }
        
        UrlPageModel refererPage = new UrlPageModel();
        refererPage.chanName = CHAN_NAME;
        refererPage.boardName = model.boardName;
        if (model.threadNumber == null) {
            refererPage.type = UrlPageModel.TYPE_BOARDPAGE;
            refererPage.boardPage = UrlPageModel.DEFAULT_FIRST_PAGE;
        } else {
            refererPage.type = UrlPageModel.TYPE_THREADPAGE;
            refererPage.threadNumber = model.threadNumber;
        }
        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, buildUrl(refererPage)) };
        HttpRequestModel request =
                HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setCustomHeaders(customHeaders).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, listener, task);
            if (response.statusCode == 200) {
                Logger.d(TAG, "200 OK");
                return null;
            } else if (response.statusCode == 303) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        return fixRelativeUrl(header.getValue());
                    }
                }
            } else if (response.statusCode == 400) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (htmlResponse.contains("dnsbls_bypass.php")) {
                    needTorCaptcha = true;
                    throw new Exception("Please complete your CAPTCHA.");
                } else if (htmlResponse.contains("<h1>Error</h1>")) {
                    Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                    if (errorMatcher.find()) {
                        String error = errorMatcher.group(1);
                        if (error.contains("To post on 8chan over Tor, you must use the hidden service for security reasons."))
                            throw new Exception("To post on 8chan over Tor, you must use the onion domain.");
                        throw new Exception(error);
                    }
                }
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "post.php";
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("delete_" + model.postNumber, "on"));
        if (model.onlyFiles) pairs.add(new BasicNameValuePair("file", "on"));
        pairs.add(new BasicNameValuePair("password", model.password));
        pairs.add(new BasicNameValuePair("delete", "Delete"));
        pairs.add(new BasicNameValuePair("reason", ""));
        
        UrlPageModel refererPage = new UrlPageModel();
        refererPage.type = UrlPageModel.TYPE_THREADPAGE;
        refererPage.chanName = CHAN_NAME;
        refererPage.boardName = model.boardName;
        refererPage.threadNumber = model.threadNumber;
        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, buildUrl(refererPage)) };
        HttpRequestModel rqModel = HttpRequestModel.builder().
                setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setCustomHeaders(customHeaders).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (response.statusCode == 200 || response.statusCode == 303) {
                Logger.d(TAG, response.statusCode + " - " + response.statusReason);
                return null;
            } else if (response.statusCode == 400) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (htmlResponse.contains("dnsbls_bypass.php")) {
                    needTorCaptcha = true;
                    throw new Exception("Please complete your CAPTCHA.\n(try to post anything)");
                } else if (htmlResponse.contains("<h1>Error</h1>")) {
                    Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                    if (errorMatcher.find()) {
                        String error = errorMatcher.group(1);
                        if (error.contains("To post on 8chan over Tor, you must use the hidden service for security reasons."))
                            throw new Exception("To post on 8chan over Tor, you must use the onion domain."); //? Tor users cannot into deleting
                        throw new Exception(error);
                    }
                }
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(CHAN_NAME)) throw new IllegalArgumentException("wrong chan");
        if (model.boardName != null && !model.boardName.matches("\\w+")) throw new IllegalArgumentException("wrong board name");
        StringBuilder url = new StringBuilder(getUsingUrl());
        try {
            switch (model.type) {
                case UrlPageModel.TYPE_INDEXPAGE:
                    return url.toString();
                case UrlPageModel.TYPE_BOARDPAGE:
                    if (model.boardPage == UrlPageModel.DEFAULT_FIRST_PAGE || model.boardPage == 1)
                        return url.append(model.boardName).append('/').toString();
                    return url.append(model.boardName).append('/').append(model.boardPage).append(".html").toString();
                case UrlPageModel.TYPE_CATALOGPAGE:
                    return url.append(model.boardName).append("/catalog.html").toString();
                case UrlPageModel.TYPE_THREADPAGE:
                    return url.append(model.boardName).append("/res/").append(model.threadNumber).append(".html").
                            append(model.postNumber == null || model.postNumber.length() == 0 ? "" : ("#" + model.postNumber)).toString();
                case UrlPageModel.TYPE_OTHERPAGE:
                    return url.append(model.otherPath.startsWith("/") ? model.otherPath.substring(1) : model.otherPath).toString();
            }
        } catch (Exception e) {}
        throw new IllegalArgumentException("wrong page type");
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String domain;
        String path = "";
        Matcher parseUrl = Pattern.compile("https?://(?:www\\.)?(.+)", Pattern.CASE_INSENSITIVE).matcher(url);
        if (!parseUrl.find()) throw new IllegalArgumentException("incorrect url");
        String urlPath = parseUrl.group(1);
        Matcher parsePath = Pattern.compile("(.+?)(?:/(.*))").matcher(urlPath);
        if (parsePath.find()) {
            domain = parsePath.group(1).toLowerCase(Locale.US);
            path = parsePath.group(2);
        } else {
            domain = parseUrl.group(1).toLowerCase(Locale.US);
        }
        
        boolean matchDomain = false;
        for (String d : DOMAINS) {
            if (d.equalsIgnoreCase(domain)) {
                matchDomain = true;
                break;
            }
        }
        if (!matchDomain) throw new IllegalArgumentException("wrong chan");
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = CHAN_NAME;
        
        if (path.length() == 0 || path.equals("index.html")) {
            model.type = UrlPageModel.TYPE_INDEXPAGE;
            return model;
        }
        
        Matcher threadPage = Pattern.compile("([^/]+)/res/(\\d+)\\.html(?:#(\\d+))?").matcher(path);
        if (threadPage.find()) {
            model.type = UrlPageModel.TYPE_THREADPAGE;
            model.boardName = threadPage.group(1);
            model.threadNumber = threadPage.group(2);
            model.postNumber = threadPage.group(3);
            return model;
        }
        
        Matcher catalogPage = Pattern.compile("([^/]+)/catalog\\.html").matcher(path);
        if (catalogPage.find()) {
            model.boardName = catalogPage.group(1);
            model.type = UrlPageModel.TYPE_CATALOGPAGE;
            model.catalogType = 0;
            return model;
        }
        
        Matcher firstBoardPage = Pattern.compile("([^/]+)/index\\.html").matcher(path);
        if (firstBoardPage.find()) {
            model.boardName = firstBoardPage.group(1);
            model.type = UrlPageModel.TYPE_BOARDPAGE;
            model.boardPage = 1;
            return model;
        }
        
        Matcher boardPage = Pattern.compile("([^/]+)(?:/(?:(\\d+)\\.html)?)?").matcher(path);
        if (boardPage.find()) {
            model.type = UrlPageModel.TYPE_BOARDPAGE;
            model.boardName = boardPage.group(1);
            String page = boardPage.group(2);
            model.boardPage = page == null ? 1 : Integer.parseInt(page);
            return model;
        }
        
        model.type = UrlPageModel.TYPE_OTHERPAGE;
        model.otherPath = path;
        return model;
    }
    
}
