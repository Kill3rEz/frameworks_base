/*
 * Copyright (C) 2019 The Dirty Unicorns Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.android.systemui.statusbar.phone;

import android.app.ListActivity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.systemui.res.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AppPicker extends ListActivity {

    protected PackageManager packageManager;
    protected List<ApplicationInfo> applist;
    protected Adapter listadapter;
    protected List<ActivityInfo> mActivitiesList;
    protected boolean mIsActivitiesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(android.R.layout.list_content);
        setTitle(R.string.active_edge_app_select_title);

        packageManager = getPackageManager();
        new LoadApplications().execute();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        mIsActivitiesList = false;
        finish();
    }

    @Override
    public void onBackPressed() {
        if (mIsActivitiesList) {
            setListAdapter(listadapter);
            setTitle(R.string.active_edge_app_select_title);
            mIsActivitiesList = false;
        } else {
            finish();
        }
    }

    private List<ApplicationInfo> checkForLaunchIntent(List<ApplicationInfo> list) {
        ArrayList<ApplicationInfo> launchableApps = new ArrayList<>();
        String[] blacklistedPackages = {
                "com.google.android.as",
                "com.google.android.GoogleCamera",
                "com.google.android.imaging.easel.service",
                "com.android.traceur"
        };

        for (ApplicationInfo info : list) {
            if (!Arrays.asList(blacklistedPackages).contains(info.packageName)
                    && packageManager.getLaunchIntentForPackage(info.packageName) != null) {
                launchableApps.add(info);
            }
        }
        Collections.sort(launchableApps, new ApplicationInfo.DisplayNameComparator(packageManager));
        return launchableApps;
    }

    class LoadApplications extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            applist = checkForLaunchIntent(packageManager.getInstalledApplications(
                    PackageManager.GET_META_DATA));
            listadapter = new Adapter(AppPicker.this, R.layout.app_list_item, applist,
                    packageManager);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            setListAdapter(listadapter);
            getListView().setLongClickable(true);
            getListView().setOnItemLongClickListener(
                    new AdapterView.OnItemLongClickListener() {
                        @Override
                        public boolean onItemLongClick(AdapterView<?> parent, View view,
                                int position, long id) {
                            onLongClick(position);
                            return true;
                        }
                    });
        }
    }

    protected void onLongClick(int position) {
    }

    protected void showActivitiesDialog(String packageName) {
        ArrayList<ActivityInfo> list;
        try {
            PackageInfo pi = packageManager.getPackageInfo(packageName,
                    PackageManager.GET_ACTIVITIES);
            list = new ArrayList<>(Arrays.asList(pi.activities));
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }

        if (list.isEmpty()) {
            return;
        }
        mIsActivitiesList = true;
        mActivitiesList = list;
        setTitle(R.string.active_edge_activity_select_title);
        setListAdapter(new ActivitiesAdapter(this, R.layout.app_list_item, list, packageManager));
    }

    class Adapter extends ArrayAdapter<ApplicationInfo> {
        private final List<ApplicationInfo> mAppList;
        private final Context mContext;
        private final PackageManager mPackageManager;

        Adapter(Context context, int resource, List<ApplicationInfo> objects, PackageManager pm) {
            super(context, resource, objects);
            mContext = context;
            mAppList = objects;
            mPackageManager = pm;
        }

        @Override
        public int getCount() {
            return mAppList == null ? 0 : mAppList.size();
        }

        @Override
        public ApplicationInfo getItem(int position) {
            return mAppList == null ? null : mAppList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.app_list_item, parent, false);
            }

            ApplicationInfo data = mAppList.get(position);
            TextView appName = view.findViewById(R.id.app_name);
            ImageView iconView = view.findViewById(R.id.app_icon);
            appName.setText(data.loadLabel(mPackageManager));
            iconView.setImageDrawable(data.loadIcon(mPackageManager));
            return view;
        }
    }

    class ActivitiesAdapter extends ArrayAdapter<ActivityInfo> {
        private final List<ActivityInfo> mActivityList;
        private final Context mContext;

        ActivitiesAdapter(Context context, int resource, List<ActivityInfo> objects,
                PackageManager pm) {
            super(context, resource, objects);
            mContext = context;
            mActivityList = objects;
        }

        @Override
        public int getCount() {
            return mActivityList == null ? 0 : mActivityList.size();
        }

        @Override
        public ActivityInfo getItem(int position) {
            return mActivityList == null ? null : mActivityList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            ActivityInfo data = mActivityList.get(position);
            TextView appName = view.findViewById(android.R.id.text1);
            appName.setText(data.name);
            return view;
        }
    }
}
