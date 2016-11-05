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

package nya.miku.wishmaster.chans.vichan;

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
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;

public class VichanModule extends AbstractVichanModule {
    private static final String CHAN_NAME = "pl.vichan.net";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Radom", "Ogólne", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "cp", "Chłodne Pasty", "Ogólne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "id", "Inteligentne dyskusje", "Ogólne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "int", "True International", "Ogólne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r+oc", "Prośby + Oryginalna zawartość", "Ogólne", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "slav", "Słowianie", "Ogólne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "veto", "Wolne! Nie pozwalam!", "Ogólne", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "waifu", "Waifu i husbando", "Ogólne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "wiz", "Mizoginia", "Ogólne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "btc", "Biznes i ekonomia", "Tematyczne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c", "Informatyka, Elektronika, Gadżety", "Tematyczne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c++", "Zaawansowana informatyka", "Tematyczne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fso", "Motoryzacja", "Tematyczne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "h", "Hobby i zainteresowania", "Tematyczne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "kib", "Kibice", "Tematyczne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ku", "Kuchnia", "Tematyczne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lsd", "Substancje psychoaktywne", "Tematyczne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "psl", "Polityka", "Tematyczne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sci", "Nauka", "Tematyczne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "trv", "Podróże", "Tematyczne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "Gry video i online", "Tematyczne", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Anime i Manga", "Kultura", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ac", "Komiks i animacja", "Kultura", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "az", "Azja", "Kultura", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fr", "Filozofia & religia", "Kultura", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hk", "Historia & Kultura", "Kultura", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lit", "Literatura", "Kultura", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mu", "Muzyka", "Kultura", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tv", "Filmy i seriale", "Kultura", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vp", "Pokémon", "Kultura", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "x", "Wróżbita Maciej", "Kultura", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "med", "Medyczny", "Życiowe", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pr", "Prawny", "Życiowe", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pro", "Problemy i protipy", "Życiowe", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "psy", "Psychologia", "Życiowe", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sex", "Seks i związki", "Życiowe", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "soc", "Socjalizacja i atencja", "Życiowe", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sr", "Samorozwój", "Życiowe", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "swag", "Styl i wygląd", "Życiowe", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "trap", "Transseksualizm", "Życiowe", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "chan", "Chany i ich kultura", "Chanowe", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "meta", "Administracyjny", "Chanowe", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mit", "Mitomania", "Chanowe", true),
    };

    public VichanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Polski vichan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_vichan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return "pl.vichan.net";
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
        model.attachmentsMaxCount = 4;
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        return model;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        super.sendPost(model, listener, task);
        return null;
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        return super.parseUrl(url.replaceAll("-\\w+.*html", ".html"));
    }

    @Override
    public String fixRelativeUrl(String url) {
        if (url.startsWith("?/")) url = url.substring(1);
        return super.fixRelativeUrl(url);
    }
}
