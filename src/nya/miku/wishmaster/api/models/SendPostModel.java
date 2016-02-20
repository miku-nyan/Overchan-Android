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

import java.io.File;
import java.io.Serializable;

import nya.miku.wishmaster.api.ChanModule;

import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer.Tag;

/**
 * Модель отправки поста 
 * @author miku-nyan
 *
 */
public class SendPostModel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /** Название имиджборды (то, что возвращает {@link ChanModule#getChanName()} модуль данной имиджборды) */
    @Tag(0) public String chanName;
    
    /** Название доски (код доски), например: "b", "int" */
    @Tag(1) public String boardName;
    /** Номер треда, в который добавляется сообщение, или null, если создаётся новый тред */
    @Tag(2) public String threadNumber;
    
    /** Имя отправителя */
    @Tag(3) public String name;
    /** Тема сообщения */
    @Tag(4) public String subject;
    /** E-mail адрес отправителя */
    @Tag(5) public String email;
    /** Комментарий (текст сообщения) */
    @Tag(6) public String comment;
    /** Позиция курсора в комментарии (тексте сообщения) */
    @Tag(15) public int commentPosition = -1;
    /** Пароль для удаления поста или прикреплённых файлов */
    @Tag(7) public String password;
    /** Индекс значка в массиве {@link BoardModel#iconDescriptions}.
     *  (в случае, если доска поддерживает выбор значка пользователем, {@link BoardModel#allowIcons} равно true). */
    @Tag(8) public int icon = -1;
    
    /** Должно принимать true, если сообщение отправляется с сажей (не поднимать тред) */
    @Tag(9) public boolean sage;
    
    @Deprecated @Tag(10) public boolean watermark;
    
    /** Должно принимать true, если пост отправляется с дополнительным модификатором */
    @Tag(11) public boolean custommark;
    
    /** Должно принимать true, если к прикрепляемым файлам необходимо применить "произвольный хэш" */
    @Tag(12) public boolean randomHash;
    
    /** Массив прикрепляемых к посту файлов */
    @Tag(13) public File[] attachments;
    
    /** Ответ на капчу */
    @Tag(14) public String captchaAnswer;
}
