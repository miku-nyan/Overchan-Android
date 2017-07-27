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

package nya.miku.wishmaster.http.cloudflare;

import nya.miku.wishmaster.api.HttpChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.interactive.InteractiveException;

import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;

/**
 * Исключение, вызванное запросом проверки Cloudflare
 * @author miku-nyan
 *
 */
public class CloudflareException extends InteractiveException {
    private static final long serialVersionUID = 1L;
    
    private static final String SERVICE_NAME = "Cloudflare";
    private static final String COOKIE_NAME = "cf_clearance";
    private static final String RECAPTCHA_KEY = "6LfBixYUAAAAABhdHynFUIMA_sa4s-XsJvnjtgB0";
    
    private static final Pattern PATTERN_STOKEN = Pattern.compile("stoken=\"?([^\"&]+)");
    private static final Pattern PATTERN_ID = Pattern.compile("data-ray=\"?([^\"&]+)");
    
    private boolean recaptcha;
    private String url;
    private String sToken;
    private boolean fallback;
    private String checkCaptchaUrlFormat;
    private String chanName;
    
    //для создания экземплятов используются статические методы 
    private CloudflareException() {}
    
    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }
    
    /**
     * Создать новый экземпляр cloudflare-исключения (обычная js-antiddos проверка, без рекапчи).
     * @param url адрес, по которому вызвана проверка
     * @param cfCookieName название cloudflare-куки
     * @param chanName название модуля чана (модуль должен имплементировать {@link HttpChanModule})
     * @return созданный объект
     */
    public static CloudflareException antiDDOS(String url, String chanName) {
        CloudflareException e = new CloudflareException();
        e.url = url;
        e.chanName = chanName;
        e.recaptcha = false;
        return e;
    }
    
    @Deprecated
    public static CloudflareException antiDDOS(String url, String cookie, String chanName) {
        return antiDDOS(url, chanName);
    }
    
    public static CloudflareException withRecaptcha(String url, String chanName, String sToken, String checkUrlFormat, boolean fallback) {
        CloudflareException e = new CloudflareException();
        e.url = url;
        e.recaptcha = true;
        e.sToken = sToken;
        e.checkCaptchaUrlFormat = checkUrlFormat;
        e.chanName = chanName;
        e.fallback = fallback;
        return e;
    }
    
    @Deprecated
    public static CloudflareException withRecaptcha(String key, String url, String cookie, String chanName) {
        return withRecaptcha(url, chanName, "", true);
    }
    
    /**
     * Создать новый экземпляр cloudflare-исключения (проверка с рекапчей).
     * @param url адрес, по которому вызвана проверка
     * @param chanName название модуля чана
     * @param htmlString строка с html-страницей, загрузившейся с запросом проверки
     * @param fallback использовать ли проверку в fallback-режиме (без js)
     * @return созданный объект
     */
    public static CloudflareException withRecaptcha(String url, String chanName, String htmlString, boolean fallback) {
        String token = null;
        Matcher m = PATTERN_STOKEN.matcher(htmlString);
        if (m.find()) token = m.group(1);
        
        String id = null;
        m = PATTERN_ID.matcher(htmlString);
        if (m.find()) id = m.group(1);
        
        try {
            URL baseUrl = new URL(url);
            url = baseUrl.getProtocol() + "://" + baseUrl.getHost() + "/";
        } catch (Exception e) {
            if (!url.endsWith("/")) url = url + "/";
        }
        String checkUrl = url + "cdn-cgi/l/chk_captcha?" + (id != null ? ("id=" + id + "&") : "") + "g-recaptcha-response=%s";
        return withRecaptcha(url, chanName, token, checkUrl, fallback);
    }
    
    /**
     * определить тип проверки (рекапча или обычная anti-ddos)
     * @return true, если проверка с рекапчей
     */
    /*package*/ boolean isRecaptcha() {
        return recaptcha;
    }
    
    /**
     * получить url, по которому была вызвана проверка
     * @return url
     */
    /*package*/ String getCheckUrl() {
        return url;
    }
    
    /**
     * получить открытый ключ рекапчи
     * @return открытый ключ
     */
    /*package*/ String getRecaptchaPublicKey() {
        return RECAPTCHA_KEY;
    }
    
    /**
     * получить secure token (data-stoken, stoken) для рекапчи
     */
    /*package*/ String getRecaptchaSecureToken() {
        return sToken;
    }
    
    /**
     * определяет, требуется ли использовать проверку recaptcha в fallback-режиме (без js)
     * @return true, если в режиме fallback
     */
    /*package*/ boolean isRecaptchaFallback() {
        return fallback;
    }
    
    /**
     * получить строку-формат URL для проверки рекапчи
     * @return формат (первый %s - challenge, второй %s - ответ на капчу)
     */
    /*package*/ String getCheckCaptchaUrlFormat() {
        return checkCaptchaUrlFormat;
    }
    
    /**
     * получить название cloudflare-куки, которую необходимо получить
     * @return название cookie
     */
    /*package*/ String getRequiredCookieName() {
        return COOKIE_NAME;
    }
    
    @Override
    public void handle(Activity activity, CancellableTask task, Callback callback) {
        CloudflareUIHandler.handleCloudflare(this, (HttpChanModule) MainApplication.getInstance().getChanModule(chanName), activity, task, callback);
    }
}
