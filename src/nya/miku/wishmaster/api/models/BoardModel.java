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

import nya.miku.wishmaster.api.ChanModule;

import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer.Tag;

/**
 * Модель доски (форума) на имиджборде
 * @author miku-nyan
 *
 */
public class BoardModel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /** Название имиджборды (то, что возвращает {@link ChanModule#getChanName()} модуль данной имиджборды). */
    @Tag(0) public String chan;
    /** Код доски (короткое название), например: "b", "s", "int". */
    @Tag(1) public String boardName;
    /** Описание доски (полное название), например: "Бред", "Программы", "International". */
    @Tag(2) public String boardDescription;
    /** Категория, к которой относится доска (в случае деления досок по категориям). Может принимать null. */
    @Tag(3) public String boardCategory;
    /** Должно принимать true, если на доске содержится контент 18+ или доска является немодерируемой. */
    @Tag(4) public boolean nsfw;
    
    /** Должно принимать true, если на данной доске гарантируется, что все (реальные) имена файлов вложений и превью различны. */
    @Tag(5) public boolean uniqueAttachmentNames;
    
    /** Часовой пояс, используемый на данной доске, например: "GMT+3", "US/Eastern". */
    @Tag(6) public String timeZoneId;
    
    /** Имя отправителя, используемое на данной доске по умолчанию, например: "Аноним". */
    @Tag(7) public String defaultUserName;
    /** Количество постов до бамп лимита (после которого новые посты без сажи не поднимают тред наверх). */
    @Tag(8) public int bumpLimit;
    
    /** Должно принимать true, если данная доска доступна только для чтения. */
    @Tag(9) public boolean readonlyBoard;
    /** Должно принимать true, если на этой доске необходимо прикрепить файл, чтобы создать тред. Только если {@link #readonlyBoard} равно false. */
    @Tag(10) public boolean requiredFileForNewThread;
    /** Должно принимать true, если на доске можно удалять свои посты. Только если {@link #readonlyBoard} равно false. */
    @Tag(11) public boolean allowDeletePosts;
    /** Должно принимать true, если на доске можно удалять вложения в своих постах. Только если {@link #readonlyBoard} равно false. */
    @Tag(12) public boolean allowDeleteFiles;
    /** Разрешено ли на данной доске жаловаться на сообщения (сообщить модератору), одно из константных значений:
     *  {@link #REPORT_NOT_ALLOWED}, {@link #REPORT_SIMPLE}, {@link #REPORT_WITH_COMMENT} */
    @Tag(31) public int allowReport;
    /** Должно принимать true, если на доске можно указывать имя отправителя. Только если {@link #readonlyBoard} равно false. */
    @Tag(13) public boolean allowNames;
    /** Должно принимать true, если на доске можно указывать темы сообщенй. Только если {@link #readonlyBoard} равно false. */
    @Tag(14) public boolean allowSubjects;
    /** Должно принимать true, если на доске можно отправлять посты с сажей (не поднимая тред). Только если {@link #readonlyBoard} равно false. */
    @Tag(15) public boolean allowSage;
    /** Должно принимать true, если на доске можно указывать e-mail адрес отправителя. Только если {@link #readonlyBoard} равно false. */
    @Tag(16) public boolean allowEmails;
    /** Должно принимать true, если на доске при отправке поста с сажей невозможно указать e-mail. Только если {@link #readonlyBoard} равно false. */
    @Tag(17) public boolean ignoreEmailIfSage;
    
    @Deprecated @Tag(18) public boolean allowWatermark;
    
    /** Должно принимать true, если на доске можно отправить пост с дополнительным модификатором (по умолчанию "ОП треда").
     *  Только если {@link #readonlyBoard} равно false. */
    @Tag(19) public boolean allowCustomMark;
    /** Описание дополнительного модификатора (только если {@link #allowCustomMark} равно true), по умолчанию "ОП треда". */
    @Tag(32) public String customMarkDescription;
    /** Должно принимать true, если на доске нельзя постить одинаковые файлы, но можно это обойти, добавив в хвост несколько случайных байт
     *  (произвольных хэш). Только если {@link #readonlyBoard} равно false. */
    @Tag(20) public boolean allowRandomHash;
    /** Должно принимать true, если на доске при отправке сообщения можно выбрать из списка значок (например, политических предпочтений).
     *  Только если {@link #readonlyBoard} равно false. */
    @Tag(21) public boolean allowIcons;
    /** Массив с описаниями доступных значков. Только если {@link #allowIcons} равно true, в противном случае может принимать null. */
    @Tag(22) public String[] iconDescriptions;
    /** Максимальное количество файлов, которое можно прикрепить к посту. Только если {@link #readonlyBoard} равно false.
     *  Если {@link #requiredFileForNewThread} равно true, должно быть больше нуля. */
    @Tag(23) public int attachmentsMaxCount;
    /** Массив с фильтрами допустимых форматов (расширений) прикрепляемых файлов, например: ["jpg", "png", "gif"].
     *  Значение null означает, что допустимо прикреплять любые файлы.
     *  Только если {@link #readonlyBoard} равно false. */
    @Tag(24) public String[] attachmentsFormatFilters;
    /** Тип допустимой разметки в теле отправляемого поста,
     *  одно из константных значений: {@link #MARK_NOMARK}, {@link #MARK_WAKABAMARK}, {@link #MARK_BBCODE}.
     *  Только если {@link #readonlyBoard} равно false. */
    @Tag(30) public int markType;
    
    /** Номер первой (нулевой) страницы на данной доске. */
    @Tag(25) public int firstPage;
    /** Номер последней страницы на данной доске.
     *  Если не удаётся определить, или информация не предоставляется имиджбордой, может принимать значение {@link #LAST_PAGE_UNDEFINED}. */
    @Tag(26) public int lastPage;
    
    /** Должно принимать true, если на данной доске реализована функция поиска по всей доске. */
    @Tag(27) public boolean searchAllowed;
    /** Должно принимать true, если на данной доске реализована пагинация результатов поиска. */
    @Tag(33) public boolean searchPagination;
    /** Должно принимать true, если на данной доске реализована функция каталога. */
    @Tag(28) public boolean catalogAllowed;
    /** Массив с описаниями способов показа каталога, например: ["Сортировка по бампам", "Сортировка по размеру изображения"].
     *  Только если {@link #catalogAllowed} равно true, в противном случае может принимать null. */
    @Tag(29) public String[] catalogTypeDescriptions;
    
    /** Константное значение для обозначения номера последней страницы, если настоящий номер неизвестен. */
    public static final int LAST_PAGE_UNDEFINED = Integer.MAX_VALUE;
    
    /** Константное значение для обозначения типа допустимой разметки при отправке поста - без разметки */
    public static final int MARK_NOMARK = 0;
    /** Константное значение для обозначения типа допустимой разметки при отправке поста - модифицированный wakabamark.<br>
     *  *italic* **bold** %%spoiler%% strike^H^H^H^H^H^H `code` */
    public static final int MARK_WAKABAMARK = 1;
    /** Константное значение для обозначения типа допустимой разметки при отправке поста - BBCode.<br>
     *  [i]italic[/i] [b]bold[/b] [u]underline[/u] [spoiler]spoiler[/spoiler] [s]strike[/s] */
    public static final int MARK_BBCODE = 2;
    /** Константное значение для обозначения типа допустимой разметки при отправке поста - 4chan.<br>
     *  *italic* **bold** __underline__ [spoiler]spoiler[/spoiler] */
    public static final int MARK_4CHAN = 3;
    /** Константное значение для обозначения типа допустимой разметки при отправке поста - 0chan.hk.<br>
     *  *italic* **bold** %%spoiler%% -strike- `code` */
    public static final int MARK_NULL_CHAN = 4;
    /** Константное значение для обозначения типа допустимой разметки при отправке поста - Infinity.<br>
     *  ''italic'' '''bold''' __underline__ **spoiler** ~~strike~~ */
    public static final int MARK_INFINITY = 5;
    
    /** Константное значение, если на данной доске нет возможности пожаловаться на сообщения */
    public static final int REPORT_NOT_ALLOWED = 0;
    /** Константное значение, если на данной доске при репорте есть возможность указать только номер сообщения */
    public static final int REPORT_SIMPLE = 1;
    /** Константное значение, если на данной доске при репорте есть возможность указать номер сообщения и комментарий */
    public static final int REPORT_WITH_COMMENT = 2;
    
}
