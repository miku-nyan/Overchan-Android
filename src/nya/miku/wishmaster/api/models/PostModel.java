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

import com.esotericsoftware.kryo.serializers.TaggedFieldSerializer.Tag;

import android.graphics.Color;

/**
 * Модель поста (сообщения)
 * @author miku-nyan
 *
 */
//при изменении полей не забывать обновить методы класса ChanModels (хэширование и др.)
public class PostModel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /** Номер поста */
    @Tag(0) public String number;
    /** Имя отправителя */
    @Tag(1) public String name;
    /** Цвет, отображаемый в заголовки сообщения (напр. для визуального определения id пользователя) */
    @Tag(13) public int color = Color.TRANSPARENT;
    /** Тема сообщения */
    @Tag(2) public String subject;
    /** Комментарий в формате HTML.<br>
     *  Допустимо использовать все тэги, поддерживаемые {@link android.text.Html#fromHtml(String)}, а также:<ul>
     *  <li><b>&lt;ol&gt;</b>, <b>&lt;ul&gt;</b>, <b>&lt;li&gt;</b> - списки</li>
     *  <li><b>&lt;s&gt;</b>, <b>&lt;strike&gt;</b>, <b>&lt;del&gt;</b> - перечёркнутый текст</li>
     *  <li><b>&lt;code&gt;</b> - отображается моноширинным шрифтом</li>
     *  <li><b>&lt;blockquote class="unkfunc"&gt;</b> - форумная цитата (отображается цветом выбранной темы оформления), выделяется абзацами</li>
     *  <li><b>&lt;span class="unkfunc"&gt;</b>, <b>&lt;span class="quote"&gt;</b> - аналогично предыдущему, не выделяется абзацами</li>
     *  <li><b>&lt;span class="spoiler"&gt;</b> - спойлер, затемнённый текст (отображается цветами выбранной темы оформления)</li>
     *  <li><b>&lt;span class="s"&gt;</b> - перечёркнутый текст</li>
     *  <li><b>&lt;span class="u"&gt;</b> - подчёркнутый текст</li>
     *  <li><b>&lt;span style="..."&gt;</b> и <b>&lt;font style="..."&gt;</b> - CSS-стиль, поддерживаются color и background-color</li>
     *  <li><b>&lt;aibquote&gt;</b> и <b>&lt;aibspoiler&gt;</b> - псевдотэги, аналогичные
     *  <b>&lt;span class="unkfunc"&gt;</b> и <b>&lt;span class="spoiler"&gt;</b>.</li></ul> */
    @Tag(3) public String comment;
    /** E-mail отправителя. Может принимать null. */
    @Tag(4) public String email;
    /** Трип-код отправителя, также может использоваться как пометка пользователя как модератора. Может принимать null. */
    @Tag(5) public String trip;
    /** Массив моделей иконок (флаг страны, значок политических предпочтений и т.д.). Может принимать null. */
    @Tag(6) public BadgeIconModel[] icons;
    /** Должно принимать true, если установлена отметка, что сообщение отправил ОП (создатель треда). */
    @Tag(7) public boolean op;
    /** Должно принимать true, если сообщение отправлено с сажей (не поднимает тред). */
    @Tag(8) public boolean sage;
    /** Дата отправки сообщения в виде timestamp (кол-во миллисекунд с 1 января 1970 года 00:00:00 UTC) */
    @Tag(9) public long timestamp;
    /** Номер треда, к которому принадлежит сообщение. */
    @Tag(10) public String parentThread;
    /** Массив моделей вложений, прикреплённых к сообщению. Может принимать null. */
    @Tag(11) public AttachmentModel[] attachments;
    
    /** Отметка об удалении (должно принимать true, если на самом деле пост уже удалён). */
    @Tag(12) public boolean deleted;
}
