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

package nya.miku.wishmaster.api.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.cache.SerializablePage;

public class ChanModels {
    private static final String NULL_KEY = null;
    private static final String SEPARATOR = "---";
    
    /**
     * Рассчитать хэш для модели адреса страницы
     * @param model модель
     * @return строка хэша
     * @throws IllegalArgumentException если данная модель не является адресом страницы АИБ с постами или тредами
     * (т.е. страница не представима в {@link SerializablePage})
     */
    public static String hashUrlPageModel(UrlPageModel model) throws IllegalArgumentException {
        if (model.type == UrlPageModel.TYPE_OTHERPAGE) {
            throw new IllegalArgumentException("cannot compute hash for non-board page");
        }
        StringBuilder key = new StringBuilder(model.chanName);
        if (model.type != UrlPageModel.TYPE_INDEXPAGE) {
            key.append(SEPARATOR).append(model.boardName).append(SEPARATOR);
        }
        switch (model.type) {
            case UrlPageModel.TYPE_BOARDPAGE:
                key.append("boardPage").append(model.boardPage);
                break;
            case UrlPageModel.TYPE_CATALOGPAGE:
                key.append("catalogType").append(model.catalogType);
                break;
            case UrlPageModel.TYPE_THREADPAGE:
                key.append("threadNumber").append(model.threadNumber);
                break;
            case UrlPageModel.TYPE_SEARCHPAGE:
                key.append("searchRequest").append(model.searchRequest).append(SEPARATOR).append(model.boardPage);
                break;
        }
        return CryptoUtils.computeMD5(key.toString());
    }
    
    /**
     * Рассчитать хэш для модели поста. Параметр {@link PostModel#deleted} не влияет на значение контрольной суммы.
     * @param m модель
     * @return контрольная сумма (хэш), целое число
     */
    public static int hashPostModel(PostModel m) {
        int hash = 1;
        for (Object o : new Object[] { m.number, m.name, m.subject, m.comment, m.email, m.trip, m.op, m.sage, m.timestamp, m.parentThread })
            hash = hash*31 + (o==null ? 0 : o.hashCode());
        
        if (m.icons != null)
            for (BadgeIconModel icon : m.icons)
                for (Object o : new Object[] { icon.source, icon.description })
                    hash = hash*31 + (o==null ? 0 : o.hashCode());
        
        if (m.attachments != null)
            for (AttachmentModel attach : m.attachments)
                for (Object o : new Object[] { attach.thumbnail, attach.path, attach.originalName, attach.type, attach.size, attach.isSpoiler })
                    hash = hash*31 + (o==null ? 0 : o.hashCode());
        
        if (m.color != 0) hash = hash*31 + m.color;
        
        return hash;
    }
    
    /**
     * Определить примерный (может отличаться на разных платформах) размер модели поста (объекта {@link PostModel}) в памяти (Heap) в байтах
     * @param model модель 
     * @return размер в байтах
     */
    public static int getPostModelSize(PostModel model) {
        int size = 64;
        for (String s : new String[] { model.number, model.name, model.subject, model.comment, model.email, model.trip, model.parentThread })
            if (s != null) size += (40 + (s.length() * 2));
        if (model.attachments != null) {
            size += (12 + (model.attachments.length * 4));
            for (AttachmentModel attachment : model.attachments)
                if (attachment != null) {
                    size += 48;
                    if (attachment.thumbnail != null)
                        size += (40 + (attachment.thumbnail.length() * 2));
                    if (attachment.path != null)
                        size += (40 + (attachment.path.length() * 2));
                    if (attachment.originalName != null)
                        size += (40 + (attachment.originalName.length() * 2));
                }
        }
        if (model.icons != null) {
            size += (12 + (model.icons.length * 4));
            for (BadgeIconModel icon : model.icons)
                if (icon != null) {
                    size += 16;
                    if (icon.source != null)
                        size += (40 + (icon.source.length() * 2));
                    if (icon.description != null)
                        size += (40 + (icon.description.length() * 2));
                }
        }
        return size;
    }
    
