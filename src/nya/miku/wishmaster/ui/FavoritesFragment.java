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

import java.util.ArrayList;
import java.util.List;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.common.CompatibilityImpl;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.ui.Database.FavoritesEntry;
import nya.miku.wishmaster.ui.settings.ApplicationSettings;
import nya.miku.wishmaster.ui.tabs.UrlHandler;

import org.apache.commons.lang3.tuple.Pair;

import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class FavoritesFragment extends Fragment implements AdapterView.OnItemClickListener {
    
    private MainActivity activity;
    private Resources resources;
    private ApplicationSettings settings;
    private LayoutInflater inflater;
    private PagerAdapter pagerAdapter;
    private ViewPager viewPager;
    private List<Pair<ListView, String>> listViews;
    
    public static final int PAGE_ALL = 0;
    public static final int PAGE_CHANS = 1;
    public static final int PAGE_BOARDS = 2;
    public static final int PAGE_THREADS = 3;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (MainActivity) getActivity();
        resources = MainApplication.getInstance().resources;
        settings = MainApplication.getInstance().settings;
        inflater = LayoutInflater.from(activity);
        setHasOptionsMenu(true);
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity.setTitle(R.string.tabs_favorites);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) CompatibilityImpl.setActionBarDefaultIcon(activity);
        viewPager = (ViewPager) inflater.inflate(R.layout.favorites_fragment, container, false);
        update();
        return viewPager;
    }
    
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Object item = parent.getAdapter().getItem(position);
        if (item instanceof Database.FavoritesEntry) {
            UrlHandler.open(((Database.FavoritesEntry) item).url, activity);
        }
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, R.id.menu_clear_favorites, 101, R.string.menu_clear_favorites).setIcon(android.R.drawable.ic_menu_close_clear_cancel);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_clear_favorites) {
            if (pagerAdapter != null) {
                MainApplication.getInstance().database.clearFavorites();
                update();
            }
            return true;
        }
        return false;
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(Menu.NONE, R.id.context_menu_open_browser, 1, R.string.context_menu_open_browser);
        menu.add(Menu.NONE, R.id.context_menu_remove_favorites, 2, R.string.context_menu_remove_favorites);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        View v = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).targetView;
        Database.FavoritesEntry entry = (FavoritesEntry) v.getTag();
        switch (item.getItemId()) {
            case R.id.context_menu_remove_favorites:
                MainApplication.getInstance().database.removeFavorite(entry.chan, entry.board, entry.boardPage, entry.thread);
                for (Pair<ListView,String> p : listViews) ((FavoritesAdapter) p.getLeft().getAdapter()).remove(entry);
                return true;
            case R.id.context_menu_open_browser:
                UrlHandler.launchExternalBrowser(activity, entry.url);
                return true;
        }
        return false;
    }
    
    public void update() {
        initLists();
        pagerAdapter = new ViewPagerFavoritesAdapter(listViews);
        viewPager.setAdapter(pagerAdapter);
        int current = -1;
        int reqPage = settings.getLastFavoritesPage();
        for (int i=0; i<listViews.size(); ++i) {
            if (reqPage == PAGE_ALL && listViews.get(i).getRight().equals(resources.getString(R.string.favorites_all))) current = i;
            else if (reqPage == PAGE_CHANS && listViews.get(i).getRight().equals(resources.getString(R.string.favorites_chans))) current = i;
            else if (reqPage == PAGE_BOARDS && listViews.get(i).getRight().equals(resources.getString(R.string.favorites_boards))) current = i;
            else if (reqPage == PAGE_THREADS && listViews.get(i).getRight().equals(resources.getString(R.string.favorites_threads))) current = i;
            else continue;
            break;
        }
        if (current != -1) viewPager.setCurrentItem(current);
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                String title = listViews.get(position).getRight();
                if (title.equals(resources.getString(R.string.favorites_all))) settings.saveLastFavoritesPage(PAGE_ALL);
                else if (title.equals(resources.getString(R.string.favorites_chans))) settings.saveLastFavoritesPage(PAGE_CHANS);
                else if (title.equals(resources.getString(R.string.favorites_boards))) settings.saveLastFavoritesPage(PAGE_BOARDS);
                else if (title.equals(resources.getString(R.string.favorites_threads))) settings.saveLastFavoritesPage(PAGE_THREADS);
            }
        });
    }
    
    private void initLists() {
        listViews = new ArrayList<Pair<ListView,String>>();
        List<Database.FavoritesEntry> favorites = MainApplication.getInstance().database.getFavorites();
        if (favorites.isEmpty()) {
            listViews.add(Pair.of(getListView(favorites), resources.getString(R.string.favorites_empty)));
            return;
        }
        List<Database.FavoritesEntry> chans = new ArrayList<Database.FavoritesEntry>();
        List<Database.FavoritesEntry> boards = new ArrayList<Database.FavoritesEntry>();
        List<Database.FavoritesEntry> threads = new ArrayList<Database.FavoritesEntry>();
        for (Database.FavoritesEntry entry : favorites) {
            if (Database.isNull(entry.board)) chans.add(entry);
            else if (Database.isNull(entry.thread)) boards.add(entry);
            else threads.add(entry);
        }
        int listsCount = (chans.isEmpty() ? 0 : 1) + (boards.isEmpty() ? 0 : 1) + (threads.isEmpty() ? 0 : 1);
        if (listsCount > 1) listViews.add(Pair.of(getListView(favorites), resources.getString(R.string.favorites_all)));
        if (!chans.isEmpty()) listViews.add(Pair.of(getListView(chans), resources.getString(R.string.favorites_chans)));
        if (!boards.isEmpty()) listViews.add(Pair.of(getListView(boards), resources.getString(R.string.favorites_boards)));
        if (!threads.isEmpty()) listViews.add(Pair.of(getListView(threads), resources.getString(R.string.favorites_threads)));
    }
    
    private ListView getListView(List<Database.FavoritesEntry> list) {
        ListView lv = (ListView) inflater.inflate(R.layout.favorites_listview, viewPager, false);
        lv.setAdapter(new FavoritesAdapter(list, activity));
        lv.setOnItemClickListener(this);
        registerForContextMenu(lv);
        return lv;
    }
    
    private static class ViewPagerFavoritesAdapter extends PagerAdapter {
        private final List<Pair<ListView, String>> listViews;
        
        public ViewPagerFavoritesAdapter(List<Pair<ListView, String>> listViews) {
            this.listViews = listViews;
        }
        
        @Override
        public int getCount() {
            return listViews.size();
        }
        
        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
        
        @Override
        public CharSequence getPageTitle(int position) {
            return listViews.get(position).getRight();
        }
        
        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View v = listViews.get(position).getLeft();
            container.addView(v);
            return v;
        }
        
        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }
        
    }
    
    private static class FavoritesAdapter extends ArrayAdapter<Database.FavoritesEntry> {
        private int drawablePadding;
        private LayoutInflater inflater;
        
        public FavoritesAdapter(List<FavoritesEntry> objects, MainActivity activity) {
            super(activity, 0, objects);
            drawablePadding = (int) (activity.getResources().getDisplayMetrics().density * 5 + 0.5f);
            inflater = LayoutInflater.from(activity);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Database.FavoritesEntry item = getItem(position);
            View v = convertView == null ? inflater.inflate(android.R.layout.simple_list_item_2, parent, false) : convertView;
            TextView tv1 = (TextView) v.findViewById(android.R.id.text1);
            TextView tv2 = (TextView) v.findViewById(android.R.id.text2);
            tv1.setSingleLine();
            tv2.setSingleLine();
            tv1.setEllipsize(TextUtils.TruncateAt.END);
            tv2.setEllipsize(TextUtils.TruncateAt.START);
            tv1.setText(item.title);
            tv2.setText(item.url);
            ChanModule chan = MainApplication.getInstance().getChanModule(item.chan);
            if (chan != null) {
                tv1.setCompoundDrawablesWithIntrinsicBounds(chan.getChanFavicon(), null, null, null);
                tv1.setCompoundDrawablePadding(drawablePadding);
            }
            v.setTag(item);
            return v;
        }
        
    }
    
}
