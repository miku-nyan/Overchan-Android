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

package nya.miku.wishmaster.chans.clairews;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.chans.nullchan.NullchanccModule;

public class ClairewsModule extends NullchanccModule {
    private static final String CHAN_NAME = "claire.ws";
    private static final String DOMAIN = "claire.ws";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Random", "Boards", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "Videogames", "Boards", false)
    };
    
    public ClairewsModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Kurisu (claire.ws)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_clairews, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN;
    }
    
    @Override
    protected boolean canHttps() {
        return false;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel board = super.getBoard(shortName, listener, task);
        board.defaultUserName = "Anonymous";
        board.allowNames = true;
        return board;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        if (boardName.equals("b")) return null;
        return super.getNewCaptcha(boardName, threadNumber, listener, task);
    }
}
