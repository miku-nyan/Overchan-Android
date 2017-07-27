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

package nya.miku.wishmaster.ui.presentation;

import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.tuple.Triple;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.PageLoaderFromChan;
import nya.miku.wishmaster.cache.BitmapCache;
import nya.miku.wishmaster.cache.PagesCache;
import nya.miku.wishmaster.cache.SerializablePage;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.containers.ReadableContainer;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.lib.ClickableLinksTextView;
import nya.miku.wishmaster.lib.ClickableToast;
import nya.miku.wishmaster.lib.JellyBeanSpanFixTextView;
import nya.miku.wishmaster.lib.SwipeDismissListViewTouchListener;
import nya.miku.wishmaster.lib.pullable_layout.SwipeRefreshLayout;
import nya.miku.wishmaster.ui.AppearanceUtils;
import nya.miku.wishmaster.ui.Attachments;
import nya.miku.wishmaster.ui.BoardsListFragment;
import nya.miku.wishmaster.ui.Clipboard;
import nya.miku.wishmaster.ui.CompatibilityImpl;
import nya.miku.wishmaster.ui.Database;
import nya.miku.wishmaster.ui.MainActivity;
import nya.miku.wishmaster.ui.QuickAccess;
import nya.miku.wishmaster.ui.ReverseImageSearch;
import nya.miku.wishmaster.ui.CompatibilityUtils;
import nya.miku.wishmaster.ui.downloading.DownloadingService;
import nya.miku.wishmaster.ui.gallery.GalleryActivity;
import nya.miku.wishmaster.ui.gallery.GallerySettings;
import nya.miku.wishmaster.ui.downloading.BackgroundThumbDownloader;
import nya.miku.wishmaster.ui.posting.PostFormActivity;
import nya.miku.wishmaster.ui.posting.PostingService;
import nya.miku.wishmaster.ui.presentation.ClickableURLSpan.URLSpanClickListener;
import nya.miku.wishmaster.ui.presentation.FlowTextHelper.FloatingModel;
import nya.miku.wishmaster.ui.presentation.HtmlParser.ImageGetter;
import nya.miku.wishmaster.ui.settings.ApplicationSettings;
import nya.miku.wishmaster.ui.settings.Wifi;
import nya.miku.wishmaster.ui.settings.ApplicationSettings.StaticSettingsContainer;
import nya.miku.wishmaster.ui.tabs.TabModel;
import nya.miku.wishmaster.ui.tabs.TabsState;
import nya.miku.wishmaster.ui.tabs.TabsTrackerService;
import nya.miku.wishmaster.ui.tabs.UrlHandler;
import nya.miku.wishmaster.ui.theme.ThemeUtils;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Основной фрагмент UI (показывает страницу имиджборды)
 * @author miku-nyan
 *
 */
public class BoardFragment extends Fragment implements AdapterView.OnItemClickListener, VolatileSpanClickListener.Listener {
    private static final String TAG = "BoardFragment";
    
    public static final String BROADCAST_PAGE_LOADED = "nya.miku.wishmaster.BROADCAST_ACTION_PAGE_LOADED";
    
    public static View lastFocusedView = null;
    
    private boolean isFailInstance = false;
    
    private PagesCache pagesCache = MainApplication.getInstance().pagesCache;
    private BitmapCache bitmapCache = MainApplication.getInstance().bitmapCache;
    private ApplicationSettings settings = MainApplication.getInstance().settings;
    private MainActivity activity;
    private StaticSettingsContainer staticSettings;
    private Resources resources;
    private Database database;
    private Subscriptions subscriptions;
    private ReadableContainer localFile;
    private ChanModule chan;
    
    private PresentationModel presentationModel;
    
    private volatile boolean listLoaded = false;
    
    private static final int TYPE_THREADSLIST = 0;
    private static final int TYPE_POSTSLIST = 1;
    private static final int TYPE_SEARCHLIST = 2;
    private int pageType;
    
    private TabModel tabModel;
    private String startItem;
    private int startItemPosition = -1;
    private int startItemTop;
    private int firstUnreadPosition = 0;
    private boolean forceUpdateFirstTime;
    
    private int nullAdapterSavedPosition;
    private int nullAdapterSavedTop;
    private String nullAdapterSavedNumber;
    private boolean nullAdapterIsSet = false;
    
    private Menu menu;
    private Boolean enableQuickAccessMenu = null;
    
    private View rootView;
    private View loadingView;
    private View errorView;
    private TextView errorTextView;
    private SwipeRefreshLayout pullableLayout;
    private long pullableLayoutSetRefreshingTime;
    private ListView listView;
    private PostsListAdapter adapter;
    private View navigationBarView;
    private Spinner catalogBarView;
    private View searchBarView;
    
    private boolean searchBarInitialized = false;
    private String cachedSearchRequest = null;
    private List<Integer> cachedSearchResults = null;
    private SparseArray<Spanned> cachedSearchHighlightedSpanables = null;
    private boolean searchHighlightActive = false;
    
    private FloatingModel[] floatingModels;
    private ImageGetter imageGetter;
    private URLSpanClickListener spanClickListener;
    private CancellableTask currentTask;
    private CancellableTask imagesDownloadTask = new CancellableTask.BaseCancellableTask();
    private ExecutorService imagesDownloadExecutor = Executors.newFixedThreadPool(4, Async.LOW_PRIORITY_FACTORY);
    private OpenedDialogs dialogs = new OpenedDialogs();
    
    private boolean updatingNow = false;
    
    /** измеряется при вызове {@link #measureFloatingModels(LayoutInflater)} */
    private int postItemWidth = 0;
    /** измеряется при вызове {@link #measureFloatingModels(LayoutInflater)} */
    private int postItemPadding = 0;
    /** измеряется при вызове {@link #measureFloatingModels(LayoutInflater)} */
    private int thumbnailWidth = 0;
    /** измеряется при вызове {@link #measureFloatingModels(LayoutInflater)} */
    private int thumbnailMargin = 0;
    /** количество строк в предпросмотре (оп-посте) треда в списке тредов.
     *  измеряется при вызове {@link #measureFloatingModels(LayoutInflater)} */
    private int maxItemLines = 0;
    
    /** максимальная длина строки заголовка вкладки */
    private static final int MAX_TITLE_LENGHT = 200;
    
    private static final long PULLABLE_ANIMATION_DELAY = 600;
    
    /** позиция (в адаптере) последнего выбранного элемента при создании контекстного меню из всплывающего окна
     *  или -1, если контекстное меню создано из listView */
    private int lastContextMenuPosition;
    
    /** выбранное вложение (аттачмент) при создании контекстного меню из превью-картинки */
    private View lastContextMenuAttachment;
    
