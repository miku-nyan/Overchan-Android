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

package nya.miku.wishmaster.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.cache.BitmapCache;
import nya.miku.wishmaster.cache.DraftsCache;
import nya.miku.wishmaster.cache.FileCache;
import nya.miku.wishmaster.cache.PagesCache;
import nya.miku.wishmaster.cache.Serializer;
import nya.miku.wishmaster.http.SSLCompatibility;
import nya.miku.wishmaster.http.client.ExtendedTrustManager;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.ui.Database;
import nya.miku.wishmaster.ui.downloading.DownloadingLocker;
import nya.miku.wishmaster.ui.presentation.Subscriptions;
import nya.miku.wishmaster.ui.settings.ApplicationSettings;
import nya.miku.wishmaster.ui.settings.Wifi;
import nya.miku.wishmaster.ui.tabs.TabModel;
import nya.miku.wishmaster.ui.tabs.TabsState;
import nya.miku.wishmaster.ui.tabs.TabsSwitcher;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;

/**
 * Класс приложения (расширяет {@link Application}).<br>
 * Экземпляр ({@link #getInstance()) хранит объекты, используемые в различных частях проекта.
 * @author miku-nyan
 *
 */

@ReportsCrashes(
        formUri = ACRAConstants.ACRA_FORM_URL,
        reportType = org.acra.sender.HttpSender.Type.JSON,
        httpMethod = org.acra.sender.HttpSender.Method.PUT,
        formUriBasicAuthLogin = ACRAConstants.ACRA_LOGIN,
        formUriBasicAuthPassword = ACRAConstants.ACRA_PASSWORD,
        mode = org.acra.ReportingInteractionMode.DIALOG,
        resDialogText = R.string.crash_dialog_text,
        resDialogIcon = android.R.drawable.ic_dialog_info,
        resDialogTitle = R.string.crash_dialog_title,
        resDialogCommentPrompt = R.string.crash_dialog_comment_prompt,
        resDialogOkToast = R.string.crash_dialog_ok_toast )

public class MainApplication extends Application {
    
    private static final String[] MODULES = new String[] {
            "nya.miku.wishmaster.chans.fourchan.FourchanModule",
            "nya.miku.wishmaster.chans.krautchan.KrautModule",
            "nya.miku.wishmaster.chans.infinity.InfinityModule",
            "nya.miku.wishmaster.chans.cirno.CirnoModule",
            "nya.miku.wishmaster.chans.cirno.MikubaModule",
            "nya.miku.wishmaster.chans.dobrochan.DobroModule",
            "nya.miku.wishmaster.chans.dvach.DvachModule",
            "nya.miku.wishmaster.chans.sevenchan.SevenchanModule",
            "nya.miku.wishmaster.chans.infinity.InfinityPlModule",
            "nya.miku.wishmaster.chans.cirno.NowereModule",
            "nya.miku.wishmaster.chans.cirno.Chan410Module",
            "nya.miku.wishmaster.chans.chan420.Chan420Module",
            "nya.miku.wishmaster.chans.cirno.OwlchanModule",
            "nya.miku.wishmaster.chans.horochan.HorochanModule",
            "nya.miku.wishmaster.chans.allchan.AllchanModule",
            "nya.miku.wishmaster.chans.ponyach.PonyachModule",
            "nya.miku.wishmaster.chans.uchan.UchanModule",
            "nya.miku.wishmaster.chans.nullchan.NullchanclubModule",
            "nya.miku.wishmaster.chans.nullchan.NullchaneuModule",
            "nya.miku.wishmaster.chans.nullchan.Null_chanModule",
            "nya.miku.wishmaster.chans.dvachnet.DvachnetModule",
            "nya.miku.wishmaster.chans.mentachsu.MentachsuModule",
            "nya.miku.wishmaster.chans.synch.SynchModule",
            "nya.miku.wishmaster.chans.inach.InachModule",
            "nya.miku.wishmaster.chans.kurisach.KurisachModule",
            "nya.miku.wishmaster.chans.chan10.Chan10Module",
            "nya.miku.wishmaster.chans.haruhichan.HaruhiModule",
            "nya.miku.wishmaster.chans.vichan.VichanModule",
            "nya.miku.wishmaster.chans.lainchan.LainModule",
            "nya.miku.wishmaster.chans.tohnochan.TohnoChanModule",
            "nya.miku.wishmaster.chans.chan76.Chan76Module",
            "nya.miku.wishmaster.chans.dfwk.DFWKModule",
            "nya.miku.wishmaster.chans.newnullchan.NewNullchanModule",
            "nya.miku.wishmaster.chans.anonfm.AnonFmModule",
            "nya.miku.wishmaster.chans.makaba.MakabaModule",
            "nya.miku.wishmaster.chans.arhivach.ArhivachModule",
            "nya.miku.wishmaster.chans.samachan.SamachanModule",
            "nya.miku.wishmaster.chans.tumbach.TumbachModule",
            "nya.miku.wishmaster.chans.wizchan.WizchanModule",
            "nya.miku.wishmaster.chans.infinity.BrchanModule",
            "nya.miku.wishmaster.chans.lampach.LampachModule",
            "nya.miku.wishmaster.chans.kropyvach.KropyvachModule",
            "nya.miku.wishmaster.chans.mentachnet.MentachnetModule",
            "nya.miku.wishmaster.chans.hispachan.HispachanModule",
            "nya.miku.wishmaster.chans.ernstchan.ErnstModule",
            "nya.miku.wishmaster.chans.nullchan.NullchanoneModule",
            "nya.miku.wishmaster.chans.depreschan.DepresModule",
    };
    
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
    public Subscriptions subscriptions;
    public DownloadingLocker downloadingLocker;
    
