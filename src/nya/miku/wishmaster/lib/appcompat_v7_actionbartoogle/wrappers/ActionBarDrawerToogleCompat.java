package nya.miku.wishmaster.lib.appcompat_v7_actionbartoogle.wrappers;

import android.content.res.Configuration;
import android.support.v4.widget.DrawerLayout.DrawerListener;
import android.view.MenuItem;

public interface ActionBarDrawerToogleCompat extends DrawerListener {
    boolean onOptionsItemSelected(MenuItem item);

    void onConfigurationChanged(Configuration newConfig);

    void syncState();
}