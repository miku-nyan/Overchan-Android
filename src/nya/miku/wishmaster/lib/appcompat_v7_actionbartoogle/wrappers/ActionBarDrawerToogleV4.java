package nya.miku.wishmaster.lib.appcompat_v7_actionbartoogle.wrappers;

import android.app.Activity;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;

@SuppressWarnings("deprecation")
public class ActionBarDrawerToogleV4 extends ActionBarDrawerToggle implements ActionBarDrawerToogleCompat {
    public ActionBarDrawerToogleV4(
            Activity activity, DrawerLayout drawerLayout, int drawerImageRes, int openDrawerContentDescRes, int closeDrawerContentDescRes) {
        super(activity, drawerLayout, drawerImageRes, openDrawerContentDescRes, closeDrawerContentDescRes);
    }
}
