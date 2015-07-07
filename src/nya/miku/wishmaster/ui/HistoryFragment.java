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

import java.util.Calendar;
import java.util.LinkedList;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.common.CompatibilityImpl;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.ui.tabs.TabModel;
import nya.miku.wishmaster.ui.tabs.UrlHandler;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class HistoryFragment extends Fragment implements AdapterView.OnItemClickListener {
    private MainActivity activity;
    private HistoryAdapter adapter;
    private ListView listView;
    
    private static LinkedList<Database.HistoryEntry> lastClosed = new LinkedList<>();
    
    public static void setLastClosed(TabModel tab) {
         if (tab != null && tab.pageModel != null) {
             switch (tab.pageModel.type) {
                 case UrlPageModel.TYPE_INDEXPAGE:
                     lastClosed.add(new Database.HistoryEntry(tab.pageModel.chanName, null, null, null, tab.title, tab.webUrl, 0));
                     break;
                 case UrlPageModel.TYPE_BOARDPAGE:
                     lastClosed.add(new Database.HistoryEntry(tab.pageModel.chanName, tab.pageModel.boardName,
                             Integer.toString(tab.pageModel.boardPage), null, tab.title, tab.webUrl, 0));
                     break;
                 case UrlPageModel.TYPE_THREADPAGE:
                     lastClosed.add(new Database.HistoryEntry(tab.pageModel.chanName, tab.pageModel.boardName,
                             null, tab.pageModel.threadNumber, tab.title, tab.webUrl, 0));
                     break;
             }
             if (MainApplication.getInstance().tabsSwitcher.currentFragment instanceof HistoryFragment) {
                 ((HistoryFragment) MainApplication.getInstance().tabsSwitcher.currentFragment).init();
             }
             if (lastClosed.size() > 30) lastClosed.removeFirst();
         }
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (MainActivity) getActivity();
        setHasOptionsMenu(true);
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity.setTitle(R.string.tabs_history);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) CompatibilityImpl.setActionBarDefaultIcon(activity);
        listView = (ListView) inflater.inflate(R.layout.history_fragment, container, false);
        listView.setOnItemClickListener(this);
        registerForContextMenu(listView);
        init();
        return listView;
    }
    
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (adapter == null) return;
        Object item = adapter.getItem(position);
        if (item instanceof Database.HistoryEntry) {
            if (((Database.HistoryEntry) item).date == 0) lastClosed.removeLast();
            UrlHandler.open(((Database.HistoryEntry) item).url, activity);
        }
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, R.id.menu_clear_history, 101, R.string.menu_clear_history).setIcon(android.R.drawable.ic_menu_close_clear_cancel);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_clear_history) {
            if (adapter != null) {
                MainApplication.getInstance().database.clearHistory();
                lastClosed = new LinkedList<>();
                init();
            }
            return true;
        }
        return false;
    }
    
    private void init() {
        adapter = new HistoryAdapter(activity);
        listView.setAdapter(adapter);
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (adapter == null) return;
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Object item = adapter.getItem(info.position);
        if (item instanceof Database.HistoryEntry) {
            Database.HistoryEntry entry = (Database.HistoryEntry) item;
            if (entry.date != 0) {
                menu.add(Menu.NONE, R.id.context_menu_remove_history, 1, R.string.context_menu_remove_history);
                menu.add(Menu.NONE, R.id.context_menu_open_browser, 2, R.string.context_menu_open_browser);
                menu.add(Menu.NONE, R.id.context_menu_favorites_from_fragment, 3,
                        MainApplication.getInstance().database.isFavorite(entry.chan, entry.board, entry.boardPage, entry.thread) ?
                        R.string.context_menu_remove_favorites : R.string.context_menu_add_favorites);
            }
        }
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position;
        Object listItem = adapter.getItem(position);
        if (listItem instanceof Database.HistoryEntry) {
            Database database = MainApplication.getInstance().database;
            Database.HistoryEntry entry = (Database.HistoryEntry) listItem;
            switch (item.getItemId()) {
                case R.id.context_menu_remove_history:
                    database.removeHistory(entry.chan, entry.board, entry.boardPage, entry.thread);
                    adapter.remove(entry);
                    break;
                case R.id.context_menu_open_browser:
                    UrlHandler.launchExternalBrowser(activity, entry.url);
                    break;
                case R.id.context_menu_favorites_from_fragment:
                    if (database.isFavorite(entry.chan, entry.board, entry.boardPage, entry.thread)) {
                        database.removeFavorite(entry.chan, entry.board, entry.boardPage, entry.thread);
                    } else {
                        database.addFavorite(entry.chan, entry.board, entry.boardPage, entry.thread, entry.title, entry.url);
                    }
            }
            return true;
        }
        return false;
    }
    
    private static class HistoryAdapter extends ArrayAdapter<Object> {
        private static final int SEPARATOR = 0;
        private static final int NORMAL_ITEM = 1;
        
        private LayoutInflater inflater;
        private int drawablePadding;
        
        public HistoryAdapter(MainActivity activity) {
            super(activity, 0);
            Resources resources = activity.getResources();
            inflater = LayoutInflater.from(activity);
            drawablePadding = (int) (resources.getDisplayMetrics().density * 5 + 0.5f);
            
            long midnight = getMidnight();
            int current = 0;
            int previous = -1;
            if (lastClosed.size() > 0) {
                add(resources.getString(R.string.history_last_closed));
                add(lastClosed.getLast());
            }
            for (Database.HistoryEntry entity : MainApplication.getInstance().database.getHistory()) {
                while (entity.date < midnight) {
                    if (current == 0) {
                        current = 1;
                        midnight -= 86400 * 1000;
                    } else if (current == 1) {
                        current = 2;
                        midnight -= 86400 * 1000 * 6;
                    } else {
                        current = 3;
                        midnight = 0;
                    }
                }
                if (previous != current) {
                    switch (current) {
                        case 0:
                            add(resources.getString(R.string.history_today));
                            break;
                        case 1:
                            add(resources.getString(R.string.history_yesterday));
                            break;
                        case 2:
                            add(resources.getString(R.string.history_last_week));
                            break;
                        case 3:
                            add(resources.getString(R.string.history_other));
                            break;
                    }
                    previous = current;
                }
                add(entity);
            }
            if (getCount() == 0) add(resources.getString(R.string.history_empty));
        }
        
        private long getMidnight() {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object item = getItem(position);
            View v;
            if (item instanceof Database.HistoryEntry) {
                v = convertView == null ? inflater.inflate(android.R.layout.simple_list_item_2, parent, false) : convertView;
                TextView tv1 = (TextView) v.findViewById(android.R.id.text1);
                TextView tv2 = (TextView) v.findViewById(android.R.id.text2);
                tv1.setSingleLine();
                tv2.setSingleLine();
                tv1.setEllipsize(TextUtils.TruncateAt.END);
                tv2.setEllipsize(TextUtils.TruncateAt.START);
                tv1.setText(((Database.HistoryEntry) item).title);
                tv2.setText(((Database.HistoryEntry) item).url);
                tv1.setCompoundDrawablesWithIntrinsicBounds(
                        MainApplication.getInstance().getChanModule(((Database.HistoryEntry) item).chan).getChanFavicon(), null, null, null);
                tv1.setCompoundDrawablePadding(drawablePadding);
            } else {
                v = convertView == null ? inflater.inflate(R.layout.list_separator, parent, false) : convertView;
                TextView tv = (TextView) v;
                tv.setText((String) item); 
            }
            return v;
        }
        
        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position) instanceof Database.HistoryEntry ? NORMAL_ITEM : SEPARATOR;
        }
        
    }
}
