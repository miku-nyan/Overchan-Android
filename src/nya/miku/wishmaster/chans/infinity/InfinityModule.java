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

package nya.miku.wishmaster.chans.infinity;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.util.TextUtils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
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
import nya.miku.wishmaster.api.util.FastHtmlTagParser;
import nya.miku.wishmaster.api.util.LazyPreferences;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.base64.Base64;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class InfinityModule extends AbstractVichanModule {
    private static final String TAG = "InfinityModule";
    
    private static final String CHAN_NAME = "8chan";
    private static final String DEFAULT_DOMAIN = "8ch.net";
    private static final String SYSTEM_DOMAIN = "sys.8ch.net";
    private static final String MEDIA_DOMAIN = "media.8ch.net";
    private static final String MEDIA2_DOMAIN = "media2.8ch.net";
    private static final String ONION_DOMAIN = "oxwugzccvk3dk6tj.onion";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN, ONION_DOMAIN, "8chan.co" };
    
    private static final String[] ATTACHMENT_FORMATS = new String[] { "jpg", "jpeg", "gif", "png", "webm", "mp4", "swf" };
    private static final FastHtmlTagParser.TagReplaceHandler QUOTE_REPLACER = new FastHtmlTagParser.TagReplaceHandler() {
        @Override
        public FastHtmlTagParser.TagsPair replace(FastHtmlTagParser.TagsPair source) {
            if (source.openTag.equalsIgnoreCase("<p class=\"body-line ltr quote\">"))
                return new FastHtmlTagParser.TagsPair("<span class=\"quote\">", "</span><br />");
            return null;
        }
    };
    private static final FastHtmlTagParser.TagReplaceHandler PARAGRAPH_REPLACER = new FastHtmlTagParser.TagReplaceHandler() {
        @Override
        public FastHtmlTagParser.TagsPair replace(FastHtmlTagParser.TagsPair source) {
            if (source.openTag.equalsIgnoreCase("<p class=\"body-line ltr \">"))
                return new FastHtmlTagParser.TagsPair("", "<br />");
            return null;
        }
    };
    
    private static final Pattern CAPTCHA_BASE64 = Pattern.compile("data:image/png;base64,([^\"]*)\"");
    private static final Pattern CAPTCHA_COOKIE = Pattern.compile("<input[^>]*name='captcha_cookie'[^>]*value='([^']*)'");
    private static final Pattern CAPTCHA_ID = Pattern.compile("CAPTCHA ID: (.*?)<");
    
    private static final Pattern ERROR_PATTERN = Pattern.compile("<h2 [^>]*>(.*?)</h2>");
    private static final Pattern BAN_REASON_PATTERN = Pattern.compile("<p class=\"reason\">(.*?)</p>");
    
    protected static final String PREF_KEY_USE_ONION = "PREF_KEY_USE_ONION";
    
    private Map<String, BoardModel> boardsMap = new HashMap<>();
    protected Set<String> boardsThreadCaptcha = new HashSet<>();
    protected Set<String> boardsPostCaptcha = new HashSet<>();
    private boolean needTorCaptcha = false;
    private String torCaptchaCookie = null;
    protected boolean needNewThreadCaptcha = false;
    protected String newThreadCaptchaId = null;
    
    
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
    protected String getCloudflareCookieDomain() {
        return DEFAULT_DOMAIN;
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        addPasswordPreference(preferenceGroup);
        CheckBoxPreference httpsPref = addHttpsPreference(preferenceGroup, true);
        CheckBoxPreference onionPref = new LazyPreferences.CheckBoxPreference(context);
        onionPref.setTitle(R.string.pref_use_onion);
        onionPref.setSummary(R.string.pref_use_onion_summary);
        onionPref.setKey(getSharedKey(PREF_KEY_USE_ONION));
        onionPref.setDefaultValue(false);
        onionPref.setDisableDependentsState(true);
        preferenceGroup.addPreference(onionPref);
        httpsPref.setDependency(getSharedKey(PREF_KEY_USE_ONION));
        addProxyPreferences(preferenceGroup);
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    protected String getUsingDomain() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_ONION), false) ? ONION_DOMAIN : DEFAULT_DOMAIN;
    }
    
    @Override
    protected String[] getAllDomains() {
        return DOMAINS;
    }
    
    @Override
    protected boolean useHttps() {
        return !preferences.getBoolean(getSharedKey(PREF_KEY_USE_ONION), false) && useHttps(true);
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
        } catch (Exception e) {
            if (responseModel != null) HttpStreamer.getInstance().removeFromModifiedMap(url);
            throw e;
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
        }
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel fromMap = boardsMap.get(shortName);
        if (fromMap != null) return fromMap;
        String url = getUsingUrl() + "settings.php?board=" + shortName;
        JSONObject json;
        try {
            json = downloadJSONObject(url, false, listener, task);
        } catch (Exception e) {
            json = new JSONObject();
        }
        BoardModel model = new BoardModel();
        model.chan = getChanName();
        model.boardName = shortName;
        model.boardDescription = json.optString("title", shortName);
        model.uniqueAttachmentNames = true;
        model.timeZoneId = "US/Eastern";
        model.defaultUserName = json.optString("anonymous", "Anonymous");
        model.bumpLimit = json.optInt("reply_limit", 500);
        model.readonlyBoard = false;
        model.requiredFileForNewThread = json.optBoolean("force_image_op", false);
        model.allowDeletePosts = json.optBoolean("allow_delete", false);
        model.allowDeleteFiles = model.allowDeletePosts;
        model.allowNames = !json.optBoolean("field_disable_name", false);
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = true;
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = json.optBoolean("disable_images", false) ? 0 : json.optInt("max_images", 5);
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        model.markType = BoardModel.MARK_NOMARK;
        model.firstPage = 1;
        model.lastPage = json.optInt("max_pages", BoardModel.LAST_PAGE_UNDEFINED);
        model.searchAllowed = false;
        model.catalogAllowed = true;
        boardsMap.put(shortName, model);
        JSONObject captcha = json.optJSONObject("captcha");
        if ((captcha != null) && captcha.optBoolean("enabled")) {
            boardsPostCaptcha.add(shortName);
            boardsThreadCaptcha.add(shortName);
        } else if (json.optBoolean("new_thread_capt")) {
            boardsThreadCaptcha.add(shortName);
        }
        return model;
    }
    
    @Override
    protected PostModel mapPostModel(JSONObject object, String boardName) {
        PostModel model = super.mapPostModel(object, boardName);
        try {
            model.comment = FastHtmlTagParser.getPTagParser().replace(model.comment, QUOTE_REPLACER);
            model.comment = FastHtmlTagParser.getPTagParser().replace(model.comment, PARAGRAPH_REPLACER);
            model.comment = model.comment.replaceAll("<br />$", "");
        } catch (Exception e) {}
        return model;
    }
    
    @Override
    protected AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        AttachmentModel attachment = super.mapAttachment(object, boardName, isSpoiler);
        String ext = object.optString("ext", "");
        String thumbnail_ext = ext;
        if (attachment != null) {
            String tim = object.optString("tim", "");
            String thumbLocation = tim.length() == 64 ? "/file_store/thumb/" : "/" + boardName + "/thumb/";
            String fileLocation = tim.length() == 64 ? "/file_store/" : "/" + boardName + "/src/";
            if (tim.length() > 0) {
                if(tim.length()!=64 || (tim.length() == 64 && attachment.type == AttachmentModel.TYPE_VIDEO)){
                    thumbnail_ext = ".jpg";
                }
                attachment.thumbnail = isSpoiler || attachment.type == AttachmentModel.TYPE_AUDIO ? null :
                        (thumbLocation + tim + thumbnail_ext);
                attachment.path = fileLocation + tim + ext;
                if (getUsingDomain().equals(DEFAULT_DOMAIN)){
                    if (attachment.thumbnail != null) {
                        attachment.thumbnail = fixRelativeUrl(attachment.thumbnail).replaceFirst(DEFAULT_DOMAIN, MEDIA_DOMAIN);
                    }
                    attachment.path = fixRelativeUrl(attachment.path).replaceFirst(DEFAULT_DOMAIN, MEDIA_DOMAIN);
                }
                return attachment;
            }
        }
        return attachment;
    }

    @Override
    public void downloadFile(String url, OutputStream out, ProgressListener listener, CancellableTask task) throws Exception {
        String fixedUrl = fixRelativeUrl(url);
        try {
            super.downloadFile(fixedUrl, out, listener, task);
        } catch (HttpWrongStatusCodeException e) {
            if (e.getStatusCode() == 404 && (url.contains("/file_store/") || url.contains("/src/"))) {
                switch (Uri.parse(fixedUrl).getHost()) {
                    case MEDIA_DOMAIN:
                        downloadFile(fixedUrl.replaceFirst(MEDIA_DOMAIN, MEDIA2_DOMAIN), out, listener, task);
                        break;
                    case MEDIA2_DOMAIN:
                        downloadFile(fixedUrl.replaceFirst(MEDIA2_DOMAIN, DEFAULT_DOMAIN), out, listener, task);
                        break;
                    default:
                        throw e;
                }
            } else {
                throw e;
            }
        }
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        try {
            return super.getThreadsList(boardName, page, listener, task, oldList);
        } catch (JSONException e) {
            if (page >= 3) throw new Exception("Back pages are disabled. Use the catalog to find threads on pages greater than 3.", e);
            throw e;
        }
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        needNewThreadCaptcha = threadNumber == null ?
                boardsThreadCaptcha.contains(boardName) :
                    boardsPostCaptcha.contains(boardName);
        if (needTorCaptcha) {
            String url = getUsingUrl() + "dnsbls_bypass.php";
            String response =
                    HttpStreamer.getInstance().getStringFromUrl(url, HttpRequestModel.DEFAULT_GET, httpClient, listener, task, false);
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
        if (needNewThreadCaptcha) {
            String url = getUsingUrl() + "8chan-captcha/entrypoint.php?mode=get&extra=abcdefghijklmnopqrstuvwxyz&nojs=true";
            HttpRequestModel request = HttpRequestModel.builder().setGET()
                    .setCustomHeaders(new Header[] { new BasicHeader(HttpHeaders.CACHE_CONTROL, "max-age=0") }).build();
            String response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
            Matcher base64Matcher = CAPTCHA_BASE64.matcher(response);
            Matcher captchaIdMatcher = CAPTCHA_ID.matcher(response);
            if (base64Matcher.find() && captchaIdMatcher.find()) {
                byte[] bitmap = Base64.decode(base64Matcher.group(1), Base64.DEFAULT);
                newThreadCaptchaId = captchaIdMatcher.group(1);
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
            HttpRequestModel rqModel = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setTimeout(30000).build();
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
        if (needTorCaptcha) {
            checkCaptcha(model.captchaAnswer, task);
            if (needNewThreadCaptcha) {
                throw new Exception("DNSBL passed successfully. Please complete CAPTCHA to make your post."); 
            }
        }
        if (task != null && task.isCancelled()) throw new InterruptedException("interrupted");
        String url;
        if (DEFAULT_DOMAIN.equals(getUsingDomain())) {
            url = (useHttps() ? "https://" : "http://") + SYSTEM_DOMAIN + "/post.php";
        } else {
            url = getUsingUrl() + "post.php";
        }
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("name", model.name).
                addString("email", model.sage ? "sage" : model.email).
                addString("subject", model.subject).
                addString("body", model.comment).
                addString("post", model.threadNumber == null ? "New Topic" : "New Reply").
                addString("board", model.boardName);
        if (model.threadNumber != null) postEntityBuilder.addString("thread", model.threadNumber);
        if (model.custommark) postEntityBuilder.addString("spoiler", "on");
        postEntityBuilder.addString("password", TextUtils.isEmpty(model.password) ? getDefaultPassword() : model.password);
        if (model.attachments != null) {
            String[] images = new String[] { "file", "file2", "file3", "file4", "file5" };
            for (int i=0; i<model.attachments.length; ++i) {
                postEntityBuilder.addFile(images[i], model.attachments[i], model.randomHash);
            }
        }
        if (needNewThreadCaptcha) {
            postEntityBuilder.addString("captcha_text", model.captchaAnswer).addString("captcha_cookie", newThreadCaptchaId);
        }
        
        UrlPageModel refererPage = new UrlPageModel();
        refererPage.chanName = getChanName();
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
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (htmlResponse.contains("dnsbls_bypass.php")) {
                    needTorCaptcha = true;
                    throw new Exception("Please complete your CAPTCHA. (Bypass DNSBL)");
                } else if (htmlResponse.contains("<div class=\"ban\">")) {
                    String error = "You are banned! ;_;";
                    Matcher banReasonMatcher = BAN_REASON_PATTERN.matcher(htmlResponse);
                    if (banReasonMatcher.find()) {
                        error += "\nReason: " + banReasonMatcher.group(1);
                    }
                    throw new Exception(error);
                }
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
                    throw new Exception("Please complete your CAPTCHA. (Bypass DNSBL)");
                } else if (htmlResponse.contains("/entrypoint")) {
                    boardsThreadCaptcha.add(model.boardName);
                    if (model.threadNumber != null) {
                        boardsPostCaptcha.add(model.boardName);
                    }
                    throw new Exception(needNewThreadCaptcha ?
                            "You seem to have mistyped the verification, or your CAPTCHA expired. Please fill it out again." :
                                "Please complete your CAPTCHA.");
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
        refererPage.chanName = getChanName();
        refererPage.boardName = model.boardName;
        refererPage.threadNumber = model.threadNumber;
        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, buildUrl(refererPage)) };
        HttpRequestModel rqModel = HttpRequestModel.builder().
                setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setCustomHeaders(customHeaders).setNoRedirect(true).build();
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
                            throw new Exception(resources.getString(R.string.infinity_tor_message)); //? Tor users cannot into deleting
                        throw new Exception(error);
                    }
                }
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
    }
    
}
