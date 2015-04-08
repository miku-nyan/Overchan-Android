package nya.miku.wishmaster.ui;

import java.util.ArrayList;
import java.util.List;

import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class QuickAccess {
    private static final String TAG = "QuickAccess";
    
    private QuickAccess() {}
    
    public static class Entry {
        public ChanModule chan;
        public String boardName;
        public String boardDescription;
    }
    
    private static List<Entry> getQuickAccessListFromJson(String json) {
        try {
            List<Entry> result = new ArrayList<>();
            int allChansElCount = 0;
            JSONArray jsonArray = new JSONArray(json);
            for (int i=0, len=jsonArray.length(); i<len; ++i) {
                Entry current = new Entry();
                JSONObject currentJson = jsonArray.getJSONObject(i);
                String chan = currentJson.optString("chan", null);
                if (chan != null) {
                    current.chan = MainApplication.getInstance().getChanModule(chan);
                    String board = currentJson.optString("board", null);
                    if (board != null) {
                        current.boardName = board;
                        current.boardDescription = currentJson.optString("description", null);
                    }
                } else {
                    ++allChansElCount;
                }
                result.add(current);
            }
            if (allChansElCount == 1) return result;
            throw new Exception("invalid json of quick access list: "+allChansElCount+" title elements");
        } catch (Exception e) {
            Logger.e(TAG, e);
            List<Entry> result = new ArrayList<>();
            result.add(new Entry());
            return result;
        }
    }
    
    private static String saveQuickAccessListToJson(List<Entry> list) {
        JSONArray jsonArray = new JSONArray();
        for (Entry entry : list) {
            JSONObject current = new JSONObject();
            if (entry.chan != null) current.put("chan", entry.chan.getChanName());
            if (entry.boardName != null) current.put("board", entry.boardName);
            if (entry.boardDescription != null) current.put("description", entry.boardDescription);
            jsonArray.put(current);
        }
        return jsonArray.toString();
    }
    
    public static List<Entry> getQuickAccessFromPreferences() {
        return getQuickAccessListFromJson(MainApplication.getInstance().settings.getQuickAccessListJson());
    }
    
    public static void saveQuickAccessToPreferences(List<Entry> list) {
        MainApplication.getInstance().settings.saveQuickAccessListJson(saveQuickAccessListToJson(list));
    }
}
