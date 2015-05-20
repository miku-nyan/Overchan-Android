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

package nya.miku.wishmaster.api.util;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.res.Resources;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.cache.SerializablePage;
import nya.miku.wishmaster.common.CryptoUtils;

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
                key.append("searchRequest").append(model.searchRequest);
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
     * Получить ID ресурса миниатюры по умолчанию (в зависимости от типа вложения)
     * @param attachmentType тип вложения ({@link AttachmentModel})
     * @return ID ресурса (drawable)
     */
    public static int getDefaultThumbnailResId(int attachmentType) {
        switch (attachmentType) {
            case AttachmentModel.TYPE_IMAGE_STATIC:
            case AttachmentModel.TYPE_IMAGE_GIF:
                return R.drawable.thumbnail_default_image;
            case AttachmentModel.TYPE_VIDEO:
                return R.drawable.thumbnail_default_video;
            case AttachmentModel.TYPE_AUDIO:
                return R.drawable.thumbnail_default_audio;
            case AttachmentModel.TYPE_OTHER_FILE:
                return R.drawable.thumbnail_default_other;
            case AttachmentModel.TYPE_OTHER_NOTFILE:
                return R.drawable.thumbnail_default_link;
        }
        return R.drawable.thumbnail_default_image;
    }
    
    /**
     * Получить строку с информацией о размере вложения
     * @param attachment модель вложения
     * @param resources объект ресурсов
     */
    public static String getAttachmentSizeString(AttachmentModel attachment, Resources resources) {
        int kb = attachment.size;
        if (kb == -1) return "";
        if (kb >= 1000) {
            double mb = (double)kb / 1024;
            return resources.getString(R.string.postitem_attachment_size_mb_format, mb);
        } else {
            return resources.getString(R.string.postitem_attachment_size_kb_format, kb);
        }
    }
    
    /**
     * Получить строку с информацией о вложении (размер, разрешение, имя)
     * @param chan модуль имиджборды
     * @param attachment модель вложения
     * @param resources объект ресурсов
     */
    public static String getAttachmentInfoString(ChanModule chan, AttachmentModel attachment, Resources resources) {
        StringBuilder info = new StringBuilder(chan.fixRelativeUrl(attachment.path)).append('\n');
        if (attachment.size != -1)
            info.append(resources.getString(R.string.attachment_info_size_format, getAttachmentSizeString(attachment, resources))).append('\n');
        if (attachment.width > 0 && attachment.height > 0)
            info.append(resources.getString(R.string.attachment_info_resolution_format, attachment.width, attachment.height)).append('\n');
        if (attachment.originalName != null)
            info.append(resources.getString(R.string.attachment_info_original_name_format, attachment.originalName)).append('\n');
        return info.substring(0, info.length() - 1);
    }
    
    /**
     * Получить строку с отображаемым названием вложения (не обязательно совпадает с именем файла)
     * @param attachment модель вложения
     */
    public static String getAttachmentDisplayName(AttachmentModel attachment) {
        if (attachment.originalName != null && attachment.originalName.length() != 0) return attachment.originalName;
        if (attachment.type == AttachmentModel.TYPE_OTHER_NOTFILE) {
            return attachment.path == null ? "" : attachment.path;
        }
        String usingPath = attachment.path != null ? attachment.path : attachment.thumbnail;
        if (usingPath == null) return "";
        return usingPath.substring(usingPath.lastIndexOf('/') + 1);
    }
    
    /**
     * Получить локальное имя файла вложения, с указанием доски, к которой относится вложение
     * и с учётом возможности наличия файлов с одинаковыми именами на доске (в зависимости от значения {@link BoardModel#uniqueAttachmentNames})
     * @param attachment модель вложения
     * @param boardModel модель доски, к которой относится вложение
     */
    public static String getAttachmentLocalFileName(AttachmentModel attachment, BoardModel boardModel) {
        final String result;
        if (attachment.type == AttachmentModel.TYPE_OTHER_NOTFILE) return attachment.path;
        if (attachment.path == null) return null;
        String filename = attachment.path.substring(attachment.path.lastIndexOf('/') + 1);
        if (filename.length() == 0) return null;
        int dotLastPos = filename.lastIndexOf('.');
        if (dotLastPos == -1) dotLastPos = filename.length();
        if (boardModel == null) {
            result = filename.substring(0, dotLastPos) + '-' + hashAttachmentModel(attachment).substring(0, 4) + filename.substring(dotLastPos);
        } else {
            String dashBoardName = boardModel.boardName == null || boardModel.boardName.length() == 0 ? "" : '-' + boardModel.boardName;
            if (boardModel.uniqueAttachmentNames) {
                result = filename.substring(0, dotLastPos) + dashBoardName + filename.substring(dotLastPos);
            } else {
                result = filename.substring(0, dotLastPos) + dashBoardName + '-' + hashAttachmentModel(attachment).substring(0, 4) +
                        filename.substring(dotLastPos);
            }
        }
        try {
            return URLDecoder.decode(result, "UTF-8");
        } catch (Exception e) {
            return result;
        }
    }
    
    /**
     * Получить короткое локальное название вложения, используемое в сервисе загрузок
     * @param attachment модель вложения
     * @param boardModel модель доски, к которой относится вложение
     */
    public static String getAttachmentLocalShortName(AttachmentModel attachment, BoardModel boardModel) {
        final String result;
        if (attachment.type == AttachmentModel.TYPE_OTHER_NOTFILE) return attachment.path;
        if (attachment.path == null) return null;
        String filename = attachment.path.substring(attachment.path.lastIndexOf('/') + 1);
        if (filename.length() == 0) return null;
        int dotLastPos = filename.lastIndexOf('.');
        if (dotLastPos == -1) dotLastPos = filename.length();
        if (boardModel == null || boardModel.boardName == null || boardModel.boardName.length() == 0) {
            result = filename.substring(0, dotLastPos);
        } else {
            result = filename.substring(0, dotLastPos) + '-' + boardModel.boardName;
        }
        try {
            return URLDecoder.decode(result, "UTF-8");
        } catch (Exception e) {
            return result;
        }
    }
    
    /**
     * Получить расширение файла вложения, включая точку (например: ".jpg")
     * @param attachment модель вложения
     * @return расширение файла, включая точку
     */
    public static String getAttachmentExtention(AttachmentModel attachment) {
        if (attachment.type == AttachmentModel.TYPE_OTHER_NOTFILE) return null;
        int dotLastPos = attachment.path.lastIndexOf('.');
        if (dotLastPos == -1) return "";
        return attachment.path.substring(dotLastPos);
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
     * Слияние списков постов, с сохранением удалённых постов (которые присутствовали в старом списке, но отсутствуют в новом).<br>
     * Сложность O(N)
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
    
}
