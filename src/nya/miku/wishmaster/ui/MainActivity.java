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

package nya.miku.wishmaster.ui;

import java.io.Serializable;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.client.ExtendedTrustManager;
import nya.miku.wishmaster.lib.appcompat_v7_actionbartoogle.wrappers.ActionBarDrawerToogleCompat;
import nya.miku.wishmaster.lib.appcompat_v7_actionbartoogle.wrappers.ActionBarDrawerToogleV4;
import nya.miku.wishmaster.lib.appcompat_v7_actionbartoogle.wrappers.ActionBarDrawerToogleV7;
import nya.miku.wishmaster.lib.dslv.DragSortController;
import nya.miku.wishmaster.lib.dslv.DragSortListView;
import nya.miku.wishmaster.lib.dslv.DragSortListView.DropListener;
import nya.miku.wishmaster.ui.posting.PostFormActivity;
import nya.miku.wishmaster.ui.posting.PostingService;
import nya.miku.wishmaster.ui.presentation.BoardFragment;
import nya.miku.wishmaster.ui.presentation.FlowTextHelper;
import nya.miku.wishmaster.ui.settings.PreferencesActivity;
import nya.miku.wishmaster.ui.settings.ApplicationSettings.StaticSettingsContainer;
import nya.miku.wishmaster.ui.tabs.LocalHandler;
import nya.miku.wishmaster.ui.tabs.TabModel;
import nya.miku.wishmaster.ui.tabs.TabsAdapter;
import nya.miku.wishmaster.ui.tabs.TabsState;
import nya.miku.wishmaster.ui.tabs.TabsTrackerService;
import nya.miku.wishmaster.ui.tabs.UrlHandler;
import nya.miku.wishmaster.ui.theme.GenericThemeEntry;
import nya.miku.wishmaster.ui.theme.ThemeUtils;
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
import android.support.v4.app.Fragment;
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
import android.view.ViewGroup;
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
    private GenericThemeEntry theme;
    private int thumbnailSize;
    private int autohideRulesHash;
    private float rootViewWeight = 0;
    private boolean tabsPanelRight;
    private boolean openSpoilers;
    private boolean highlightSubscriptions;
    private boolean swipeToHideThread;
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
            int drawerResId = ThemeUtils.getThemeResId(getTheme(), R.attr.iconDrawer);
            drawerToggle = new ActionBarDrawerToogleV4(this, drawerLayout, drawerResId, R.string.drawer_open, R.string.drawer_close) {
                @SuppressWarnings("deprecation")
                @Override
                public void onDrawerClosed(View drawerView) {
                    super.onDrawerClosed(drawerView);
                    if (tabsAdapter != null) tabsAdapter.setDraggingItem(-1);
                }
                @SuppressWarnings("deprecation")
                @Override
                public void onDrawerSlide(View drawerView, float slideOffset) {
                    super.onDrawerSlide(drawerView, slideOffset);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) CompatibilityImpl.showActionBar(MainActivity.this);
                }
            };
        } else {
            drawerToggle = new ActionBarDrawerToogleV7(this, drawerLayout, R.string.drawer_open, R.string.drawer_close) {
                @Override
                public void onDrawerClosed(View drawerView) {
                    super.onDrawerClosed(drawerView);
                    if (tabsAdapter != null) tabsAdapter.setDraggingItem(-1);
                }
                @Override
                public void onDrawerSlide(View drawerView, float slideOffset) {
                    super.onDrawerSlide(drawerView, slideOffset);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) CompatibilityImpl.showActionBar(MainActivity.this);
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
        Menu subMenu = menu.addSubMenu(Menu.NONE, R.id.menu_sub_settings, 203, R.string.menu_preferences).
                setIcon(android.R.drawable.ic_menu_preferences);
        subMenu.add(Menu.NONE, R.id.menu_sub_settings_suspend, 1, R.string.menu_sub_preferences_suspend);
        subMenu.add(Menu.NONE, R.id.menu_sub_settings_autoupdate, 2, R.string.menu_sub_preferences_autoupdate).setCheckable(true);
        subMenu.add(Menu.NONE, R.id.menu_sub_settings_maskpictures, 3, R.string.menu_sub_preferences_maskpictures).setCheckable(true);
        subMenu.add(Menu.NONE, R.id.menu_sub_settings_all, 4, R.string.menu_sub_preferences_all);
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
        MenuItem preferencesMenuItem = menu.findItem(R.id.menu_settings);
        MenuItem preferencesSubMenuItem = menu.findItem(R.id.menu_sub_settings);
        try {
            if (MainApplication.getInstance().settings.preferencesSubmenu()) {
                preferencesSubMenuItem.setVisible(true);
                preferencesMenuItem.setVisible(false);
                Menu subMenu = preferencesSubMenuItem.getSubMenu();
                subMenu.findItem(R.id.menu_sub_settings_suspend).setVisible(MainApplication.getInstance().settings.isAutoupdateEnabled());
                subMenu.findItem(R.id.menu_sub_settings_autoupdate).setChecked(MainApplication.getInstance().settings.isAutoupdateEnabled());
                subMenu.findItem(R.id.menu_sub_settings_maskpictures).setChecked(MainApplication.getInstance().settings.maskPictures());
            } else {
                preferencesMenuItem.setVisible(true);
                preferencesSubMenuItem.setVisible(false);
            }
        } catch (Exception e) {
            Logger.e(TAG, e);
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
            case R.id.menu_sub_settings_all:
                Intent preferencesIntent = new Intent(this, PreferencesActivity.class);
                this.startActivity(preferencesIntent);
                return true;
            case R.id.menu_sub_settings_suspend:
                Toast.makeText(this, R.string.notification_suspend, Toast.LENGTH_LONG).show();
                if (TabsTrackerService.isRunning()) stopService(new Intent(MainActivity.this, TabsTrackerService.class));
                finish();
                return true;
            case R.id.menu_sub_settings_autoupdate:
                MainApplication.getInstance().settings.setAutoupdateEnabled(!MainApplication.getInstance().settings.isAutoupdateEnabled());
                if (TabsTrackerService.isRunning()) stopService(new Intent(this, TabsTrackerService.class));
                if (MainApplication.getInstance().settings.isAutoupdateEnabled()) startService(new Intent(this, TabsTrackerService.class));
                return true;
            case R.id.menu_sub_settings_maskpictures:
                MainApplication.getInstance().settings.setMaskPictures(!MainApplication.getInstance().settings.maskPictures());
                MainApplication.getInstance().settings.updateStaticSettings(settings);
                reloadCurrentBoardFragment();
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
                menu.add(Menu.NONE, R.id.context_menu_share_link, 3, R.string.context_menu_share_link);
            }
            if (canFavorite(model)) {
                menu.add(Menu.NONE, R.id.context_menu_favorites, 4,
                        isFavorite(model) ? R.string.context_menu_remove_favorites : R.string.context_menu_add_favorites);
            }
            if (model.type == TabModel.TYPE_NORMAL && model.pageModel != null && model.pageModel.type == UrlPageModel.TYPE_THREADPAGE) {
                boolean backgroundAutoupdateEnabled =
                        MainApplication.getInstance().settings.isAutoupdateEnabled() &&
                        MainApplication.getInstance().settings.isAutoupdateBackground();
                menu.add(Menu.NONE, R.id.context_menu_autoupdate_background, 5,
                        backgroundAutoupdateEnabled ? R.string.context_menu_autoupdate_background : R.string.context_menu_autoupdate_background_off).
                        setCheckable(true).setChecked(model.autoupdateBackground);
            }
            if (model.autoupdateBackground && TabsTrackerService.getCurrentUpdatingTabId() == -1) {
                menu.add(Menu.NONE, R.id.context_menu_autoupdate_now, 6, R.string.context_menu_autoupdate_now);
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
            case R.id.context_menu_share_link:
                url = tabsAdapter.getItem(menuInfo.position).webUrl;
                if (url != null) {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, url);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, url);
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)));
                }
                return true;
            case R.id.context_menu_favorites:
                handleFavorite(tabsAdapter.getItem(menuInfo.position));
                return true;
            case R.id.context_menu_autoupdate_background:
                tabsAdapter.getItem(menuInfo.position).autoupdateBackground = !tabsAdapter.getItem(menuInfo.position).autoupdateBackground;
                tabsAdapter.notifyDataSetChanged();
                return true;
            case R.id.context_menu_autoupdate_now:
                startService(new Intent(this, TabsTrackerService.class).putExtra(TabsTrackerService.EXTRA_UPDATE_IMMEDIATELY, true));
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
        tabsPanelRight = MainApplication.getInstance().settings.isTabsPanelOnRight();
        openSpoilers = MainApplication.getInstance().settings.openSpoilers();
        highlightSubscriptions = MainApplication.getInstance().settings.highlightSubscriptions();
        swipeToHideThread = MainApplication.getInstance().settings.swipeToHideThread();
        isHorizontalOrientation = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        (theme = MainApplication.getInstance().settings.getTheme()).setTo(this);
        thumbnailSize = MainApplication.getInstance().settings.getPostThumbnailSize();
        super.onCreate(savedInstanceState);
        if (MainApplication.getInstance().settings.showSidePanel()) {
            setContentView(tabsPanelRight ? R.layout.main_activity_tablet_right : R.layout.main_activity_tablet);
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
                    TabsTrackerService.clearUnread();
                }
            }
        };
        intentFilter = new IntentFilter();
        intentFilter.addAction(PostingService.BROADCAST_ACTION_STATUS);
        intentFilter.addAction(TabsTrackerService.BROADCAST_ACTION_NOTIFY);
        
        if (!TabsTrackerService.isRunning() && MainApplication.getInstance().settings.isAutoupdateEnabled())
            startService(new Intent(this, TabsTrackerService.class));
        
        if (MainApplication.getInstance().settings.isSFWRelease()) NewsReader.checkNews(this);
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(broadcastReceiver, intentFilter);
        tabsAdapter.notifyDataSetChanged(false);
        ExtendedTrustManager.bindActivity(this);
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(broadcastReceiver);
        ExtendedTrustManager.unbindActivity();
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
        TabsTrackerService.clearUnread();
        
        StaticSettingsContainer newSettings = MainApplication.getInstance().settings.getStaticSettings();
        
        boolean shouldClearCache = false;
        boolean shouldReloadBoardFragment = false;
        boolean shouldRestartActivity = false;
        
        if (!MainApplication.getInstance().settings.getTheme().equals(theme) ||
                MainApplication.getInstance().settings.getRootViewWeight() != rootViewWeight) {
            shouldClearCache = true;
            shouldRestartActivity = true;
        }
        
        if (MainApplication.getInstance().settings.isTabsPanelOnRight() != tabsPanelRight) {
            shouldRestartActivity = true;
        }
        
        if (settings.isDisplayDate != newSettings.isDisplayDate ||
                (settings.isDisplayDate && (settings.isLocalTime != newSettings.isLocalTime)) ||
                MainApplication.getInstance().settings.getAutohideRulesJson().hashCode() != autohideRulesHash ||
                MainApplication.getInstance().settings.openSpoilers() != openSpoilers ||
                MainApplication.getInstance().settings.highlightSubscriptions() != highlightSubscriptions ||
                MainApplication.getInstance().settings.subscriptionsClear()) {
            shouldClearCache = true;
            shouldReloadBoardFragment = true;
        }
        
        if (thumbnailSize != MainApplication.getInstance().settings.getPostThumbnailSize()) {
            shouldClearCache = true;
            shouldReloadBoardFragment = true;
            thumbnailSize = MainApplication.getInstance().settings.getPostThumbnailSize();
        }

        MainApplication.getInstance().settings.setSubscriptionsClear(false);
        
        if (settings.repliesOnlyQuantity != newSettings.repliesOnlyQuantity ||
                settings.showHiddenItems != newSettings.showHiddenItems ||
                settings.maskPictures != newSettings.maskPictures ||
                MainApplication.getInstance().settings.swipeToHideThread() != swipeToHideThread) {
            shouldReloadBoardFragment = true;
        }
        
        if (shouldClearCache) clearCache();
        if (shouldRestartActivity) {
            restartActivity();
            return;
        }
        
        MainApplication.getInstance().settings.updateStaticSettings(settings);
        autohideRulesHash = MainApplication.getInstance().settings.getAutohideRulesJson().hashCode();
        openSpoilers = MainApplication.getInstance().settings.openSpoilers();
        highlightSubscriptions = MainApplication.getInstance().settings.highlightSubscriptions();
        swipeToHideThread = MainApplication.getInstance().settings.swipeToHideThread();
        updateTabPanelTabletWeight();
        
        if (shouldReloadBoardFragment) reloadCurrentBoardFragment();
        handleOrientationChange(getResources().getConfiguration(), shouldReloadBoardFragment);
        
        if ((MainApplication.getInstance().pagesToOpen != null) && (MainApplication.getInstance().pagesToOpen.size() > 0)){
            this.tabsAdapter.setSelectedItem(-1);
            for (TabModel page : MainApplication.getInstance().pagesToOpen){
                UrlHandler.open(page.pageModel, this, false, page.title);
            }
            MainApplication.getInstance().pagesToOpen = null;
        }
    }
    
    private void clearCache() {
        MainApplication.getInstance().pagesCache.clearLru();
    }
    
    private void reloadCurrentBoardFragment() {
        Fragment currentFragment = MainApplication.getInstance().tabsSwitcher.currentFragment;
        if (currentFragment instanceof BoardFragment) {
            Long id = MainApplication.getInstance().tabsSwitcher.currentId;
            if (id != null) {
                TabModel tab = MainApplication.getInstance().tabsState.findTabById(id);
                MainApplication.getInstance().tabsSwitcher.switchTo(tab, getSupportFragmentManager(), true);
            }
        }
    }
    
    private void restartActivity() {
        MainApplication.getInstance().tabsSwitcher.currentId = null;
        MainApplication.getInstance().tabsSwitcher.currentFragment = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // https://code.google.com/p/android/issues/detail?id=93731
            Async.runAsync(new Runnable() {
                @Override
                public void run() {
                    try { Thread.sleep(10); } catch (Exception e) {}
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            CompatibilityImpl.recreateActivity(MainActivity.this);
                        }
                    });
                }
            });
        } else {
            final Intent i = new Intent(this.getIntent());
            this.finish();
            Async.runAsync(new Runnable() {
                @Override
                public void run() {
                    //сначала должно уничтожиться старое activity; onDestroy() старого -> onCreate() нового
                    while (!isDestroyed) Thread.yield();
                    Async.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.this.startActivity(i);
                        }
                    });
                }
            });
        }
    }
    
    private void handleOrientationChange(Configuration configuration) {
        handleOrientationChange(configuration, false);
    }
    
    private void handleOrientationChange(Configuration configuration, boolean doNotReloadBoardFragment) {
        boolean newOrientation = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (newOrientation != isHorizontalOrientation) {
            if (rootViewWeight != 1.0f) {
                clearCache();
                restartActivity();
            } else {
                if (!doNotReloadBoardFragment && FlowTextHelper.IS_AVAILABLE) {
                    reloadCurrentBoardFragment();
                }
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
            TypedValue typedValue;
            if (selected) {
                typedValue = ThemeUtils.resolveAttribute(getTheme(), R.attr.sidebarSelectedItem, true);
            } else {
                typedValue = new TypedValue();
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
    
    private boolean hasChild(View view, View child) {
        if (view == child) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i=0; i<group.getChildCount(); ++i) {
                if (hasChild(group.getChildAt(i), child)) return true;
            }
        }
        return false;
    }
    
    private boolean focusActionBar() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) return false;
        if (drawerLayout == null || drawerLayout.isDrawerOpen(DRAWER_GRAVITY)) return false;
        try {
            int resId = getResources().getIdentifier("action_bar_container", "id", "android");
            View actionBar = getWindow().getDecorView().findViewById(resId);
            if (hasChild(actionBar, getCurrentFocus())) return false;
            actionBar.requestFocus();
            return true;
        } catch (Exception e) {
            Logger.e(TAG, e);
            return false;
        }
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (settings.scrollVolumeButtons) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (settings.scrollVolumeButtons) {
                    try {
                        Fragment currentFragment = MainApplication.getInstance().tabsSwitcher.currentFragment;
                        if (currentFragment instanceof BoardFragment) {
                            BoardFragment boardFragment = (BoardFragment) currentFragment;
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) boardFragment.scrollUp(); else boardFragment.scrollDown();
                            return true;
                        }
                    } catch (Exception e) {
                        Logger.e(TAG, e);
                    }
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (onBack()) return true;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (focusActionBar()) return true;
                break;
        }
        return super.onKeyDown(keyCode, event);
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
        TabsTrackerService.clearUnread();
        if (intent != null) {
            if (intent.getBooleanExtra(TabsTrackerService.EXTRA_CLEAR_SUBSCRIPTIONS, false)) TabsTrackerService.clearSubscriptions();
            if (MainApplication.getInstance().settings.useFakeBrowser()) FakeBrowser.dismiss();
            if (intent.getData() != null && URLUtil.isFileUrl(intent.getDataString())) {
                LocalHandler.open(intent.getData().getPath(), this);
                return;
            }
            String url = intent.getDataString();
            if (url != null && url.length() != 0) {
                UrlHandler.open(url, this, MainApplication.getInstance().settings.useFakeBrowser());
            }
            intent.setData(null);
        }
    }
    
    @Override
    public void startActivity(Intent intent){
        if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_CHOOSER)) {
            Fragment currentFragment = MainApplication.getInstance().tabsSwitcher.currentFragment;
            if (currentFragment instanceof BoardFragment) {
                Intent sendIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (sendIntent != null) {
                    ((BoardFragment) currentFragment).setIntentExtras(sendIntent);
                }
            }
        }
        super.startActivity(intent);
    }
}
