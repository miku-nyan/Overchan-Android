package nya.miku.wishmaster.chans.wizchan;

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

public class WizchanModule extends AbstractVichanModule {
    private static final String CHAN_NAME = "wizchan.org";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[]{
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "wiz", "General", "", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "dep", "Depression", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hob", "Hobbies", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lounge", "Lounge", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "jp", "Japanese Culture and Media", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "meta", "Suggestions and Feedback", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "games", "Video Games", "", false),
    };

    private static final String DEFAULT_DOMAIN = "wizchan.org";

    public WizchanModule(SharedPreferences preferences, Resources resources){
        super(preferences, resources);
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
    public String getChanName() {
        return CHAN_NAME;
    }

    @Override
    public String getDisplayingName() {
        return "Wizardchan";
    }

    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_wizchan, null);
    }

    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }

    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.allowEmails = false;
        model.attachmentsMaxCount = 3;
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        return model;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String superResult = super.sendPost(model, listener, task);
        return model.sage ? null : superResult;
    }
    
    @Override
    protected String getSendPostEmail(SendPostModel model) {
        return model.sage ? "sage" : "noko";
    }
}
