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

package nya.miku.wishmaster.ui.posting;

import android.text.Editable;
import android.widget.EditText;
import nya.miku.wishmaster.api.models.BoardModel;

public class PostFormMarkup {
    public static final int FEATURE_BOLD = 1;
    public static final int FEATURE_ITALIC = 2;
    public static final int FEATURE_UNDERLINE = 3;
    public static final int FEATURE_STRIKE = 4;
    public static final int FEATURE_SPOILER = 5;
    public static final int FEATURE_QUOTE = 6;
    //http://wakaba.c3.cx/docs/docs.html#WakabaMark
    public static final int FEATURE_CODE = 7;
    
    public static boolean hasMarkupFeature(int markType, int feature) {
        switch (markType) {
            case BoardModel.MARK_NOMARK: return false;
            case BoardModel.MARK_BBCODE: return feature != FEATURE_CODE;
            case BoardModel.MARK_4CHAN: return (feature != FEATURE_STRIKE) && (feature != FEATURE_CODE);
            case BoardModel.MARK_WAKABAMARK: return feature != FEATURE_UNDERLINE;
            case BoardModel.MARK_NULL_CHAN: return feature != FEATURE_UNDERLINE;
            case BoardModel.MARK_INFINITY: return feature != FEATURE_CODE;
        }
        return false;
    }
    
