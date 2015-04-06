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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.cache.BitmapCache;
import nya.miku.wishmaster.cache.DraftsCache;
import nya.miku.wishmaster.cache.FileCache;
import nya.miku.wishmaster.cache.PagesCache;
import nya.miku.wishmaster.cache.Serializer;
import nya.miku.wishmaster.chans.cirno.Chan410Module;
import nya.miku.wishmaster.chans.cirno.CirnoModule;
import nya.miku.wishmaster.chans.cirno.MikubaModule;
import nya.miku.wishmaster.chans.fourchan.FourchanModule;
import nya.miku.wishmaster.chans.makaba.MakabaModule;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.ui.Database;
import nya.miku.wishmaster.ui.downloading.DownloadingLocker;
import nya.miku.wishmaster.ui.settings.ApplicationSettings;
import nya.miku.wishmaster.ui.tabs.TabsState;
import nya.miku.wishmaster.ui.tabs.TabsSwitcher;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;

/**
 * Класс приложения (расширяет {@link Application}).<br>
 * Экземпляр ({@link #getInstance()) хранит объекты, используемые в различных частях проекта.
 * @author miku-nyan
 *
 */

@ReportsCrashes(
        formKey = "",
        formUri = ACRAConstants.ACRA_FORM_URL,
        reportType = org.acra.sender.HttpSender.Type.JSON,
        httpMethod = org.acra.sender.HttpSender.Method.PUT,
        formUriBasicAuthLogin = ACRAConstants.ACRA_LOGIN,
        formUriBasicAuthPassword = ACRAConstants.ACRA_PASSWORD )

public class MainApplication extends Application {
    
    private static MainApplication instance;
    public static MainApplication getInstance() {
        if (instance == null) throw new IllegalStateException("Must be called after onCreate()");
        return instance;
    }
    
    public SharedPreferences preferences;
    public Resources resources;
    public ApplicationSettings settings;
    public FileCache fileCache;
    public Serializer serializer;
    public BitmapCache bitmapCache;
    public PagesCache pagesCache;
    public DraftsCache draftsCache;
    public Database database;
    public DownloadingLocker downloadingLocker;
    
    public TabsState tabsState;
    public TabsSwitcher tabsSwitcher;
        
    public Map<String, Integer> chanModulesIndex;
    public List<ChanModule> chanModulesList;
    
    private void registerChanModules() {
        addChanModule(new CirnoModule(preferences, resources));
        addChanModule(new MikubaModule(preferences, resources));
        addChanModule(new Chan410Module(preferences, resources));
        addChanModule(new MakabaModule(preferences, resources));
        addChanModule(new FourchanModule(preferences, resources));
    }
    
    public ChanModule getChanModule(String chanName) {
        if (!chanModulesIndex.containsKey(chanName)) return null;
        return chanModulesList.get(chanModulesIndex.get(chanName).intValue());
    }
    
    public void addChanModule(ChanModule module) {
        chanModulesIndex.put(module.getChanName(), chanModulesList.size());
        chanModulesList.add(module);
    }
    
    private void initObjects() {
        HttpStreamer.initInstance();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        resources = this.getResources();
        settings = new ApplicationSettings(preferences, resources);
        fileCache = new FileCache(getAvailableCacheDir(), settings.getMaxCacheSize());
        serializer = new Serializer(fileCache);
        tabsState = serializer.deserializeTabsState();
        tabsSwitcher = new TabsSwitcher();
        
        long maxHeapSize = Runtime.getRuntime().maxMemory();
        bitmapCache = new BitmapCache((int)Math.min(maxHeapSize / 8, Integer.MAX_VALUE), fileCache);
        pagesCache = new PagesCache((int)Math.min(maxHeapSize / 4, Integer.MAX_VALUE), serializer);
        draftsCache = new DraftsCache(10, serializer);
        
        database = new Database(this);
        downloadingLocker = new DownloadingLocker();
        
        chanModulesIndex = new HashMap<String, Integer>();
        chanModulesList = new ArrayList<ChanModule>();
        registerChanModules();
    }
    
    private File getAvailableCacheDir() {
        File externalCacheDir = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            externalCacheDir = CompatibilityImpl.getExternalCacheDir(this);
        } else if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            externalCacheDir = new File(Environment.getExternalStorageDirectory(), "/Android/data/" + getPackageName() + "/cache/");
        }
        return externalCacheDir != null ? externalCacheDir : getCacheDir();
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        if (ACRAConstants.ACRA_ENABLED) ACRA.init(this);
        initObjects();
        instance = this;
    }
    
    @Override
    public void onLowMemory() {
        clearCaches();
        super.onLowMemory();
    }
    
    /**
     * Очистить все кэши в памяти. Вызывать в случае гроб-гроб-кладбище-OutOfMemory, иногда может помочь
     */
    public static void freeMemory() {
        try {
            getInstance().freeMemoryInternal();
        } catch (Exception e) {} //если синглтон MainApplication не создан 
    }
    
    private void freeMemoryInternal() {
        clearCaches();
        System.gc();
        try {
            Thread.sleep(1000);
        } catch (Throwable t) {}
    }
    
    private void clearCaches() {
        pagesCache.clearLru();
        bitmapCache.clearLru();
        draftsCache.clearLru();
    }
    
}
