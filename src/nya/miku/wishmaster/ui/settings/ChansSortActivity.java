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

package nya.miku.wishmaster.ui.settings;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.lib.dslv.DragSortController;
import nya.miku.wishmaster.lib.dslv.DragSortListView;
import nya.miku.wishmaster.lib.org_json.JSONArray;

public class ChansSortActivity extends Activity {
    private List<ChanModule> list = new ArrayList<>();
    private List<ChanModule> listBefore = null;
    private boolean changed = true;
    
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApplicationSettings settings = MainApplication.getInstance().settings;
        setTheme(settings.getTheme());
        getTheme().applyStyle(settings.getFontSizeStyle(), true);
        setTitle(R.string.pref_chans_rearrange);
        boolean listNotFull = false;
        for (ChanModule chan : MainApplication.getInstance().chanModulesList) {
            if (settings.isUnlockedChan(chan.getChanName())) list.add(chan); else listNotFull = true;
        }
        if (listNotFull) listBefore = new ArrayList<>(list);
        
        final DragSortListView listView = new DragSortListView(this, null);
        
        final ArrayAdapter<ChanModule> adapter = new ArrayAdapter<ChanModule>(this, 0, list) {
            LayoutInflater inflater = LayoutInflater.from(ChansSortActivity.this);
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView == null ? inflater.inflate(R.layout.sidebar_tabitem, parent, false) : convertView;
                View dragHandler = view.findViewById(R.id.tab_drag_handle);
                ImageView favIcon = (ImageView)view.findViewById(R.id.tab_favicon);
                TextView title = (TextView)view.findViewById(R.id.tab_text_view);
                ImageView closeBtn = (ImageView)view.findViewById(R.id.tab_close_button);
                dragHandler.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                dragHandler.setLayoutParams(dragHandler.getLayoutParams());
                view.setBackgroundColor(Color.TRANSPARENT);
                closeBtn.setVisibility(View.GONE);
                title.setText(getItem(position).getDisplayingName());
                favIcon.setImageDrawable(getItem(position).getChanFavicon());
                return view;
            }
        };
        
        DragSortController controller = new DragSortController(listView, R.id.tab_drag_handle, DragSortController.ON_DRAG, 0) {
            @Override
            public View onCreateFloatView(int position) { return adapter.getView(position, null, listView); }
            @Override
            public void onDragFloatView(View floatView, Point floatPoint, Point touchPoint) {}
            @Override
            public void onDestroyFloatView(View floatView) {}
        };
        
        listView.setDropListener(new DragSortListView.DropListener() {
            @Override
            public void drop(int from, int to) {
                if (from != to) {
                    ChanModule chan = list.remove(from);
                    list.add(to, chan);
                    adapter.notifyDataSetChanged();
                    changed = true;
                }
            }
        });
        
        listView.setAdapter(adapter);
        listView.setDragEnabled(true);
        listView.setFloatViewManager(controller);
        listView.setOnTouchListener(controller);
        
        setContentView(listView);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (changed) {
            changed = false;
            
            List<ChanModule> before = listBefore == null ? MainApplication.getInstance().chanModulesList : listBefore;
            if (before.size() != list.size()) {
                Toast.makeText(this, R.string.error_unknown, Toast.LENGTH_LONG).show();
                return;
            }
            
            boolean needSave = false;
            for (int i=0; i<before.size(); ++i) {
                if (!before.get(i).getChanName().equals(list.get(i).getChanName())) needSave = true;
            }
            if (!needSave) return;
            
            JSONArray jsonArray = new JSONArray();
            for (ChanModule chan : list) jsonArray.put(chan.getClass().getName());
            MainApplication.getInstance().settings.saveChansOrderJson(jsonArray.toString());
            MainApplication.getInstance().updateChanModulesOrder();
            PreferencesActivity.needUpdateChansScreen = true;
        }
    }
    
}
