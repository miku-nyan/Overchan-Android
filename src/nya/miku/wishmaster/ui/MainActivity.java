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

package nya.miku.wishmaster.ui;

import java.io.Serializable;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.common.CompatibilityImpl;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.common.PriorityThreadFactory;
import nya.miku.wishmaster.lib.appcompat_v7_actionbartoogle.wrappers.ActionBarDrawerToogleCompat;
import nya.miku.wishmaster.lib.appcompat_v7_actionbartoogle.wrappers.ActionBarDrawerToogleV4;
import nya.miku.wishmaster.lib.appcompat_v7_actionbartoogle.wrappers.ActionBarDrawerToogleV7;
import nya.miku.wishmaster.lib.dslv.DragSortController;
import nya.miku.wishmaster.lib.dslv.DragSortListView;
import nya.miku.wishmaster.lib.dslv.DragSortListView.DropListener;
import nya.miku.wishmaster.ui.posting.PostFormActivity;
import nya.miku.wishmaster.ui.posting.PostingService;
import nya.miku.wishmaster.ui.presentation.FlowTextHelper;
import nya.miku.wishmaster.ui.settings.PreferencesActivity;
import nya.miku.wishmaster.ui.settings.ApplicationSettings.StaticSettingsContainer;
import nya.miku.wishmaster.ui.tabs.LocalHandler;
import nya.miku.wishmaster.ui.tabs.TabModel;
import nya.miku.wishmaster.ui.tabs.TabsAdapter;
import nya.miku.wishmaster.ui.tabs.TabsState;
import nya.miku.wishmaster.ui.tabs.TabsTrackerService;
import nya.miku.wishmaster.ui.tabs.UrlHandler;
import nya.miku.wishmaster.ui.tabs.TabsAdapter.TabSelectListener;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.DrawerLayout;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

public class MainActivity extends FragmentActivity {
    private static final String TAG = "MainActivity";
    
    @SuppressLint("InlinedApi")
    private static final int DRAWER_GRAVITY = Gravity.START;
    
    private NotificationManager notificationManager;
    private BroadcastReceiver broadcastReceiver;
    private IntentFilter intentFilter;
    
    public TabsAdapter tabsAdapter = null;
    public StaticSettingsContainer settings;
    private int autohideRulesHash;
    private float rootViewWeight = 0;
    private boolean openSpoilers;
    private boolean isHorizontalOrientation;
    private boolean isPaused = false;
    private boolean isDestroyed = false;
    
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToogleCompat drawerToggle;
    
    private HiddenTabsSection hiddenTabsSection = null;
    
