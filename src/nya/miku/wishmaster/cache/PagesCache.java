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

import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.ui.presentation.PresentationModel;
import android.support.v4.util.LruCache;

/**
 * Кэш страниц, для хранения сериализованных страниц с чана (в файлах) и готовых к показу объектов PresentationModel (только в памяти)
 * @author miku-nyan
 *
 */
public class PagesCache {
    private static final String TAG = "PagesCache";
    
    private final Serializer serializer;
    private final LruCache<String, PresentationModel> lru;
    
    /**
     * Конструктор
     * @param maxSize максимальный размер кэша в памяти (определение размера см. {@link PresentationModel#getSerializablePageSize})
     * @param serizlizer сериализатор
     */
    public PagesCache(int maxSize, Serializer serializer) {
        this.serializer = serializer;
        this.lru = new LruCache<String, PresentationModel>(maxSize) {
            @Override
            protected int sizeOf(String key, PresentationModel value) {
                return value.size;
            }
        };
    }
    
    /**
     * Очистить LRU-кэш в памяти.
     * Вызывать при смене темы или других параметров отображения презентационных моделей (или просто в случае нехватки памяти)
     */
    public void clearLru() {
        lru.evictAll();
    }
    
    /**
     * Попытаться получить презентационную модель из кэша в памяти
     * @param hash хэш страницы
     * @return презентационная модель или null, если таковая отсутствует в кэше в памяти
     */
    public PresentationModel getPresentationModel(String hash) {
        return lru.get(hash);
    }
    
    /**
     * Положить презентационную модель в LRU-кэш в памяти, а также соответствующую сериализованную модель {@link SerializablePage} в файловый кэш.
     * @param hash хэш страницы
     * @param model презентационная модель
     */
    public void putPresentationModel(String hash, PresentationModel model) {
        putPresentationModel(hash, model, true);
    }
    
    /**
     * Положить презентационную модель в LRU-кэш в памяти
     * @param hash хэш страницы
     * @param model презентационная модель
     * @param putToFileCache положить также соответствующую сериализованную модель {@link SerializablePage} в файловый кэш
     */
    public void putPresentationModel(String hash, PresentationModel model, boolean putToFileCache) {
        if (hash == null || model == null) {
            if (hash == null) Logger.e(TAG, "received null hash");
            if (model == null) Logger.e(TAG, "received null object for hash: "+hash);
            return;
        }
        lru.put(hash, model);
        if (putToFileCache) {
            putSerializablePage(hash, model.source);
        }
    }
    
    /**
     * Попытаться получить сериализованную модель страницы из кэша в памяти или файлового кэша. 
     * @param hash хэш страницы
     * @return сериализованная модель или null, если таковая отсутствует и в LRU-кэше в памяти, и в файловом кэше
     */
    public SerializablePage getSerializablePage(String hash) {
        PresentationModel fromLRU = getPresentationModel(hash);
        if (fromLRU != null) return fromLRU.source;
        return serializer.deserializePage(hash);
    }
    
    /**
     * Положить сериализованную модель страницы в файловый кэш.
     * @param hash хэш страницы
     * @param page сериализованная модель
     */
    public void putSerializablePage(String hash, SerializablePage page) {
        serializer.serializePage(hash, page);
    }
    
    /**
     * Попытаться получить сериализованную модель списка досок из файлового кэша.
     * @param hash хэш
     * @return сериализованная модель или null, если таковая отсутствует в файловом кэше
     */
    public SerializableBoardsList getSerializableBoardsList(String hash) {
        return serializer.deserializeBoardsList(hash);
    }
    
    /**
     * Положить сериализованную модель списка досок в файловый кэш.
     * @param hash хэш
     * @param boardsList сериализованная модель
     */
    public void putSerializableBoardsList(String hash, SerializableBoardsList boardsList) {
        serializer.serializeBoardsList(hash, boardsList);
    }

}
