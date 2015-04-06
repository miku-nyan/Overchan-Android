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
    
    /** Должно принимать true, если на данной доске гарантируется, что все имена файлов вложений (реальные) различны. */
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
    /** Должно принимать true, если на доске можно отправить пост с ватермаркой (к прикрепляемым изображениям).
     *  Только если {@link #readonlyBoard} равно false. */
    @Tag(18) public boolean allowWatermark;
    /** Должно принимать true, если на доске можно при постинге указать, когда подписываться ОПом. Только если {@link #readonlyBoard} равно false. */
    @Tag(19) public boolean allowOpMark;
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
     *  Только если {@link #readonlyBoard} равно false, в противном случае может принимать null. */
    @Tag(24) public String[] attachmentsFormatFilters;
    
    /** Номер первой (нулевой) страницы на данной доске. */
    @Tag(25) public int firstPage;
    /** Номер последней страницы на данной доске.
     *  Если не удаётся определить, или информация не предоставляется имиджбордой, может принимать значение {@link #LAST_PAGE_UNDEFINED}. */
    @Tag(26) public int lastPage;
    
    /** Должно принимать true, если на данной доске реализована функция поиска по всей доске. */
    @Tag(27) public boolean searchAllowed;
    /** Должно принимать true, если на данной доске реализована функция каталога. */
    @Tag(28) public boolean catalogAllowed;
    /** Массив с описаниями способов показа каталога, например: ["Сортировка по бампам", "Сортировка по размеру изображения"].
     *  Только если {@link #catalogAllowed} равно true, в противном случае может принимать null. */
    @Tag(29) public String[] catalogTypeDescriptions;
    
    /** Константное значения для обозначения номера последней страницы, если настоящий номер неизвестен. */
    public static final int LAST_PAGE_UNDEFINED = Integer.MAX_VALUE;
    
}