    /**
     * Рассчитать хэш для модели вложения. Параметр {@link AttachmentModel#isSpoiler} не учитывается.
     * @param model модель
     * @return строка хэша
     */
    public static String hashAttachmentModel(AttachmentModel model) {
        StringBuilder key = new StringBuilder();
        for (String s : new String[] { model.thumbnail, model.path, model.originalName })
            key.append(s).append(SEPARATOR);
        key.append(model.type).append(SEPARATOR).append(model.size);
        return CryptoUtils.computeMD5(key.toString());
    }
    
    /**
     * Рассчитать хэш для модели иконки бэйджа (флаг страны, политические предпочтения и т.д.)
     * @param model модель
     * @param chanName название АИБ (модуля чана)
     * @return строка хэша
     */
    public static String hashBadgeIconModel(BadgeIconModel model, String chanName) {
        StringBuilder key = new StringBuilder();
        for (String s : new String[] { chanName, model.source, model.description })
            key.append(s).append(SEPARATOR);
        
        return CryptoUtils.computeMD5(key.toString());
    }
    
    /**
     * Создать упрощённую модель доски ({@link SimpleBoardModel}
     * @param chan название имиджборды
     * @param boardName код доски (короткое название)
     * @param description описание доски (полное название)
     * @param category категория, к которой относится доска (в случае деления досок по категориям), может принимать null.
     * @param nsfw должно принимать true, если на доске содержится контент 18+ или доска является немодерируемой
     */
    public static SimpleBoardModel obtainSimpleBoardModel(String chan, String boardName, String description, String category, boolean nsfw) {
        SimpleBoardModel model = new SimpleBoardModel();
        model.chan = chan;
        model.boardName = boardName;
        model.boardDescription = description;
        model.boardCategory = category;
        model.nsfw = nsfw;
        return model;
    }
    
    /**
     * Слияние списков постов, с сохранением удалённых постов (которые присутствовали в старом списке, но отсутствуют в новом).<br>
     * Сложность O(N) в среднем
     * @param oldList старый список постов
     * @param newList новый список постов
     * @return полученный массив
     */
    public static PostModel[] mergePostsLists(List<PostModel> oldList, List<PostModel> newList) {
        Set<String> newListNumbers;
        if (postNumbersSet(oldList) == null || (newListNumbers = postNumbersSet(newList)) == null)
            return newList.toArray(new PostModel[newList.size()]);
        
        Map<String, PostModel> skipped = new HashMap<String, PostModel>();
        for (int i=0; i<oldList.size(); ++i)
            if (!newListNumbers.contains(oldList.get(i).number))
                skipped.put(i>0 ? oldList.get(i-1).number : NULL_KEY, oldList.get(i));
        
        if (skipped.isEmpty()) return newList.toArray(new PostModel[newList.size()]);
        
        List<PostModel> result = new ArrayList<PostModel>(newList.size() + skipped.size());
        if (skipped.containsKey(NULL_KEY)) {
            PostModel toResult = skipped.get(NULL_KEY);
            toResult.deleted = true;
            result.add(toResult);
            while (skipped.containsKey(toResult.number)) {
                toResult = skipped.get(toResult.number);
                toResult.deleted = true;
                result.add(toResult);
            }
        }
        for (int i=0; i<newList.size(); ++i) {
            PostModel toResult = newList.get(i);
            result.add(toResult);
            while (skipped.containsKey(toResult.number)) {
                toResult = skipped.get(toResult.number);
                toResult.deleted = true;
                result.add(toResult);
            }
        }
        return result.toArray(new PostModel[result.size()]);
    }
    
    /**
     * Создать множество идентификаторов ({@link PostModel#number}) постов.
     * Используется также для проверки наличия дубликатов идентификаторов, если метод вернёт null
     * @param source коллекция с исходными постами
     * @return созданное множество или null, если присутствуют дубликаты по значению {@link PostModel#number}
     * или элементы со значением {@link PostModel#number}, равным null
     */
    private static Set<String> postNumbersSet(Collection<PostModel> source) {
        Set<String> result = new HashSet<String>(Math.max((int) (source.size()/.75f) + 1, 16), .75f);
        for (PostModel post : source) {
            if (post.number == null || result.contains(post.number)) return null;
            result.add(post.number);
        }
        return result;
    }
    
}