    /** listener-обработчик, используется, т.к. при создании контекстного меню из диалога onContextItemSelected (метод фрагмента) не вызывается */
    private MenuItem.OnMenuItemClickListener contextMenuListener = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            return onContextItemSelected(item);
        }
    };
    
    public static Fragment newInstance(long tabId) {
        TabsState tabsState = MainApplication.getInstance().tabsState;
        if (tabsState == null) throw new IllegalStateException("tabsState was not initialized in the MainApplication singleton");
        TabModel model = tabsState.findTabById(tabId);
        if (model == null) throw new IllegalArgumentException("cannot find tab with id "+tabId);
        
        if (model.pageModel.type == UrlPageModel.TYPE_INDEXPAGE) {
            Logger.d(TAG, "instantiating BoardsListFragment");
            return BoardsListFragment.newInstance(tabId);
        }
        if (model.pageModel.type == UrlPageModel.TYPE_OTHERPAGE)
            throw new IllegalArgumentException("page could not be handled (pageModel.type == TYPE_OTHERPAGE)"); 
        BoardFragment fragment = new BoardFragment();
        Bundle args = new Bundle(1);
        args.putLong("TabModelId", tabId);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (MainActivity) getActivity();
        staticSettings = activity.settings;
        resources = MainApplication.getInstance().resources;
        database = MainApplication.getInstance().database;
        subscriptions = MainApplication.getInstance().subscriptions;
        Wifi.updateState(activity);
        
        TabsState tabsState = MainApplication.getInstance().tabsState;
        if (tabsState == null) throw new IllegalStateException("tabsState was not initialized in the MainApplication singleton");
        long tabId = getArguments().getLong("TabModelId");
        tabModel = tabsState.findTabById(tabId);
        if (tabModel == null) { //периодически (видно в крашрепортах ACRA) создаются инстансы с несуществующим (удалённым??) tabId
            isFailInstance = true;
            return;
        }
        
        startItem = tabModel.startItemNumber;
        startItemTop = tabModel.startItemTop;
        forceUpdateFirstTime = tabModel.forceUpdate;
        firstUnreadPosition = tabModel.firstUnreadPosition;
        if (tabModel.forceUpdate || tabModel.autoupdateError || tabModel.unreadPostsCount > 0) {
            tabModel.forceUpdate = false;
            tabModel.autoupdateError = false;
            tabModel.unreadSubscriptions = false;
            tabModel.unreadPostsCount = 0;
            MainApplication.getInstance().serializer.serializeTabsState(tabsState);
            if (activity.tabsAdapter != null) activity.tabsAdapter.notifyDataSetChanged(false);
        }
        
        chan = MainApplication.getInstance().getChanModule(tabModel.pageModel.chanName);
        setHasOptionsMenu(true);
        switch (tabModel.pageModel.type) {
            case UrlPageModel.TYPE_BOARDPAGE:
            case UrlPageModel.TYPE_CATALOGPAGE:
                pageType = TYPE_THREADSLIST;
                break;
            case UrlPageModel.TYPE_SEARCHPAGE:
                pageType = TYPE_SEARCHLIST;
                break;
            case UrlPageModel.TYPE_THREADPAGE:
                pageType = TYPE_POSTSLIST;
                break;
        }
        if (tabModel.type == TabModel.TYPE_LOCAL) {
            try {
                localFile = ReadableContainer.obtain(new File(tabModel.localFilePath));
                MainApplication.getInstance().database.addSavedThread(chan.getChanName(), tabModel.title, tabModel.localFilePath);
            } catch (Exception e) {
                MainApplication.getInstance().database.removeSavedThread(tabModel.localFilePath);
                localFile = null;
                Logger.e(TAG, "cannot open local file", e);
            }
        }
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (isFailInstance) {
            Logger.e(TAG, "an instance with NULL tabModel was created");
            Toast.makeText(activity, R.string.error_unknown, Toast.LENGTH_LONG).show();
            return new View(activity);
        }
        rootView = inflater.inflate(R.layout.board_fragment, container, false);
        /*{
            ImageView mikuView = new ImageView(activity);
            mikuView.setImageResource(R.drawable.miku);
            ((FrameLayout) rootView.findViewById(R.id.board_main_frame)).addView(mikuView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.RIGHT));
        }*/
        loadingView = rootView.findViewById(R.id.board_loading);
        errorView = rootView.findViewById(R.id.board_error);
        errorTextView = (TextView)errorView.findViewById(R.id.frame_error_text);
        catalogBarView = (Spinner) rootView.findViewById(R.id.board_catalog_bar);
        navigationBarView = rootView.findViewById(R.id.board_navigation_bar);
        searchBarView = rootView.findViewById(R.id.board_search_bar);
        pullableLayout = (SwipeRefreshLayout)rootView.findViewById(R.id.board_pullable_layout);
        listView = (ListView)rootView.findViewById(android.R.id.list);
        if (pageType != TYPE_POSTSLIST) listView.setOnItemClickListener(this);
        registerForContextMenu(listView);
        
        pullableLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                pullableLayoutSetRefreshingTime = System.currentTimeMillis();
                if (tabModel.type == TabModel.TYPE_LOCAL) {
                    setPullableNoRefreshing();
                    openFromChan();
                } else {
                    update(true, false, false);
                }
            }
        });
        
        BitmapCache bitmapCache = MainApplication.getInstance().bitmapCache;
        imageGetter = new AsyncImageGetter(resources, R.dimen.inpost_image_size, bitmapCache,
                chan, imagesDownloadExecutor, imagesDownloadTask, listView, Async.UI_HANDLER, staticSettings);
        spanClickListener = new VolatileSpanClickListener(this);
        floatingModels = measureFloatingModels(inflater);
        
        activity.setTitle(tabModel.title);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            CompatibilityImpl.setActionBarCustomFavicon(activity, chan.getChanFavicon());
        update(forceUpdateFirstTime, false, false);
        return rootView;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        presentationModel = null; //если фрагмент всё-таки не уничтожится, позволить GC убрать хотя бы основные данные
        
        finalizeSearchBar();
        if (listView != null) {
            listView.setOnCreateContextMenuListener(null);
            listView.setOnItemClickListener(null);
            listView.setOnTouchListener(null);
            listView.setOnScrollListener(null);
            listView.setAdapter(null);
        }
        if (pullableLayout != null) {
            pullableLayout.setOnRefreshListener(null);
            pullableLayout.setOnEdgeReachedListener(null);
        }
        
        if (tabModel != null && tabModel.type == TabModel.TYPE_LOCAL) {
            try {
                if (localFile != null) localFile.close();
            } catch (Exception e) {
                Logger.e(TAG, "cannot close local file", e);
            }
        }
        
        imagesDownloadExecutor.shutdown();
        
        if (tabModel != null) dialogs.onDestroyFragment(tabModel.id);
    }
    
    private void saveHistory() {
        if (tabModel.type == TabModel.TYPE_LOCAL) return;
        if (tabModel.pageModel.type != UrlPageModel.TYPE_BOARDPAGE && tabModel.pageModel.type != UrlPageModel.TYPE_THREADPAGE) return;
        database.addHistory(
                tabModel.pageModel.chanName,
                tabModel.pageModel.boardName,
                tabModel.pageModel.type == UrlPageModel.TYPE_BOARDPAGE ? Integer.toString(tabModel.pageModel.boardPage) : null,
                tabModel.pageModel.type == UrlPageModel.TYPE_THREADPAGE ? tabModel.pageModel.threadNumber : null,
                tabModel.title,
                tabModel.webUrl);
    }
    
    private void updateHistoryFavorites() {
        if (tabModel.type == TabModel.TYPE_LOCAL) return;
        if (tabModel.pageModel.type != UrlPageModel.TYPE_BOARDPAGE && tabModel.pageModel.type != UrlPageModel.TYPE_THREADPAGE) return;
        database.updateHistoryFavoritesEntries(
                tabModel.pageModel.chanName,
                tabModel.pageModel.boardName,
                tabModel.pageModel.type == UrlPageModel.TYPE_BOARDPAGE ? Integer.toString(tabModel.pageModel.boardPage) : null,
                tabModel.pageModel.type == UrlPageModel.TYPE_THREADPAGE ? tabModel.pageModel.threadNumber : null,
                tabModel.title);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        MenuItem itemAddPost = menu.add(Menu.NONE, R.id.menu_add_post, 101,
                resources.getString(pageType == TYPE_POSTSLIST ? R.string.menu_add_post : R.string.menu_add_thread));
        MenuItem itemUpdate = menu.add(Menu.NONE, R.id.menu_update, 102, 
                resources.getString(tabModel.type != TabModel.TYPE_LOCAL ? R.string.menu_update : R.string.menu_from_internet));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            itemAddPost.setIcon(ThemeUtils.getActionbarIcon(activity.getTheme(), resources, R.attr.actionAddPost));
            itemUpdate.setIcon(ThemeUtils.getActionbarIcon(activity.getTheme(), resources, R.attr.actionRefresh));
            CompatibilityImpl.setShowAsActionIfRoom(itemAddPost);
            CompatibilityImpl.setShowAsActionIfRoom(itemUpdate);
        } else {
            itemAddPost.setIcon(R.drawable.ic_menu_edit);
            itemUpdate.setIcon(R.drawable.ic_menu_refresh);
        }
        menu.add(Menu.NONE, R.id.menu_catalog, 103, resources.getString(R.string.menu_catalog)).setIcon(R.drawable.ic_menu_list);
        menu.add(Menu.NONE, R.id.menu_search, 104, resources.getString(R.string.menu_search)).setIcon(android.R.drawable.ic_menu_search);
        menu.add(Menu.NONE, R.id.menu_save_page, 105, resources.getString(R.string.menu_save_page)).setIcon(android.R.drawable.ic_menu_save);
        menu.add(Menu.NONE, R.id.menu_board_gallery, 106, resources.getString(R.string.menu_board_gallery)).setIcon(android.R.drawable.
                ic_menu_slideshow);
        menu.add(Menu.NONE, R.id.menu_quickaccess_add, 107, resources.getString(R.string.menu_quickaccess_add)).setIcon(R.drawable.
                ic_menu_add_bookmark);
        this.menu = menu;
        updateMenu();
    }
    
    private void updateMenu() {
        if (this.menu == null) return;
        try {
            boolean addPostMenuVisible = false;
            boolean updateMenuVisible = false;
            boolean catalogMenuVisible = false;
            boolean searchMenuVisible = false;
            boolean savePageMenuVisible = false;
            boolean boardGallryMenuVisible = false;
            boolean quickaccessAddMenuVisible = false;
            if (tabModel.type != TabModel.TYPE_LOCAL && pageType != TYPE_SEARCHLIST && listLoaded &&
                    !presentationModel.source.boardModel.readonlyBoard) {
                addPostMenuVisible = true;
            }
            if (tabModel.type != TabModel.TYPE_LOCAL || listLoaded) {
                updateMenuVisible = true;
            }
            if (pageType == TYPE_THREADSLIST && listLoaded) {
                if (presentationModel.source.boardModel.catalogAllowed) {
                    catalogMenuVisible = true;
                }
                if (presentationModel.source.boardModel.searchAllowed || tabModel.pageModel.type == UrlPageModel.TYPE_CATALOGPAGE) {
                    searchMenuVisible = true;
                }
                if (enableQuickAccessMenu == null) {
                    quickaccessAddMenuVisible = true;
                    String chanName = tabModel.pageModel.chanName;
                    String boardName = tabModel.pageModel.boardName;
                    for (QuickAccess.Entry entry : QuickAccess.getQuickAccessFromPreferences()) {
                        if (entry.boardName != null && entry.chan != null &&
                                entry.chan.getChanName().equals(chanName) && entry.boardName.equals(boardName)) {
                            quickaccessAddMenuVisible = false;
                            break;
                        }
                    }
                    enableQuickAccessMenu = Boolean.valueOf(quickaccessAddMenuVisible);
                } else {
                    quickaccessAddMenuVisible = enableQuickAccessMenu.booleanValue();
                }
            }
            if (pageType == TYPE_POSTSLIST && listLoaded) {
                searchMenuVisible = true;
                boardGallryMenuVisible = true;
            }
            if (tabModel.type != TabModel.TYPE_LOCAL && pageType == TYPE_POSTSLIST && listLoaded) {
                savePageMenuVisible = true;
            }
            menu.findItem(R.id.menu_add_post).setVisible(addPostMenuVisible);
            menu.findItem(R.id.menu_update).setVisible(updateMenuVisible);
            menu.findItem(R.id.menu_catalog).setVisible(catalogMenuVisible);
            menu.findItem(R.id.menu_search).setVisible(searchMenuVisible);
            menu.findItem(R.id.menu_save_page).setVisible(savePageMenuVisible);
            menu.findItem(R.id.menu_board_gallery).setVisible(boardGallryMenuVisible);
            menu.findItem(R.id.menu_quickaccess_add).setVisible(quickaccessAddMenuVisible);
        } catch (NullPointerException e) {
            Logger.e(TAG, e);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        UrlPageModel model;
        switch (item.getItemId()) {
            case R.id.menu_add_post:
                openPostForm(tabModel.hash, presentationModel.source.boardModel, getSendPostModel());
                return true;
            case R.id.menu_update:
                if (tabModel.type == TabModel.TYPE_LOCAL) {
                    openFromChan();
                } else {
                    update();
                }
                return true;
            case R.id.menu_catalog:
                model = new UrlPageModel();
                model.chanName = chan.getChanName();
                model.type = UrlPageModel.TYPE_CATALOGPAGE;
                model.boardName = tabModel.pageModel.boardName;
                UrlHandler.open(model, activity);
                return true;
            case R.id.menu_search:
                initSearchBar();
                searchBarView.setVisibility(View.VISIBLE);
                ((EditText) searchBarView.findViewById(R.id.board_search_field)).requestFocus();
                return true;
            case R.id.menu_save_page:
                saveThisPage();
                return true;
            case R.id.menu_board_gallery:
                openGridGallery();
                return true;
            case R.id.menu_quickaccess_add:
                QuickAccess.Entry newEntry = new QuickAccess.Entry();
                newEntry.chan = chan;
                newEntry.boardName = presentationModel.source.boardModel.boardName;
                newEntry.boardDescription = presentationModel.source.boardModel.boardDescription;
                List<QuickAccess.Entry> quickaccessList = QuickAccess.getQuickAccessFromPreferences();
                quickaccessList.add(0, newEntry);
                QuickAccess.saveQuickAccessToPreferences(quickaccessList);
                enableQuickAccessMenu = Boolean.FALSE;
                item.setVisible(false);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    protected static void setViewSize(View view, int size){
        ViewGroup.LayoutParams viewLayoutParams = view.getLayoutParams();
        viewLayoutParams.width = size;
        viewLayoutParams.height = size;
        view.setLayoutParams(viewLayoutParams);
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        
        //контекстное меню для превью-аттачментов
        if (v.getTag() != null && v.getTag() instanceof AttachmentModel) {
            lastContextMenuAttachment = v;
            AttachmentModel model = (AttachmentModel) v.getTag();
            
            View tnView = v.findViewById(R.id.post_thumbnail_image);
            setViewSize(tnView, settings.getPostThumbnailSize());
            if (tnView != null && tnView.getTag() == Boolean.FALSE && !downloadThumbnails()) {
                menu.add(Menu.NONE, R.id.context_menu_thumb_load_thumb, 1, R.string.context_menu_show_thumbnail).
                        setOnMenuItemClickListener(contextMenuListener);
            }
            menu.add(Menu.NONE, R.id.context_menu_thumb_download, 2, R.string.context_menu_download_file);
            menu.add(Menu.NONE, R.id.context_menu_thumb_copy_url, 3, R.string.context_menu_copy_url);
            menu.add(Menu.NONE, R.id.context_menu_thumb_attachment_info, 4, R.string.context_menu_attachment_info);
            menu.add(Menu.NONE, R.id.context_menu_thumb_reverse_search, 5, R.string.context_menu_reverse_search);
            for (int id : new int[] {
                    R.id.context_menu_thumb_download,
                    R.id.context_menu_thumb_copy_url,
                    R.id.context_menu_thumb_attachment_info,
                    R.id.context_menu_thumb_reverse_search } ) {
                menu.findItem(id).setOnMenuItemClickListener(contextMenuListener);
            }
            switch (model.type) {
                case AttachmentModel.TYPE_AUDIO:
                case AttachmentModel.TYPE_VIDEO:
                case AttachmentModel.TYPE_OTHER_FILE:
                    menu.findItem(R.id.context_menu_thumb_reverse_search).setVisible(false);
                    break;
                case AttachmentModel.TYPE_OTHER_NOTFILE:
                    menu.findItem(R.id.context_menu_thumb_reverse_search).setVisible(false);
                    menu.findItem(R.id.context_menu_thumb_download).setVisible(false);
                    break;
            }
            if (tabModel.type == TabModel.TYPE_LOCAL) {
                menu.findItem(R.id.context_menu_thumb_download).setVisible(false);
            }
            return;
        }
        if (menu.findItem(R.id.context_menu_thumb_copy_url) != null) return;
        
        //контекстное меню для обычных элементов
        boolean isList = true;
        lastContextMenuPosition = -1;
        
        final PresentationItemModel model;
        if (v.getId() == android.R.id.list) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            model = adapter.getItem(info.position);
            if (model.hidden) {
                return;
            }
        } else {
            if (v.getTag() != null && v.getTag() instanceof PostsListAdapter.PostViewTag) {
                PostsListAdapter.PostViewTag tag = (PostsListAdapter.PostViewTag) v.getTag();
                if (!tag.isPopupDialog) return;
                isList = false;
                lastContextMenuPosition = tag.position;
                model = adapter.getItem(lastContextMenuPosition);
            } else {
                return;
            }
        }
        
        if (pageType == TYPE_POSTSLIST) {
            menu.add(Menu.NONE, R.id.context_menu_reply, 1, R.string.context_menu_reply);
            menu.add(Menu.NONE, R.id.context_menu_reply_with_quote, 2, R.string.context_menu_reply_with_quote);
            menu.add(Menu.NONE, R.id.context_menu_select_text, 3, Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && isList ?
                    R.string.context_menu_select_text : R.string.context_menu_copy_text);
            menu.add(Menu.NONE, R.id.context_menu_share, 4, R.string.context_menu_share);
            menu.add(Menu.NONE, R.id.context_menu_hide, 5, R.string.context_menu_hide_post);
            menu.add(Menu.NONE, R.id.context_menu_delete, 6, R.string.context_menu_delete);
            menu.add(Menu.NONE, R.id.context_menu_report, 7, R.string.context_menu_report);
            menu.add(Menu.NONE, R.id.context_menu_subscribe, 8, R.string.context_menu_subscribe);
            if (!isList) {
                for (int id : new int[] {
                        R.id.context_menu_reply,
                        R.id.context_menu_reply_with_quote,
                        R.id.context_menu_select_text,
                        R.id.context_menu_share,
                        R.id.context_menu_hide,
                        R.id.context_menu_delete,
                        R.id.context_menu_report,
                        R.id.context_menu_subscribe} ) {
                    menu.findItem(id).setOnMenuItemClickListener(contextMenuListener);
                }
            }
            if (presentationModel.source.boardModel.readonlyBoard || tabModel.type == TabModel.TYPE_LOCAL) {
                menu.findItem(R.id.context_menu_reply).setVisible(false);
                menu.findItem(R.id.context_menu_reply_with_quote).setVisible(false);
            }
            if (model.isDeleted || (!presentationModel.source.boardModel.allowDeletePosts && (!presentationModel.source.boardModel.allowDeleteFiles ||
                    model.sourceModel.attachments == null || model.sourceModel.attachments.length == 0)) || tabModel.type == TabModel.TYPE_LOCAL) {
                menu.findItem(R.id.context_menu_delete).setVisible(false);
            }
            if (model.isDeleted || presentationModel.source.boardModel.allowReport == BoardModel.REPORT_NOT_ALLOWED ||
                    tabModel.type == TabModel.TYPE_LOCAL) {
                menu.findItem(R.id.context_menu_report).setVisible(false);
            }
            if (settings.isSubscriptionsEnabled()) {
                if (subscriptions.hasSubscription(chan.getChanName(), presentationModel.source.boardModel.boardName,
                        presentationModel.source.pageModel.threadNumber, model.sourceModel.number)) {
                    menu.findItem(R.id.context_menu_subscribe).setTitle(R.string.context_menu_unsubscribe);
                }
            } else {
                menu.findItem(R.id.context_menu_subscribe).setVisible(false);
            }
        } else if (pageType == TYPE_THREADSLIST && isList) {
            menu.add(Menu.NONE, R.id.context_menu_open_in_new_tab, 1, R.string.context_menu_open_in_new_tab);
            menu.add(Menu.NONE, R.id.context_menu_thread_preview, 2, R.string.context_menu_thread_preview);
            menu.add(Menu.NONE, R.id.context_menu_reply_no_reading, 3, R.string.context_menu_reply_no_reading);
            menu.add(Menu.NONE, R.id.context_menu_hide, 4, R.string.context_menu_hide_thread);
            if (presentationModel.source.boardModel.readonlyBoard || tabModel.type == TabModel.TYPE_LOCAL) {
                menu.findItem(R.id.context_menu_reply_no_reading).setVisible(false);
            }
        }
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        //контекстное меню для превью-аттачментов
        switch (item.getItemId()) {
            case R.id.context_menu_thumb_load_thumb:
                bitmapCache.asyncGet(
                        ChanModels.hashAttachmentModel((AttachmentModel) lastContextMenuAttachment.getTag()),
                        ((AttachmentModel) lastContextMenuAttachment.getTag()).thumbnail,
                        settings.getPostThumbnailSize(),
                        chan,
                        null,
                        imagesDownloadTask,
                        (ImageView) lastContextMenuAttachment.findViewById(R.id.post_thumbnail_image),
                        imagesDownloadExecutor,
                        Async.UI_HANDLER,
                        true,
                        R.drawable.thumbnail_error);
                return true;
            case R.id.context_menu_thumb_download:
                downloadFile((AttachmentModel) lastContextMenuAttachment.getTag());
                return true;
            case R.id.context_menu_thumb_copy_url:
                String url = chan.fixRelativeUrl(((AttachmentModel) lastContextMenuAttachment.getTag()).path);
                Clipboard.copyText(activity, url);
                Toast.makeText(activity, resources.getString(R.string.notification_url_copied, url), Toast.LENGTH_LONG).show();
                return true;
            case R.id.context_menu_thumb_attachment_info:
                String info = Attachments.getAttachmentInfoString(chan, ((AttachmentModel) lastContextMenuAttachment.getTag()), resources);
                Toast.makeText(activity, info, Toast.LENGTH_LONG).show();
                return true;
            case R.id.context_menu_thumb_reverse_search:
                ReverseImageSearch.openDialog(activity, chan.fixRelativeUrl(((AttachmentModel) lastContextMenuAttachment.getTag()).path));
                return true;
        }
        
        //контекстное меню для обычных постов
        int position = lastContextMenuPosition;
        if (item.getMenuInfo() != null && item.getMenuInfo() instanceof AdapterView.AdapterContextMenuInfo) {
            position = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position;
        }
        if (nullAdapterIsSet || position == -1 || adapter.getCount() <= position) return false;
        switch (item.getItemId()) {
            case R.id.context_menu_open_in_new_tab:
                UrlPageModel modelNewTab = new UrlPageModel();
                modelNewTab.chanName = chan.getChanName();
                modelNewTab.type = UrlPageModel.TYPE_THREADPAGE;
                modelNewTab.boardName = tabModel.pageModel.boardName;
                modelNewTab.threadNumber = adapter.getItem(position).sourceModel.parentThread;
                String tabTitle = null;
                String subject = adapter.getItem(position).sourceModel.subject;
                if (subject != null && subject.length() != 0) {
                    tabTitle = subject;
                } else {
                    Spanned spannedComment = adapter.getItem(position).spannedComment;
                    if (spannedComment != null) {
                        tabTitle = spannedComment.toString().replace('\n', ' ');
                        if (tabTitle.length() > MAX_TITLE_LENGHT) tabTitle = tabTitle.substring(0, MAX_TITLE_LENGHT);
                    }
                }
                if (tabTitle != null) tabTitle = resources.getString(R.string.tabs_title_threadpage_loaded, modelNewTab.boardName, tabTitle);
                UrlHandler.open(modelNewTab, activity, false, tabTitle);
                return true;
            case R.id.context_menu_thread_preview:
                showThreadPreviewDialog(position);
                return true;
            case R.id.context_menu_reply_no_reading:
                UrlPageModel model = new UrlPageModel();
                model.chanName = chan.getChanName();
                model.type = UrlPageModel.TYPE_THREADPAGE;
                model.boardName = tabModel.pageModel.boardName;
                model.threadNumber = adapter.getItem(position).sourceModel.parentThread;
                openPostForm(ChanModels.hashUrlPageModel(model), presentationModel.source.boardModel, getSendPostModel(model));
                return true;
            case R.id.context_menu_hide:
                adapter.getItem(position).hidden = true;
                database.addHidden(
                        tabModel.pageModel.chanName,
                        tabModel.pageModel.boardName,
                        pageType == TYPE_POSTSLIST ? tabModel.pageModel.threadNumber : adapter.getItem(position).sourceModel.number,
                        pageType == TYPE_POSTSLIST ? adapter.getItem(position).sourceModel.number : null);
                adapter.notifyDataSetChanged();
                return true;
            case R.id.context_menu_reply:
                openReply(position, false, null);
                return true;
            case R.id.context_menu_reply_with_quote:
                openReply(position, true, null);
                return true;
            case R.id.context_menu_select_text:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && lastContextMenuPosition == -1) {
                    int firstPosition = listView.getFirstVisiblePosition() - listView.getHeaderViewsCount();
                    int wantedChild = position - firstPosition;
                    if (wantedChild >= 0 && wantedChild < listView.getChildCount()) {
                        View v = listView.getChildAt(wantedChild);
                        if (v != null && v.getTag() != null && v.getTag() instanceof PostsListAdapter.PostViewTag) {
                            ((PostsListAdapter.PostViewTag)v.getTag()).commentView.startSelection();
                            return true;
                        }
                    }
                }
                Clipboard.copyText(activity, adapter.getItem(position).spannedComment.toString());
                Toast.makeText(activity, resources.getString(R.string.notification_comment_copied), Toast.LENGTH_LONG).show();
                return true;
            case R.id.context_menu_share:
                UrlPageModel sharePostUrlPageModel = new UrlPageModel();
                sharePostUrlPageModel.chanName = chan.getChanName();
                sharePostUrlPageModel.type = UrlPageModel.TYPE_THREADPAGE;
                sharePostUrlPageModel.boardName = tabModel.pageModel.boardName;
                sharePostUrlPageModel.threadNumber = tabModel.pageModel.threadNumber;
                sharePostUrlPageModel.postNumber = adapter.getItem(position).sourceModel.number;
                
                Intent sharePostIntent = new Intent(Intent.ACTION_SEND);
                sharePostIntent.setType("text/plain");
                sharePostIntent.putExtra(Intent.EXTRA_SUBJECT, chan.buildUrl(sharePostUrlPageModel));
                sharePostIntent.putExtra(Intent.EXTRA_TEXT, adapter.getItem(position).spannedComment.toString());
                startActivity(Intent.createChooser(sharePostIntent, resources.getString(R.string.share_via)));
                return true;
            case R.id.context_menu_delete:
                DeletePostModel delModel = new DeletePostModel();
                delModel.chanName = chan.getChanName();
                delModel.boardName = tabModel.pageModel.boardName;
                delModel.threadNumber = tabModel.pageModel.threadNumber;
                delModel.postNumber = adapter.getItem(position).sourceModel.number;
                runDelete(delModel,
                        adapter.getItem(position).sourceModel.attachments != null && adapter.getItem(position).sourceModel.attachments.length > 0);
                return true;
            case R.id.context_menu_report:
                DeletePostModel reportModel = new DeletePostModel();
                reportModel.chanName = chan.getChanName();
                reportModel.boardName = tabModel.pageModel.boardName;
                reportModel.threadNumber = tabModel.pageModel.threadNumber;
                reportModel.postNumber = adapter.getItem(position).sourceModel.number;
                runReport(reportModel);
                return true;
            case R.id.context_menu_subscribe:
                String chanName = chan.getChanName();
                String board = tabModel.pageModel.boardName;
                String thread = tabModel.pageModel.threadNumber;
                String post = adapter.getItem(position).sourceModel.number;
                if (subscriptions.hasSubscription(chanName, board, thread, post)) {
                    subscriptions.removeSubscription(chanName, board, thread, post);
                    for (int i=position; i<adapter.getCount(); ++i) adapter.getItem(i).onUnsubscribe(post);
                } else {
                    subscriptions.addSubscription(chanName, board, thread, post);
                    for (int i=position; i<adapter.getCount(); ++i) adapter.getItem(i).onSubscribe(post);
                }
                adapter.notifyDataSetChanged();
                return true;
        }
        return false;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (adapter.getItem(position).hidden) {
            PresentationItemModel model = adapter.getItem(position);
            model.hidden = false;
            database.removeHidden(
                    tabModel.pageModel.chanName,
                    tabModel.pageModel.boardName,
                    pageType == TYPE_POSTSLIST ? tabModel.pageModel.threadNumber : model.sourceModel.number,
                    pageType == TYPE_POSTSLIST ? model.sourceModel.number : null);
            adapter.notifyDataSetChanged();
            return;
        }
        if (pageType != TYPE_POSTSLIST) {
            UrlPageModel model = new UrlPageModel();
            model.chanName = chan.getChanName();
            model.type = UrlPageModel.TYPE_THREADPAGE;
            model.boardName = tabModel.pageModel.boardName;
            model.threadNumber = adapter.getItem(position).sourceModel.parentThread;
            if (pageType == TYPE_SEARCHLIST) {
                PostModel postModel = adapter.getItem(position).sourceModel;
                if (!postModel.parentThread.equals(postModel.number)) {
                    model.postNumber = postModel.number; 
                }
            }
            String tabTitle = null;
            if (pageType == TYPE_THREADSLIST) {
                String subject = adapter.getItem(position).sourceModel.subject;
                if (subject != null && subject.length() != 0) {
                    tabTitle = subject;
                } else {
                    Spanned spannedComment = adapter.getItem(position).spannedComment;
                    if (spannedComment != null) {
                        tabTitle = spannedComment.toString().replace('\n', ' ');
                        if (tabTitle.length() > MAX_TITLE_LENGHT) tabTitle = tabTitle.substring(0, MAX_TITLE_LENGHT);
                    }
                }
                if (tabTitle != null) tabTitle = resources.getString(R.string.tabs_title_threadpage_loaded, model.boardName, tabTitle);
            }
            UrlHandler.open(model, activity, true, tabTitle);
        }
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveCurrentPostPosition();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        activity.setDrawerLock(DrawerLayout.LOCK_MODE_UNLOCKED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) CompatibilityImpl.showActionBar(activity);
        saveCurrentPostPosition();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        try {
            TabsTrackerService.onResumeTab(activity, tabModel.webUrl, tabModel.title);
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (currentTask != null) {
            currentTask.cancel();
        }
        if (imagesDownloadTask != null) {
            imagesDownloadTask.cancel();
        }
        saveCurrentPostPosition();
    }
    
    private void saveCurrentPostPosition() {
        try {
            String startItemNumber;
            int startItemTop;
            if (/*pageType == TYPE_POSTSLIST && */nullAdapterIsSet) {
                startItemNumber = nullAdapterSavedNumber;
                startItemTop = nullAdapterSavedTop;
            } else if (listView != null && listView.getChildCount() > 0 && adapter != null) {
                View v = listView.getChildAt(0);
                int position = listView.getPositionForView(v);
                PresentationItemModel model = adapter.getItem(position);
                startItemNumber = model.sourceModel.number;
                startItemTop = v == null ? 0 : v.getTop();
            } else return;
            if (startItemTop != tabModel.startItemTop || !startItemNumber.equals(tabModel.startItemNumber)) {
                tabModel.startItemNumber = startItemNumber;
                tabModel.startItemTop = startItemTop;
                MainApplication.getInstance().serializer.serializeTabsState(MainApplication.getInstance().tabsState);
            }
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
    }
    
    private void resetFirstUnreadPosition() {
        firstUnreadPosition = adapter.getCount();
        tabModel.firstUnreadPosition = firstUnreadPosition;
        MainApplication.getInstance().serializer.serializeTabsState(MainApplication.getInstance().tabsState);
        adapter.notifyDataSetChanged();
    }
    
    @Override
    public void onURLSpanClick(View v, ClickableURLSpan span, String url, String referer) {
        if (presentationModel == null || presentationModel.presentationList == null) return;
        if (tabModel.pageModel.type != UrlPageModel.TYPE_THREADPAGE) {
            if (!url.startsWith("#")) UrlHandler.open(chan.fixRelativeUrl(url), activity);
            return;
        }
        
        if (url.startsWith(PresentationItemModel.ALL_REFERENCES_URI)) {
            openReferencesList(url.substring(PresentationItemModel.ALL_REFERENCES_URI.length()));
            return;
        }
        
        boolean sameThread = false;
        if (url.startsWith("#")) {
            UrlPageModel thisThreadModel = new UrlPageModel();
            thisThreadModel.chanName = chan.getChanName();
            thisThreadModel.type = UrlPageModel.TYPE_THREADPAGE;
            thisThreadModel.boardName = tabModel.pageModel.boardName;
            thisThreadModel.threadNumber = tabModel.pageModel.threadNumber;
            url = chan.buildUrl(thisThreadModel) + url;
        }
        String fixedUrl = chan.fixRelativeUrl(url);
        UrlPageModel model = UrlHandler.getPageModel(fixedUrl);
        if (model != null && model.type != UrlPageModel.TYPE_OTHERPAGE &&
                (tabModel.type != TabModel.TYPE_LOCAL ?
                        ChanModels.hashUrlPageModel(model).equals(tabModel.hash) :
                        ChanModels.hashUrlPageModel(model).equals(ChanModels.hashUrlPageModel(tabModel.pageModel)))) {
            sameThread = true;
        }
        if (sameThread) {
            if (TextUtils.isEmpty(model.postNumber)) model.postNumber = model.threadNumber;
            int itemPosition = -1;
            for (int i=0; i<presentationModel.presentationList.size(); ++i) {
                if (presentationModel.presentationList.get(i).sourceModel.number.equals(model.postNumber)) {
                    itemPosition = i;
                    break;
                }
            }
            if (itemPosition != -1) {
                if (settings.isPopupLinks()) {
                    String refererPost = null;
                    if (referer != null) {
                        if (referer.startsWith(PresentationItemModel.POST_REFERER)) {
                            refererPost = referer.substring(PresentationItemModel.POST_REFERER.length());
                        } else {
                            try {
                                refererPost = UrlHandler.getPageModel(referer).postNumber;
                            } catch (Exception e) {}
                        }
                    }
                    boolean tabletMode = settings.isRealTablet() && !staticSettings.repliesOnlyQuantity;
                    showPostPopupDialog(itemPosition, tabletMode, getSpanCoordinates(v, span), refererPost);
                } else {
                    listView.setSelection(itemPosition);
                }
            } else {
                Toast.makeText(activity, R.string.notification_post_not_found, Toast.LENGTH_LONG).show();
            }
        } else {
            UrlHandler.open(fixedUrl, activity);
        }
    }
    
    /**
     * Измеряет thumbnail view и создаёт модели обтекания его текстом.
     * Также сохраняет ширину thumbnail view в поле {@link #thumbnailWidth}
     */
    private FloatingModel[] measureFloatingModels(LayoutInflater inflater) {
        Point displaySize = AppearanceUtils.getDisplaySize(activity.getWindowManager().getDefaultDisplay());
        
        LinearLayout view = (LinearLayout)inflater.inflate(R.layout.post_item_layout, (ViewGroup) rootView, false);
        
        TextView commentView = (TextView)view.findViewById(R.id.post_comment);
        TextPaint textPaint = commentView.getPaint(); 
        int textLineHeight = Math.max(1, commentView.getLineHeight());
        int rootWidth = (int) (displaySize.x * settings.getRootViewWeight()); 
        postItemPadding = view.getPaddingLeft() + view.getPaddingRight();
        int textWidth = postItemWidth = rootWidth - postItemPadding;
        
        View thumbnailView = view.findViewById(R.id.post_thumbnail);
        View thumbnailImage = thumbnailView.findViewById(R.id.post_thumbnail_image);
        setViewSize(thumbnailImage, settings.getPostThumbnailSize());
        ViewGroup.MarginLayoutParams thumbnailLayoutParams = (ViewGroup.MarginLayoutParams)thumbnailView.getLayoutParams();
        thumbnailMargin = thumbnailLayoutParams.leftMargin + thumbnailLayoutParams.rightMargin;
        
        View attachmentTypeView = thumbnailView.findViewById(R.id.post_thumbnail_attachment_type);
        ((TextView)attachmentTypeView).setMaxWidth(settings.getPostThumbnailSize());
        FloatingModel[] floatingModels = new FloatingModel[2];
        
        attachmentTypeView.setVisibility(View.GONE);
        thumbnailView.measure(displaySize.x, displaySize.y);
        Point thumbnailSize = new Point(thumbnailMargin + thumbnailView.getMeasuredWidth(), thumbnailView.getMeasuredHeight());
        floatingModels[0] = new FloatingModel(thumbnailSize, textWidth, textPaint);
        
        attachmentTypeView.setVisibility(View.VISIBLE);
        thumbnailView.measure(displaySize.x, displaySize.y);
        thumbnailSize = new Point(thumbnailMargin + thumbnailView.getMeasuredWidth(), thumbnailView.getMeasuredHeight());
        floatingModels[1] = new FloatingModel(thumbnailSize, textWidth, textPaint);
        
        thumbnailWidth = thumbnailSize.x;
        maxItemLines = divcell(thumbnailSize.y, textLineHeight);
        
        return floatingModels;
    }
    
    private void switchToLoadingView() {
        loadingView.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        pullableLayout.setVisibility(View.GONE);
        catalogBarView.setVisibility(View.GONE);
        searchBarView.setVisibility(View.GONE);
        navigationBarView.setVisibility(View.GONE);
    }
    
    private String fixErrorMessage(String message) {
        if (message == null || message.length() == 0) {
            return resources.getString(R.string.error_unknown);
        }
        return message;
    }
    
    private void switchToErrorView(String message) {
        switchToErrorView(message, false);
    }
    
    private void switchToErrorView(String message, boolean silent) {
        if (listLoaded) {
            setPullableNoRefreshing();
            if (!silent) showUpdateError(message);
            return;
        }
        loadingView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        pullableLayout.setVisibility(View.GONE);
        catalogBarView.setVisibility(View.GONE);
        searchBarView.setVisibility(View.GONE);
        navigationBarView.setVisibility(View.GONE);
        errorTextView.setText(fixErrorMessage(message));
    }
    
    private void showUpdateError(String message) {
        Toast.makeText(activity, fixErrorMessage(message), Toast.LENGTH_LONG).show();
    }
    
    private void switchToListView() {
        loadingView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        pullableLayout.setVisibility(View.VISIBLE);
        catalogBarView.setVisibility(tabModel.pageModel.type == UrlPageModel.TYPE_CATALOGPAGE ? View.VISIBLE : View.GONE);
        navigationBarView.setVisibility((tabModel.pageModel.type == UrlPageModel.TYPE_BOARDPAGE) || ((tabModel.pageModel.type == UrlPageModel.TYPE_SEARCHPAGE) && presentationModel.source.boardModel.searchPagination) ? View.VISIBLE : View.GONE);
        searchBarView.setVisibility(View.GONE);
        setNavigationCatalogBar();
    }
    
    private class PageGetter extends CancellableTask.BaseCancellableTask implements Runnable {
        private final boolean forceUpdate;
        private final boolean silent;
        
        private PageLoaderFromChan pageLoader = null;
        private final boolean isThreadPage;
        
        public PageGetter(boolean forceUpdate, boolean silent) {
            this.forceUpdate = forceUpdate;
            this.silent = silent;
            isThreadPage = pageType == TYPE_POSTSLIST;
        }
        
        @Override
        public void run() {
            if (forceUpdate) saveHistory();
            
            while (TabsTrackerService.getCurrentUpdatingTabId() == tabModel.id) Thread.yield();
            
            //обработать случай, когда вкладка - локально сохранённая страница
            if (tabModel.type == TabModel.TYPE_LOCAL) {
                if (!forceUpdate) {
                    presentationModel = pagesCache.getPresentationModel(tabModel.hash);
                    if (presentationModel != null) {
                        ((AsyncImageGetter)presentationModel.imageGetter).setObjects(
                                imagesDownloadExecutor, imagesDownloadTask, listView, Async.UI_HANDLER, staticSettings);
                        ((VolatileSpanClickListener)presentationModel.spanClickListener).setListener(BoardFragment.this);
                        presentationModel.setFloatingModels(floatingModels);
                        if (presentationModel == null) return;
                        if (presentationModel.isNotReady()) presentationModel.updateViewModels(true, this, null);
                        toListView(forceUpdate);
                        return;
                    }
                }
                if (localFile != null) {
                    SerializablePage page;
                    try {
                        page = MainApplication.getInstance().serializer.loadPage(localFile.openStream(DownloadingService.MAIN_OBJECT_FILE));
                    } catch (Exception e) {
                        Logger.e(TAG, "cannot deserialize local page from json", e);
                        page = null;
                    }
                    if (page != null) {
                        createPresentationModel(page, false, false);
                        return;
                    }
                }
                Async.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        switchToErrorView(resources.getString(R.string.error_open_local));
                    }
                });
                return;
            }
            
            //нужно ли пытаться получить из кэша/десериализовать
            boolean tryGetFromCache = !listLoaded && (!forceUpdate || isThreadPage);
            
            if (tryGetFromCache) {
                //попробовать получить сразу PresentationModel из LRU-кэша PagesCache в памяти
                presentationModel = pagesCache.getPresentationModel(tabModel.hash);
                if (presentationModel != null) {
                    ((AsyncImageGetter)presentationModel.imageGetter).setObjects(
                            imagesDownloadExecutor, imagesDownloadTask, listView, Async.UI_HANDLER, staticSettings);
                    ((VolatileSpanClickListener)presentationModel.spanClickListener).setListener(BoardFragment.this);
                    presentationModel.setFloatingModels(floatingModels);
                    if (presentationModel == null) return;
                    if (presentationModel.isNotReady()) presentationModel.updateViewModels(isThreadPage, this, null);
                    toListView(forceUpdate);
                } else {
                    SerializablePage pageFromFileCache = pagesCache.getSerializablePage(tabModel.hash);
                    if (pageFromFileCache != null) {
                        createPresentationModel(pageFromFileCache, forceUpdate, false);
                    } else {
                        loadFromChan();
                    }
                }
            } else if (forceUpdate) {
                loadFromChan();
            }
            
        }
        
        /** после загрузки с чана отправляет на ListView */
        private void loadFromChan() {
            final SerializablePage pageFromChan;
            final boolean fromScratch;
            if (presentationModel != null && presentationModel.source != null) {
                pageFromChan = presentationModel.source;
                fromScratch = false;
            } else {
                pageFromChan = new SerializablePage();
                pageFromChan.pageModel = tabModel.pageModel;
                fromScratch = true;
            }
            final int itemsCountBefore =
                    pageFromChan.posts != null ? pageFromChan.posts.length :
                        (pageFromChan.threads != null ? pageFromChan.threads.length : 0);
            pageLoader = new PageLoaderFromChan(pageFromChan, new PageLoaderFromChan.PageLoaderCallback() {
                @Override
                public void onSuccess() {
                    updatingNow = false;
                    if (isCancelled()) return;
                    BackgroundThumbDownloader.download(pageFromChan, imagesDownloadTask);
                    MainApplication.getInstance().subscriptions.checkOwnPost(pageFromChan, itemsCountBefore);
                    if (isCancelled()) return;
                    if (fromScratch) {
                        createPresentationModel(pageFromChan, false, true);
                    } else {
                        presentationModel.updateViewModels(isThreadPage, PageGetter.this, new PresentationModel.RebuildCallback() {
                            @Override
                            public void onRebuild() {
                                try {
                                    View v = listView.getChildAt(0);
                                    nullAdapterSavedPosition = listView.getPositionForView(v);
                                    nullAdapterSavedTop = v.getTop();
                                    nullAdapterSavedNumber = adapter.getItem(nullAdapterSavedPosition).sourceModel.number;
                                } catch (Exception e) {
                                    Logger.e(TAG, e);
                                }
                                nullAdapterIsSet = true;
                                nullAdapter();
                            }
                        });
                        presentationModel = new PresentationModel(presentationModel); //обновить immutable-значение postsCount
                        pagesCache.putPresentationModel(tabModel.hash, presentationModel);
                        if (isCancelled()) return;
                        if (startItem != null) {
                            for (int i=0; i<presentationModel.presentationList.size(); ++i) {
                                if (presentationModel.presentationList.get(i).sourceModel.number.equals(startItem)) {
                                    startItemPosition = i;
                                    break;
                                }
                            }
                        }
                        startItem = null; //уже загрузили с чана а не из кэша, так что дальше искать якорь на данный пост смысла нет
                        if (isCancelled()) return;
                        int checkSubscriptions = subscriptions.checkSubscriptions(pageFromChan, itemsCountBefore);
                        final String newSubscription = checkSubscriptions >= 0 ? pageFromChan.posts[checkSubscriptions].number : null;
                        if (isCancelled()) return;
                        Async.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (presentationModel == null || presentationModel.isNotReady() || adapter == null)
                                    Toast.makeText(activity, R.string.error_unknown, Toast.LENGTH_LONG).show();
                                
                                if (adapter == null) return;
                                if (nullAdapterIsSet) {
                                    listView.setAdapter(adapter);
                                    listView.requestFocus();
                                    hackListViewSetPosition(listView, nullAdapterSavedPosition, nullAdapterSavedTop);
                                    nullAdapterIsSet = false;
                                }
                                adapter.notifyDataSetChanged();
                                if (isThreadPage && adapter.getCount() != itemsCountBefore) resetSearchCache();
                                setPullableNoRefreshing();
                                if (startItemPosition != -1) {
                                    hackListViewSetPosition(listView, startItemPosition, startItemTop);
                                    startItemPosition = -1;
                                }
                                String notification;
                                boolean toastToNewPosts = false;
                                if (isThreadPage) {
                                    int newPostsCount = adapter.getCount() - itemsCountBefore;
                                    if (newPostsCount <= 0) {
                                        notification = resources.getString(R.string.postslist_no_new_posts);
                                    } else {
                                        notification = resources.getQuantityString(
                                                R.plurals.postslist_new_posts_quantity, newPostsCount, newPostsCount);
                                        toastToNewPosts = true;
                                        if (silent && activity.isPaused()) {
                                            TabsTrackerService.setUnread();
                                            if (newSubscription != null) {
                                                TabsTrackerService.addSubscriptionNotification(tabModel.webUrl, newSubscription, tabModel.title);
                                            }
                                        }
                                    }
                                } else {
                                    notification = resources.getString(R.string.postslist_list_updated);
                                }
                                if (!silent) {
                                    if (toastToNewPosts) {
                                        ClickableToast.showText(activity, notification, new ClickableToast.OnClickListener() {
                                            @Override
                                            public void onClick() {
                                                listView.setSelection(itemsCountBefore);
                                            }
                                        });
                                    } else {
                                        Toast.makeText(activity, notification, Toast.LENGTH_LONG).show();
                                    }
                                }
                            }
                        });
                    }
                }
                @Override
                public void onError(final String message) {
                    updatingNow = false;
                    if (isCancelled()) return;
                    Async.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            switchToErrorView(message, silent);
                        }
                    });
                }
                @Override
                public void onInteractiveException(final InteractiveException e) {
                    if (isCancelled()) return;
                    if (silent && activity.isPaused()) {
                        Async.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setPullableNoRefreshing();
                            }
                        });
                        return;
                    }
                    e.handle(activity, PageGetter.this, new InteractiveException.Callback() {
                        @Override public void onSuccess() { updatingNow = false; update(true, false, false); }
                        @Override public void onError(String message) { updatingNow = false; switchToErrorView(message); }
                    });
                }
            }, chan, this);
            if (isCancelled()) return;
            updatingNow = true;
            pageLoader.run();
        }
        
        /**
         * Создаёт (с нуля) {@link PresentationModel} и отправляет на listView
         * @param serializablePage
         * @param needUpdateAfter требуется ли обновить страницу на чане после создания и показа {@link PresentationModel}
         * @param putToFileCache положить соответствующую сериализованную модель SerializablePage в файловый кэш
         */
        private void createPresentationModel(SerializablePage serializablePage, boolean needUpdateAfter, boolean putToFileCache) {
            presentationModel = new PresentationModel(
                    serializablePage,
                    settings.isLocalTime(),
                    settings.isReduceNames(),
                    spanClickListener,
                    imageGetter,
                    activity.getTheme(),
                    pageType == TYPE_THREADSLIST ? null : floatingModels);
            presentationModel.updateViewModels(isThreadPage, PageGetter.this, null);
            pagesCache.putPresentationModel(tabModel.hash, presentationModel, putToFileCache);
            if (isCancelled()) return;
            activity.sendBroadcast(new Intent(BROADCAST_PAGE_LOADED));
            toListView(needUpdateAfter);
        }
        
        private volatile boolean nullAdapterFlag;
        /** обнулить адаптер listView (пока производятся манипуляции с внутренним list), из не-UI потока */
        private void nullAdapter() {
            nullAdapterFlag = true;
            Async.runOnUiThread(new Runnable() {
                public void run() {
                    listView.setAdapter(null);
                    nullAdapterFlag = false;
                }
            });
            while (nullAdapterFlag) Thread.yield();
        }
        
        /** @param needUpdateAfter - требуется ли обновить страницу на чане после показа */
        private void toListView(final boolean needUpdateAfter) {
            if (presentationModel == null || presentationModel.presentationList == null) return;
            adapter = new PostsListAdapter(BoardFragment.this);
            if (presentationModel == null) return;
            if (pageType == TYPE_POSTSLIST && tabModel.firstUnreadPosition == 0) {
                resetFirstUnreadPosition();
            }
            String oldTabTitle = tabModel.title != null ? tabModel.title : "";
            if (presentationModel == null) return;
            if (isThreadPage && presentationModel.presentationList.size() > 0) {
                String tabTitle;
                String subject = presentationModel.presentationList.get(0).sourceModel.subject;
                if (subject != null && subject.length() != 0) {
                    tabTitle = subject;
                } else {
                    tabTitle = presentationModel.presentationList.get(0).spannedComment.toString().replace('\n', ' ');
                    if (tabTitle.length() > MAX_TITLE_LENGHT) {
                        tabTitle = tabTitle.substring(0, MAX_TITLE_LENGHT);
                    }
                }
                tabModel.title = resources.getString(R.string.tabs_title_threadpage_loaded, tabModel.pageModel.boardName, tabTitle);
            } else if (tabModel.pageModel.type == UrlPageModel.TYPE_BOARDPAGE
                    && tabModel.pageModel.boardPage == presentationModel.source.boardModel.firstPage) {
                tabModel.title = resources.getString(R.string.tabs_title_boardpage_first, tabModel.pageModel.boardName);
            }
            final boolean tabTitleChanged = !oldTabTitle.equals(tabModel.title);
            if (tabTitleChanged) updateHistoryFavorites();
            if (startItem != null) {
                for (int i=0; i<presentationModel.presentationList.size(); ++i) {
                    if (presentationModel.presentationList.get(i).sourceModel.number.equals(startItem)) {
                        startItemPosition = i;
                        startItem = null;
                        break;
                    }
                }
            }
            listLoaded = true;
            Async.runOnUiThread(new Runnable() {
                /** установить SwipeDismissListViewTouchListener, если требуется (соответствует версия ОС, открыт список тредов, включена настройка);
                 *  возвращает созданный OnScrollListener */
                private ListView.OnScrollListener setSwipeDismissListener() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1 && pageType == TYPE_THREADSLIST &&
                            settings.swipeToHideThread()) {
                        final SwipeDismissListViewTouchListener touchListener = new SwipeDismissListViewTouchListener(listView,
                                new SwipeDismissListViewTouchListener.DismissCallbacks() {
                            @Override
                            public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                                for (int i : reverseSortedPositions) {
                                    adapter.getItem(i).hidden = true;
                                    database.addHidden(tabModel.pageModel.chanName, tabModel.pageModel.boardName,
                                            adapter.getItem(i).sourceModel.number, null);
                                    adapter.notifyDataSetChanged();
                                }
                            }
                            @Override
                            public boolean canDismiss(int position) {
                                return !adapter.getItem(position).hidden;
                            }
                        });
                        listView.setOnTouchListener(touchListener);
                        return touchListener.makeScrollListener();
                    }
                    return null;
                }
                @Override
                public void run() {
                    if (presentationModel == null || presentationModel.isNotReady())
                        Toast.makeText(activity, R.string.error_unknown, Toast.LENGTH_LONG).show();
                    
                    listView.setAdapter(adapter);
                    listView.requestFocus();
                    final ListView.OnScrollListener swipeDismissOnScrollListener = setSwipeDismissListener();
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ECLAIR_MR1) {
                        //busy состояние адаптера, не загружать картинки из интернета, во время скроллинга
                        listView.setOnScrollListener(new ListView.OnScrollListener() {
                            @Override
                            public void onScrollStateChanged(AbsListView view, int scrollState) {
                                if (swipeDismissOnScrollListener != null) swipeDismissOnScrollListener.onScrollStateChanged(view, scrollState);
                                if (scrollState == ListView.OnScrollListener.SCROLL_STATE_IDLE) {
                                    adapter.setBusy(false);
                                } else {
                                    adapter.setBusy(true);
                                }
                            }
                            
                            @Override
                            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                                //скрытие actionbar
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB ||
                                        !staticSettings.hideActionBar || view.getChildCount() <= 0) return;
                                
                                int firstVisibleTop = view.getChildAt(0).getTop();
                                int topDelta = firstVisibleTop - lastFirstVisibleTop;
                                if (firstVisibleItem == lastFirstVisibleItem && Math.abs(topDelta) < maxTopDelta) {
                                    if ((currentTopDelta < 0) == (topDelta < 0)) currentTopDelta += topDelta; else currentTopDelta = topDelta;
                                } else if (firstVisibleItem != lastFirstVisibleItem && adapter.isBusy) {
                                    currentTopDelta = Integer.signum(lastFirstVisibleItem - firstVisibleItem) * (maxTopDelta + 1);
                                } else {
                                    currentTopDelta = 0;
                                }
                                
                                boolean top = firstVisibleItem == 0 && firstVisibleTop == 0;
                                long currentTime = System.currentTimeMillis();
                                if (top || currentTime - lastActionTime > 1000) {
                                    if (currentTopDelta < -maxTopDelta) {
                                        if (CompatibilityImpl.hideActionBar(activity)) {
                                            lastActionTime = currentTime;
                                            currentTopDelta = 0;
                                        }
                                    } else if (top || currentTopDelta > maxTopDelta) {
                                        if (CompatibilityImpl.showActionBar(activity)) {
                                            lastActionTime = currentTime;
                                            currentTopDelta = 0;
                                        }
                                    }
                                }
                                
                                lastFirstVisibleItem = firstVisibleItem;
                                lastFirstVisibleTop = firstVisibleTop;
                            }
                            private int lastFirstVisibleItem = Integer.MAX_VALUE;
                            private int lastFirstVisibleTop = Integer.MAX_VALUE;
                            private int currentTopDelta = 0;
                            private int maxTopDelta = (int) (resources.getDisplayMetrics().density * 24 + 0.5f);
                            private long lastActionTime = System.currentTimeMillis();
                        });
                        pullableLayout.setOnEdgeReachedListener(new SwipeRefreshLayout.OnEdgeReachedListener() {
                            @Override
                            public void onEdgeReached() {
                                adapter.setBusy(false);
                            }
                        });
                    }
                    switchToListView();
                    updateMenu();
                    if (isThreadPage) {
                        activity.setTitle(tabModel.title);
                    } else if (pageType == TYPE_THREADSLIST) {
                        if (presentationModel != null) activity.setTitle(presentationModel.source.boardModel.boardDescription);
                    }
                    if (activity.tabsAdapter != null && tabTitleChanged) {
                        activity.tabsAdapter.notifyDataSetChanged();
                    }
                    if (startItemPosition != -1) {
                        hackListViewSetPosition(listView, startItemPosition, startItemTop);
                        startItemPosition = -1;
                    }
                    if (needUpdateAfter) {
                        AppearanceUtils.callWhenLoaded(pullableLayout, new Runnable() {
                            @Override
                            public void run() {
                                update(true, true, silent);
                            }
                        });
                    }
                }
            });
        }
        
        @Override
        public void cancel() {
            super.cancel();
            updatingNow = false;
        }
        
    }
    
    private static void hackListViewSetPosition(final ListView listView, final int position, final int top) {
        try {
            listView.setSelectionFromTop(position, top);
            AppearanceUtils.callWhenLoaded(listView, new Runnable() {
                @Override
                public void run() {
                    try {
                        int setPosition = listView.getFirstVisiblePosition();
                        int setTop = listView.getChildAt(0).getTop();
                        int incTop = listView.getChildCount() < 2 ? 0 : Math.max(0, -listView.getChildAt(1).getTop());
                        if (setPosition != position || setTop != top || incTop > 0) {
                            listView.setSelectionFromTop(position, top + incTop);
                        }
                    } catch(Exception e) {
                        Logger.e(TAG, e);
                    }
                }
            });
        } catch(Exception e) {
            Logger.e(TAG, e);
        }
    }
    
    public Intent setIntentExtras(Intent sendIntent) {
        return adapter.setIntentExtras(lastFocusedView, sendIntent);
    }
    
    private static class PostsListAdapter extends ArrayAdapter<PresentationItemModel> {
        private static final int ITEM_VIEW_TYPE_NORMAL = 0;
        private static final int ITEM_VIEW_TYPE_HIDDEN = 1;
        
        private final WeakReference<BoardFragment> fragmentRef;
        
        private volatile boolean isBusy = false;
        
        private final LayoutInflater inflater;
        private final SparseBooleanArray expanded = new SparseBooleanArray();
        private final int thumbnailsInRowCount;
        
        private int currentCount;
        
        private int[] hackListViewPosition = null; //смещение скроллинга, если в последних есть сокращённый длинный пост ("Показать весь текст")
        
        private BoardFragment fragment() {
            return fragmentRef.get();
        }
        
        private static class WeakOnCreateContextMenuListener implements View.OnCreateContextMenuListener {
            private final WeakReference<BoardFragment> fragmentRef;
            public WeakOnCreateContextMenuListener(BoardFragment fragment) {
                this.fragmentRef = new WeakReference<>(fragment);
            }
            @Override
            public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
                BoardFragment fragment = fragmentRef.get();
                if (fragment == null || fragment.presentationModel == null) {
                    Fragment currentFragment = MainApplication.getInstance().tabsSwitcher.currentFragment;
                    if (currentFragment instanceof BoardFragment) fragment = (BoardFragment) currentFragment;
                }
                if (fragment != null) {
                    try {
                        fragment.onCreateContextMenu(menu, v, menuInfo);
                    } catch (Exception e) {
                        Logger.e(TAG, e);
                    }
                }
            }
        }
        
        private void weakRegisterForContextMenu(View v) {
            v.setOnCreateContextMenuListener(new WeakOnCreateContextMenuListener(fragment()));
        }
        
        private static class OnUnreadFrameListener implements View.OnClickListener, View.OnLongClickListener {
            private WeakReference<BoardFragment> fragmentRef;
            public OnUnreadFrameListener(WeakReference<BoardFragment> fragmentRef) {
                this.fragmentRef = fragmentRef;
            }
            @Override
            public boolean onLongClick(View v) {
                fragmentRef.get().resetFirstUnreadPosition();
                return true;
            }
            @Override
            public void onClick(View v) {
                fragmentRef.get().resetFirstUnreadPosition();
            }
        }
        
        private static class OnAttachmentClickListener implements View.OnClickListener {
            private WeakReference<BoardFragment> fragmentRef;
            public OnAttachmentClickListener(WeakReference<BoardFragment> fragmentRef) {
                this.fragmentRef = fragmentRef;
            }
            @Override
            public void onClick(View v) {
                BoardFragment fragment = fragmentRef.get();
                if (fragment == null || fragment.presentationModel == null) {
                    Fragment currentFragment = MainApplication.getInstance().tabsSwitcher.currentFragment;
                    if (currentFragment instanceof BoardFragment) fragment = (BoardFragment) currentFragment;
                }
                if (fragment != null) fragment.openAttachment((AttachmentModel)v.getTag());
            }
        }
        
        private OnUnreadFrameListener onUnreadFrameListener;
        private OnAttachmentClickListener onAttachmentClickListener;
        
        public PostsListAdapter(BoardFragment fragment) {
            super(fragment.activity, 0, fragment.presentationModel.presentationList);
            fragmentRef = new WeakReference<BoardFragment>(fragment);
            onUnreadFrameListener = new OnUnreadFrameListener(fragmentRef);
            onAttachmentClickListener = new OnAttachmentClickListener(fragmentRef);
            if (fragment.presentationModel != null) // может обнулиться в BoardFragment.onDestroy() (т.к. метод работает асинхронно)
                this.currentCount = fragment.presentationModel.presentationList.size();
            this.inflater = LayoutInflater.from(fragment.activity);
            this.thumbnailsInRowCount = Math.max(1, fragment.postItemWidth / fragment.thumbnailWidth);
        }
        
        static class PostViewTag {
            public static final int MAX_BADGE_ICONS = 10;
            public static final int MAX_THUMBNAILS = 20;
            public static final int MAX_THUMBNAIL_ROWS = 20;
            
            public int position;
            public boolean isPopupDialog = false;
            public boolean clickableLinksSet = false;
            
            public View unreadFrame;
            public boolean unreadFrameIsVisible = false;
            
            public JellyBeanSpanFixTextView headerView;
            public TextView threadConditionView;
            public boolean threadConditionIsVisible = false;
            public View deletedPostView;
            public boolean deletedPostViewIsVisible = false;
            public TextView dateView;
            public boolean dateIsVisible = false;
            
            public LinearLayout badgeViewContainer;
            public ImageView[] badgeIcons = new ImageView[MAX_BADGE_ICONS];
            public int badgeIconsInflatedCount = 0;
            public int badgeIconsVisibleCount = 0;
            public TextView badgeText;
            public boolean badgeIsVisible = false;
            
            public LinearLayout multiThumbnailsViewContainer;
            public LinearLayout[] multiThumbnailsRows = new LinearLayout[MAX_THUMBNAIL_ROWS];
            public View[] multiThumbnails = new View[MAX_THUMBNAILS];
            public int multiThumbnailsInflatedCount = 0;
            public int multiThumbnailsVisibleCount = 0;
            public boolean multiThumbnailsIsVisible = false;
            
            public View singleThumbnailView;
            public boolean singleThumbnailIsVisible = false;
            
            public ClickableLinksTextView commentView;
            public boolean commentFloatingPosition = false;
            
            public TextView showFullTextView;
            public boolean showFullTextIsVisible = false;
            public JellyBeanSpanFixTextView repliesView;
            public boolean repliesIsVisible = false;
            public TextView postsCountView;
            public boolean postsCountIsVisible = false;
        }
        
        public Intent setIntentExtras(View v, Intent sendIntent) {
            if (v == null) return sendIntent;
            Object tag = v.getTag();
            if ((tag != null) && (tag instanceof PostViewTag)) {
                String quote = getSelectedText((PostViewTag) tag);
                SendPostModel sendReplyModel = fragment().getSendPostModel();
                sendReplyModel.password = null;
                PresentationItemModel item = fragment().adapter.getItem(((PostViewTag) tag).position);
                String postNumber = item.sourceModel.number;
                MainApplication instance = MainApplication.getInstance();
                ChanModule chan = instance.getChanModule(sendReplyModel.chanName);
                UrlPageModel model = new UrlPageModel();
                model.type = model.TYPE_THREADPAGE;
                model.chanName = sendReplyModel.chanName;
                model.boardName = sendReplyModel.boardName;
                model.threadNumber = sendReplyModel.threadNumber;
                model.postNumber = postNumber;
                String postURI = chan.buildUrl(model);
                Bundle bundle = new Bundle();
                bundle.putSerializable(fragment().getString(R.string.intent_overchan_send_post_model), sendReplyModel);
                bundle.putString(fragment().getString(R.string.intent_overchan_quote_text), quote);
                bundle.putString(fragment().getString(R.string.intent_overchan_post_number), postNumber);
                bundle.putString(fragment().getString(R.string.intent_overchan_post_uri), postURI);
                sendIntent.putExtra(fragment().getString(R.string.intent_overchan_extras), bundle);
            }
            return sendIntent;
        }
        
        private String getSelectedText(PostViewTag tag) {
            int start = tag.commentView.getSelectionStart();
            int end = tag.commentView.getSelectionEnd();
            return tag.commentView.getText().subSequence(start, end).toString();
        }

        @Override
        public int getCount() {
            return currentCount;
        }
        
        @Override
        public void notifyDataSetChanged() {
            try {
                currentCount = fragment().presentationModel.presentationList.size();
                if (fragment().pageType != TYPE_THREADSLIST && fragment().staticSettings.itemHeight != 0) {
                    boolean needHack = false;
                    for (int i=0, len=fragment().listView.getChildCount(); i<len; ++i) {
                        View v = fragment().listView.getChildAt(i);
                        if (v.getTag() instanceof PostViewTag && ((PostViewTag) v.getTag()).showFullTextIsVisible) {
                            needHack = true;
                            break;
                        }
                    }
                    if (needHack) {
                        View v = fragment().listView.getChildAt(0);
                        int position = fragment().listView.getPositionForView(v);
                        hackListViewPosition = new int[] { position, v.getTop() };
                    } else {
                        hackListViewPosition = null;
                    }
                }
            } catch (Exception e) {
                Logger.e(TAG, e);
                hackListViewPosition = null;
            }
            super.notifyDataSetChanged();
        }
        
        @Override
        public int getViewTypeCount() {
            return 2;
        }
        
        @Override
        public int getItemViewType(int position) {
            return this.getItem(position).hidden ? ITEM_VIEW_TYPE_HIDDEN : ITEM_VIEW_TYPE_NORMAL;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getView(position, convertView, parent, null);
        }
        
        /**
         * Измерить значение ширины вида (view) элемента, выше которого устанавливать не имеет смысла
         * @param position позиция элемента в списке
         * @return значение ширины
         */
        public int measureViewWidth(int position) {
            View tmp = getView(position, null, null, Integer.MAX_VALUE);
            tmp.findViewById(R.id.post_frame_main).
                    measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            PostViewTag tag = (PostViewTag)tmp.getTag();
            int width = tmp.findViewById(R.id.post_frame_main).getMeasuredWidth() + (tag.commentFloatingPosition ? fragment().thumbnailWidth : 0);
            if (!tag.dateIsVisible) return width;
            tag.headerView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            tag.dateView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int margin = Math.round(12 * (fragment().resources.getDisplayMetrics().xdpi / DisplayMetrics.DENSITY_DEFAULT));
            int forHeader = tag.headerView.getMeasuredWidth() + tag.dateView.getMeasuredWidth() + margin - width;
            if (forHeader > 0) width += forHeader;
            return width;
        }
        
        /**
         * Построить вид (view) элемента
         * @param position позиция элемента в списке
         * @param convertView старый view для вторичного использования (при вызове через адаптер)
         * @param parent родительский view, к которому будет прикреплён создающийся (при вызове через адаптер)
         * @param popupWidth ширина окна, в котором вид будет отображён (если необходимо получить view для всплывающего диалога).
         * Для получения вида по умолчанию (в адаптере listview) необходимо передать null.
         * @return
         */
        public View getView(int position, View convertView, ViewGroup parent, Integer popupWidth) {
            return getView(position, convertView, parent, popupWidth, null, null);
        }
        
        /**
         * @param position позиция элемента в списке
         * @param convertView старый view для вторичного использования (при вызове через адаптер)
         * @param parent родительский view, к которому будет прикреплён создающийся (при вызове через адаптер)
         * @param popupWidth ширина окна, в котором вид будет отображён (если необходимо получить view для всплывающего диалога).
         * Для получения вида по умолчанию (в адаптере listview) необходимо передать null.
         * @param custom если != null, строится View (только для всплывающего диалога, popupWidth не должно быть равно null)
         * не для элемента на позиции position, а для этой модели. При этом комментарий не переносится в ScrollView (т.к. в диалоге будет ListView)
         */
        public View getView(int position, View convertView, ViewGroup parent, Integer popupWidth, PresentationItemModel custom) {
            return getView(position, convertView, parent, popupWidth, custom, null);
        }
        
        /**
         * @param position позиция элемента в списке
         * @param convertView старый view для вторичного использования (при вызове через адаптер)
         * @param parent родительский view, к которому будет прикреплён создающийся (при вызове через адаптер)
         * @param popupWidth ширина окна, в котором вид будет отображён (если необходимо получить view для всплывающего диалога).
         * Для получения вида по умолчанию (в адаптере listview) необходимо передать null.
         * @param referer номер поста, из которого открывается диалог (здесь будет выделен цветом {@link ThemeUtils.ThemeColors#refererForeground})
         */
        public View getView(int position, View convertView, ViewGroup parent, Integer popupWidth, String referer) {
            return getView(position, convertView, parent, popupWidth, null, referer);
        }
        
        /**
         * @param position позиция элемента в списке
         * @param convertView старый view для вторичного использования (при вызове через адаптер)
         * @param parent родительский view, к которому будет прикреплён создающийся (при вызове через адаптер)
         * @param popupWidth ширина окна, в котором вид будет отображён (если необходимо получить view для всплывающего диалога).
         * Для получения вида по умолчанию (в адаптере listview) необходимо передать null.
         * @param custom если != null, строится View (только для всплывающего диалога, popupWidth не должно быть равно null)
         * не для элемента на позиции position, а для этой модели. При этом комментарий не переносится в ScrollView (т.к. в диалоге будет ListView)
         * @param referer номер поста, из которого открывается диалог (здесь будет выделен цветом {@link ThemeUtils.ThemeColors#refererForeground})
         */
        public View getView(int position, View convertView, ViewGroup parent, Integer popupWidth, PresentationItemModel custom, String referer) {
            final PresentationItemModel model = custom == null ? this.getItem(position) : custom;
            
            //(popupWidth == null) <=> (элемент не для всплывающего диалога, а для ListView)            

            if (popupWidth == null && model.hidden) {
                if (fragment().staticSettings.showHiddenItems) {
                    View view = convertView == null ? inflater.inflate(R.layout.post_item_hidden, parent, false) : convertView;
                    view.setTag(Integer.valueOf(position));
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            fragment().onItemClick(null, null, (Integer)v.getTag(), 0);
                        }
                    });
                    ((TextView) view).setText(fragment().resources.getString(
                            fragment().pageType == TYPE_THREADSLIST ? R.string.postitem_hidden_thread : R.string.postitem_hidden_post,
                            model.sourceModel.number,
                            model.autohideReason != null ? model.autohideReason : model.spannedComment.toString()));
                    return view;
                } else {
                    return convertView == null ? inflater.inflate(R.layout.post_item_null, parent, false) : convertView;
                }
            }
            
            final View view = convertView == null ? inflater.inflate(R.layout.post_item_frame, parent, false) : convertView;
            final PostViewTag tag;
            if (view.getTag() != null) {
                tag = (PostViewTag) view.getTag();
            } else {
                tag = new PostViewTag();
                tag.unreadFrame = view.findViewById(R.id.post_frame_unread);
                tag.unreadFrame.setOnClickListener(onUnreadFrameListener);
                tag.unreadFrame.setOnLongClickListener(onUnreadFrameListener);
                tag.headerView = (JellyBeanSpanFixTextView) view.findViewById(R.id.post_header);
                tag.threadConditionView = (TextView) view.findViewById(R.id.post_thread_condition);
                tag.deletedPostView = view.findViewById(R.id.post_deleted_mark);
                tag.dateView = (TextView) view.findViewById(R.id.post_date);
                tag.badgeViewContainer = (LinearLayout) view.findViewById(R.id.post_badge_container);
                tag.badgeText = (TextView) view.findViewById(R.id.post_badge_title);
                tag.multiThumbnailsViewContainer = (LinearLayout) view.findViewById(R.id.post_multi_thumbnails_container);
                tag.singleThumbnailView = view.findViewById(R.id.post_thumbnail);
                View thumbnailImage = tag.singleThumbnailView.findViewById(R.id.post_thumbnail_image);
                setViewSize(thumbnailImage, MainApplication.getInstance().settings.getPostThumbnailSize());
                TextView size = (TextView) tag.singleThumbnailView.findViewById(R.id.post_thumbnail_attachment_size);
                size.setMaxWidth(MainApplication.getInstance().settings.getPostThumbnailSize());
                TextView type = (TextView) tag.singleThumbnailView.findViewById(R.id.post_thumbnail_attachment_type);
                type.setMaxWidth(MainApplication.getInstance().settings.getPostThumbnailSize());
                tag.commentView = (ClickableLinksTextView) view.findViewById(R.id.post_comment);
                tag.commentView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (hasFocus) lastFocusedView = view;
                    }
                });
                tag.showFullTextView = (TextView) view.findViewById(R.id.post_show_full_text);
                tag.repliesView = (JellyBeanSpanFixTextView) view.findViewById(R.id.post_replies);
                tag.postsCountView = (TextView) view.findViewById(R.id.post_posts_count);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && fragment().pageType == TYPE_POSTSLIST) {
                    CompatibilityImpl.setCustomSelectionActionModeMenuCallback(tag.commentView,
                            R.string.context_menu_reply_with_quote,
                            ThemeUtils.getActionbarIcon(fragment().activity.getTheme(), fragment().resources, R.attr.actionAddPost),
                            new CompatibilityImpl.CustomSelectionActionModeCallback() {
                        @Override
                        public void onClick() {
                            try {
                                String quote = getSelectedText(tag);
                                fragment().openReply(tag.position, true, quote);
                            } catch (Exception e) {
                                Logger.e(TAG, e);
                            }
                        }
                        @Override
                        public void onCreate() {
                            try {
                                if (tag.isPopupDialog || tag.position != getCount() - 1) return;
                                final int margin = (int) (50 * fragment().resources.getDisplayMetrics().density + 0.5f);
                                ViewGroup.LayoutParams params = tag.commentView.getLayoutParams();
                                if (params.height != ViewGroup.LayoutParams.WRAP_CONTENT) return;
                                params.height = tag.commentView.getHeight() + margin;
                                tag.commentView.setLayoutParams(params);
                                fragment().scrollDown();
                                
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                                    final int selectionStart = tag.commentView.getSelectionStart();
                                    final int selectionEnd = tag.commentView.getSelectionEnd();
                                    AppearanceUtils.callWhenLoaded(tag.commentView, new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                ViewGroup.LayoutParams params = tag.commentView.getLayoutParams();
                                                if (params.height != ViewGroup.LayoutParams.WRAP_CONTENT) return;
                                                params.height = tag.commentView.getHeight() + margin;
                                                tag.commentView.setLayoutParams(params);
                                                fragment().scrollDown();
                                                
                                                AppearanceUtils.callWhenLoaded(tag.commentView, new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        try {
                                                            tag.commentView.startSelection();
                                                            Selection.setSelection(
                                                                    (Spannable) tag.commentView.getText(), selectionStart, selectionEnd);
                                                        } catch (Exception e) {
                                                            Logger.e(TAG, e);
                                                        }
                                                    }
                                                });
                                            } catch (Exception e) {
                                                Logger.e(TAG, e);
                                            }
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                Logger.e(TAG, e);
                            }
                        }
                        @Override
                        public void onDestroy() {
                            try {
                                ViewGroup.LayoutParams params = tag.commentView.getLayoutParams();
                                if (params.height == ViewGroup.LayoutParams.WRAP_CONTENT) return;
                                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                                tag.commentView.setLayoutParams(params);
                            } catch (Exception e) {
                                Logger.e(TAG, e);
                            }
                        }
                    });
                }
                view.setTag(tag);
            }
            tag.position = position;
            
            // заголовок и дата
            tag.headerView.setText(model.spannedHeader);
            tag.dateView.setText(model.dateString);
            if (fragment().staticSettings.isDisplayDate) {
                if (!tag.dateIsVisible) {
                    tag.dateView.setVisibility(View.VISIBLE);
                    tag.dateIsVisible = true;
                }
            } else {
                if (tag.dateIsVisible) {
                    tag.dateView.setVisibility(View.GONE);
                    tag.dateIsVisible = false;
                }
            }
            
            // заполнение бэйджа
            int badgeIconsCount = Math.min(model.sourceModel.icons == null ? 0 : model.sourceModel.icons.length, PostViewTag.MAX_BADGE_ICONS);
            if (badgeIconsCount > 0 || (model.badgeTitle != null && model.badgeTitle.length() != 0)) {
                tag.badgeText.setText(model.badgeTitle != null && model.badgeTitle.length() != 0 ? model.badgeTitle : "");
                
                for (int i=tag.badgeIconsVisibleCount; i<badgeIconsCount && i<tag.badgeIconsInflatedCount; ++i)
                    tag.badgeIcons[i].setVisibility(View.VISIBLE);
                for (int i=badgeIconsCount; i<tag.badgeIconsVisibleCount; ++i) tag.badgeIcons[i].setVisibility(View.GONE);
                for (int i=tag.badgeIconsInflatedCount; i<badgeIconsCount; ++i) {
                    tag.badgeIcons[i] = (ImageView)inflater.inflate(R.layout.post_badge_icon, tag.badgeViewContainer, false);
                    tag.badgeViewContainer.addView(tag.badgeIcons[i], tag.badgeIconsInflatedCount);
                    ++tag.badgeIconsInflatedCount;
                }
                tag.badgeIconsVisibleCount = badgeIconsCount;
                
                for (int i=0; i<badgeIconsCount; ++i)
                    fillBadge(tag.badgeIcons[i], model.sourceModel.icons[i].source, model.badgeHashes[i], popupWidth != null);
                
                if (!tag.badgeIsVisible) {
                    tag.badgeViewContainer.setVisibility(View.VISIBLE);
                    tag.badgeIsVisible = true;
                }
            } else {
                if (tag.badgeIsVisible) {
                    tag.badgeViewContainer.setVisibility(View.GONE);
                    tag.badgeIsVisible = false;
                }
            }
            
            //заполнение аттачментов
            int attachmentsCount = Math.min(model.attachmentHashes.length, PostViewTag.MAX_THUMBNAILS);
            if (attachmentsCount == 0) {
                if (tag.singleThumbnailIsVisible) {
                    tag.singleThumbnailView.setVisibility(View.GONE);
                    tag.singleThumbnailIsVisible = false;
                }
                if (tag.multiThumbnailsIsVisible) {
                    tag.multiThumbnailsViewContainer.setVisibility(View.GONE);
                    tag.multiThumbnailsIsVisible = false;
                }
            } else if (attachmentsCount == 1) {
                if (tag.multiThumbnailsIsVisible) {
                    tag.multiThumbnailsViewContainer.setVisibility(View.GONE);
                    tag.multiThumbnailsIsVisible = false;
                }
                if (!tag.singleThumbnailIsVisible) {
                    tag.singleThumbnailView.setVisibility(View.VISIBLE);
                    tag.singleThumbnailIsVisible = true;
                }
                fillThumbnail(tag.singleThumbnailView, model.sourceModel.attachments[0], model.attachmentHashes[0], popupWidth != null);
            } else {
                if (tag.singleThumbnailIsVisible) {
                    tag.singleThumbnailView.setVisibility(View.GONE);
                    tag.singleThumbnailIsVisible = false;
                }
                if (!tag.multiThumbnailsIsVisible) {
                    tag.multiThumbnailsViewContainer.setVisibility(View.VISIBLE);
                    tag.multiThumbnailsIsVisible = true;
                }
                int currentThumbnailsInRowCount = popupWidth == null ? thumbnailsInRowCount : Math.max(1, popupWidth / fragment().thumbnailWidth);
                int layoutsInflated = divcell(tag.multiThumbnailsInflatedCount, currentThumbnailsInRowCount);
                int layoutsVisible = divcell(tag.multiThumbnailsVisibleCount, currentThumbnailsInRowCount);
                int layoutsRequired = divcell(attachmentsCount, currentThumbnailsInRowCount);
                
                for (int i=layoutsVisible; i<layoutsRequired && i<layoutsInflated; ++i) tag.multiThumbnailsRows[i].setVisibility(View.VISIBLE);
                for (int i=layoutsRequired; i<layoutsVisible; ++i) tag.multiThumbnailsRows[i].setVisibility(View.GONE);
                for (int i=layoutsInflated; i<layoutsRequired; ++i) {
                    tag.multiThumbnailsRows[i] = new LinearLayout(fragment().activity);
                    tag.multiThumbnailsRows[i].setOrientation(LinearLayout.HORIZONTAL);
                    tag.multiThumbnailsViewContainer.addView(tag.multiThumbnailsRows[i]);
                }
                
                for (int i=tag.multiThumbnailsVisibleCount; i<attachmentsCount && i<tag.multiThumbnailsInflatedCount; ++i)
                    tag.multiThumbnails[i].setVisibility(View.VISIBLE);
                for (int i=attachmentsCount; i<tag.multiThumbnailsVisibleCount; ++i) tag.multiThumbnails[i].setVisibility(View.GONE);
                for (int i=tag.multiThumbnailsInflatedCount; i<attachmentsCount; ++i) {
                    int curLayout = i / currentThumbnailsInRowCount;
                    tag.multiThumbnails[i] = inflater.inflate(R.layout.post_thumbnail, tag.multiThumbnailsRows[curLayout], false);
                    View thumbnailImage = tag.multiThumbnails[i].findViewById(R.id.post_thumbnail_image);
                    setViewSize(thumbnailImage, MainApplication.getInstance().settings.getPostThumbnailSize());
                    TextView size = (TextView) tag.multiThumbnails[i].findViewById(R.id.post_thumbnail_attachment_size);
                    size.setMaxWidth(MainApplication.getInstance().settings.getPostThumbnailSize());
                    TextView type = (TextView) tag.multiThumbnails[i].findViewById(R.id.post_thumbnail_attachment_type);
                    type.setMaxWidth(MainApplication.getInstance().settings.getPostThumbnailSize());
                    ((ViewGroup.MarginLayoutParams)tag.multiThumbnails[i].getLayoutParams()).setMargins(0, 0, fragment().thumbnailMargin, 0);
                    tag.multiThumbnailsRows[curLayout].addView(tag.multiThumbnails[i]);
                    ++tag.multiThumbnailsInflatedCount;
                }
                tag.multiThumbnailsVisibleCount = attachmentsCount;
                
                for (int i=0; i<attachmentsCount; ++i)
                    fillThumbnail(tag.multiThumbnails[i], model.sourceModel.attachments[i], model.attachmentHashes[i], popupWidth != null);
            }
            
            //комментарий
            boolean isFloating;
            int refererHighlightColor = ThemeUtils.ThemeColors.getInstance(fragment().activity.getTheme()).refererForeground;
            if (popupWidth == null) {
                tag.commentView.setText(highlightReferer(referer, refererHighlightColor,
                        fragment().searchHighlightActive && fragment().cachedSearchHighlightedSpanables != null &&
                        fragment().cachedSearchHighlightedSpanables.get(position) != null ?
                                fragment().cachedSearchHighlightedSpanables.get(position) : model.spannedComment));
                isFloating = model.floating;
            } else {
                PresentationItemModel.SpannedCommentContainer customSpanned =
                        model.getSpannedCommentForCustomWidth(popupWidth - fragment().postItemPadding, fragment().floatingModels);
                tag.commentView.setText(highlightReferer(referer, refererHighlightColor, customSpanned.spanned));
                isFloating = customSpanned.floating;
            }
            if (attachmentsCount == 1) {
                if (isFloating) {
                    if (!tag.commentFloatingPosition) {
                        FlowTextHelper.setFloatLayoutPosition(tag.singleThumbnailView, tag.commentView);
                        tag.commentFloatingPosition = true;
                    }
                } else {
                    if (tag.commentFloatingPosition) {
                        FlowTextHelper.setDefaultLayoutPosition(tag.singleThumbnailView, tag.commentView);
                        tag.commentFloatingPosition = false;
                    }
                }
            }
            
            if ((popupWidth != null || fragment().pageType == TYPE_POSTSLIST) && !tag.clickableLinksSet) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    CompatibilityImpl.setTextIsSelectable(tag.commentView);
                } else {
                    tag.commentView.setMovementMethod(FixedLinkMovementMethod.getInstance());
                }
                tag.headerView.setMovementMethod(FixedLinkMovementMethod.getInstance());
                tag.repliesView.setMovementMethod(FixedLinkMovementMethod.getInstance());
                tag.clickableLinksSet = true;
            }
            
            if (popupWidth == null && fragment().pageType == TYPE_POSTSLIST) {
                //подсветка непрочитанных сообщений
                if (position >= fragment().firstUnreadPosition) {
                    if (!tag.unreadFrameIsVisible) {
                        tag.unreadFrame.setVisibility(View.VISIBLE);
                        tag.unreadFrameIsVisible = true;
                    }
                } else {
                    if (tag.unreadFrameIsVisible) {
                        tag.unreadFrame.setVisibility(View.GONE);
                        tag.unreadFrameIsVisible = false;
                    }
                }
            }
            //отметка удалённого поста
            if (model.isDeleted) {
                if (!tag.deletedPostViewIsVisible) {
                    tag.deletedPostView.setVisibility(View.VISIBLE);
                    tag.deletedPostViewIsVisible = true;
                }
            } else {
                if (tag.deletedPostViewIsVisible) {
                    tag.deletedPostView.setVisibility(View.GONE);
                    tag.deletedPostViewIsVisible = false;
                }
            }
            //сократить (кнопка "Показать весь текст") длинные посты
            if (tag.showFullTextIsVisible) {
                tag.showFullTextView.setVisibility(View.GONE);
                tag.showFullTextIsVisible = false;
            }
            if (popupWidth == null) {
                if (fragment().pageType != TYPE_THREADSLIST) {
                    if (fragment().staticSettings.itemHeight != 0 && !expanded.get(tag.position)) {
                        tag.commentView.setMaxHeight(fragment().staticSettings.itemHeight);
                        tag.commentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                if (fragment() == null) return false;
                                if (hackListViewPosition != null) {
                                    fragment().listView.setSelectionFromTop(hackListViewPosition[0], hackListViewPosition[1]);
                                    hackListViewPosition = null;
                                }
                                tag.commentView.getViewTreeObserver().removeOnPreDrawListener(this);
                                if (tag.commentView.getHeight() < fragment().staticSettings.itemHeight) {
                                    return true;
                                }
                                tag.showFullTextView.setVisibility(View.VISIBLE);
                                tag.showFullTextIsVisible = true;
                                tag.showFullTextView.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        expanded.put(tag.position, true);
                                        tag.commentView.setMaxHeight(Integer.MAX_VALUE);
                                        tag.showFullTextView.setVisibility(View.GONE);
                                        tag.showFullTextIsVisible = false;
                                    }
                                });
                                return false;
                            }
                        });
                    } else {
                        tag.commentView.setMaxHeight(Integer.MAX_VALUE);
                    }
                } else {
                    tag.commentView.setMaxLines(fragment().maxItemLines);
                }
            }
            //ссылки на ответы
            Spanned usingReferencesString = fragment().staticSettings.repliesOnlyQuantity ? model.referencesQuantityString : model.referencesString;
            if (usingReferencesString != null && usingReferencesString.length() != 0) {
                tag.repliesView.setText(highlightReferer(referer, refererHighlightColor, usingReferencesString));
                if (!tag.repliesIsVisible) {
                    tag.repliesView.setVisibility(View.VISIBLE);
                    tag.repliesIsVisible = true;
                }
            } else {
                if (tag.repliesIsVisible) {
                    tag.repliesView.setVisibility(View.GONE);
                    tag.repliesIsVisible = false;
                }
            }
            //информация о треде (для списка тредов), количество постов, надпись о закрытом/прикрепленном треде
            if (popupWidth == null && fragment().pageType == TYPE_THREADSLIST) {
                if (model.postsCountString != null) {
                    tag.postsCountView.setText(model.postsCountString);
                    if (!tag.postsCountIsVisible) {
                        tag.postsCountView.setVisibility(View.VISIBLE);
                        tag.postsCountIsVisible = true;
                    }
                } else {
                    if (tag.postsCountIsVisible) {
                        tag.postsCountView.setVisibility(View.GONE);
                        tag.postsCountIsVisible = false;
                    }
                }
                if (model.threadConditionString != null) {
                    tag.threadConditionView.setText(model.threadConditionString);
                    if (!tag.threadConditionIsVisible) {
                        tag.threadConditionView.setVisibility(View.VISIBLE);
                        tag.threadConditionIsVisible = true;
                    }
                } else {
                    if (tag.threadConditionIsVisible) {
                        tag.threadConditionView.setVisibility(View.GONE);
                        tag.threadConditionIsVisible = false;
                    }
                }
            }
            //для построения диалогового окна
            if (popupWidth != null) {
                if (fragment().pageType == TYPE_POSTSLIST) {
                    weakRegisterForContextMenu(view);
                    weakRegisterForContextMenu(view.findViewById(R.id.post_content_layout));
                    tag.isPopupDialog = true;
                }
                if (custom != null) return view;
                
                final ScrollView scrollContent = (ScrollView) view.findViewById(R.id.post_scroll_content);
                RelativeLayout contentLayout = (RelativeLayout) view.findViewById(R.id.post_content_layout);
                ((ViewGroup) contentLayout.getParent()).removeView(contentLayout);
                scrollContent.addView(contentLayout);
                scrollContent.setVisibility(View.VISIBLE);
                
                final ScrollView scrollReplies = (ScrollView) view.findViewById(R.id.post_scroll_replies);
                ((ViewGroup) tag.repliesView.getParent()).removeView(tag.repliesView);
                scrollReplies.addView(tag.repliesView);
                scrollReplies.setVisibility(View.VISIBLE);
                
                tag.repliesView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        tag.repliesView.getViewTreeObserver().removeOnPreDrawListener(this);
                        
                        int contentHeight = tag.commentView.getHeight();
                        if (tag.singleThumbnailIsVisible) contentHeight = Math.max(contentHeight, tag.singleThumbnailView.getHeight());
                        else if (tag.multiThumbnailsIsVisible) contentHeight += tag.multiThumbnailsViewContainer.getHeight(); 
                        
                        if (scrollContent.getHeight() == 0 || contentHeight > scrollContent.getHeight()) {
                            int maxHeight = (scrollContent.getHeight() + scrollReplies.getHeight()) / 2;
                            if (maxHeight == 0) {
                                Logger.e(TAG, "error: can't measure replies view height");
                            } else if (contentHeight != 0 && contentHeight < maxHeight) {
                                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) scrollContent.getLayoutParams();
                                params.height = contentHeight;
                                params.weight = 0;
                            } else if (tag.repliesView.getHeight() > maxHeight) {
                                scrollReplies.getLayoutParams().height = maxHeight;
                                scrollReplies.removeAllViews();
                                scrollReplies.addView(tag.repliesView);
                            }
                            scrollContent.scrollTo(0, 0);
                        }
                        
                        return true;
                    }
                });
            } else {
                tag.isPopupDialog = false;
                if (fragment().pageType == TYPE_POSTSLIST) {
                    weakRegisterForContextMenu(view);
                }
            }
            
            return view;
        }
        
        private Spanned highlightReferer(String referer, int color, Spanned spanned) {
            if (referer == null || referer.length() == 0) return spanned;
            SpannableStringBuilder builder = null;
            ClickableURLSpan[] spans = spanned.getSpans(0, spanned.length(), ClickableURLSpan.class);
            for (ClickableURLSpan span : spans) {
                int spanStart = spanned.getSpanStart(span);
                int spanEnd = spanned.getSpanEnd(span);
                if (spanned.subSequence(spanStart, spanEnd).toString().contains(referer)) {
                    if (builder == null) builder = new SpannableStringBuilder(spanned);
                    builder.setSpan(new ForegroundColorSpan(color), spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            return builder == null ? spanned : builder;
        }

        public void setBusy(boolean isBusy) {
            if (isBusy == this.isBusy) return;
            this.isBusy = isBusy;
            if (!isBusy) setNonBusy();
            fragment().activity.setDrawerLock(isBusy ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);
        }
        
        private void setNonBusy() {
            if (!fragment().downloadThumbnails()) return;
            int count = fragment().listView.getChildCount();
            for (int i=0; i<count; ++i) {
                View v = fragment().listView.getChildAt(i);
                int position = fragment().listView.getPositionForView(v);
                PresentationItemModel model = getItem(position);
                if (model.hidden || !(v.getTag() instanceof PostViewTag)) continue;
                PostViewTag tag = (PostViewTag) v.getTag();
                
                int attachmentsCount = Math.min(model.attachmentHashes.length, PostViewTag.MAX_THUMBNAILS);
                if (attachmentsCount == 1) {
                    Object picTag = tag.singleThumbnailView.findViewById(R.id.post_thumbnail_image).getTag();
                    if (picTag == null || picTag == Boolean.FALSE) {
                        fillThumbnail(tag.singleThumbnailView, model.sourceModel.attachments[0], model.attachmentHashes[0], true);
                    }
                } else {
                    for (int j=0; j<attachmentsCount; ++j) {
                        Object picTag = tag.multiThumbnails[j].findViewById(R.id.post_thumbnail_image).getTag();
                        if (picTag == null || picTag == Boolean.FALSE) { 
                            fillThumbnail(tag.multiThumbnails[j], model.sourceModel.attachments[j], model.attachmentHashes[j], true);
                        }
                    }
                }
                
                int badgeIconsCount = Math.min(model.sourceModel.icons == null ? 0 : model.sourceModel.icons.length, PostViewTag.MAX_BADGE_ICONS);
                for (int j=0; j<badgeIconsCount; ++j) {
                    if (tag.badgeIcons[j].getTag() == null || tag.badgeIcons[j].getTag() == Boolean.FALSE) {
                        fillBadge(tag.badgeIcons[j], model.sourceModel.icons[j].source, model.badgeHashes[j], true);
                    }
                }
            }
        }
        
        private void setImageViewSpoiler(ImageView imageView, boolean isSpoiler) {
            int alphaValue = isSpoiler ? 8 : 255;
            CompatibilityUtils.setImageAlpha(imageView, alphaValue);
        }
        
        private void fillThumbnail(View thumbnailView, AttachmentModel attachment, String hash, boolean nonBusy) {
            BoardFragment fragment = fragment();
            weakRegisterForContextMenu(thumbnailView);
            thumbnailView.setOnClickListener(onAttachmentClickListener);
            thumbnailView.setTag(attachment);
            ImageView thumbnailPic = (ImageView) thumbnailView.findViewById(R.id.post_thumbnail_image);
            setViewSize(thumbnailPic, MainApplication.getInstance().settings.getPostThumbnailSize());
            TextView size = (TextView) thumbnailView.findViewById(R.id.post_thumbnail_attachment_size);
            size.setMaxWidth(MainApplication.getInstance().settings.getPostThumbnailSize());
            TextView type = (TextView) thumbnailView.findViewById(R.id.post_thumbnail_attachment_type);
            type.setMaxWidth(MainApplication.getInstance().settings.getPostThumbnailSize());
            setImageViewSpoiler(thumbnailPic, attachment.isSpoiler || fragment.staticSettings.maskPictures);
            switch (attachment.type) {
                case AttachmentModel.TYPE_IMAGE_GIF:
                    type.setText(R.string.postitem_gif);
                    break;
                case AttachmentModel.TYPE_VIDEO:
                    type.setText(R.string.postitem_video);
                    break;
                case AttachmentModel.TYPE_AUDIO:
                    type.setText(R.string.postitem_audio);
                    break;
                case AttachmentModel.TYPE_OTHER_FILE:
                    type.setText(R.string.postitem_file);
                    break;
                case AttachmentModel.TYPE_OTHER_NOTFILE:
                    type.setText(R.string.postitem_link);
                    break;
            }
            if (attachment.type == AttachmentModel.TYPE_IMAGE_STATIC || attachment.type == AttachmentModel.TYPE_IMAGE_SVG) {
                type.setVisibility(View.GONE);
            } else {
                type.setVisibility(View.VISIBLE);
            }
            if (attachment.type == AttachmentModel.TYPE_OTHER_NOTFILE) {
                size.setVisibility(View.GONE);
            } else {
                size.setText(Attachments.getAttachmentSizeString(attachment, fragment.resources));
                size.setVisibility(View.VISIBLE);
            }
            
            boolean curBusy = isBusy && !nonBusy;
            if (attachment.thumbnail != null && attachment.thumbnail.length() != 0) {
                CancellableTask imagesDownloadTask = fragment.imagesDownloadTask;
                ExecutorService imagesDownloadExecutor = fragment.imagesDownloadExecutor;
                if (fragment.presentationModel == null) {
                    Fragment currentFragment = MainApplication.getInstance().tabsSwitcher.currentFragment;
                    if (currentFragment instanceof BoardFragment) {
                        imagesDownloadTask = ((BoardFragment) currentFragment).imagesDownloadTask;
                        imagesDownloadExecutor = ((BoardFragment) currentFragment).imagesDownloadExecutor;
                    }
                }
                thumbnailPic.setTag(Boolean.FALSE);
                fragment.bitmapCache.asyncGet(
                        hash,
                        attachment.thumbnail,
                        fragment.settings.getPostThumbnailSize(),
                        fragment.chan,
                        fragment.tabModel.type == TabModel.TYPE_LOCAL ? fragment.localFile : null,
                        imagesDownloadTask,
                        thumbnailPic,
                        imagesDownloadExecutor,
                        Async.UI_HANDLER,
                        fragment.downloadThumbnails() && !curBusy,
                        fragment.downloadThumbnails() ? (curBusy ? 0 : R.drawable.thumbnail_error) :
                            Attachments.getDefaultThumbnailResId(attachment.type));
            } else {
                thumbnailPic.setTag(Boolean.TRUE);
                thumbnailPic.setImageResource(Attachments.getDefaultThumbnailResId(attachment.type));
            }
        }
        
        /**
         * 
         * @param badgeIcon
         * @param url исходный (возможно, относительный) непофикшеный
         * @param hash
         * @param nonBusy если true, значение поля isBusy игнорируется, загрузка происходит всегда, если только settings.
         */
        private void fillBadge(ImageView badgeIcon, String url, String hash, boolean nonBusy) {
            BoardFragment fragment = fragment();
            CancellableTask imagesDownloadTask = fragment.imagesDownloadTask;
            ExecutorService imagesDownloadExecutor = fragment.imagesDownloadExecutor;
            if (fragment.presentationModel == null) {
                Fragment currentFragment = MainApplication.getInstance().tabsSwitcher.currentFragment;
                if (currentFragment instanceof BoardFragment) {
                    imagesDownloadTask = ((BoardFragment) currentFragment).imagesDownloadTask;
                    imagesDownloadExecutor = ((BoardFragment) currentFragment).imagesDownloadExecutor;
                }
            }
            badgeIcon.setTag(Boolean.FALSE);
            boolean curBusy = isBusy && !nonBusy;
            fragment().bitmapCache.asyncGet(
                    hash,
                    url,
                    fragment.resources.getDimensionPixelSize(R.dimen.post_badge_size),
                    fragment.chan,
                    fragment.tabModel.type == TabModel.TYPE_LOCAL ? fragment().localFile : null,
                    imagesDownloadTask,
                    badgeIcon,
                    imagesDownloadExecutor,
                    Async.UI_HANDLER,
                    fragment.downloadThumbnails() && !curBusy,
                    0);
        }
    }
    
    /**
     * Целочисленное деление с округлением в большую сторону
     */
    private static int divcell(int a, int b) {
        int res = a/b;
        if (a%b != 0) ++res;
        return res;
    }
    
    /**
     * Загружать ли миниатюры автоматически
     */
    private boolean downloadThumbnails() {
        switch (staticSettings.downloadThumbnails) {
            case ALWAYS: return true;
            case WIFI_ONLY: return Wifi.isConnected();
            default: return false;
        }
    }
    
    /**
     * Обновить страницу
     */
    public void update() {
       update(true, true, false); 
    }
    
    /**
     * Обновить страницу, без вывода всплывающего уведомления
     */
    public void updateSilent() {
        if (!listLoaded) {
            Logger.e(TAG, "called updateSilent() but the list is not loaded");
        } else if (updatingNow) {
            Logger.d(TAG, "already updating now");
        } else {
            update(true, true, true);
        }
    }
    
    /**
     * Загрузить или обновить страницу
     * @param forceUpdate нужно ли обновлять страницу из интернета, если её версия уже есть в кэше
     * @param setRefreshingLayout установить обновление pullableLayout, вызывает {@link SwipeRefreshLayout#setRefreshing(boolean)}
     * @param silent не выводить уведомление (Toast) после обновления
     */
    private void update(boolean forceUpdate, boolean setRefreshingLayout, boolean silent) {
        if (currentTask != null) {
            currentTask.cancel();
        }
        if (listLoaded) {
            if (setRefreshingLayout) {
                pullableLayout.setRefreshing(true);
            }
        } else {
            switchToLoadingView();
        }
        PageGetter pageGetter = new PageGetter(forceUpdate, silent);
        currentTask = pageGetter;
        if (listLoaded) {
            Async.runAsync(pageGetter);
        } else {
            new Thread(pageGetter).start();
        }
    }
    
    /**
     * Остановить обновление pullableLayout (убрать крутящийся круг).
     * Костыль для решения следующей проблемы: в случае обновления свайпом,
     * если после этого обновление проходит слишком быстро, анимация не останавливается,
     * когда вызывается просто setRefreshing(false)
     */
    private void setPullableNoRefreshing() {
        long time = System.currentTimeMillis() - pullableLayoutSetRefreshingTime;
        pullableLayoutSetRefreshingTime = 0;
        if (time >= PULLABLE_ANIMATION_DELAY) {
            pullableLayout.setRefreshing(false);
        } else Async.runOnUiThreadDelayed(new Runnable() {
            @Override
            public void run() {
                pullableLayout.setRefreshing(false);
            }
        }, PULLABLE_ANIMATION_DELAY - time);
    }
    
    /**
     * Проскролить спиок до заданного элемента (поста)
     * @param number номер поста
     */
    public void scrollToItem(String number) {
        if (listLoaded) {
            for (int i=0; i<presentationModel.presentationList.size(); ++i) {
                PresentationItemModel model = presentationModel.presentationList.get(i);
                if (model.sourceModel.number.equals(number)) {
                    listView.setSelection(i);
                    break;
                }
            }
        }
    }
    
    /**
     * Проскроллить список вверх на 50 dp
     */
    public void scrollUp() {
        scroll(true);
    }
    
    /**
     * Проскроллить список вниз на 50 dp
     */
    public void scrollDown() {
        scroll(false);
    }
    
    private void scroll(boolean up) {
        if (listLoaded) {
            int step = (int) (50 * resources.getDisplayMetrics().density + 0.5f);
            View v = listView.getChildAt(0);
            int position = listView.getPositionForView(v);
            int top = v.getTop();
            listView.setSelectionFromTop(position, top + step * (up ? 1 : -1));
        }
    }
    
    private void setNavigationCatalogBar() {
        if (presentationModel == null) return;
        if ((tabModel.pageModel.type == UrlPageModel.TYPE_BOARDPAGE) || (tabModel.pageModel.type == UrlPageModel.TYPE_SEARCHPAGE)) {
            View.OnClickListener navigationBarOnClickListener = new NavigationBarOnClickListener(this);
            for (int id : new int[] {R.id.board_navigation_previous, R.id.board_navigation_next, R.id.board_navigation_page }) {
                navigationBarView.findViewById(id).setOnClickListener(navigationBarOnClickListener);
            }
            ((TextView) navigationBarView.findViewById(R.id.board_navigation_page)).setText(String.valueOf(tabModel.pageModel.boardPage));
            if (tabModel.pageModel.boardPage == presentationModel.source.boardModel.firstPage) {
                navigationBarView.findViewById(R.id.board_navigation_previous).setVisibility(View.INVISIBLE);
            }
            if (tabModel.pageModel.boardPage == presentationModel.source.boardModel.lastPage) {
                navigationBarView.findViewById(R.id.board_navigation_next).setVisibility(View.INVISIBLE);
            }
            
        } else if (tabModel.pageModel.type == UrlPageModel.TYPE_CATALOGPAGE) {
            String[] catalogTypes = presentationModel.source.boardModel.catalogTypeDescriptions;
            if (catalogTypes == null) catalogTypes = new String[] { resources.getString(R.string.catalog_default) }; 
            catalogBarView.setAdapter(new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_dropdown_item, catalogTypes));
            catalogBarView.setSelection(tabModel.pageModel.catalogType);
            catalogBarView.setOnItemSelectedListener(new CatalogOnSelectedListener(this));
        }
    }
    
    private static class NavigationBarOnClickListener implements View.OnClickListener {
        private final WeakReference<BoardFragment> fragmentRef;
        
        public NavigationBarOnClickListener(BoardFragment fragment) {
            fragmentRef = new WeakReference<BoardFragment>(fragment);
        }
        
        @Override
        public void onClick(View v) {
            final UrlPageModel model = new UrlPageModel();
            model.type = fragmentRef.get().tabModel.pageModel.type;//UrlPageModel.TYPE_BOARDPAGE;
            if (model.type == UrlPageModel.TYPE_SEARCHPAGE)
                model.searchRequest = fragmentRef.get().tabModel.pageModel.searchRequest;
            model.chanName = fragmentRef.get().chan.getChanName();
            model.boardName = fragmentRef.get().tabModel.pageModel.boardName;
            switch (v.getId()) {
                case R.id.board_navigation_previous:
                    model.boardPage = fragmentRef.get().tabModel.pageModel.boardPage - 1;
                    UrlHandler.open(model, fragmentRef.get().activity);
                    break;
                case R.id.board_navigation_next:
                    model.boardPage = fragmentRef.get().tabModel.pageModel.boardPage + 1;
                    UrlHandler.open(model, fragmentRef.get().activity);
                    break;
                case R.id.board_navigation_page:
                    final EditText inputField = new EditText(fragmentRef.get().activity);
                    String pageNumberHint = fragmentRef.get().resources.getString(R.string.dialog_switch_page_hint) +
                            (fragmentRef.get().presentationModel.source.boardModel.lastPage == BoardModel.LAST_PAGE_UNDEFINED ? "" : " (" +
                                    fragmentRef.get().presentationModel.source.boardModel.firstPage + "-" +
                                    fragmentRef.get().presentationModel.source.boardModel.lastPage + ")");
                    inputField.setHint(pageNumberHint);
                    inputField.setInputType(fragmentRef.get().presentationModel.source.boardModel.firstPage >= 0 ?
                            InputType.TYPE_CLASS_NUMBER : (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED));
                    DialogInterface.OnClickListener dialogOnClickListener = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == DialogInterface.BUTTON_POSITIVE) {
                                if (inputField.getText().length() == 0) return;
                                try {
                                    model.boardPage = Integer.parseInt(inputField.getText().toString());
                                    if (model.boardPage < fragmentRef.get().presentationModel.source.boardModel.firstPage ||
                                            model.boardPage > fragmentRef.get().presentationModel.source.boardModel.lastPage)
                                        throw new NumberFormatException();
                                    if (model.boardPage != fragmentRef.get().tabModel.pageModel.boardPage) {
                                        UrlHandler.open(model, fragmentRef.get().activity);
                                    }
                                } catch (NumberFormatException e) {
                                    Toast.makeText(fragmentRef.get().activity, R.string.dialog_switch_page_incorrect, Toast.LENGTH_LONG).show();
                                }
                            }
                        }
                    };
                    new AlertDialog.Builder(fragmentRef.get().activity).
                            setTitle(R.string.dialog_switch_page_title).
                            setView(inputField).
                            setPositiveButton(R.string.dialog_switch_page_go, dialogOnClickListener).
                            setNegativeButton(android.R.string.cancel, dialogOnClickListener).
                            show();
                    break;
            }
        }
    }
    
    private static class CatalogOnSelectedListener implements AdapterView.OnItemSelectedListener {
        private final WeakReference<BoardFragment> fragmentRef;
        
        public CatalogOnSelectedListener(BoardFragment fragment) {
            fragmentRef = new WeakReference<BoardFragment>(fragment);
        }
        
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (fragmentRef.get().tabModel.pageModel.catalogType == position) return;
            UrlPageModel model = new UrlPageModel();
            model.type = UrlPageModel.TYPE_CATALOGPAGE;
            model.chanName = fragmentRef.get().chan.getChanName();
            model.boardName = fragmentRef.get().tabModel.pageModel.boardName;
            model.catalogType = position;
            UrlHandler.open(model, fragmentRef.get().activity);
        }
        @Override
        public void onNothingSelected(AdapterView<?> parent) { }
    }
    
    private void initSearchBar() {
        if (searchBarInitialized) return;
        final EditText field = (EditText) searchBarView.findViewById(R.id.board_search_field);
        final TextView results = (TextView) searchBarView.findViewById(R.id.board_search_result);
        if (pageType == TYPE_POSTSLIST) {
            field.setHint(R.string.search_bar_in_thread_hint);
        }
        final View.OnClickListener searchOnClickListener = new View.OnClickListener() {
            private int lastFound = -1;
            @Override
            public void onClick(View v) {
                if (v != null && v.getId() == R.id.board_search_close) {
                    searchHighlightActive = false;
                    adapter.notifyDataSetChanged();
                    searchBarView.setVisibility(View.GONE);
                } else if (listView != null && listView.getChildCount() > 0 && adapter != null && cachedSearchResults != null) {
                    boolean atEnd = listView.getChildAt(listView.getChildCount() - 1).getTop() +
                            listView.getChildAt(listView.getChildCount() - 1).getHeight() == listView.getHeight();
                    
                    View topView = listView.getChildAt(0);
                    if ((v == null || v.getId() == R.id.board_search_previous) &&
                            topView.getTop() < 0 && listView.getChildCount() > 1) topView = listView.getChildAt(1);
                    int currentListPosition = listView.getPositionForView(topView);
                    
                    int newResultIndex = Collections.binarySearch(cachedSearchResults, currentListPosition);
                    if (newResultIndex >= 0) {
                        if (v != null) {
                            if (v.getId() == R.id.board_search_next) ++newResultIndex;
                            else if (v.getId() == R.id.board_search_previous) --newResultIndex;
                        }
                    } else {
                        newResultIndex = -newResultIndex - 1;
                        if (v != null && v.getId() == R.id.board_search_previous) --newResultIndex;
                    }
                    while (newResultIndex < 0) newResultIndex += cachedSearchResults.size();
                    newResultIndex %= cachedSearchResults.size();
                    
                    if (v != null && v.getId() == R.id.board_search_next && lastFound == newResultIndex && atEnd) newResultIndex = 0;
                    lastFound = newResultIndex;
                    
                    listView.setSelection(cachedSearchResults.get(newResultIndex));
                    results.setText((newResultIndex + 1) + "/" + cachedSearchResults.size());
                }
            }
        };
        for (int id : new int[] { R.id.board_search_close, R.id.board_search_previous, R.id.board_search_next }) {
            searchBarView.findViewById(id).setOnClickListener(searchOnClickListener);
        }
        field.setOnKeyListener(new View.OnKeyListener() {
            private boolean searchUsingChan() {
                if (pageType != TYPE_THREADSLIST) return false;
                if (presentationModel != null)
                    if (presentationModel.source != null) 
                        if (presentationModel.source.boardModel != null)
                            if (!presentationModel.source.boardModel.searchAllowed) return false;
                return true;
            }
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    if (searchUsingChan()) {
                        UrlPageModel model = new UrlPageModel();
                        model.chanName = chan.getChanName();
                        model.type = UrlPageModel.TYPE_SEARCHPAGE;
                        model.boardName = tabModel.pageModel.boardName;
                        model.searchRequest = field.getText().toString();
                        model.boardPage = presentationModel.source.boardModel.firstPage;
                        UrlHandler.open(model, activity);
                    } else {
                        int highlightColor = ThemeUtils.getThemeColor(activity.getTheme(), R.attr.searchHighlightBackground, Color.RED);
                        String request = field.getText().toString().toLowerCase(Locale.US);
                        
                        if (cachedSearchRequest == null || !request.equals(cachedSearchRequest)) {
                            cachedSearchRequest = request;
                            cachedSearchResults = new ArrayList<Integer>();
                            cachedSearchHighlightedSpanables = new SparseArray<Spanned>();
                            List<PresentationItemModel> safePresentationList = presentationModel.getSafePresentationList();
                            if (safePresentationList != null) {
                                for (int i=0; i<safePresentationList.size(); ++i) {
                                    PresentationItemModel model = safePresentationList.get(i);
                                    if (model.hidden && !staticSettings.showHiddenItems) continue;
                                    String comment = model.spannedComment.toString().toLowerCase(Locale.US).replace('\n', ' ');
                                    List<Integer> altFoundPositions = null;
                                    if (model.floating) {
                                        int floatingpos = FlowTextHelper.getFloatingPosition(model.spannedComment);
                                        if (floatingpos != -1 && floatingpos < model.spannedComment.length() &&
                                                model.spannedComment.charAt(floatingpos) == '\n') {
                                            String altcomment = comment.substring(0, floatingpos) + comment.substring(
                                                    floatingpos + 1, Math.min(model.spannedComment.length(), floatingpos + request.length()));
                                            int start = 0;
                                            int curpos;
                                            while (start < altcomment.length() && (curpos = altcomment.indexOf(request, start)) != -1) {
                                                if (altFoundPositions == null) altFoundPositions = new ArrayList<Integer>();
                                                altFoundPositions.add(curpos);
                                                start = curpos + request.length();
                                            }
                                        }
                                    }
                                    
                                    if (comment.contains(request) || altFoundPositions != null) {
                                        cachedSearchResults.add(Integer.valueOf(i));
                                        SpannableStringBuilder spannedHighlited =
                                                new SpannableStringBuilder(safePresentationList.get(i).spannedComment);
                                        int start = 0;
                                        int curpos;
                                        while (start < comment.length() && (curpos = comment.indexOf(request, start)) != -1) {
                                            start = curpos + request.length();
                                            if (altFoundPositions != null && Collections.binarySearch(altFoundPositions, curpos) >= 0) continue;
                                            spannedHighlited.setSpan(new BackgroundColorSpan(highlightColor),
                                                    curpos, curpos + request.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                        }
                                        if (altFoundPositions != null) {
                                            for (Integer pos : altFoundPositions) {
                                                spannedHighlited.setSpan(new BackgroundColorSpan(highlightColor),
                                                        pos, pos + request.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                            }
                                        }
                                        cachedSearchHighlightedSpanables.put(i, spannedHighlited);
                                    }
                                }
                            }
                        }
                        
                        if (cachedSearchResults.size() == 0) {
                            Toast.makeText(activity, R.string.notification_not_found, Toast.LENGTH_LONG).show();
                        } else {
                            boolean firstTime = !searchHighlightActive;
                            searchHighlightActive = true;
                            adapter.notifyDataSetChanged();
                            searchBarView.findViewById(R.id.board_search_next).setVisibility(View.VISIBLE);
                            searchBarView.findViewById(R.id.board_search_previous).setVisibility(View.VISIBLE);
                            searchBarView.findViewById(R.id.board_search_result).setVisibility(View.VISIBLE);
                            searchOnClickListener.onClick(firstTime ? null : searchBarView.findViewById(R.id.board_search_next));
                        }
                    }
                    try {
                        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
                    } catch (Exception e) {
                        Logger.e(TAG, e);
                    }
                    return true;
                }
                return false;
            }
        });
        field.addTextChangedListener(new OnSearchTextChangedListener(this));
        field.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        if (resources.getDimensionPixelSize(R.dimen.panel_height) < field.getMeasuredHeight())
            searchBarView.getLayoutParams().height = field.getMeasuredHeight();
        searchBarInitialized = true;
    }
    
    private static class OnSearchTextChangedListener implements TextWatcher {
        private final WeakReference<BoardFragment> fragmentRef;
        
        public OnSearchTextChangedListener(BoardFragment fragment) {
            this.fragmentRef = new WeakReference<BoardFragment>(fragment);
        }
        
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (fragmentRef.get().searchHighlightActive) {
                fragmentRef.get().searchHighlightActive = false;
                fragmentRef.get().adapter.notifyDataSetChanged();
            }
            fragmentRef.get().searchBarView.findViewById(R.id.board_search_next).setVisibility(View.GONE);
            fragmentRef.get().searchBarView.findViewById(R.id.board_search_previous).setVisibility(View.GONE);
            fragmentRef.get().searchBarView.findViewById(R.id.board_search_result).setVisibility(View.GONE);
        }
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void afterTextChanged(Editable s) {}
    }
    
    private void resetSearchCache() {
        searchBarView.findViewById(R.id.board_search_next).setVisibility(View.GONE);
        searchBarView.findViewById(R.id.board_search_previous).setVisibility(View.GONE);
        searchBarView.findViewById(R.id.board_search_result).setVisibility(View.GONE);
        searchHighlightActive = false;
        cachedSearchHighlightedSpanables = null;
        cachedSearchRequest = null;
        cachedSearchResults = null;
    }
    
    private void finalizeSearchBar() {
        if (!searchBarInitialized) return;
        for (int id : new int[] { R.id.board_search_close, R.id.board_search_previous, R.id.board_search_next }) {
            searchBarView.findViewById(id).setOnClickListener(null);
        }
        final EditText field = (EditText) searchBarView.findViewById(R.id.board_search_field);
        field.setOnKeyListener(null);
    }
    
    private void openPostForm(String hash, BoardModel boardModel, SendPostModel sendPostModel) {
        if (PostingService.isNowPosting()) {
            Toast.makeText(activity, resources.getString(R.string.posting_now_posting), Toast.LENGTH_LONG).show();
            return;
        }
        Intent addPostIntent = new Intent(activity.getApplicationContext(), PostFormActivity.class);
        addPostIntent.putExtra(PostingService.EXTRA_PAGE_HASH, hash);
        addPostIntent.putExtra(PostingService.EXTRA_BOARD_MODEL, boardModel);
        addPostIntent.putExtra(PostingService.EXTRA_SEND_POST_MODEL, sendPostModel);
        startActivity(addPostIntent);
    }
    
    private void openReply(int position, boolean withQuote, String quote) {
        PresentationItemModel item = adapter.getItem(position);
        SendPostModel sendReplyModel = getSendPostModel();
        int sendReplyModelPos = sendReplyModel.commentPosition;
        if (sendReplyModelPos > sendReplyModel.comment.length()) sendReplyModelPos = -1;
        if (sendReplyModelPos < 0) sendReplyModelPos = sendReplyModel.comment.length();
        String insertion;
        if (withQuote) {
            String quotedComment = (quote != null ? quote : item.spannedComment.toString().replaceAll("(^|\n)(>>\\d+(\n|\\s)?)+", "$1")).
                    replaceAll("(\n+)", "$1>");
            insertion = ">>" + item.sourceModel.number + "\n" + (quotedComment.length() > 0 ? ">" + quotedComment + "\n" : "");
        } else {
            insertion = ">>" + item.sourceModel.number + "\n";
        }
        sendReplyModel.comment = sendReplyModel.comment.substring(0, sendReplyModelPos) +
                insertion + sendReplyModel.comment.substring(sendReplyModelPos);
        sendReplyModel.commentPosition = sendReplyModelPos + insertion.length();
        openPostForm(tabModel.hash, presentationModel.source.boardModel, sendReplyModel);
    }
    
    private SendPostModel getSendPostModel() {
        SendPostModel draft = MainApplication.getInstance().draftsCache.get(tabModel.hash);
        if (draft == null) {
            draft = new SendPostModel();
            draft.chanName = tabModel.pageModel.chanName;
            draft.boardName = tabModel.pageModel.boardName;
            draft.threadNumber = pageType == TYPE_POSTSLIST ? tabModel.pageModel.threadNumber : null;
            draft.comment = "";
            BoardModel boardModel = presentationModel.source.boardModel;
            if (boardModel.allowNames) draft.name = settings.getDefaultName();
            if (boardModel.allowEmails) draft.email = settings.getDefaultEmail();
            if (boardModel.allowDeletePosts || boardModel.allowDeleteFiles) draft.password = chan.getDefaultPassword();
            if (boardModel.allowRandomHash) draft.randomHash = settings.isRandomHash();
        }
        return draft;
    }
    
    private SendPostModel getSendPostModel(UrlPageModel pageModel) {
        String hash = ChanModels.hashUrlPageModel(pageModel);
        SendPostModel draft = MainApplication.getInstance().draftsCache.get(hash);
        if (draft == null) {
            draft = new SendPostModel();
            draft.chanName = pageModel.chanName;
            draft.boardName = pageModel.boardName;
            draft.threadNumber = pageModel.threadNumber;
            BoardModel boardModel = presentationModel.source.boardModel;
            if (boardModel.allowNames) draft.name = settings.getDefaultName();
            if (boardModel.allowEmails) draft.email = settings.getDefaultEmail();
            if (boardModel.allowDeletePosts || boardModel.allowDeleteFiles) draft.password = chan.getDefaultPassword();
            if (boardModel.allowRandomHash) draft.randomHash = settings.isRandomHash();
        }
        return draft;
    }
    
    private Point getSpanCoordinates(View widget, ClickableURLSpan span) {
        TextView parentTextView = (TextView) widget;

        Rect parentTextViewRect = new Rect();

        // Initialize values for the computing of clickedText position
        SpannableString completeText = (SpannableString)(parentTextView).getText();
        Layout textViewLayout = parentTextView.getLayout();

        int startOffsetOfClickedText = completeText.getSpanStart(span);
        int endOffsetOfClickedText = completeText.getSpanEnd(span);
        double startXCoordinatesOfClickedText = textViewLayout.getPrimaryHorizontal(startOffsetOfClickedText);
        double endXCoordinatesOfClickedText = textViewLayout.getPrimaryHorizontal(endOffsetOfClickedText);


        // Get the rectangle of the clicked text
        int currentLineStartOffset = textViewLayout.getLineForOffset(startOffsetOfClickedText);
        int currentLineEndOffset = textViewLayout.getLineForOffset(endOffsetOfClickedText);
        boolean keywordIsInMultiLine = currentLineStartOffset != currentLineEndOffset;
        textViewLayout.getLineBounds(currentLineStartOffset, parentTextViewRect);


        // Update the rectangle position to his real position on screen
        int[] parentTextViewLocation = {0,0};
        parentTextView.getLocationOnScreen(parentTextViewLocation);
        
        double parentTextViewTopAndBottomOffset = (
            parentTextViewLocation[1] -
            parentTextView.getScrollY() +
            parentTextView.getCompoundPaddingTop()
        );

        Rect windowRect = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(windowRect);
        parentTextViewTopAndBottomOffset -= windowRect.top;

        parentTextViewRect.top += parentTextViewTopAndBottomOffset;
        parentTextViewRect.bottom += parentTextViewTopAndBottomOffset;

        parentTextViewRect.left += (
            parentTextViewLocation[0] +
            startXCoordinatesOfClickedText +
            parentTextView.getCompoundPaddingLeft() -
            parentTextView.getScrollX()
        );
        parentTextViewRect.right = (int) (
            parentTextViewRect.left +
            endXCoordinatesOfClickedText -
            startXCoordinatesOfClickedText
        );

        int x = (parentTextViewRect.left + parentTextViewRect.right) / 2;
        int y = (parentTextViewRect.top + parentTextViewRect.bottom) / 2;
        if (keywordIsInMultiLine) {
            x = parentTextViewRect.left;
        }

        return new Point(x, y);
    }
    
    /**
     * Открыть всплывающий диалог с постом
     * @param itemPosition позиция элемента (поста) в адаптере listView
     * @param isTablet true, если планшетный режим (задается положение окна относительно ссылки)
     * @param coordinates координаты нажатой ссылки
     */
    private void showPostPopupDialog(final int itemPosition, final boolean isTablet, final Point coordinates, final String refererPost) {
        final int bgShadowResource = ThemeUtils.getThemeResId(activity.getTheme(), R.attr.dialogBackgroundShadow);
        final int bgColor = ThemeUtils.getThemeColor(activity.getTheme(), R.attr.activityRootBackground, Color.BLACK);
        final int measuredWidth = isTablet ? adapter.measureViewWidth(itemPosition) : -1; //измерять требуется только для планшета
        final View tmpV = new View(activity);
        final Dialog tmpDlg = new Dialog(activity);
        tmpDlg.getWindow().setBackgroundDrawableResource(bgShadowResource);
        tmpDlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        tmpDlg.setCanceledOnTouchOutside(true);
        tmpDlg.setContentView(tmpV);
        final Rect activityWindowRect;
        final int dlgWindowWidth;
        final int dlgWindowHeight;
        if (isTablet) {
            activityWindowRect = new Rect();
            activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(activityWindowRect);
            dlgWindowWidth = Math.max(coordinates.x, activityWindowRect.width() - coordinates.x);
            dlgWindowHeight = Math.max(coordinates.y, activityWindowRect.height() - coordinates.y);
            tmpDlg.getWindow().setLayout(dlgWindowWidth, dlgWindowHeight);
        } else {
            activityWindowRect = null;
            dlgWindowWidth = -1;
            dlgWindowHeight = -1;
        }
        tmpDlg.show();
        
        Runnable next = new Runnable() {
            @SuppressLint("RtlHardcoded")
            @Override
            public void run() {
                int dlgWidth = tmpV.getWidth();
                int dlgHeight = tmpV.getHeight();
                tmpDlg.hide();
                tmpDlg.cancel();
                int newWidth = isTablet ? Math.min(measuredWidth, dlgWidth) : dlgWidth;
                
                View view = adapter.getView(itemPosition, null, null, newWidth, refererPost);
                view.setBackgroundColor(bgColor);
                //Logger.d(TAG, "measured: "+view.findViewById(R.id.post_frame_main).getMeasuredWidth()+
                //        "x"+view.findViewById(R.id.post_frame_main).getMeasuredHeight());
                
                Dialog dialog = new Dialog(activity);
                dialog.getWindow().setBackgroundDrawableResource(bgShadowResource);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCanceledOnTouchOutside(true);
                dialog.setContentView(view);
                if (isTablet) {
                    view.findViewById(R.id.post_frame_main).measure(
                            MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    int newWindowWidth = dlgWindowWidth - dlgWidth + newWidth;
                    int newWindowHeight = dlgWindowHeight - dlgHeight +
                            Math.min(view.findViewById(R.id.post_frame_main).getMeasuredHeight(), dlgHeight);
                    dialog.getWindow().setLayout(newWindowWidth, newWindowHeight);
                    WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
                    if (coordinates.x > activityWindowRect.width() - coordinates.x &&
                            coordinates.x + newWindowWidth > activityWindowRect.width()) {
                        params.x = activityWindowRect.width() - coordinates.x;
                        params.gravity = Gravity.RIGHT;
                    } else {
                        params.x = coordinates.x;
                        params.gravity = Gravity.LEFT;
                    }
                    if (coordinates.y > activityWindowRect.height() - coordinates.y && 
                            coordinates.y + newWindowHeight > activityWindowRect.height()) {
                        params.y = activityWindowRect.height() - coordinates.y;
                        params.gravity |= Gravity.BOTTOM;
                    } else {
                        params.y = coordinates.y;
                        params.gravity |= Gravity.TOP;
                    }
                    dialog.getWindow().setAttributes(params);
                    
                    //затемнение в планшетном режиме не нужно
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                        CompatibilityImpl.setDimAmount(dialog.getWindow(), 0.1f);
                    } else {
                        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    }
                }
                dialog.show();
                dialogs.add(dialog);
            }
        };
        
        if (tmpV.getWidth() != 0) {
            next.run();
        } else {
            AppearanceUtils.callWhenLoaded(tmpDlg.getWindow().getDecorView(), next);
        }
    }
    
    private void showThreadPreviewDialog(final int position) {
        final List<PresentationItemModel> items = new ArrayList<>();
        final int bgShadowResource = ThemeUtils.getThemeResId(activity.getTheme(), R.attr.dialogBackgroundShadow);
        final int bgColor = ThemeUtils.getThemeColor(activity.getTheme(), R.attr.activityRootBackground, Color.BLACK);
        final View tmpV = new View(activity);
        final Dialog tmpDlg = new Dialog(activity);
        tmpDlg.getWindow().setBackgroundDrawableResource(bgShadowResource);
        tmpDlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        tmpDlg.setCanceledOnTouchOutside(true);
        tmpDlg.setContentView(tmpV);
        tmpDlg.show();
        Runnable next = new Runnable() {
            @Override
            public void run() {
                final int dlgWidth = tmpV.getWidth();
                tmpDlg.hide();
                tmpDlg.cancel();
                final Dialog dialog = new Dialog(activity);
                
                if (presentationModel.source != null &&
                        presentationModel.source.threads != null &&
                        presentationModel.source.threads.length > position &&
                        presentationModel.source.threads[position].posts != null &&
                        presentationModel.source.threads[position].posts.length > 0) {
                    
                    final String threadNumber = presentationModel.source.threads[position].posts[0].number;
                    
                    ClickableURLSpan.URLSpanClickListener spanClickListener = new ClickableURLSpan.URLSpanClickListener() {
                        @Override
                        public void onClick(View v, ClickableURLSpan span, String url, String referer) {
                            if (url.startsWith("#")) {
                                try {
                                    UrlPageModel threadPageModel = new UrlPageModel();
                                    threadPageModel.chanName = chan.getChanName();
                                    threadPageModel.type = UrlPageModel.TYPE_THREADPAGE;
                                    threadPageModel.boardName = tabModel.pageModel.boardName;
                                    threadPageModel.threadNumber = threadNumber;
                                    url = chan.buildUrl(threadPageModel) + url;
                                    dialog.dismiss();
                                    UrlHandler.open(chan.fixRelativeUrl(url), activity);
                                } catch (Exception e) {
                                    Logger.e(TAG, e);
                                }
                            } else {
                                dialog.dismiss();
                                UrlHandler.open(chan.fixRelativeUrl(url), activity);
                            }
                        }
                    };
                    
                    AndroidDateFormat.initPattern();
                    String datePattern = AndroidDateFormat.getPattern();
                    DateFormat dateFormat = datePattern == null ?
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) : new SimpleDateFormat(datePattern, Locale.US);
                    dateFormat.setTimeZone(settings.isLocalTime() ?
                            TimeZone.getDefault() : TimeZone.getTimeZone(presentationModel.source.boardModel.timeZoneId));
                    
                    int postsCount = presentationModel.source.threads[position].postsCount;
                    boolean showIndex = presentationModel.source.threads[position].posts.length <= postsCount;
                    int curPostIndex = postsCount - presentationModel.source.threads[position].posts.length + 1;
                    
                    boolean openSpoilers = settings.openSpoilers();
                    
                    for (int i=0; i<presentationModel.source.threads[position].posts.length; ++i) {
                        PresentationItemModel model = new PresentationItemModel(
                                presentationModel.source.threads[position].posts[i],
                                chan.getChanName(),
                                presentationModel.source.pageModel.boardName,
                                presentationModel.source.pageModel.threadNumber,
                                dateFormat,
                                spanClickListener,
                                imageGetter,
                                ThemeUtils.ThemeColors.getInstance(activity.getTheme()),
                                openSpoilers,
                                floatingModels,
                                null);
                        model.buildSpannedHeader(showIndex ? (i == 0 ? 1 : ++curPostIndex) : -1,
                                presentationModel.source.boardModel.bumpLimit, presentationModel.source.boardModel.defaultUserName, null, false);
                        items.add(model);
                    }
                } else {
                    items.add(presentationModel.presentationList.get(position));
                }
                ListView dlgList = new ListView(activity);
                dlgList.setAdapter(new ArrayAdapter<PresentationItemModel>(activity, 0, items) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view = adapter.getView(position, convertView, parent, dlgWidth, getItem(position));
                        view.setBackgroundColor(bgColor);
                        return view;
                    }
                });
                dialog.getWindow().setBackgroundDrawableResource(bgShadowResource);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCanceledOnTouchOutside(true);
                dialog.setContentView(dlgList);
                dialog.show();
                dialogs.add(dialog);
            }
        };
        if (tmpV.getWidth() != 0) {
            next.run();
        } else {
            AppearanceUtils.callWhenLoaded(tmpDlg.getWindow().getDecorView(), next);
        }
    }
    
    private void openReferencesList(final String from) {
        final List<Integer> positions = new ArrayList<>();
        int position = -1;
        for (int i=0; i<presentationModel.presentationList.size(); ++i) {
            if (presentationModel.presentationList.get(i).sourceModel.number.equals(from)) {
                position = i;
                break;
            }
        }
        if (position != -1) {
            Spanned referencesString = presentationModel.presentationList.get(position).referencesString;
            if (referencesString == null) {
                Logger.e(TAG, "null referencesString");
                return;
            }
            ClickableURLSpan[] spans = referencesString.getSpans(0, referencesString.length(), ClickableURLSpan.class);
            for (ClickableURLSpan span : spans) {
                String url = span.getURL();
                try {
                    //url уже в нормальном виде, т.к. строится в PresentationItemModel (модулем чана)
                    UrlPageModel model = UrlHandler.getPageModel(url);
                    for (; position<presentationModel.presentationList.size(); ++position) {
                        if (presentationModel.presentationList.get(position).sourceModel.number.equals(model.postNumber)) {
                            break;
                        }
                    }
                    if (position<presentationModel.presentationList.size()) positions.add(position);
                } catch (Exception e) {
                    Logger.e(TAG, e);
                }
            }
        }
        
        if (positions.size() == 0) {
            Logger.e(TAG, "no references");
            return;
        }
        
        final int bgShadowResource = ThemeUtils.getThemeResId(activity.getTheme(), R.attr.dialogBackgroundShadow);
        final int bgColor = ThemeUtils.getThemeColor(activity.getTheme(), R.attr.activityRootBackground, Color.BLACK);
        final View tmpV = new View(activity);
        final Dialog tmpDlg = new Dialog(activity);
        tmpDlg.getWindow().setBackgroundDrawableResource(bgShadowResource);
        tmpDlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        tmpDlg.setCanceledOnTouchOutside(true);
        tmpDlg.setContentView(tmpV);
        tmpDlg.show();
        Runnable next = new Runnable() {
            @Override
            public void run() {
                final int dlgWidth = tmpV.getWidth();
                tmpDlg.hide();
                tmpDlg.cancel();
                
                ListView dlgList = new ListView(activity);
                dlgList.setAdapter(new ArrayAdapter<Integer>(activity, 0, positions) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        try {
                            int adapterPositon = getItem(position);
                            View view = adapter.getView(adapterPositon, convertView, parent, dlgWidth, adapter.getItem(adapterPositon), from);
                            view.setBackgroundColor(bgColor);
                            return view;
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            Toast.makeText(activity, R.string.error_unknown, Toast.LENGTH_LONG).show();
                            return new View(activity);
                        }
                    }
                });
                
                Dialog dialog = new Dialog(activity);
                dialog.getWindow().setBackgroundDrawableResource(bgShadowResource);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCanceledOnTouchOutside(true);
                dialog.setContentView(dlgList);
                dialog.show();
                dialogs.add(dialog);
            }
        };
        if (tmpV.getWidth() != 0) {
            next.run();
        } else {
            AppearanceUtils.callWhenLoaded(tmpDlg.getWindow().getDecorView(), next);
        }
    }
    
    private void downloadFile(AttachmentModel attachment) {
        downloadFile(attachment, false);
    }
    
    public static String getCustomSubdir(UrlPageModel pageModel) {
        if (pageModel == null || pageModel.boardName == null || pageModel.threadNumber == null || pageModel.type != UrlPageModel.TYPE_THREADPAGE)
            return null;
        return pageModel.boardName + "-" + pageModel.threadNumber + "_originals";
    }
    
    private boolean downloadFile(AttachmentModel attachment, boolean fromGridGallery) {
        if (!CompatibilityUtils.hasAccessStorage(activity)) return true;
        if (attachment.type == AttachmentModel.TYPE_OTHER_NOTFILE) return true;
        String subdir = (fromGridGallery && tabModel.pageModel.type == UrlPageModel.TYPE_THREADPAGE) ? getCustomSubdir(tabModel.pageModel) : null;
        DownloadingService.DownloadingQueueItem item = (subdir != null) ?
                new DownloadingService.DownloadingQueueItem(attachment, subdir, presentationModel.source.boardModel) :
                    new DownloadingService.DownloadingQueueItem(attachment, presentationModel.source.boardModel);
        String fileName = Attachments.getAttachmentLocalFileName(attachment, presentationModel.source.boardModel);
        
        String itemName = Attachments.getAttachmentLocalShortName(attachment, presentationModel.source.boardModel);
        if (DownloadingService.isInQueue(item)) {
            if (!fromGridGallery)
                Toast.makeText(activity, resources.getString(R.string.notification_download_already_in_queue, itemName), Toast.LENGTH_LONG).show();
            return false;
        } else {
            File dir = new File(settings.getDownloadDirectory(), tabModel.pageModel.chanName);
            if (subdir != null) dir = new File(dir, subdir);
            if (new File(dir, fileName).exists()) {
                if (!fromGridGallery)
                    Toast.makeText(activity, resources.getString(R.string.notification_download_already_exists, fileName), Toast.LENGTH_LONG).show();
                return false;
            } else {
                Intent downloadIntent = new Intent(activity, DownloadingService.class);
                downloadIntent.putExtra(DownloadingService.EXTRA_DOWNLOADING_ITEM, item);
                activity.startService(downloadIntent);
                return true;
            }
        }
    }
    
    @SuppressLint("InflateParams")
    private void saveThisPage() {
        if (!CompatibilityUtils.hasAccessStorage(activity)) return;
        DownloadingService.DownloadingQueueItem check = new DownloadingService.DownloadingQueueItem(
                tabModel.pageModel, presentationModel.source.boardModel, DownloadingService.MODE_DOWNLOAD_ALL);
        String itemName = resources.getString(R.string.downloading_thread_format, tabModel.pageModel.boardName, tabModel.pageModel.threadNumber);
        if (DownloadingService.isInQueue(check)) {
            Toast.makeText(activity, resources.getString(R.string.notification_download_already_in_queue, itemName), Toast.LENGTH_LONG).show();
        } else {
            Context dialogContext = Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB ?
                    new ContextThemeWrapper(activity, R.style.Theme_Neutron) : activity;
            View saveThreadDialogView = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_save_thread, null);
            final CheckBox saveThumbsChkbox = (CheckBox) saveThreadDialogView.findViewById(R.id.dialog_save_thread_thumbs);
            final CheckBox saveAllChkbox = (CheckBox) saveThreadDialogView.findViewById(R.id.dialog_save_thread_all);
            switch (settings.getDownloadThreadMode()) {
                case DownloadingService.MODE_DOWNLOAD_ALL:
                    saveThumbsChkbox.setChecked(true);
                    saveAllChkbox.setChecked(true);
                    break;
                case DownloadingService.MODE_DOWNLOAD_THUMBS:
                    saveThumbsChkbox.setChecked(true);
                    saveAllChkbox.setChecked(false);
                    break;
                default:
                    saveThumbsChkbox.setChecked(false);
                    saveAllChkbox.setChecked(false);
                    saveAllChkbox.setEnabled(false);
                    break;
            }
            saveThumbsChkbox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (saveThumbsChkbox.isChecked()) {
                        saveAllChkbox.setEnabled(true);
                    } else {
                        saveAllChkbox.setEnabled(false);
                        saveAllChkbox.setChecked(false);
                    }
                }
            });
            DialogInterface.OnClickListener save = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int mode = DownloadingService.MODE_ONLY_CACHE;
                    if (saveThumbsChkbox.isChecked()) {
                        mode = DownloadingService.MODE_DOWNLOAD_THUMBS;
                    }
                    if (saveAllChkbox.isChecked()) {
                        mode = DownloadingService.MODE_DOWNLOAD_ALL;
                    }
                    settings.saveDownloadThreadMode(mode);
                    Intent savePageIntent = new Intent(activity, DownloadingService.class);
                    savePageIntent.putExtra(DownloadingService.EXTRA_DOWNLOADING_ITEM,
                            new DownloadingService.DownloadingQueueItem(tabModel.pageModel, presentationModel.source.boardModel, mode));
                    activity.startService(savePageIntent);
                }
            };
            AlertDialog saveThreadDialog = new AlertDialog.Builder(dialogContext).setView(saveThreadDialogView).
                    setTitle(R.string.dialog_save_thread_title).
                    setPositiveButton(R.string.dialog_save_thread_save, save).
                    setNegativeButton(android.R.string.cancel, null).create();
            saveThreadDialog.setCanceledOnTouchOutside(false);
            saveThreadDialog.show();
        }
    }
    
    @SuppressLint("InlinedApi")
    private void openGridGallery() {
        final int tnSize = settings.getPostThumbnailSize();
        
        class GridGalleryAdapter extends ArrayAdapter<Triple<AttachmentModel, String, String>>
                implements View.OnClickListener, AbsListView.OnScrollListener {
            private final GridView view;
            private boolean selectingMode = false;
            private boolean[] isSelected = null;
            private volatile boolean isBusy = false;
            public GridGalleryAdapter(GridView view, List<Triple<AttachmentModel, String, String>> list) {
                super(activity, 0, list);
                this.view = view;
                this.isSelected = new boolean[list.size()];
            }
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {}
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    if (isBusy) setNonBusy();
                    isBusy = false;
                } else isBusy = true;
            }
            private void setNonBusy() {
                if (!downloadThumbnails()) return;
                for (int i=0; i<view.getChildCount(); ++i) {
                    View v = view.getChildAt(i);
                    Object tnTag = v.findViewById(R.id.post_thumbnail_image).getTag();
                    if (tnTag == null || tnTag == Boolean.FALSE) fill(view.getPositionForView(v), v, false);
                }
            }
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = new FrameLayout(activity);
                    convertView.setLayoutParams(new AbsListView.LayoutParams(tnSize, tnSize));
                    ImageView tnImage = new ImageView(activity);
                    tnImage.setLayoutParams(new FrameLayout.LayoutParams(tnSize, tnSize, Gravity.CENTER));
                    tnImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    tnImage.setId(R.id.post_thumbnail_image);
                    setViewSize(tnImage, MainApplication.getInstance().settings.getPostThumbnailSize());
                    ((FrameLayout) convertView).addView(tnImage);
                }
                convertView.setTag(getItem(position).getLeft());
                safeRegisterForContextMenu(convertView);
                convertView.setOnClickListener(this);
                fill(position, convertView, isBusy);
                if (isSelected[position]) {
                    /*ImageView overlay = new ImageView(activity);
                    overlay.setImageResource(android.R.drawable.checkbox_on_background);*/
                    FrameLayout overlay = new FrameLayout(activity);
                    overlay.setBackgroundColor(Color.argb(128, 0, 255, 0));
                    if (((FrameLayout) convertView).getChildCount() < 2) ((FrameLayout) convertView).addView(overlay);
                    
                } else {
                    if (((FrameLayout) convertView).getChildCount() > 1) ((FrameLayout) convertView).removeViewAt(1);
                }
                return convertView;
            }
            private void safeRegisterForContextMenu(View view) {
                try {
                    view.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                        @Override
                        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
                            if (presentationModel == null) {
                                Fragment currentFragment = MainApplication.getInstance().tabsSwitcher.currentFragment;
                                if (currentFragment instanceof BoardFragment) {
                                    currentFragment.onCreateContextMenu(menu, v, menuInfo);
                                }
                            } else {
                                BoardFragment.this.onCreateContextMenu(menu, v, menuInfo);
                            }
                        }
                    });
                } catch (Exception e) {
                    Logger.e(TAG, e);
                }
            }
            @Override
            public void onClick(View v) {
                if (selectingMode) {
                    int position = view.getPositionForView(v);
                    isSelected[position] = !isSelected[position];
                    notifyDataSetChanged();
                } else {
                    BoardFragment fragment = BoardFragment.this;
                    if (presentationModel == null) {
                        Fragment currentFragment = MainApplication.getInstance().tabsSwitcher.currentFragment;
                        if (currentFragment instanceof BoardFragment) fragment = (BoardFragment) currentFragment;
                    }
                    fragment.openAttachment((AttachmentModel) v.getTag());
                }
            }
            private void fill(int position, View view, boolean isBusy) {
                AttachmentModel attachment = getItem(position).getLeft();
                String attachmentHash = getItem(position).getMiddle();
                ImageView tnImage = (ImageView) view.findViewById(R.id.post_thumbnail_image);
                setViewSize(tnImage, MainApplication.getInstance().settings.getPostThumbnailSize());
                if (attachment.thumbnail == null || attachment.thumbnail.length() == 0) {
                    tnImage.setTag(Boolean.TRUE);
                    tnImage.setImageResource(Attachments.getDefaultThumbnailResId(attachment.type));
                    return;
                }
                tnImage.setTag(Boolean.FALSE);
                CancellableTask imagesDownloadTask = BoardFragment.this.imagesDownloadTask;
                ExecutorService imagesDownloadExecutor = BoardFragment.this.imagesDownloadExecutor;
                if (presentationModel == null) {
                    Fragment currentFragment = MainApplication.getInstance().tabsSwitcher.currentFragment;
                    if (currentFragment instanceof BoardFragment) {
                        imagesDownloadTask = ((BoardFragment) currentFragment).imagesDownloadTask;
                        imagesDownloadExecutor = ((BoardFragment) currentFragment).imagesDownloadExecutor;
                    }
                }
                bitmapCache.asyncGet(
                        attachmentHash,
                        attachment.thumbnail,
                        tnSize,
                        chan,
                        localFile,
                        imagesDownloadTask,
                        tnImage,
                        imagesDownloadExecutor,
                        Async.UI_HANDLER,
                        downloadThumbnails() && !isBusy,
                        downloadThumbnails() ? (isBusy ? 0 : R.drawable.thumbnail_error) :
                            Attachments.getDefaultThumbnailResId(attachment.type));
            }
            public void setSelectingMode(boolean selectingMode) {
                this.selectingMode = selectingMode;
                if (!selectingMode) {
                    Arrays.fill(isSelected, false);
                    notifyDataSetChanged();
                }
            }
            public void selectAll() {
                if (selectingMode) {
                    Arrays.fill(isSelected, true);
                    notifyDataSetChanged();
                }
            }
            public void downloadSelected(final Runnable onFinish) {
                final Dialog progressDialog = ProgressDialog.show(activity,
                        resources.getString(R.string.grid_gallery_dlg_title), resources.getString(R.string.grid_gallery_dlg_message), true, false);
                Async.runAsync(new Runnable() {
                    @Override
                    public void run() {
                        BoardFragment fragment = BoardFragment.this;
                        if (fragment.presentationModel == null) {
                            Fragment currentFragment = MainApplication.getInstance().tabsSwitcher.currentFragment;
                            if (currentFragment instanceof BoardFragment) fragment = (BoardFragment) currentFragment;
                        }
                        boolean flag = false;
                        for (int i=0; i<isSelected.length; ++i)
                            if (isSelected[i])
                                if (!fragment.downloadFile(getItem(i).getLeft(), true))
                                    flag = true;
                        final boolean toast = flag;
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (toast) Toast.makeText(activity, R.string.notification_download_exists_or_in_queue, Toast.LENGTH_LONG).show();
                                progressDialog.dismiss();
                                onFinish.run();
                            }
                        });
                    }
                });
            }
        }
        
        try {
            List<Triple<AttachmentModel, String, String>> list = presentationModel.getAttachments();
            if (list == null) {
                Toast.makeText(activity, R.string.notifacation_updating_now, Toast.LENGTH_LONG).show();
                return;
            }
            
            GridView grid = new GridView(activity);
            final GridGalleryAdapter gridAdapter = new GridGalleryAdapter(grid, list);
            grid.setNumColumns(GridView.AUTO_FIT);
            grid.setColumnWidth(tnSize);
            int spacing = (int) (resources.getDisplayMetrics().density * 5 + 0.5f);
            grid.setVerticalSpacing(spacing);
            grid.setHorizontalSpacing(spacing);
            grid.setPadding(spacing, spacing, spacing, spacing);
            grid.setAdapter(gridAdapter);
            grid.setOnScrollListener(gridAdapter);
            grid.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            
            final Button btnToSelecting = new Button(activity);
            btnToSelecting.setText(R.string.grid_gallery_select);
            CompatibilityUtils.setTextAppearance(btnToSelecting, android.R.style.TextAppearance_Small);
            btnToSelecting.setSingleLine();
            btnToSelecting.setVisibility(View.VISIBLE);
            btnToSelecting.setLayoutParams(
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            
            final LinearLayout layoutSelectingButtons = new LinearLayout(activity);
            layoutSelectingButtons.setOrientation(LinearLayout.HORIZONTAL);
            layoutSelectingButtons.setWeightSum(10f);
            Button btnDownload = new Button(activity);
            btnDownload.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3.25f));
            btnDownload.setText(R.string.grid_gallery_download);
            CompatibilityUtils.setTextAppearance(btnDownload, android.R.style.TextAppearance_Small);
            btnDownload.setSingleLine();
            Button btnSelectAll = new Button(activity);
            btnSelectAll.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3.75f));
            btnSelectAll.setText(android.R.string.selectAll);
            CompatibilityUtils.setTextAppearance(btnSelectAll, android.R.style.TextAppearance_Small);
            btnSelectAll.setSingleLine();
            Button btnCancel = new Button(activity);
            btnCancel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f));
            btnCancel.setText(android.R.string.cancel);
            CompatibilityUtils.setTextAppearance(btnCancel, android.R.style.TextAppearance_Small);
            btnCancel.setSingleLine();
            layoutSelectingButtons.addView(btnDownload);
            layoutSelectingButtons.addView(btnSelectAll);
            layoutSelectingButtons.addView(btnCancel);
            layoutSelectingButtons.setVisibility(View.GONE);
            layoutSelectingButtons.setLayoutParams(
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            
            btnToSelecting.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    btnToSelecting.setVisibility(View.GONE);
                    layoutSelectingButtons.setVisibility(View.VISIBLE);
                    gridAdapter.setSelectingMode(true);
                }
            });
            
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    btnToSelecting.setVisibility(View.VISIBLE);
                    layoutSelectingButtons.setVisibility(View.GONE);
                    gridAdapter.setSelectingMode(false);
                }
            });
            
            btnSelectAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    gridAdapter.selectAll();
                }
            });
            
            btnDownload.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    gridAdapter.downloadSelected(new Runnable() {
                        @Override
                        public void run() {
                            btnToSelecting.setVisibility(View.VISIBLE);
                            layoutSelectingButtons.setVisibility(View.GONE);
                            gridAdapter.setSelectingMode(false);
                        }
                    });
                }
            });
            
            LinearLayout dlgLayout = new LinearLayout(activity);
            dlgLayout.setOrientation(LinearLayout.VERTICAL);
            dlgLayout.addView(btnToSelecting);
            dlgLayout.addView(layoutSelectingButtons);
            dlgLayout.addView(grid);
            
            Dialog gridDialog = new Dialog(activity);
            gridDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            gridDialog.setContentView(dlgLayout);
            gridDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gridDialog.show();
        } catch (OutOfMemoryError oom) {
            MainApplication.freeMemory();
            Logger.e(TAG, oom);
            Toast.makeText(activity, R.string.error_out_of_memory, Toast.LENGTH_LONG).show();
        }
    }
    
    private void openAttachment(AttachmentModel attachment) {
        if (attachment.type == AttachmentModel.TYPE_OTHER_NOTFILE) {
            UrlHandler.open(chan.fixRelativeUrl(attachment.path), activity);
            return;
        }
        if (presentationModel == null || presentationModel.source == null || presentationModel.source.boardModel == null) return;
        Intent galleryIntent = new Intent(activity.getApplicationContext(), GalleryActivity.class);
        galleryIntent.putExtra(GalleryActivity.EXTRA_SETTINGS, GallerySettings.fromSettings(settings));
        galleryIntent.putExtra(GalleryActivity.EXTRA_ATTACHMENT, attachment);
        galleryIntent.putExtra(GalleryActivity.EXTRA_BOARDMODEL, presentationModel.source.boardModel);
        galleryIntent.putExtra(GalleryActivity.EXTRA_PAGEHASH, tabModel.hash);
        if (tabModel.type == TabModel.TYPE_LOCAL) {
            galleryIntent.putExtra(GalleryActivity.EXTRA_LOCALFILENAME, tabModel.localFilePath);
        }
        startActivity(galleryIntent);
    }
    
    @SuppressLint("InflateParams")
    private void runDelete(final DeletePostModel deletePostModel, final boolean hasFiles) {
        Context dialogContext = Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB ?
                new ContextThemeWrapper(activity, R.style.Theme_Neutron) : activity;
        View dlgLayout = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_delete, null);
        final EditText inputField = (EditText) dlgLayout.findViewById(R.id.dialog_delete_password_field);
        final CheckBox onlyFiles = (CheckBox) dlgLayout.findViewById(R.id.dialog_delete_only_files);
        inputField.setText(chan.getDefaultPassword());
        
        if (!presentationModel.source.boardModel.allowDeletePosts && !presentationModel.source.boardModel.allowDeleteFiles) {
            Logger.e(TAG, "board model doesn't support deleting");
            return;
        } else if (!presentationModel.source.boardModel.allowDeletePosts) {
            onlyFiles.setEnabled(false);
            onlyFiles.setChecked(true);
        } else if (presentationModel.source.boardModel.allowDeleteFiles && hasFiles) {
            onlyFiles.setEnabled(true);
        } else {
            onlyFiles.setEnabled(false);
        }
        
        DialogInterface.OnClickListener dlgOnClick = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                if (currentTask != null) currentTask.cancel();
                if (pullableLayout.isRefreshing()) setPullableNoRefreshing();
                deletePostModel.onlyFiles = onlyFiles.isChecked();
                deletePostModel.password = inputField.getText().toString();
                final ProgressDialog progressDlg = new ProgressDialog(activity);
                final CancellableTask deleteTask = new CancellableTask.BaseCancellableTask();
                progressDlg.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        deleteTask.cancel();
                    }
                });
                progressDlg.setCanceledOnTouchOutside(false);
                progressDlg.setMessage(resources.getString(R.string.dialog_delete_progress));
                progressDlg.show();
                Async.runAsync(new Runnable() {
                    @Override
                    public void run() {
                        String error = null;
                        String targetUrl = null;
                        if (deleteTask.isCancelled()) return;
                        try {
                            targetUrl = chan.deletePost(deletePostModel, null, deleteTask);
                        } catch (Exception e) {
                            if (e instanceof InteractiveException) {
                                if (deleteTask.isCancelled()) return;
                                ((InteractiveException) e).handle(activity, deleteTask, new InteractiveException.Callback() {
                                    @Override
                                    public void onSuccess() {
                                        if (!deleteTask.isCancelled()) {
                                            progressDlg.dismiss();
                                            onClick(dialog, which);
                                        }
                                    }
                                    @Override
                                    public void onError(String message) {
                                        if (!deleteTask.isCancelled()) {
                                            progressDlg.dismiss();
                                            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                                            runDelete(deletePostModel, hasFiles);
                                        }
                                    }
                                });
                                return;
                            }
                            
                            Logger.e(TAG, "cannot delete post", e);
                            error = e.getMessage() == null ? "" : e.getMessage();
                        }
                        if (deleteTask.isCancelled()) return;
                        final boolean success = error == null;
                        final String result = success ? targetUrl : error;
                        Async.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (deleteTask.isCancelled()) return;
                                progressDlg.dismiss();
                                if (success) {
                                    if (result == null) {
                                        update();
                                    } else {
                                        UrlHandler.open(result, activity);
                                    }
                                } else {
                                    Toast.makeText(activity, TextUtils.isEmpty(result) ? resources.getString(R.string.error_unknown) : result,
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                });
            }
        };
        new AlertDialog.Builder(activity).
                setTitle(R.string.dialog_delete_password).
                setView(dlgLayout).
                setPositiveButton(R.string.dialog_delete_button, dlgOnClick).
                setNegativeButton(android.R.string.cancel, null).
                create().
                show();
    }
    
    private void runReport(final DeletePostModel reportPostModel) {
        final EditText inputField = new EditText(activity);
        inputField.setSingleLine();
        if (presentationModel.source.boardModel.allowReport != BoardModel.REPORT_WITH_COMMENT) {
            inputField.setEnabled(false);
            inputField.setKeyListener(null);
        } else {
            inputField.setText(reportPostModel.reportReason == null ? "" : reportPostModel.reportReason);
        }
        
        DialogInterface.OnClickListener dlgOnClick = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                if (currentTask != null) currentTask.cancel();
                if (pullableLayout.isRefreshing()) setPullableNoRefreshing();
                reportPostModel.reportReason = inputField.getText().toString();
                final ProgressDialog progressDlg = new ProgressDialog(activity);
                final CancellableTask reportTask = new CancellableTask.BaseCancellableTask();
                progressDlg.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        reportTask.cancel();
                    }
                });
                progressDlg.setCanceledOnTouchOutside(false);
                progressDlg.setMessage(resources.getString(R.string.dialog_report_progress));
                progressDlg.show();
                Async.runAsync(new Runnable() {
                    @Override
                    public void run() {
                        String error = null;
                        String targetUrl = null;
                        if (reportTask.isCancelled()) return;
                        try {
                            targetUrl = chan.reportPost(reportPostModel, null, reportTask);
                        } catch (Exception e) {
                            if (e instanceof InteractiveException) {
                                if (reportTask.isCancelled()) return;
                                ((InteractiveException) e).handle(activity, reportTask, new InteractiveException.Callback() {
                                    @Override
                                    public void onSuccess() {
                                        if (!reportTask.isCancelled()) {
                                            progressDlg.dismiss();
                                            onClick(dialog, which);
                                        }
                                    }
                                    @Override
                                    public void onError(String message) {
                                        if (!reportTask.isCancelled()) {
                                            progressDlg.dismiss();
                                            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                                            runReport(reportPostModel);
                                        }
                                    }
                                });
                                return;
                            }
                            
                            Logger.e(TAG, "cannot report post", e);
                            error = e.getMessage() == null ? "" : e.getMessage();
                        }
                        if (reportTask.isCancelled()) return;
                        final boolean success = error == null;
                        final String result = success ? targetUrl : error;
                        Async.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (reportTask.isCancelled()) return;
                                progressDlg.dismiss();
                                if (success) {
                                    if (result == null) {
                                        update();
                                    } else {
                                        UrlHandler.open(result, activity);
                                    }
                                } else {
                                    Toast.makeText(activity, TextUtils.isEmpty(result) ? resources.getString(R.string.error_unknown) : result,
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                });
            }
        };
        new AlertDialog.Builder(activity).
                setTitle(R.string.dialog_report_reason).
                setView(inputField).
                setPositiveButton(R.string.dialog_report_button, dlgOnClick).
                setNegativeButton(android.R.string.cancel, null).
                create().
                show();
    }
    
    private void openFromChan() {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    UrlHandler.open(presentationModel.source.pageModel, activity);
                }
            }
        };
        new AlertDialog.Builder(activity).setMessage(R.string.dialog_open_chan_text).
            setPositiveButton(android.R.string.yes, dialogClickListener).setNegativeButton(android.R.string.no, dialogClickListener).create().show();
    }
    
    private static class OpenedDialogs {
        private List<WeakReference<Dialog>> refsList = new ArrayList<>();
        private ReferenceQueue<Dialog> queue = new ReferenceQueue<>();
        
        private void reduce() {
            Reference<? extends Dialog> r;
            while ((r = queue.poll()) != null) {
                int i = refsList.indexOf(r);
                if (i != -1) refsList.remove(i);
            }
        }
        
        private synchronized void add(Dialog dialog) {
            reduce();
            refsList.add(new WeakReference<>(dialog));
        }
        
        public void onDestroyFragment(long tabId) {
            Fragment currentFragment = MainApplication.getInstance().tabsSwitcher.currentFragment;
            if (currentFragment instanceof BoardFragment && currentFragment.getArguments().getLong("TabModelId") == tabId) return;
            
            reduce();
            for (int i=0; i<refsList.size(); ++i) {
                Dialog dialog = refsList.get(i).get();
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
            }
        }
    }
    
}