    public TabsState tabsState;
    public TabsSwitcher tabsSwitcher;
    
    public List<TabModel> pagesToOpen = null;
    
    public List<ChanModule> chanModulesList;
    private Map<String, Integer> chanModulesIndex;
    
    private void registerChanModules() {
        chanModulesIndex = new HashMap<String, Integer>();
        chanModulesList = new ArrayList<ChanModule>();
        registerChanModules(chanModulesList, chanModulesIndex);
    }
    
    public void updateChanModulesOrder() {
        Map<String, ChanModule> instantiatedMap = new HashMap<>();
        for (ChanModule chan : chanModulesList) instantiatedMap.put(chan.getClass().getName(), chan);
        
        Map<String, Integer> indexMap = new HashMap<>();
        List<ChanModule> list = new ArrayList<>();
        registerChanModules(list, indexMap, instantiatedMap);
        chanModulesIndex = indexMap;
        chanModulesList = list;
    }
    
    private void registerChanModules(List<ChanModule> outList, Map<String, Integer> outIndexMap) {
        registerChanModules(outList, outIndexMap, null);
    }
    
    private void registerChanModules(List<ChanModule> outList, Map<String, Integer> outIndexMap, Map<String, ChanModule> instantiatedClassMap) {
        Set<String> added = new HashSet<>();
        JSONArray order;
        try {
            order = new JSONArray(settings.getChansOrderJson());
        } catch (Exception e) {
            order = new JSONArray();
        }
        for (int i=0; i<order.length(); ++i) {
            String module = order.optString(i);
            if (!added.contains(module)) {
                if (instantiatedClassMap != null && instantiatedClassMap.containsKey(module)) {
                    addChanModule(instantiatedClassMap.get(module), outList, outIndexMap);
                } else {
                    addChanModule(module, outList, outIndexMap);
                }
            }
            added.add(module);
        }
        for (String module : MODULES) {
            if (!added.contains(module)) {
                if (instantiatedClassMap != null && instantiatedClassMap.containsKey(module)) {
                    addChanModule(instantiatedClassMap.get(module), outList, outIndexMap);
                } else {
                    addChanModule(module, outList, outIndexMap);
                }
            }
            added.add(module);
        }
    }
    
    private void addChanModule(String className, List<ChanModule> list, Map<String, Integer> indexMap) {
        try {
            Class<?> c = Class.forName(className);
            addChanModule((ChanModule) c.getConstructor(SharedPreferences.class, Resources.class).newInstance(preferences, resources),
                    list, indexMap);
        } catch (Exception e) {}
    }
    
    private void addChanModule(ChanModule module, List<ChanModule> list, Map<String, Integer> indexMap) {
        indexMap.put(module.getChanName(), list.size());
        list.add(module);
    }
    
    public ChanModule getChanModule(String chanName) {
        if (!chanModulesIndex.containsKey(chanName)) return null;
        return chanModulesList.get(chanModulesIndex.get(chanName).intValue());
    }
    
    private void initObjects() {
        SSLCompatibility.fixSSLs(this);
        ExtendedTrustManager.setAppContext(this);
        HttpStreamer.initInstance();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        resources = this.getResources();
        settings = new ApplicationSettings(preferences, resources);
        fileCache = new FileCache(this, settings.getMaxCacheSize());
        serializer = new Serializer(fileCache);
        tabsState = serializer.deserializeTabsState();
        tabsSwitcher = new TabsSwitcher();
        
        long maxHeapSize = Runtime.getRuntime().maxMemory();
        bitmapCache = new BitmapCache((int)Math.min(maxHeapSize / 16, Integer.MAX_VALUE), fileCache);
        pagesCache = new PagesCache((int)Math.min(maxHeapSize / 6, Integer.MAX_VALUE), serializer);
        draftsCache = new DraftsCache(10, serializer);
        
        database = new Database(this);
        subscriptions = new Subscriptions(this);
        downloadingLocker = new DownloadingLocker();
        
        registerChanModules();
        
        Wifi.register(this);
    }
    
    private String getProcessName() {
        int myPid = android.os.Process.myPid();
        for (RunningAppProcessInfo process : ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getRunningAppProcesses()) {
            if (myPid == process.pid) return process.processName;
        }
        return null;
    }
    
    private boolean isGalleryProcess() {
        try {
            return getProcessName().endsWith(":Gallery");
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        if (ACRAConstants.ACRA_ENABLED) ACRA.init(this);
        if (isGalleryProcess()) return;
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
        if (isGalleryProcess()) return;
        pagesCache.clearLru();
        bitmapCache.clearLru();
        draftsCache.clearLru();
    }
    
}
