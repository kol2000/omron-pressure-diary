package ru.personal.omronscan;

import android.app.Activity;
import android.content.res.Configuration;

final class Theme {
    static boolean dark(Activity activity) {
        android.content.SharedPreferences prefs=activity.getSharedPreferences("scanner",Activity.MODE_PRIVATE);
        boolean system=(activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        return prefs.getBoolean("dark_theme",system);
    }
    static void apply(Activity activity) {
        activity.setTheme(dark(activity)?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
    }
}
