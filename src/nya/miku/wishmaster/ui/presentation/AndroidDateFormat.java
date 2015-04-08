package nya.miku.wishmaster.ui.presentation;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import nya.miku.wishmaster.common.MainApplication;

public class AndroidDateFormat {
    
    private static String pattern = null;
    
    public static String getPattern() {
        return pattern;
    }
    
    public static void initPattern() {
        if (pattern != null) return;
        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(MainApplication.getInstance());
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(MainApplication.getInstance());
        if (dateFormat instanceof SimpleDateFormat && timeFormat instanceof SimpleDateFormat) {
            pattern = ((SimpleDateFormat) dateFormat).toPattern() + " " + ((SimpleDateFormat) timeFormat).toPattern();
        }
    }
}
