package com.getcapacitor.community.genericoauth2.handler

public interface AccessTokenCallback {
    public fun onSuccess(accessToken: String?)

    public fun onCancel()

    public fun onError(error: Exception?)
}
