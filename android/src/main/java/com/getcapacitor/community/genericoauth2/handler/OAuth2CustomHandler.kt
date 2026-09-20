package com.getcapacitor.community.genericoauth2.handler

import android.app.Activity
import com.getcapacitor.PluginCall

public interface OAuth2CustomHandler {
    public fun getAccessToken(activity: Activity, pluginCall: PluginCall, callback: AccessTokenCallback)

    public fun logout(activity: Activity, pluginCall: PluginCall): Boolean
}