    private void initDrawer() {
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer);
        if (drawerLayout == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) return;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(R.attr.iconDrawer, typedValue, true);
            drawerToggle = new ActionBarDrawerToogleV4(this, drawerLayout, typedValue.resourceId, R.string.drawer_open, R.string.drawer_close) {
                @SuppressWarnings("deprecation")
                @Override
                public void onDrawerClosed(View drawerView) {
                    super.onDrawerClosed(drawerView);
                    if (tabsAdapter != null) tabsAdapter.setDraggingItem(-1);
                }
            };
        } else {
            drawerToggle = new ActionBarDrawerToogleV7(this, drawerLayout, R.string.drawer_open, R.string.drawer_close) {
                @Override
                public void onDrawerClosed(View drawerView) {
                    super.onDrawerClosed(drawerView);
                    if (tabsAdapter != null) tabsAdapter.setDraggingItem(-1);
                }
            };
        }
        drawerLayout.setDrawerListener(drawerToggle);
        
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, DRAWER_GRAVITY);
        CompatibilityImpl.activeActionBar(this);
    }
    
    private void openDrawer() {
        if (drawerLayout != null) drawerLayout.openDrawer(DRAWER_GRAVITY);
    }
    
    private void closeDrawer() {
        if (drawerLayout != null) drawerLayout.closeDrawers();
    }
    
    public void setDrawerLock(int lockMode) {
        if (drawerLayout != null) drawerLayout.setDrawerLockMode(lockMode, DRAWER_GRAVITY);
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (drawerToggle != null) {
            drawerToggle.onConfigurationChanged(newConfig);
        }
        if (!isPaused) handleOrientationChange(newConfig);
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (drawerToggle != null) {
            drawerToggle.syncState();
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!MainApplication.getInstance().settings.showSidePanel() && Build.VERSION.SDK_INT < 11) {
            menu.add(Menu.NONE, R.id.menu_open_close_drawer, Menu.FIRST, R.string.menu_open_drawer).setIcon(R.drawable.ic_menu_windows);
        }
        if (tabsAdapter != null && tabsAdapter.getSelectedItem() >= 0) {
            TabModel tab = tabsAdapter.getItem(tabsAdapter.getSelectedItem());
            if (canFavorite(tab)) {
                menu.add(Menu.NONE, R.id.menu_favorites, 201, R.string.menu_add_favorites).setIcon(R.drawable.ic_menu_add_bookmark);
            }
            if (tab.webUrl != null) {
                menu.add(Menu.NONE, R.id.menu_open_browser, 202, R.string.menu_open_browser).setIcon(R.drawable.ic_menu_browser);
            }
        }
        menu.add(Menu.NONE, R.id.menu_settings, 203, R.string.menu_preferences).setIcon(android.R.drawable.ic_menu_preferences);
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem drawerMenuItem = menu.findItem(R.id.menu_open_close_drawer); 
        if (drawerMenuItem != null) {
            drawerMenuItem.setTitle(drawerLayout.isDrawerOpen(DRAWER_GRAVITY) ? R.string.menu_close_drawer : R.string.menu_open_drawer);
        }
        MenuItem favoritesMenuItem = menu.findItem(R.id.menu_favorites);
        if (favoritesMenuItem != null && tabsAdapter != null && tabsAdapter.getSelectedItem() >= 0) {
            TabModel tab = tabsAdapter.getItem(tabsAdapter.getSelectedItem());
            favoritesMenuItem.setTitle(isFavorite(tab) ? R.string.menu_remove_favorites : R.string.menu_add_favorites);
        }
        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_open_browser:
                if (tabsAdapter != null && tabsAdapter.getSelectedItem() >= 0 && tabsAdapter.getItem(tabsAdapter.getSelectedItem()).webUrl != null) {
                    UrlHandler.launchExternalBrowser(this, tabsAdapter.getItem(tabsAdapter.getSelectedItem()).webUrl);
                }
                return true;
            case R.id.menu_favorites:
                if (tabsAdapter != null && tabsAdapter.getSelectedItem() >= 0 && tabsAdapter.getItem(tabsAdapter.getSelectedItem()).webUrl != null) {
                    handleFavorite(tabsAdapter.getItem(tabsAdapter.getSelectedItem()));
                }
                return true;
            case R.id.menu_settings:
                Intent preferencesIntent = new Intent(this, PreferencesActivity.class);
                this.startActivity(preferencesIntent);
                return true;
            case R.id.menu_open_close_drawer:
                if (drawerLayout.isDrawerOpen(DRAWER_GRAVITY)) {
                    closeDrawer();
                } else {
                    openDrawer();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.sidebar_tabs_list) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            TabModel model = tabsAdapter.getItem(info.position);
            if (tabsAdapter.getCount() > 1) {
                menu.add(Menu.NONE, R.id.context_menu_move, 1, R.string.context_menu_move);
            }
            if (model.webUrl != null) {
                menu.add(Menu.NONE, R.id.context_menu_copy_url, 2, R.string.context_menu_copy_url);
            }
            if (canFavorite(model)) {
                menu.add(Menu.NONE, R.id.context_menu_favorites, 3,
                        isFavorite(model) ? R.string.context_menu_remove_favorites : R.string.context_menu_add_favorites);
            }
            if (MainApplication.getInstance().settings.isAutoupdateEnabled() && MainApplication.getInstance().settings.isAutoupdateBackground() &&
                    model.type == TabModel.TYPE_NORMAL && model.pageModel != null && model.pageModel.type == UrlPageModel.TYPE_THREADPAGE) {
                menu.add(Menu.NONE, R.id.context_menu_autoupdate_background, 4, R.string.context_menu_autoupdate_background).
                        setCheckable(true).setChecked(model.autoupdateBackground);
            }
        }
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.context_menu_move:
                tabsAdapter.setDraggingItem(menuInfo.position);
                return true;
            case R.id.context_menu_copy_url:
                String url = tabsAdapter.getItem(menuInfo.position).webUrl;
                if (url != null) {
                    Clipboard.copyText(this, url);
                    Toast.makeText(this, getString(R.string.notification_url_copied, url), Toast.LENGTH_LONG).show();
                }
                return true;
            case R.id.context_menu_favorites:
                handleFavorite(tabsAdapter.getItem(menuInfo.position));
                return true;
            case R.id.context_menu_autoupdate_background:
                tabsAdapter.getItem(menuInfo.position).autoupdateBackground = !tabsAdapter.getItem(menuInfo.position).autoupdateBackground;
                tabsAdapter.notifyDataSetChanged();
                return true;
        }
        return false;
    }
    
    private void updateTabPanelTabletWeight() {
        if (MainApplication.getInstance().settings.isRealTablet() && !MainApplication.getInstance().settings.showSidePanel()) {
            DrawerLayout.LayoutParams sidebarLayoutParams = (DrawerLayout.LayoutParams) findViewById(R.id.sidebar).getLayoutParams();
            Point displaySize = AppearanceUtils.getDisplaySize(getWindowManager().getDefaultDisplay());
            sidebarLayoutParams.width = (int) (displaySize.x * MainApplication.getInstance().settings.getTabPanelTabletWeight());
            findViewById(R.id.sidebar).setLayoutParams(sidebarLayoutParams);
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logger.d(TAG, "main activity creating");
        settings = MainApplication.getInstance().settings.getStaticSettings();
        autohideRulesHash = MainApplication.getInstance().settings.getAutohideRulesJson().hashCode();
        rootViewWeight = MainApplication.getInstance().settings.getRootViewWeight();
        openSpoilers = MainApplication.getInstance().settings.openSpoilers();
        isHorizontalOrientation = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        setTheme(settings.theme);
        super.onCreate(savedInstanceState);
        if (MainApplication.getInstance().settings.showSidePanel()) {
            setContentView(R.layout.main_activity_tablet);
            LinearLayout.LayoutParams sidebarLayoutParams = (LinearLayout.LayoutParams) findViewById(R.id.sidebar).getLayoutParams();
            Point displaySize = AppearanceUtils.getDisplaySize(getWindowManager().getDefaultDisplay());
            int rootWidth = (int) (displaySize.x * rootViewWeight);
            sidebarLayoutParams.width = displaySize.x - rootWidth;
            findViewById(R.id.sidebar).setLayoutParams(sidebarLayoutParams);
        } else {
            setContentView(R.layout.main_activity_drawer);
            updateTabPanelTabletWeight();
        }
        initDrawer();
        
		View[] sidebarButtons =
		    new View[] { findViewById(R.id.sidebar_btn_newtab), findViewById(R.id.sidebar_btn_history), findViewById(R.id.sidebar_btn_favorites) };
        hiddenTabsSection = new HiddenTabsSection(sidebarButtons);
        
        DragSortListView list = (DragSortListView)findViewById(R.id.sidebar_tabs_list);
        TabsState state = MainApplication.getInstance().tabsState;
        tabsAdapter = initTabsListView(list, state);
        handleUriIntent(getIntent());
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) {
                    Logger.e(TAG, "received broadcast with NULL action");
                    return;
                }
                if (action.equals(PostingService.BROADCAST_ACTION_STATUS)) {
                    int progress = intent.getIntExtra(PostingService.EXTRA_BROADCAST_PROGRESS_STATUS, -1);
                    switch (progress) {
                        case PostingService.BROADCAST_STATUS_SUCCESS:
                            UrlHandler.open(intent.getStringExtra(PostingService.EXTRA_TARGET_URL), MainActivity.this);
                            notificationManager.cancel(PostingService.POSTING_NOTIFICATION_ID);
                            break;
                        case PostingService.BROADCAST_STATUS_ERROR:
                            Intent toPostForm = new Intent(MainActivity.this, PostFormActivity.class);
                            toPostForm.putExtra(PostingService.EXTRA_PAGE_HASH, intent.getStringExtra(PostingService.EXTRA_PAGE_HASH));
                            toPostForm.putExtra(PostingService.EXTRA_SEND_POST_MODEL,
                                    intent.getSerializableExtra(PostingService.EXTRA_SEND_POST_MODEL));
                            toPostForm.putExtra(PostingService.EXTRA_BOARD_MODEL, intent.getSerializableExtra(PostingService.EXTRA_BOARD_MODEL));
                            toPostForm.putExtra(PostingService.EXTRA_RETURN_FROM_SERVICE, true);
                            toPostForm.putExtra(PostingService.EXTRA_RETURN_REASON, intent.getIntExtra(PostingService.EXTRA_RETURN_REASON, 0));
                            String error = intent.getStringExtra(PostingService.EXTRA_RETURN_REASON_ERROR);
                            Serializable interactiveException = intent.getSerializableExtra(PostingService.EXTRA_RETURN_REASON_INTERACTIVE_EXCEPTION);
                            if (error != null) {
                                toPostForm.putExtra(PostingService.EXTRA_RETURN_REASON_ERROR, error);
                            }
                            if (interactiveException != null) {
                                toPostForm.putExtra(PostingService.EXTRA_RETURN_REASON_INTERACTIVE_EXCEPTION, interactiveException);
                            }
                            startActivity(toPostForm);
                            notificationManager.cancel(PostingService.POSTING_NOTIFICATION_ID);
                            break;
                    }
                } else if (action.equals(TabsTrackerService.BROADCAST_ACTION_NOTIFY)) {
                    tabsAdapter.notifyDataSetChanged(false);
                    TabsTrackerService.unread = false;
                }
            }
        };
        intentFilter = new IntentFilter();
        intentFilter.addAction(PostingService.BROADCAST_ACTION_STATUS);
        intentFilter.addAction(TabsTrackerService.BROADCAST_ACTION_NOTIFY);
        
        if (!TabsTrackerService.running && MainApplication.getInstance().settings.isAutoupdateEnabled())
            startService(new Intent(this, TabsTrackerService.class));
    }
    
    @Override
    protected void onStart() {
    	super.onStart();
    	registerReceiver(broadcastReceiver, intentFilter);
    	tabsAdapter.notifyDataSetChanged(false);
    }
    
    @Override
    protected void onStop() {
    	super.onStop();
    	unregisterReceiver(broadcastReceiver);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        MainApplication.getInstance().tabsSwitcher.currentId = null;
        MainApplication.getInstance().tabsSwitcher.currentFragment = null;
        isDestroyed = true;
        Logger.d(TAG, "main activity destroyed");
    }
    
    public boolean isPaused() {
        return isPaused;
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        isPaused = true;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        isPaused = false;
        TabsTrackerService.unread = false;
        StaticSettingsContainer newSettings = MainApplication.getInstance().settings.getStaticSettings();
        if (settings.theme != newSettings.theme || settings.isDisplayDate != newSettings.isDisplayDate ||
                (settings.isDisplayDate && (settings.isLocalTime != newSettings.isLocalTime)) ||
                MainApplication.getInstance().settings.getAutohideRulesJson().hashCode() != autohideRulesHash ||
                MainApplication.getInstance().settings.getRootViewWeight() != rootViewWeight ||
                MainApplication.getInstance().settings.openSpoilers() != openSpoilers) {
            Logger.d(TAG, "appearance settings were changed; clearing LRU cache and restarting activity");
            restartActivityClearCache();
            return;
        } else {
            MainApplication.getInstance().settings.updateStaticSettings(settings);
            updateTabPanelTabletWeight();
        }
        handleOrientationChange(getResources().getConfiguration());
    }
    
    private void restartActivityClearCache() {
        MainApplication.getInstance().tabsSwitcher.currentId = null;
        MainApplication.getInstance().tabsSwitcher.currentFragment = null;
        MainApplication.getInstance().pagesCache.clearLru();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            CompatibilityImpl.recreateActivity(this);
        } else {
            final Intent i = new Intent(this.getIntent());
            this.finish();
            final Handler handler = new Handler();
            PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
                @Override
                public void run() {
                    //сначала должно уничтожиться старое activity; onDestroy() старого -> onCreate() нового
                    while (!isDestroyed) Thread.yield();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.this.startActivity(i);
                        }
                    });
                }
            }).start();
        }
    }
    
    private void handleOrientationChange(Configuration configuration) {
        boolean newOrientation = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (newOrientation != isHorizontalOrientation) {
            if (FlowTextHelper.IS_AVAILABLE) {
                Logger.d(TAG, "changed display orientation; clearing LRU cache and restarting activity");
                restartActivityClearCache();
            } else {
                isHorizontalOrientation = newOrientation;
            }
        }
    }
    
    class HiddenTabsSection implements View.OnClickListener {
        private View btnNewTab;
        private View btnHistory;
        private View btnFavorites;
        public HiddenTabsSection(View[] views) {
            for (View view : views) {
                view.setOnClickListener(this);
                switch (view.getId()) {
                    case R.id.sidebar_btn_newtab:
                        btnNewTab = view;
                        break;
                    case R.id.sidebar_btn_favorites:
                        btnFavorites = view;
                        break;
                    case R.id.sidebar_btn_history:
                        btnHistory = view;
                        break;
                }
            }
        }
        
        public void updateViewSelection(int selectedPosition) {
            setSelectedBackground(btnNewTab, selectedPosition == TabModel.POSITION_NEWTAB);
            setSelectedBackground(btnHistory, selectedPosition == TabModel.POSITION_HISTORY);
            setSelectedBackground(btnFavorites, selectedPosition == TabModel.POSITION_FAVORITES);
        }
        
        @Override
        public void onClick(View v) {
            tabsAdapter.setDraggingItem(-1);
            int position = TabModel.POSITION_NEWTAB;
            switch (v.getId()) {
                case R.id.sidebar_btn_newtab:
                    position = TabModel.POSITION_NEWTAB;
                    break;
                case R.id.sidebar_btn_favorites:
                    position = TabModel.POSITION_FAVORITES;
                    break;
                case R.id.sidebar_btn_history:
                    position = TabModel.POSITION_HISTORY;
                    break;
            }
            tabsAdapter.setDraggingItem(-1);
            boolean needSerialize = tabsAdapter.getSelectedItem() != position;
            tabsAdapter.setSelectedItem(position, needSerialize);
            closeDrawer();
        }
        
        private void setSelectedBackground(View view, boolean selected) {
            TypedValue typedValue = new TypedValue();
            if (selected) {
                getTheme().resolveAttribute(R.attr.sidebarSelectedItem, typedValue, true);
            } else {
                TypedArray typedArray = obtainStyledAttributes(R.style.SelectableItem, new int[] { android.R.attr.background });
                typedArray.getValue(0, typedValue);
                typedArray.recycle();
            }
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                view.setBackgroundColor(typedValue.data);
            } else {
                view.setBackgroundResource(typedValue.resourceId); 
            }
        }
        
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private TabsAdapter initTabsListView(final DragSortListView list, final TabsState tabsState) {
        final TabsAdapter adapter = new TabsAdapter(this, tabsState, new TabSelectListener() {
            private final int selectionOffset = (int)(getResources().getDimension(R.dimen.tab_height) * 0.35f);
            @Override
            public void onTabSelected(int position) {
                hiddenTabsSection.updateViewSelection(position);
                if (position >= 0) {
                    list.setItemChecked(position, true);
                    boolean visible = false;
                    int absChildCount = list.getChildCount();
                    for (int i=0; i<absChildCount; ++i) {
                        if (position == list.getPositionForView(list.getChildAt(i))) {
                            visible = true;
                            break;
                        }
                    }
                    if (!visible) {
                        if (position == 0) list.setSelection(0);
                        else list.setSelectionFromTop(position, selectionOffset);
                    }
                    MainApplication.getInstance().tabsSwitcher.switchTo(tabsState.tabsArray.get(position), getSupportFragmentManager());
                } else {
                    list.clearChoices();
                    MainApplication.getInstance().tabsSwitcher.switchTo(position, getSupportFragmentManager());
                }
            }
        });
        
        DragSortController controller = new DragSortController(list, R.id.tab_drag_handle, DragSortController.ON_DRAG, 0) {
            @Override
            public View onCreateFloatView(int position) { return adapter.getView(position, null, list); }
            @Override
            public void onDragFloatView(View floatView, Point floatPoint, Point touchPoint) {}
            @Override
            public void onDestroyFloatView(View floatView) {}
        };
        
        list.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                boolean needCloseDrawer = adapter.getDraggingItem() == -1 || position != adapter.getSelectedItem();
                adapter.setDraggingItem(-1);
                boolean needSerialize = adapter.getSelectedItem() != position;
                adapter.setSelectedItem(position, needSerialize);
                if (needCloseDrawer) closeDrawer();
            }
        });
        
        list.setDropListener(new DropListener() {
            @Override
            public void drop(int from, int to) {
                if (from != to) {
                    adapter.setDraggingItem(-1);
                    int newSelected = to;
                    if (from != adapter.getSelectedItem()) {
                        newSelected = adapter.getSelectedItem();
                        if (from < adapter.getSelectedItem() && to >= adapter.getSelectedItem()) --newSelected;
                        else if (from > adapter.getSelectedItem() && to <= adapter.getSelectedItem()) ++newSelected;
                    }
                    TabModel model = tabsState.tabsArray.remove(from);
                    tabsState.tabsArray.add(to, model);
                    adapter.setSelectedItem(newSelected); //serialization enabled
                }
            }
        });
        
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setAdapter(adapter);
        list.setDragEnabled(true);
        list.setFloatViewManager(controller);
        list.setOnTouchListener(controller);
        registerForContextMenu(list);
        adapter.setSelectedItem(tabsState.position, false);
        return adapter;
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && onBack()) {
            return true;
        } else { 
            return super.onKeyDown(keyCode, event);
        }
    }
    
    private boolean onBack() {
        if (tabsAdapter != null && tabsAdapter.getDraggingItem() != -1) {
            tabsAdapter.setDraggingItem(-1);
            return true;
        }
        if (drawerLayout != null && drawerLayout.isDrawerOpen(DRAWER_GRAVITY)) {
            closeDrawer();
            return true;
        }
        if (tabsAdapter != null && tabsAdapter.back()) {
            return true;
        }
        return false;
    }
    
    private boolean canFavorite(TabModel tab) {
        if (tab.type == TabModel.TYPE_LOCAL || tab.pageModel == null) return false;
        switch (tab.pageModel.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
            case UrlPageModel.TYPE_BOARDPAGE:
            case UrlPageModel.TYPE_THREADPAGE:
                return true;
        }
        return false;
    }
    
    private boolean isFavorite(TabModel tab) {
        if (tab.type == TabModel.TYPE_LOCAL || tab.pageModel == null) return false;
        Database database = MainApplication.getInstance().database;
        switch (tab.pageModel.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                return database.isFavorite(tab.pageModel.chanName, null, null, null);
            case UrlPageModel.TYPE_BOARDPAGE:
                return database.isFavorite(tab.pageModel.chanName, tab.pageModel.boardName, Integer.toString(tab.pageModel.boardPage), null);
            case UrlPageModel.TYPE_THREADPAGE:
                return database.isFavorite(tab.pageModel.chanName, tab.pageModel.boardName, null, tab.pageModel.threadNumber);
        }
        return false;
    }
    
    private void handleFavorite(TabModel tab) {
        if (tab.type == TabModel.TYPE_LOCAL || tab.pageModel == null) return;
        Database database = MainApplication.getInstance().database;
        switch (tab.pageModel.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                if (database.isFavorite(tab.pageModel.chanName, null, null, null)) {
                    database.removeFavorite(tab.pageModel.chanName, null, null, null);
                } else {
                    database.addFavorite(tab.pageModel.chanName, null, null, null, tab.title, tab.webUrl);
                }
                break;
            case UrlPageModel.TYPE_BOARDPAGE:
                if (database.isFavorite(tab.pageModel.chanName, tab.pageModel.boardName, Integer.toString(tab.pageModel.boardPage), null)) {
                    database.removeFavorite(tab.pageModel.chanName, tab.pageModel.boardName, Integer.toString(tab.pageModel.boardPage), null);
                } else {
                    database.addFavorite(tab.pageModel.chanName, tab.pageModel.boardName, Integer.toString(tab.pageModel.boardPage), null,
                            tab.title, tab.webUrl);
                }
                break;
            case UrlPageModel.TYPE_THREADPAGE:
                if (database.isFavorite(tab.pageModel.chanName, tab.pageModel.boardName, null, tab.pageModel.threadNumber)) {
                    database.removeFavorite(tab.pageModel.chanName, tab.pageModel.boardName, null, tab.pageModel.threadNumber);
                } else {
                    database.addFavorite(tab.pageModel.chanName, tab.pageModel.boardName, null, tab.pageModel.threadNumber, tab.title, tab.webUrl);
                }
                break;
        }
        if (MainApplication.getInstance().tabsSwitcher.currentId != null &&
                MainApplication.getInstance().tabsSwitcher.currentId.equals(Long.valueOf(TabModel.POSITION_FAVORITES)) &&
                MainApplication.getInstance().tabsSwitcher.currentFragment instanceof FavoritesFragment) {
            ((FavoritesFragment) MainApplication.getInstance().tabsSwitcher.currentFragment).update();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleUriIntent(intent);
    }
    
    private void handleUriIntent(Intent intent) {
        TabsTrackerService.unread = false;
        if (intent != null) {
            if (intent.getData() != null && URLUtil.isFileUrl(intent.getDataString())) {
                LocalHandler.open(intent.getData().getPath(), this);
                return;
            }
            String url = intent.getDataString();
            if (url != null && url.length() != 0) {
                UrlHandler.open(url, this);
            }
        }
    }
}
