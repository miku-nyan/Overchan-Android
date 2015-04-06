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

package nya.miku.wishmaster.http.cloudflare;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.MainApplication;
import android.app.Activity;

/**
 * Исключение, вызванное запросом проверки Cloudflare
 * @author miku-nyan
 *
 */
public class CloudflareException extends InteractiveException {
    private static final long serialVersionUID = 1L;
    
    private static final String SERVICE_NAME = "Cloudflare";
    
    private boolean recaptcha;
    private String url;
    private String publicKey;
    private String checkCaptchaUrlFormat;
    private String cfCookieName;
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
     * @param chanName название модуля чана
     * @return созданный объект
     */
    public static CloudflareException antiDDOS(String url, String cfCookieName, String chanName) {
        CloudflareException e = new CloudflareException();
        e.url = url;
        e.recaptcha = false;
        e.publicKey = null;
        e.checkCaptchaUrlFormat = null;
        e.cfCookieName = cfCookieName;
        e.chanName = chanName;
        return e;
    }
    
    /**
     * Создать новый экземпляр cloudflare-исключения (проверка с рекапчей).
     * @param publicKey открытый ключ рекапчи
     * @param checkUrlFormat строка-формат URL для проверки капчи (первый %s - challenge, второй %s - ответ на капчу)
     * @param cfCookieName название cloudflare-куки
     * @param chanName название модуля чана
     * @return созданный объект
     */
    public static CloudflareException withRecaptcha(String publicKey, String checkUrlFormat, String cfCookieName, String chanName) {
        CloudflareException e = new CloudflareException();
        e.url = null;
        e.recaptcha = true;
        e.publicKey = publicKey;
        e.checkCaptchaUrlFormat = checkUrlFormat;
        e.cfCookieName = cfCookieName;
        e.chanName = chanName;
        return e;
    }
    
    /**
     * определить тип проверки (рекапча или обычная anti-ddos)
     * @return true, если проверка с рекапчей
     */
    public boolean isRecaptcha() {
        return recaptcha;
    }
    
    /**
     * получить url, по которому была вызвана проверка
     * @return url
     */
    public String getCheckUrl() {
        return url;
    }
    
    /**
     * получить открытый ключ рекапчи
     * @return открытый ключ
     */
    public String getRecaptchaPublicKey() {
        return publicKey;
    }
    
    /**
     * получить строку-формат URL для проверки рекапчи
     * @return формат (первый %s - challenge, второй %s - ответ на капчу)
     */
    public String getCheckCaptchaUrlFormat() {
        return checkCaptchaUrlFormat;
    }
    
    /**
     * получить название cloudflare-куки, которую необходимо получить
     * @return название cookie
     */
    public String getRequiredCookieName() {
        return cfCookieName;
    }
    
    @Override
    public void handle(Activity activity, CancellableTask task, Callback callback) {
        CloudflareUIHandler.handleCloudflare(this, MainApplication.getInstance().getChanModule(chanName), activity, task, callback);
    }
}
