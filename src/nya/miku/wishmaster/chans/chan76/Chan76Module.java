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

package nya.miku.wishmaster.chans.chan76;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.util.ChanModels;

public class Chan76Module extends AbstractVichanModule {
    private static final String CHAN_NAME = "76chan.org";
    private static final String CHAN_DOMAIN = "76chan.org";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "76", "76chan Discussion", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "s", "Spaghetti", null, true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "f", "Film", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "i", "Information + Fileshare", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "v", "Video Games", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "int", "International", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r7k", "Robot 7602", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Aneemay", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sp", "Sports", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "h", "Hobbies", null, false),
    };
    
    public Chan76Module(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "76chan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_76chan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return CHAN_DOMAIN;
    }
    
    @Override
    protected boolean useHttps() {
        return true;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.bumpLimit = 250;
        model.attachmentsMaxCount = 5;
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        return model;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        super.sendPost(model, listener, task);
        return null;
    }
}
