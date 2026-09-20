package com.getcapacitor.community.genericoauth2

public class OAuth2Options {
    // required
    public var appId: String? = null
    public var authorizationBaseUrl: String? = null
    public var responseType: String? = null
    public var redirectUrl: String? = null

    public var scope: String? = null
    public var state: String? = null

    public var accessTokenEndpoint: String? = null
    public var resourceUrl: String? = null
    public var additionalResourceHeaders: MutableMap<String, String>? = null

    public var isPkceEnabled: Boolean = false
    public var isLogsEnabled: Boolean = false
    public var pkceCodeVerifier: String? = null
    public var additionalParameters: MutableMap<String, String>? = null

    public var customHandlerClass: String? = null

    // Activity result handling
    public var isHandleResultOnNewIntent: Boolean = false
    public var isHandleResultOnActivityResult: Boolean = true

    public var display: String? = null
    public var loginHint: String? = null
    public var prompt: String? = null
    public var responseMode: String? = null

    // Never assigned: the options builder has no counterpart for it.
    public val logoutUrl: String? = null

    public fun addAdditionalParameter(key: String?, value: String?) {
        if (key != null && value != null) {
            val parameters = additionalParameters ?: HashMap<String, String>().also { additionalParameters = it }
            parameters[key] = value
        }
    }

    public fun addAdditionalResourceHeader(key: String?, value: String?) {
        if (key != null && value != null) {
            val headers = additionalResourceHeaders ?: HashMap<String, String>().also { additionalResourceHeaders = it }
            headers[key] = value
        }
    }
}
