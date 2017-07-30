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

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.util.TextUtils;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.base64.Base64;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class LolifoxModule extends InfinityModule {
    private static final String CHAN_NAME = "lolifox.org";
    private static final String DEFAULT_DOMAIN = "lolifox.org";
    private static final String[] DOMAINS = new String[]{DEFAULT_DOMAIN};

    private static final Pattern PROTECTED_URL_PATTERN = Pattern.compile("<a[^>]*href=\"https?://privatelink.de/\\?([^\"]*)\"[^>]*>");
    private static final Pattern CAPTCHA_BASE64 = Pattern.compile("data:image/png;base64,([^\"]+)\"");

    public LolifoxModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }

    @Override
    public String getChanName() {
        return CHAN_NAME;
    }

    @Override
    public String getDisplayingName() {
        return "LOLIFOX";
    }

    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_lolifox, null);
    }

    @Override
    protected String getUsingDomain() {
        return DEFAULT_DOMAIN;
    }

    @Override
    protected String getCloudflareCookieDomain() {
        return DEFAULT_DOMAIN;
    }

    @Override
    protected String[] getAllDomains() {
        return DOMAINS;
    }

    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addPasswordPreference(preferenceGroup);
        addHttpsPreference(preferenceGroup, true);
        addCloudflareRecaptchaFallbackPreference(preferenceGroup);
        addProxyPreferences(preferenceGroup);
    }

    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        if (model.attachmentsMaxCount > 0) model.attachmentsMaxCount = 4;
        model.timeZoneId = "Brazil/East";
        model.markType = BoardModel.MARK_BBCODE;
        return model;
    }

    @Override
    protected PostModel mapPostModel(JSONObject object, String boardName) {
        PostModel model = super.mapPostModel(object, boardName);
        model.comment = RegexUtils.replaceAll(model.comment, PROTECTED_URL_PATTERN, "<a href=\"$1\">");
        return model;
    }

    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        needNewThreadCaptcha = threadNumber == null ?
                boardsThreadCaptcha.contains(boardName) :
                boardsPostCaptcha.contains(boardName);
        if (needNewThreadCaptcha) {
            String url = getUsingUrl() + "captcha.php?board=" + boardName;
            HttpRequestModel request = HttpRequestModel.builder().setGET().
                    setCustomHeaders(new Header[]{new BasicHeader(HttpHeaders.CACHE_CONTROL, "max-age=0")}).build();
            String response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
            Matcher base64Matcher = CAPTCHA_BASE64.matcher(response);
            if (base64Matcher.find()) {
                byte[] bitmap = Base64.decode(base64Matcher.group(1), Base64.DEFAULT);
                CaptchaModel captcha = new CaptchaModel();
                captcha.type = CaptchaModel.TYPE_NORMAL;
                captcha.bitmap = BitmapFactory.decodeByteArray(bitmap, 0, bitmap.length);
                return captcha;
            }
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
                addString("post", "Ответ"). // Этот параметр можно не отправлять.
                addString("board", model.boardName);
        if (model.threadNumber != null) postEntityBuilder.addString("thread", model.threadNumber);
        if (model.custommark) postEntityBuilder.addString("spoiler", "on");
        postEntityBuilder.addString("password", TextUtils.isEmpty(model.password) ? getDefaultPassword() : model.password).
                addString("json_response", "1");
        if (model.attachments != null) {
            String[] images = new String[]{"file", "file2", "file3", "file4"};
            for (int i = 0; i < model.attachments.length; ++i) {
                postEntityBuilder.addFile(images[i], model.attachments[i], model.randomHash);
            }
        }
        if (needNewThreadCaptcha) {
            postEntityBuilder.addString("captcha_text", model.captchaAnswer);
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
        Header[] customHeaders = new Header[]{new BasicHeader(HttpHeaders.REFERER, buildUrl(refererPage))};
        HttpRequestModel request =
                HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setCustomHeaders(customHeaders).setNoRedirect(true).build();
        JSONObject json = HttpStreamer.getInstance().getJSONObjectFromUrl(url, request, httpClient, listener, task, false);
        if (json.has("error")) {
            String error = json.optString("error");
            if (error.equals("true") && json.optBoolean("banned"))
                throw new Exception("You are banned! ;_;");
            throw new Exception(error);
        } else {
            String redirect = json.optString("redirect", "");
            if (redirect.length() > 0) return fixRelativeUrl(redirect);
            return null;
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
        pairs.add(new BasicNameValuePair("delete", "Apagar"));
        pairs.add(new BasicNameValuePair("reason", ""));
        pairs.add(new BasicNameValuePair("json_response", "1"));

        UrlPageModel refererPage = new UrlPageModel();
        refererPage.type = UrlPageModel.TYPE_THREADPAGE;
        refererPage.chanName = getChanName();
        refererPage.boardName = model.boardName;
        refererPage.threadNumber = model.threadNumber;
        Header[] customHeaders = new Header[]{new BasicHeader(HttpHeaders.REFERER, buildUrl(refererPage))};
        HttpRequestModel request = HttpRequestModel.builder().
                setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setCustomHeaders(customHeaders).setNoRedirect(true).build();
        JSONObject jsonResponse = HttpStreamer.getInstance().getJSONObjectFromUrl(url, request, httpClient, listener, task, false);
        String error = jsonResponse.optString("error");
        if (error.length() > 0) throw new Exception(error);
        return null;
    }

    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        return super.parseUrl(url.replaceAll("\\+\\w+.html", ".html"));
    }

}
