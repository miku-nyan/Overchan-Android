package nya.miku.wishmaster.lib.appcompat_v7_actionbartoogle.wrappers;

import nya.miku.wishmaster.lib.appcompat_v7_actionbartoogle.ActionBarDrawerToggle;
import android.app.Activity;
import android.support.v4.widget.DrawerLayout;

public class ActionBarDrawerToogleV7 extends ActionBarDrawerToggle implements ActionBarDrawerToogleCompat {

    public ActionBarDrawerToogleV7(Activity activity, DrawerLayout drawerLayout, int openDrawerContentDescRes, int closeDrawerContentDescRes) {
        super(activity, drawerLayout, openDrawerContentDescRes, closeDrawerContentDescRes);
    }

}
