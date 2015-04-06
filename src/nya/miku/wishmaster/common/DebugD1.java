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

package nya.miku.wishmaster.common;

import java.lang.reflect.Field;
import java.util.List;

import android.text.SpannableStringBuilder;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.ui.presentation.PresentationItemModel;
import nya.miku.wishmaster.ui.presentation.PresentationModel;


//@SuppressWarnings("unused")
//метод позволяет посчитать примерное отношение реальных размеров объектов PresentationItemModel к PostModel
//В работе самого приложения не используется

public class DebugD1 {
    @SuppressWarnings("unchecked")
    private static int getPresentationItemModelSize(PresentationItemModel model) {
        int size = 112;
        size += strsize(model.dateString);
        size += strsize(model.postsCountString);
        size += strsize(model.stickyClosedString);
        size += strsize(model.badgeTitle);
        if (model.badgeHashes != null) {
            size += (12 + (model.badgeHashes.length * 4));
            for (String s : model.badgeHashes) size += strsize(s);
        }
        if (model.attachmentHashes != null) {
            size += (12 + (model.attachmentHashes.length * 4));
            for (String s : model.attachmentHashes) size += strsize(s);
        }
        size += ssbsize((SpannableStringBuilder) model.spannedComment);
        size += ssbsize((SpannableStringBuilder) model.spannedHeader);
        size += ssbsize((SpannableStringBuilder) model.referencesString);
        if (model.referencesTo != null) {
            size += (136 + (model.referencesTo.size() * 24));
            for (String s : model.referencesTo) size += strsize(s);
        }
        List<String> referencesFrom;
        try {
            Field rf = PresentationItemModel.class.getDeclaredField("referencesFrom");
            rf.setAccessible(true);
            referencesFrom = (List<String>) rf.get(model);
        } catch (Exception e) {throw new RuntimeException(e);}
        if (referencesFrom != null) {
            size += (referencesFrom.size() * 20);
            for (String s : referencesFrom) size += strsize(s);
        }
        return size;
    }
    
    private static int strsize(String s) {
        if (s==null) return 0; else return 40 + (2*s.length());
    }
    
    private static int ssbsize(SpannableStringBuilder s) {
        if (s==null) return 0; else {
            int size = 48;
            size += 12 + (s.length() * 2);
            Object[] spans = s.getSpans(0, s.length(), Object.class);
            size += (12 + (spans.length * 4)) * 4;
            size += spans.length * 16;
            size += 12 + (s.getFilters().length * 20);
            return size;
        }
    }
    
    private static long size_s, size_m;
    
    /**
     * Посчитать примерное отношение размера PresentationItemModel к PostModel в PresentationModel страницы.
     */
    public static void calcK(PresentationModel model) {
        if (model == null) {
            Logger.d("DEV", "model == null");
            return;
        }
        if (model.presentationList == null) {
            Logger.d("DEV", "presentationList == null");
            return;
        }
        int size_s = 0;
        int size_m = 0;
        for (PresentationItemModel m : model.presentationList) {
            int postSize = ChanModels.getPostModelSize(m.sourceModel);;
            int presentSize = getPresentationItemModelSize(m);
            size_s += postSize;
            size_m += presentSize;
            DebugD1.size_s += postSize;
            DebugD1.size_m += presentSize;
        }
        Logger.d("D1", "K = "+((double)size_m/size_s));
        Logger.d("D1", "(average) K = "+((double)DebugD1.size_m/DebugD1.size_s));
    }
    
}
