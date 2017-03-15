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

package nya.miku.wishmaster.api;

import java.io.OutputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceGroup;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.util.LazyPreferences;
import nya.miku.wishmaster.http.cloudflare.CloudflareException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public abstract class CloudflareChanModule extends AbstractChanModule {
    
    protected static final String PREF_KEY_CLOUDFLARE_RECAPTCHA_FALLBACK = "PREF_KEY_CLOUDFLARE_RECAPTCHA_FALLBACK";
    
    protected static final String PREF_KEY_CLOUDFLARE_COOKIE_VALUE = "PREF_KEY_CLOUDFLARE_COOKIE";
    protected static final String PREF_KEY_CLOUDFLARE_COOKIE_DOMAIN = "PREF_KEY_CLOUDFLARE_COOKIE_DOMAIN";
    
    protected static final String CLOUDFLARE_COOKIE_NAME = "cf_clearance";
    
    public CloudflareChanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    protected boolean canCloudflare() {
        return true;
    }
    
    protected String getCloudflareCookieDomain() {
        return preferences.getString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE_DOMAIN), null);
    }
    
    @Override
    protected void initHttpClient() {
        if (canCloudflare()) {
            String cloudflareCookieValue = preferences.getString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE_VALUE), null);
            String cloudflareCookieDomain = getCloudflareCookieDomain();
            if (cloudflareCookieValue != null && cloudflareCookieDomain != null) {
                BasicClientCookie c = new BasicClientCookie(CLOUDFLARE_COOKIE_NAME, cloudflareCookieValue);
                c.setDomain(cloudflareCookieDomain);
                httpClient.getCookieStore().addCookie(c);
            }
        }
    }
    
    @Override
    public void saveCookie(Cookie cookie) {
        super.saveCookie(cookie);
        if (cookie != null) {
            if (canCloudflare() && cookie.getName().equals(CLOUDFLARE_COOKIE_NAME)) {
                preferences.edit().
                        putString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE_VALUE), cookie.getValue()).
                        putString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE_DOMAIN), cookie.getDomain()).commit();
            }
        }
    }
    
    protected void checkCloudflareError(HttpWrongStatusCodeException e, String url) throws CloudflareException {
        if (e.getStatusCode() == 403) {
            String html = e.getHtmlString();
            if (html != null && html.contains("CAPTCHA")) {
                throw CloudflareException.withRecaptcha(url, getChanName(), html, cloudflareRecaptchaFallback());
            }
        } else if (e.getStatusCode() == 503) {
            String html = e.getHtmlString();
            if (html != null && html.contains("Checking your browser")) {
                throw CloudflareException.antiDDOS(url, getChanName());
            }
        }
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        super.addPreferencesOnScreen(preferenceGroup);
        addCloudflareRecaptchaFallbackPreference(preferenceGroup);
        
    }
    
    protected void addCloudflareRecaptchaFallbackPreference(PreferenceGroup preferenceGroup) {
        if (canCloudflare()) {
            Context context = preferenceGroup.getContext();
            CheckBoxPreference fallbackPref = new LazyPreferences.CheckBoxPreference(context);
            fallbackPref.setTitle(R.string.pref_cf_recaptcha_fallback);
            fallbackPref.setSummary(R.string.pref_cf_recaptcha_fallback_summary);
            fallbackPref.setKey(getSharedKey(PREF_KEY_CLOUDFLARE_RECAPTCHA_FALLBACK));
            fallbackPref.setDefaultValue(false);
            preferenceGroup.addPreference(fallbackPref);
        }
    }
    
    protected boolean cloudflareRecaptchaFallback() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_CLOUDFLARE_RECAPTCHA_FALLBACK), false);
    }
    
    @Override
    public void downloadFile(String url, OutputStream out, ProgressListener listener, CancellableTask task) throws Exception {
        String fixedUrl = fixRelativeUrl(url);
        try {
            HttpRequestModel rqModel = HttpRequestModel.DEFAULT_GET;
            HttpStreamer.getInstance().downloadFileFromUrl(fixedUrl, out, rqModel, httpClient, listener, task, canCloudflare());
        } catch (HttpWrongStatusCodeException e) {
            if (canCloudflare()) checkCloudflareError(e, fixedUrl);
            throw e;
        }
    }
    
    @Override
    protected JSONObject downloadJSONObject(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        try {
            HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
            JSONObject object = HttpStreamer.getInstance().getJSONObjectFromUrl(url, rqModel, httpClient, listener, task, canCloudflare());
            if (task != null && task.isCancelled()) throw new Exception("interrupted");
            if (listener != null) listener.setIndeterminate();
            return object;
        } catch (HttpWrongStatusCodeException e) {
            if (canCloudflare()) checkCloudflareError(e, url);
            throw e;
        }
    }
    
    @Override
    protected JSONArray downloadJSONArray(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        try {
            HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
            JSONArray array = HttpStreamer.getInstance().getJSONArrayFromUrl(url, rqModel, httpClient, listener, task, canCloudflare());
            if (task != null && task.isCancelled()) throw new Exception("interrupted");
            if (listener != null) listener.setIndeterminate();
            return array;
        } catch (HttpWrongStatusCodeException e) {
            if (canCloudflare()) checkCloudflareError(e, url);
            throw e;
        }
    }
    
}
