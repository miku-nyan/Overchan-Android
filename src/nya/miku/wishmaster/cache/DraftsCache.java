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

package nya.miku.wishmaster.cache;

import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import android.support.v4.util.LruCache;

/**
 * Кэш для черновиков (неотправленных постов).<br>
 * Для хранения моделей черновиков {@link SendPostModel} (двухуровневый LRU-кэш) и для хранения последней (используемой) капчи (только в памяти)
 * @author miku-nyan
 *
 */
public class DraftsCache {
    private final Serializer serializer;
    private final LruCache<String, SendPostModel> lru;
    
    /**
     * Конструктор
     * @param maxSize максимальный размер кэша в памяти, измеряется числом моделей черновиков
     * @param serizlizer сериализатор
     */
    public DraftsCache(int maxSize, Serializer serializer) {
        this.serializer = serializer;
        this.lru = new LruCache<String, SendPostModel>(maxSize);
    }
    
    /**
     * Очистить LRU-кэш в памяти. Вызывать в случае нехватки памяти
     */
    public void clearLru() {
        lru.evictAll();
    }
    
    /**
     * Положить модель в кэш
     * @param hash хэш страницы
     * @param model модель черновика
     */
    public void put(String hash, SendPostModel model) {
        lru.put(hash, model);
        serializer.serializeDraft(hash, model);
    }
    
    /**
     * Получить модель черновика из кэша
     * @param hash хэш страницы
     * @return модель черновика или null, если отсутствует в кэше
     */
    public SendPostModel get(String hash) {
        SendPostModel fromLru = lru.get(hash);
        if (fromLru != null) return fromLru;
        return serializer.deserializeDraft(hash);
    }
    
    /**
     * Удалить модель черновика из кэша
     * @param hash хэш страницы
     */
    public void remove(String hash) {
        lru.remove(hash);
        serializer.removeDraft(hash);
    }
    
    
    private String lastCaptchaHash;
    private CaptchaModel lastCaptcha;
    
    /**
     * Установить последнюю используемую капчу
     * @param hash хэш страницы
     * @param captcha модель капчи
     */
    public void setLastCaptcha(String hash, CaptchaModel captcha) {
        lastCaptchaHash = hash;
        lastCaptcha = captcha;
    }
    
    /**
     * Удалить информацию о последней используемой капче
     */
    public void clearLastCaptcha() {
        lastCaptchaHash = null;
        lastCaptcha = null;
    }
    
    /**
     * Получить модель последней используемой капчи
     */
    public CaptchaModel getLastCaptcha() {
        return lastCaptcha;
    }
    
    /**
     * Получить хэш страницы, для которой предназначена последняя используемая капча
     */
    public String getLastCaptchaHash() {
        return lastCaptchaHash;
    }
}
