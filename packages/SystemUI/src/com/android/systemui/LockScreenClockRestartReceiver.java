package com.android.systemui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

public class LockScreenClockRestartReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.android.systemui.action.RESTART_FOR_CLOCK_STYLE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION.equals(intent.getAction())) {
            Process.killProcess(Process.myPid());
        }
    }
}
