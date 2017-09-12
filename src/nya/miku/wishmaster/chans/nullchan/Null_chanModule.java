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

package nya.miku.wishmaster.chans.nullchan;

import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.util.TextUtils;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.chans.infinity.InfinityModule;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.base64.Base64;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class Null_chanModule extends InfinityModule {
    private static final String CHAN_NAME = "0-chan.ru";
    private static final String DOMAIN = "0-chan.ru";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Нульчан", null, true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tmp", "Временное убежище", null, true)
    };
    
    private static final String[] ATTACHMENT_FORMATS = new String[] { "jpg", "jpeg", "gif", "png" };
    
    private static final Pattern CAPTCHA_BASE64 = Pattern.compile("data:image/png;base64,([^\"]+)\"");
    private static final Pattern ERROR_PATTERN = Pattern.compile("<h2 [^>]*>(.*?)</h2>");
    
    public Null_chanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Øчан (0-chan.ru)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_0chan_1, null);
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addProxyPreferences(preferenceGroup);
    }
    
    @Override
    protected boolean canCloudflare() {
        return false;
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN;
    }
    
    @Override
    protected String[] getAllDomains() {
        return new String[] { DOMAIN };
    }
    
    @Override
    protected boolean useHttps() {
        return false;
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        SimpleBoardModel simpleModel = getBoardsMap(listener, task).get(shortName);
        BoardModel board = new BoardModel();
        board.chan = getChanName();
        board.boardName = shortName;
        board.boardDescription = shortName;
        board.uniqueAttachmentNames = true;
        board.timeZoneId = "US/Eastern";
        board.defaultUserName = "Anonymous";
        board.readonlyBoard = false;
        board.requiredFileForNewThread = false;
        board.allowDeletePosts = false;
        board.allowNames = true;
        board.allowSubjects = true;
        board.allowSage = true;
        board.allowEmails = true;
        board.ignoreEmailIfSage = true;
        board.allowCustomMark = true;
        board.customMarkDescription = "Spoiler";
        board.allowRandomHash = true;
        board.allowIcons = false;
        board.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        board.markType = BoardModel.MARK_INFINITY;
        board.firstPage = 1;
        board.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        board.searchAllowed = false;
        board.catalogAllowed = true;
        if (simpleModel != null) {
            board.boardDescription = simpleModel.boardDescription;
            board.boardCategory = simpleModel.boardCategory;
            board.nsfw = simpleModel.nsfw;
        }
        board.bumpLimit = BoardModel.LAST_PAGE_UNDEFINED;
        board.attachmentsMaxCount = 1;
        return board;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "8chan-captcha/entrypoint.php?mode=get&extra=abcdefghijklmnopqrstuvwxyz";
        HttpRequestModel request = HttpRequestModel.builder().setGET()
                .setCustomHeaders(new Header[] { new BasicHeader(HttpHeaders.CACHE_CONTROL, "max-age=0") }).build();
        JSONObject jsonResponse = HttpStreamer.getInstance().getJSONObjectFromUrl(url, request, httpClient, listener, task, false);
        Matcher base64Matcher = CAPTCHA_BASE64.matcher(jsonResponse.optString("captchahtml"));
        if (jsonResponse.has("cookie") && base64Matcher.find()) {
            byte[] bitmap = Base64.decode(base64Matcher.group(1), Base64.DEFAULT);
            newThreadCaptchaId = jsonResponse.getString("cookie");
            CaptchaModel captcha = new CaptchaModel();
            captcha.type = CaptchaModel.TYPE_NORMAL;
            captcha.bitmap = BitmapFactory.decodeByteArray(bitmap, 0, bitmap.length);
            return captcha;
        }
        return null;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        if (task != null && task.isCancelled()) throw new InterruptedException("interrupted");
        String url = getUsingUrl() + "post.php";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("name", model.name).
                addString("email", model.sage ? "sage" : model.email).
                addString("subject", model.subject).
                addString("body", model.comment).
                addString("post", model.threadNumber == null ? "New Topic" : "New Reply").
                addString("board", model.boardName).
                addString("captcha_text", model.captchaAnswer).
                addString("captcha_cookie", newThreadCaptchaId).
                addString("json_response", "1");
        if (model.threadNumber != null) postEntityBuilder.addString("thread", model.threadNumber);
        if (model.custommark) postEntityBuilder.addString("spoiler", "on");
        postEntityBuilder.addString("password", TextUtils.isEmpty(model.password) ? getDefaultPassword() : model.password);
        if ((model.attachments != null) && (model.attachments.length > 0)) {
            postEntityBuilder.addFile("file", model.attachments[0], model.randomHash);
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
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (htmlResponse.contains("banned")) {
                    throw new Exception("You are banned! ;_;");
                } else if (htmlResponse.contains("/entrypoint")) {
                    throw new Exception("You seem to have mistyped the verification, or your CAPTCHA expired. Please fill it out again.");
                }
                JSONObject result = new JSONObject(htmlResponse);
                if (result.has("error")) {
                    throw new Exception(result.optString("error"));
                } else {
                    String redirect = result.optString("redirect");
                    if (redirect.length() > 0) return fixRelativeUrl(redirect);
                    return null;
                }
            } else if (response.statusCode == 400) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (htmlResponse.contains("<h1>Error</h1>")) {
                    Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                    if (errorMatcher.find()) {
                        String error = errorMatcher.group(1);
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
