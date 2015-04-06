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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ListIterator;

/**
 * Парсер HTML тэгов для последующей замены
 * @author miku-nyan
 */
public class FastHtmlTagParser {
    /**
     * Класс определяет объект - пару строк (открытие-закрытие тэга)
     * @author miku-nyan
     */
    public static class TagsPair {
        /** Открывающийся тэг */
        public final String openTag;
        /** Закрывающийся тэг */
        public final String closeTag;
        public TagsPair(String openTag, String closeTag) {
            if (openTag == null || closeTag == null) throw new NullPointerException();
            this.openTag = openTag;
            this.closeTag = closeTag;
        }
    }
    
    /** Интерфейс обработчика для замены тэгов */
    public interface TagReplaceHandler {
        /**
         * метод замены тэга 
         * @param source найденный тэг (объект TagsPair)
         * @return то, на что необходимо заменить найденный тэг, или null, если требуется оставить данный тэг без изменений
         */
        TagsPair replace(TagsPair source);
    }
    
    /**
     * Получить объект-парсер тэга &lt;span&gt;
     */
    public static FastHtmlTagParser getSpanTagParser() {
        if (spanTagParser == null) spanTagParser = new FastHtmlTagParser("span");
        return spanTagParser;
    }
    
    /**
     * Получить объект-парсер тэга &lt;font&gt;
     */
    public static FastHtmlTagParser getFontTagParser() {
        if (fontTagParser == null) fontTagParser = new FastHtmlTagParser("font");
        return fontTagParser;
    }
    
    /**
     * Получить объект-обработчик тэгов, который удаляет тэги (заменяет на "")
     */
    public static TagReplaceHandler getRemoveTagsReplaceHandler() {
        if (removeTagsReplaceHandler == null) removeTagsReplaceHandler = new TagReplaceHandler() {
            @Override
            public TagsPair replace(TagsPair source) {
                return new TagsPair("", "");
            }
        };
        return removeTagsReplaceHandler;
    }
    
    private static FastHtmlTagParser spanTagParser = null;
    private static FastHtmlTagParser fontTagParser = null;
    
    private static TagReplaceHandler removeTagsReplaceHandler = null;
    
    /**
     * Триммер html строки (удаляет лишние переносы строк и пробелы).
     * @param source
     * @return
     */
    public static String fastTrim(String source) {
        StringBuilder builder = new StringBuilder(source.length());
        
        boolean lastSpace = true;
        boolean noEOL = true;
        for (int i = 0; i < source.length(); ++i) {
            if (source.charAt(i) == '\r' || source.charAt(i) == '\n' || source.charAt(i) == ' ') {
                if (source.charAt(i) != ' ') noEOL = false;
                if (!lastSpace) builder.append(' ');
                lastSpace = true;
            } else {
                builder.append(source.charAt(i));
                lastSpace = false;
            }
        }
        
        int len = builder.length();
        if (len == 0) return "";
        if (builder.charAt(len-1) == ' ') builder.setLength(--len);
        
        if (noEOL && source.length() == len) return source;
        return builder.toString();
    }
    
    /**
     * Конструктор
     * @param openFilter фильтр открывающегося тэга (напр "&lt;span"), обязательно в нижнем регистре!
     * @param closeFilter фильтр закрывающегося тэга (напр "&lt;/span&gt;"), обязательно в нижнем регистре!
     */
    public FastHtmlTagParser(char[] openFilter, char[] closeFilter) {
        this.openFilter = openFilter;
        this.closeFilter = closeFilter;
    }
    
    private FastHtmlTagParser(String tagName) {
        this("<".concat(tagName).toCharArray(), "</".concat(tagName).concat(">").toCharArray());
    }
    
    private class Tag {
        public int position;
        public int length;
        public boolean open;
        public boolean processed = false;
        public String newValue = null;
    }
    
    private final char[] openFilter;
    private final char[] closeFilter;
    
