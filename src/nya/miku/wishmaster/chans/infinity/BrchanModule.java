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
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class BrchanModule extends InfinityModule {
    private static final String CHAN_NAME = "brchan.org";
    private static final String DEFAULT_DOMAIN = "brchan.org";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN };
    
    public BrchanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "BRChan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_cirno, null);
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
        if (model.attachmentsMaxCount > 0) model.attachmentsMaxCount = 3;
        model.timeZoneId = "Brazil/East";
        return model;
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
                addString("post", model.threadNumber == null ? "Novo tópico" : "Responder").
                addString("board", model.boardName);
        if (model.threadNumber != null) postEntityBuilder.addString("thread", model.threadNumber);
        if (model.custommark) postEntityBuilder.addString("spoiler", "on");
        postEntityBuilder.addString("password", TextUtils.isEmpty(model.password) ? getDefaultPassword() : model.password).
                addString("json_response", "1");
        if (model.attachments != null) {
            String[] images = new String[] { "file", "file2", "file3" };
            for (int i=0; i<model.attachments.length; ++i) {
                postEntityBuilder.addFile(images[i], model.attachments[i], model.randomHash);
            }
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
        JSONObject json = HttpStreamer.getInstance().getJSONObjectFromUrl(url, request, httpClient, listener, task, false);
        if (json.has("error")) {
            String error = json.optString("error");
            if (error.equals("true") && json.optBoolean("banned")) throw new Exception("You are banned! ;_;");
            throw new Exception(error);
        } else {
            String redirect = json.optString("redirect", "");
            if (redirect.length() > 0) return fixRelativeUrl(redirect);
            return null;
        }
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        return super.parseUrl(url.replaceAll("\\+\\d+.html", ".html"));
    }
    
}
