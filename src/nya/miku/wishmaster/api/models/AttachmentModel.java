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

package nya.miku.wishmaster.api.models;

import java.io.Serializable;

import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer.Tag;

/**
 * Модель вложения (прикреплённый к посту файл или внешняя ссылка, например, на Youtube)
 * @author miku-nyan
 *
 */
public class AttachmentModel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * Тип вложения, одно из константных значений:
     * {@link #TYPE_IMAGE_STATIC}, {@link #TYPE_IMAGE_GIF}, {@link #TYPE_IMAGE_SVG},
     * {@link #TYPE_VIDEO}, {@link #TYPE_AUDIO},
     * {@link #TYPE_OTHER_FILE}, {@link #TYPE_OTHER_NOTFILE}
     */
    @Tag(0) public int type;
    
    /**
     * Размер вложения (если оно является файлом), в килобайтах.
     * Если вложение не является файлом (или размер не определяется/не предоставляется), должно быть записано значение -1.
     */
    @Tag(1) public int size;
    
    /**
     * Путь (абсолютный или относительный на имиджборде) к картинке-превью (миниатюре) данного вложения.
     * Может содержать значение null, если миниатюра отсутствует или не предоставляется для данного типа файла/на данной имиджборде.
     */
    @Tag(2) public String thumbnail;
    
    /**
     * Путь (абсолютный или относительный на имиджборде) к оригиналу файла данного вложения, если вложение является файлом;
     * адрес, по поторому содержится вложение, если является внешней ссылкой.
     */
    @Tag(3) public String path;
    
    /**
     * Ширина изображения или видео, если вложение является изображением или видео.
     * В противном случае (или если размер не определяется/не предоставляется), должно быть записано значение -1.
     */
    @Tag(4) public int width;
    
    /**
     * Высота изображения или видео, если вложение является изображением или видео.
     * В противном случае (или если размер не определяется/не предоставляется), должно быть записано значение -1.
     */
    @Tag(5) public int height;
    
    /**
     * Оригинальное имя файла (в случае, если реальное имя файла на имиджборде отличается от оригинального, как загрузил пользователь).
     * Может содержать null.
     */
    @Tag(6) public String originalName;
    
    /**
     * Должно принимать true, если вложение является спойлером (или nsfw на некоторых чанах)
     */
    @Tag(7) public boolean isSpoiler;
    
    /**
     * Константное значение для обозначения типа вложения - статичное изображение (файл JPG или PNG)
     */
    public static final int TYPE_IMAGE_STATIC = 0;
    /**
     * Константное значение для обозначения типа вложения - анимированное изображение (файл GIF)
     */
    public static final int TYPE_IMAGE_GIF = 1;
    /**
     * Константное значение для обозначения типа вложения - векторное изображение (файл SVG)
     */
    public static final int TYPE_IMAGE_SVG = 6;
    /**
     * Константное значение для обозначения типа вложения - видеофайл (файл WEBM или другой формат)
     */
    public static final int TYPE_VIDEO = 2;
    /**
     * Константное значение для обозначения типа вложения - аудиофайл (файл MP3 или другой формат)
     */
    public static final int TYPE_AUDIO = 3;
    /**
     * Константное значение для обозначения типа вложения - неопределённый файл (любой файл)
     */
    public static final int TYPE_OTHER_FILE = 4;
    /**
     * Константное значение для обозначения типа вложения - ссылка на внешний ресурс (Youtube, Vimeo и т.д.)
     */
    public static final int TYPE_OTHER_NOTFILE = 5;
    
}
