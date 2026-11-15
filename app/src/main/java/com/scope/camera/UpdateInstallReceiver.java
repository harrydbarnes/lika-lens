package com.scope.camera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles PackageInstaller session commit callbacks for in-app updates. */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AppUpdateManager.onPackageInstallerResult(context, intent);
    }
}
