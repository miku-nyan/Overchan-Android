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

package nya.miku.wishmaster.chans.kropyvach;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class KropyvachModule extends AbstractVichanModule {
    private static final String CHAN_NAME = "kropyva.ch";
    private static final String DEFAULT_DOMAIN = "kropyva.ch";
    private static final String[] ATTACHMENT_FORMATS = new String[] { "jpg", "jpeg", "png", "gif", "webm" };
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Аніме", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Балачки", "", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "bugs", "Зауваження", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c", "Кіно", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "f", "Фап", "", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "g", "Відеоігри", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "i", "International", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "m", "Музика", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "l", "Література", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "p", "Політика", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "t", "Тренування", "", false),
    };
    
    public KropyvachModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Кропивач";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_uchan, null);
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    protected String getUsingDomain() {
        return DEFAULT_DOMAIN;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.bumpLimit = 150;
        model.attachmentsMaxCount = 4;
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        return model;
    }
    
    @Override
    protected AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        AttachmentModel attachment = super.mapAttachment(object, boardName, isSpoiler);
        if ((attachment != null) && (attachment.type == AttachmentModel.TYPE_VIDEO)) {
            attachment.thumbnail = null;
        }
        return attachment;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        super.sendPost(model, listener, task);
        return null;
    }
    
    @Override
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Видалити";
    }
    
    @Override
    protected String getReportFormValue(DeletePostModel model) {
        return "Поскаржитися";
    }
}
