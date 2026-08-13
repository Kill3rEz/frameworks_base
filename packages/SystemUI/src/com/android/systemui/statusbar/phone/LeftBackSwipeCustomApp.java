package com.android.systemui.statusbar.phone;

import android.content.pm.ActivityInfo;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.widget.ListView;

public class LeftBackSwipeCustomApp extends AppPicker {

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (!mIsActivitiesList) {
            String packageName = applist.get(position).packageName;
            String friendlyAppString = (String) applist.get(position).loadLabel(packageManager);
            setPackage(packageName, friendlyAppString);
            setPackageActivity(null);
        } else {
            setPackageActivity(mActivitiesList.get(position));
        }
        mIsActivitiesList = false;
        finish();
    }

    @Override
    protected void onLongClick(int position) {
        if (mIsActivitiesList) {
            return;
        }
        String packageName = applist.get(position).packageName;
        String friendlyAppString = (String) applist.get(position).loadLabel(packageManager);
        setPackage(packageName, friendlyAppString);
        setPackageActivity(null);
        showActivitiesDialog(packageName);
    }

    protected void setPackage(String packageName, String friendlyAppString) {
        Settings.System.putStringForUser(getContentResolver(),
                Settings.System.LEFT_LONG_BACK_SWIPE_APP_ACTION, packageName,
                UserHandle.USER_CURRENT);
        Settings.System.putStringForUser(getContentResolver(),
                Settings.System.LEFT_LONG_BACK_SWIPE_APP_FR_ACTION, friendlyAppString,
                UserHandle.USER_CURRENT);
    }

    protected void setPackageActivity(ActivityInfo ai) {
        Settings.System.putStringForUser(getContentResolver(),
                Settings.System.LEFT_LONG_BACK_SWIPE_APP_ACTIVITY_ACTION,
                ai != null ? ai.name : "NONE", UserHandle.USER_CURRENT);
    }
}