    public static void markup(int markType, EditText commentField, int feature) {
        Editable comment = commentField.getEditableText();
        String text = comment.toString();
        int selectionStart = Math.max(0, commentField.getSelectionStart());
        int selectionEnd = Math.min(text.length(), commentField.getSelectionEnd());
        text = text.substring(selectionStart, selectionEnd);
        
        if (markType == BoardModel.MARK_WAKABAMARK) {
            switch (feature) {
                case FEATURE_BOLD:
                    comment.replace(selectionStart, selectionEnd, "**" + text.replace("\n", "**\n**") + "**");
                    commentField.setSelection(selectionStart + 2);
                    break;
                case FEATURE_ITALIC:
                    comment.replace(selectionStart, selectionEnd, "*" + text.replace("\n", "*\n*") + "*");
                    commentField.setSelection(selectionStart + 1);
                    break;
                case FEATURE_STRIKE:
                    StringBuilder strike = new StringBuilder();
                    for (String s : text.split("\n")) {
                        strike.append(s);
                        for (int i=0; i<s.length(); ++i) strike.append("^H");
                        strike.append('\n');
                    }
                    comment.replace(selectionStart, selectionEnd, strike.substring(0, strike.length() - 1));
                    commentField.setSelection(selectionStart);
                    break;
                case FEATURE_SPOILER:
                    comment.replace(selectionStart, selectionEnd, "%%" + text.replace("\n", "%%\n%%") + "%%");
                    commentField.setSelection(selectionStart + 2);
                    break;
                case FEATURE_QUOTE:
                    comment.replace(selectionStart, selectionEnd, ">" + text.replace("\n", "\n>"));
                    break;
                case FEATURE_CODE:
                    comment.replace(selectionStart, selectionEnd, "`" + text.replace("`", "``").replace("\n", "`\n`").replace("\n``", "\n") + "`");
                    commentField.setSelection(selectionStart + 1);
                    break;
            }
        } else if (markType == BoardModel.MARK_BBCODE) {
            switch (feature) {
                case FEATURE_BOLD:
                    comment.replace(selectionStart, selectionEnd, "[b]" + text + "[/b]");
                    commentField.setSelection(selectionStart + 3);
                    break;
                case FEATURE_ITALIC:
                    comment.replace(selectionStart, selectionEnd, "[i]" + text + "[/i]");
                    commentField.setSelection(selectionStart + 3);
                    break;
                case FEATURE_UNDERLINE:
                    comment.replace(selectionStart, selectionEnd, "[u]" + text + "[/u]");
                    commentField.setSelection(selectionStart + 3);
                    break;
                case FEATURE_STRIKE:
                    comment.replace(selectionStart, selectionEnd, "[s]" + text + "[/s]");
                    commentField.setSelection(selectionStart + 3);
                    break;
                case FEATURE_SPOILER:
                    comment.replace(selectionStart, selectionEnd, "[spoiler]" + text + "[/spoiler]");
                    commentField.setSelection(selectionStart + 9);
                    break;
                case FEATURE_QUOTE:
                    comment.replace(selectionStart, selectionEnd, ">" + text.replace("\n", "\n>"));
                    break;
            }
        } else if (markType == BoardModel.MARK_4CHAN) {
            switch (feature) {
                case FEATURE_BOLD:
                    comment.replace(selectionStart, selectionEnd, "**" + text.replace("\n", "**\n**") + "**");
                    commentField.setSelection(selectionStart + 2);
                    break;
                case FEATURE_ITALIC:
                    comment.replace(selectionStart, selectionEnd, "*" + text.replace("\n", "*\n*") + "*");
                    commentField.setSelection(selectionStart + 1);
                    break;
                case FEATURE_UNDERLINE:
                    comment.replace(selectionStart, selectionEnd, "__" + text.replace("\n", "__\n__") + "__");
                    commentField.setSelection(selectionStart + 2);
                    break;
                case FEATURE_SPOILER:
                    comment.replace(selectionStart, selectionEnd, "[spoiler]" + text + "[/spoiler]");
                    commentField.setSelection(selectionStart + 9);
                    break;
                case FEATURE_QUOTE:
                    comment.replace(selectionStart, selectionEnd, ">" + text.replace("\n", "\n>"));
                    break;
            }
        } else if (markType == BoardModel.MARK_NULL_CHAN) {
            switch (feature) {
                case FEATURE_BOLD:
                    comment.replace(selectionStart, selectionEnd, "**" + text.replace("\n", "**\n**") + "**");
                    commentField.setSelection(selectionStart + 2);
                    break;
                case FEATURE_ITALIC:
                    comment.replace(selectionStart, selectionEnd, "*" + text.replace("\n", "*\n*") + "*");
                    commentField.setSelection(selectionStart + 1);
                    break;
                case FEATURE_STRIKE:
                    comment.replace(selectionStart, selectionEnd, "-" + text.replace("\n", "-\n-") + "-");
                    commentField.setSelection(selectionStart + 1);
                    break;
                case FEATURE_SPOILER:
                    comment.replace(selectionStart, selectionEnd, "%%" + text + "%%");
                    commentField.setSelection(selectionStart + 2);
                    break;
                case FEATURE_QUOTE:
                    comment.replace(selectionStart, selectionEnd, ">" + text.replace("\n", "\n>"));
                    break;
                case FEATURE_CODE:
                    comment.replace(selectionStart, selectionEnd, "`" + text.replace("`", "``").replace("\n", "`\n`").replace("\n``", "\n") + "`");
                    commentField.setSelection(selectionStart + 1);
                    break;
            }
        } else if (markType == BoardModel.MARK_INFINITY) {
            switch (feature) {
                case FEATURE_BOLD:
                    comment.replace(selectionStart, selectionEnd, "'''" + text.replace("\n", "'''\n'''") + "'''");
                    commentField.setSelection(selectionStart + 3);
                    break;
                case FEATURE_ITALIC:
                    comment.replace(selectionStart, selectionEnd, "''" + text.replace("\n", "''\n''") + "''");
                    commentField.setSelection(selectionStart + 2);
                    break;
                case FEATURE_UNDERLINE:
                    comment.replace(selectionStart, selectionEnd, "__" + text.replace("\n", "__\n__") + "__");
                    commentField.setSelection(selectionStart + 2);
                    break;
                case FEATURE_STRIKE:
                    comment.replace(selectionStart, selectionEnd, "~~" + text.replace("\n", "~~\n~~") + "~~");
                    commentField.setSelection(selectionStart + 2);
                    break;
                case FEATURE_SPOILER:
                    comment.replace(selectionStart, selectionEnd, "**" + text.replace("\n", "**\n**") + "**");
                    commentField.setSelection(selectionStart + 2);
                    break;
                case FEATURE_QUOTE:
                    comment.replace(selectionStart, selectionEnd, ">" + text.replace("\n", "\n>"));
                    break;
            }
        }
    }
}
