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

package nya.miku.wishmaster.ui;

import java.net.URLDecoder;
import java.util.regex.Pattern;

import android.content.res.Resources;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.RegexUtils;

public class Attachments {
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
        String result = usingPath.substring(usingPath.lastIndexOf('/') + 1);
        try {
            result = URLDecoder.decode(result, "UTF-8");
        } catch (Exception e) {}
        return result;
    }
    
    /**
     * Получить локальное имя файла вложения, с указанием доски, к которой относится вложение
     * и с учётом возможности наличия файлов с одинаковыми именами на доске (в зависимости от значения {@link BoardModel#uniqueAttachmentNames})
     * @param attachment модель вложения
     * @param boardModel модель доски, к которой относится вложение
     */
    public static String getAttachmentLocalFileName(AttachmentModel attachment, BoardModel boardModel) {
        if (attachment.type == AttachmentModel.TYPE_OTHER_NOTFILE) return attachment.path;
        final String suffix;
        if (boardModel == null) {
            suffix = '-' + ChanModels.hashAttachmentModel(attachment).substring(0, 4);
        } else {
            String boardSuff = boardModel.boardName == null || boardModel.boardName.length() == 0 ? "" : '-' + boardModel.boardName;
            suffix = boardModel.uniqueAttachmentNames ? boardSuff : boardSuff + '-' + ChanModels.hashAttachmentModel(attachment).substring(0, 4);
        }
        return getLocalFilename(removeQueryString(attachment.path), suffix);
    }
    
    /**
     * Получить короткое локальное название вложения, используемое в сервисе загрузок
     * @param attachment модель вложения
     * @param boardModel модель доски, к которой относится вложение
     */
    public static String getAttachmentLocalShortName(AttachmentModel attachment, BoardModel boardModel) {
        if (attachment.type == AttachmentModel.TYPE_OTHER_NOTFILE) return attachment.path;
        return getLocalFilename(attachment.path,
                (boardModel == null || boardModel.boardName == null || boardModel.boardName.length() == 0) ? null : ('-' + boardModel.boardName));
    }
    
    /**
     * Получить расширение файла вложения, включая точку (например: ".jpg")
     * @param attachment модель вложения
     * @return расширение файла, включая точку
     */
    public static String getAttachmentExtention(AttachmentModel attachment) {
        if (attachment.type == AttachmentModel.TYPE_OTHER_NOTFILE) return null;
        String filename = attachment.path.substring(attachment.path.lastIndexOf('/') + 1);
        int dotLastPos = filename.lastIndexOf('.');
        if (dotLastPos == -1) return "";
        return removeQueryString(filename.substring(dotLastPos));
    }
    
    /**
     * Получить имя файла для данного url
     * @param url URL
     * @param suffix суффикс, который будет дописан перед расширением
     */
    private static String getLocalFilename(String url, String suffix) {
        if (url == null) return null;
        String filename = url.substring(url.lastIndexOf('/') + 1);
        filename = removeQueryString(filename);
        if (filename.length() == 0) return null;
        try {
            filename = URLDecoder.decode(filename, "UTF-8");
        } catch (Exception e) {}
        filename = RegexUtils.replaceAll(filename, INCORRECT_CHARACTERS, "_");
        
        if (suffix == null) suffix = "";
        int dotLastPos = filename.lastIndexOf('.');
        if (dotLastPos == -1) dotLastPos = filename.length();
        String filenameMain = filename.substring(0, dotLastPos).replaceFirst("(?i)^(con|prn|aux|nul|com\\d|lpt\\d)(\\.|$)", "$1_");
        String filenameSuff = suffix + filename.substring(dotLastPos);
        if (filenameSuff.length() > MAX_FILENAME_LENGTH) return filenameSuff.substring(0, MAX_FILENAME_LENGTH);
        int maxMainLength = MAX_FILENAME_LENGTH - filenameSuff.length();
        if (filenameMain.length() > maxMainLength) filenameMain = filenameMain.substring(0, maxMainLength);
        if (filenameSuff.startsWith(".") && (filenameMain.length() == 3 || filenameMain.length() == 4))
            filenameMain = filenameMain.replaceFirst("(?i)^(con|prn|aux|nul|com\\d|lpt\\d)", "___");
        
        return filenameMain + filenameSuff;
    }

    /**
     * Удалить строку GET запроса из URL
     * @param url URL
     * @return
     */
    private static String removeQueryString(String url) {
        return url.contains("?") ? url.split("\\?")[0] : url;
    }
    
    private static final Pattern INCORRECT_CHARACTERS = Pattern.compile("[\\n\\r\\t\\f\\?\\*\\|\\\\\"\0<>:`]");
    private static final int MAX_FILENAME_LENGTH = 255;
}