    private ArrayList<Tag> findAllTags(String source) throws IllegalStateException {
        ArrayList<Tag> tagsArray = new ArrayList<Tag>();
        int curPos = 0;
        int openPos = 0;
        int closePos = 0;
        int check = 0; // проверка того, последовательность открывающихся и закрывающихся тэгов верна
                       // (закрывающийся тэг должен идти после открывающегося; их количества должны быть равны)
        while (curPos < source.length()) {
            if (openFilter[openPos] == Character.toLowerCase(source.charAt(curPos))) {
                ++openPos;
            } else {
                openPos = 0;
            }
            if (closeFilter[closePos] == Character.toLowerCase(source.charAt(curPos))) {
                ++closePos;
            } else {
                closePos = 0;
            }
            if (openPos == openFilter.length) {
                Tag tag = new Tag();
                tag.open = true;
                tag.position = curPos + 1 - openFilter.length;
                while (curPos < source.length() && source.charAt(++curPos) != '>') {}
                tag.length = curPos + 1 - tag.position;
                tagsArray.add(tag);
                ++check;
                openPos = 0;
                closePos = 0;
            }
            if (closePos == closeFilter.length) {
                Tag tag = new Tag();
                tag.open = false;
                tag.position = curPos + 1 - closeFilter.length;
                tag.length = closeFilter.length;
                tagsArray.add(tag);
                --check;
                if (check < 0) {
                    throw new IllegalStateException();
                }
                openPos = 0;
                closePos = 0;
            }
            ++curPos;
        }
        if (check != 0) {
            throw new IllegalStateException();
        }
        return tagsArray;
    }
    
    /**
     * Произвести замену тэгов
     * @param source исходная HTML строка
     * @param replacer объект-обработчик для замены тэгов
     * @return результирующая строка
     * @throws IllegalStateException в случае, если исходная строка HTML некорректна,
     * например, не каждому открывающемуся тэгу соответствует закрывающийся
     */
    public String replace(String source, TagReplaceHandler replacer) throws IllegalStateException {
        ArrayList<Tag> tags = findAllTags(source);
        ListIterator<Tag> reverseIterator = tags.listIterator(tags.size());
        int newLength = source.length();
        while (reverseIterator.hasPrevious()) {
            Tag openTag = reverseIterator.previous();
            if (openTag.open && !openTag.processed) {
                while (reverseIterator.hasNext()) {
                    Tag closeTag = reverseIterator.next();
                    if (!closeTag.open && !closeTag.processed) {
                        openTag.processed = true;
                        closeTag.processed = true;
                        TagsPair result = replacer.replace(new TagsPair(
                                source.substring(openTag.position, openTag.position + openTag.length),
                                source.substring(closeTag.position, closeTag.position + closeTag.length)));
                        if (result != null) {
                            openTag.newValue = result.openTag;
                            closeTag.newValue = result.closeTag;
                            newLength += openTag.newValue.length() - openTag.length + closeTag.newValue.length() - closeTag.length;
                        }
                        break;
                    }
                }
                continue;
            }
        }
        
        boolean notRequired = true; //проверить, есть ли хоть один тэг на замену, в противном случае сразу вернуть исходную строку
        for (Tag tag : tags) {
            if (tag.newValue != null) {
                notRequired = false;
                break;
            }
        }
        if (notRequired) return source;
        
        Collections.sort(tags, new Comparator<Tag>() {
            @Override
            public int compare(Tag lhs, Tag rhs) {
                return lhs.position - rhs.position;
            }
        });
        StringBuilder builder = new StringBuilder(newLength);
        int strPosition = 0;
        for (Tag tag : tags) {
            if (tag.newValue != null) {
                builder.append(source.substring(strPosition, tag.position));
                builder.append(tag.newValue);
            } else {
                builder.append(source.substring(strPosition, tag.position + tag.length));
            }
            strPosition = tag.position + tag.length;
        }
        builder.append(source.substring(strPosition));
        return builder.toString();
    }
}
