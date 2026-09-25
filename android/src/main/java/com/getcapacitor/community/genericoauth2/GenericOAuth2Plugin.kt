package com.getcapacitor.community.genericoauth2

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginException
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.community.genericoauth2.handler.AccessTokenCallback
import com.getcapacitor.community.genericoauth2.handler.OAuth2CustomHandler
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.EndSessionResponse
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import org.json.JSONException

@CapacitorPlugin(name = "GenericOAuth2")
public class GenericOAuth2Plugin : Plugin() {
    private var oauth2Options: OAuth2Options? = null
    private var authService: AuthorizationService? = null
    private var authState: AuthState? = null
    private var callbackId: String? = null

    /**
     * Waits for AppAuth's answer to the token request, which calls back once, and returns the token response.
     */
    @PluginMethod
    public suspend fun refreshToken(call: PluginCall): JSObject {
        disposeAuthService()
        val options = buildRefreshTokenOptions(call.data)

        val appId = options.appId ?: throw PluginException(ERR_PARAM_NO_APP_ID)
        val accessTokenEndpoint = options.accessTokenEndpoint ?: throw PluginException(ERR_PARAM_NO_ACCESS_TOKEN_ENDPOINT)
        val refreshToken = options.refreshToken ?: throw PluginException(ERR_PARAM_NO_REFRESH_TOKEN)

        val service = AuthorizationService(context)
        authService = service

        val config = AuthorizationServiceConfiguration(Uri.parse(""), Uri.parse(accessTokenEndpoint))

        val state = authState ?: AuthState(config).also { authState = it }

        val tokenRequest =
            TokenRequest.Builder(config, appId)
                .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                .setScope(options.scope)
                .setRefreshToken(refreshToken)
                .build()

        val (response, ex) =
            suspendCoroutine<Pair<TokenResponse?, AuthorizationException?>> { continuation ->
                service.performTokenRequest(tokenRequest) { response, ex ->
                    state.update(response, ex)
                    continuation.resume(response to ex)
                }
            }

        if (ex != null) {
            throw PluginException(ex.error ?: ERR_GENERAL, ex.code.toString(), cause = ex)
        }
        if (response == null) {
            throw PluginException(ERR_NO_ACCESS_TOKEN)
        }
        try {
            return JSObject(response.jsonSerializeString())
        } catch (e: JSONException) {
            throw PluginException(ERR_GENERAL, cause = e)
        }
    }

    @PluginMethod
    public fun authenticate(call: PluginCall) {
        callbackId = call.callbackId
        disposeAuthService()
        val options = buildAuthenticateOptions(call.data)
        oauth2Options = options
        val customHandlerClass = options.customHandlerClass
        if (customHandlerClass != null) {
            if (options.isLogsEnabled) {
                // Logs the class of the configured name, which is always java.lang.String, as it always did
                Log.i(logTag, "Entering custom handler: " + customHandlerClass.javaClass.name)
            }
            try {
                val handler = newCustomHandler(customHandlerClass)
                handler.getAccessToken(
                    activity,
                    call,
                    object : AccessTokenCallback {
                        @Suppress("DEPRECATION")
                        override fun onSuccess(accessToken: String?) {
                            ResourceUrlAsyncTask(call, options, logTag, null, null).execute(accessToken)
                        }

                        override fun onCancel() {
                            call.reject(USER_CANCELLED)
                        }

                        override fun onError(error: Exception?) {
                            call.reject(ERR_CUSTOM_HANDLER_LOGIN, ex = error)
                        }
                    }
                )
            } catch (e: InstantiationException) {
                call.reject(ERR_CUSTOM_HANDLER_LOGIN, ex = e)
            } catch (e: IllegalAccessException) {
                call.reject(ERR_CUSTOM_HANDLER_LOGIN, ex = e)
            } catch (e: ClassNotFoundException) {
                call.reject(ERR_CUSTOM_HANDLER_LOGIN, ex = e)
            } catch (e: Exception) {
                call.reject(ERR_GENERAL, ex = e)
            }
        } else {
            // ###################################
            // ### Validate required parameter ###
            // ###################################

            val appId = options.appId ?: throw PluginException(ERR_PARAM_NO_APP_ID)
            val authorizationBaseUrl = options.authorizationBaseUrl ?: throw PluginException(ERR_PARAM_NO_AUTHORIZATION_BASE_URL)
            val responseType = options.responseType ?: throw PluginException(ERR_PARAM_NO_RESPONSE_TYPE)
            val redirectUrl = options.redirectUrl ?: throw PluginException(ERR_PARAM_NO_REDIRECT_URL)

            // ### Configure

            val authorizationUri = Uri.parse(authorizationBaseUrl)
            // appAuth does not allow to be the accessTokenUri empty although it is not used unit performTokenRequest
            val accessTokenUri = options.accessTokenEndpoint?.let { Uri.parse(it) } ?: authorizationUri

            val config = AuthorizationServiceConfiguration(authorizationUri, accessTokenUri)

            if (authState == null) {
                authState = AuthState(config)
            }

            val builder = AuthorizationRequest.Builder(config, appId, responseType, Uri.parse(redirectUrl))

            // app auth always uses a state
            options.state?.let { builder.setState(it) }
            builder.setScope(options.scope)
            builder.setCodeVerifier(if (options.isPkceEnabled) options.pkceCodeVerifier else null)
            options.prompt?.let { builder.setPrompt(it) }
            options.loginHint?.let { builder.setLoginHint(it) }
            options.responseMode?.let { builder.setResponseMode(it) }
            options.display?.let { builder.setDisplay(it) }

            options.additionalParameters?.let {
                try {
                    builder.setAdditionalParameters(it)
                } catch (e: IllegalArgumentException) {
                    // ignore all additional parameter on error
                    Log.e(logTag, "Additional parameter error", e)
                }
            }

            val req = builder.build()

            val service = AuthorizationService(context)
            authService = service
            try {
                val authIntent = service.getAuthorizationRequestIntent(req)
                bridge.saveCall(call)
                startActivityForResult(call, authIntent, "handleIntentResult")
            } catch (e: ActivityNotFoundException) {
                call.reject(ERR_ANDROID_NO_BROWSER, ex = e)
            } catch (e: Exception) {
                Log.e(logTag, "Unexpected exception on open browser for authorization request!")
                call.reject(ERR_GENERAL, ex = e)
            }
        }
    }

    @PluginMethod
    public fun logout(call: PluginCall) {
        val customHandlerClassname = ConfigUtils.getParam(String::class.java, call.data, PARAM_ANDROID_CUSTOM_HANDLER_CLASS)
        if (!customHandlerClassname.isNullOrEmpty()) {
            try {
                val handler = newCustomHandler(customHandlerClassname)
                val successful = handler.logout(activity, call)
                if (successful) {
                    call.resolve()
                } else {
                    call.reject(ERR_CUSTOM_HANDLER_LOGOUT)
                }
            } catch (e: InstantiationException) {
                call.reject(ERR_CUSTOM_HANDLER_LOGOUT, ex = e)
            } catch (e: IllegalAccessException) {
                call.reject(ERR_CUSTOM_HANDLER_LOGOUT, ex = e)
            } catch (e: ClassNotFoundException) {
                call.reject(ERR_CUSTOM_HANDLER_LOGOUT, ex = e)
            } catch (e: Exception) {
                call.reject(ERR_GENERAL, ex = e)
            }
        } else {
            val idToken = ConfigUtils.getParam(String::class.java, call.data, PARAM_ID_TOKEN)
            if (idToken == null) {
                disposeAuthService()
                discardAuthState()
                call.resolve()
                return
            }

            val options = buildAuthenticateOptions(call.data)
            oauth2Options = options

            // Uri.parse throws a NullPointerException for a missing url, as it did before the move to Kotlin.
            // OAuth2Options.logoutUrl is never assigned, so that is what happens to every logout with an id_token.
            val authorizationUri = Uri.parse(options.authorizationBaseUrl)
            // appAuth does not allow to be the accessTokenUri empty although it is not used unit performTokenRequest
            val accessTokenUri = options.accessTokenEndpoint?.let { Uri.parse(it) } ?: authorizationUri
            val logoutUri = Uri.parse(options.logoutUrl)

            val config = AuthorizationServiceConfiguration(authorizationUri, accessTokenUri)

            val endSessionRequest =
                EndSessionRequest.Builder(config)
                    .setIdTokenHint(idToken)
                    .setPostLogoutRedirectUri(logoutUri)
                    .build()

            val service = AuthorizationService(context)
            authService = service

            try {
                val endSessionIntent = service.getEndSessionRequestIntent(endSessionRequest)
                bridge.saveCall(call)
                startActivityForResult(call, endSessionIntent, "handleEndSessionIntentResult")
            } catch (e: ActivityNotFoundException) {
                call.reject(ERR_ANDROID_NO_BROWSER, ex = e)
            } catch (e: Exception) {
                Log.e(logTag, "Unexpected exception on open browser for logout request!")
                call.reject(ERR_GENERAL, ex = e)
            }
        }
    }

    override fun handleOnNewIntent(intent: Intent?) {
        // this is a experimental hook and only usable if the android system kills the app between
        if (oauth2Options?.isHandleResultOnNewIntent == true) {
            // with this I have no way to check if this intent is for this plugin
            val savedCall = bridge.getSavedCall(callbackId) ?: return
            handleAuthorizationRequestActivity(intent, savedCall)
        }
    }

    @ActivityCallback
    private fun handleIntentResult(call: PluginCall?, result: ActivityResult) {
        if (call == null) {
            return
        }
        if (oauth2Options?.isHandleResultOnActivityResult == true) {
            if (result.resultCode == Activity.RESULT_CANCELED) {
                call.reject(USER_CANCELLED)
            } else {
                handleAuthorizationRequestActivity(result.data, call)
            }
        }
    }

    @ActivityCallback
    private fun handleEndSessionIntentResult(call: PluginCall?, result: ActivityResult) {
        if (call == null) {
            return
        }
        if (result.resultCode == Activity.RESULT_CANCELED) {
            call.reject(USER_CANCELLED)
        } else {
            val data = result.data ?: return
            try {
                // EndSessionResponse.fromIntent returns null for an intent without a response; the resulting
                // NullPointerException has always been reported as ERR_GENERAL
                val resp = EndSessionResponse.fromIntent(data)!!
                val json = JSObject(resp.jsonSerializeString())

                disposeAuthService()
                discardAuthState()

                call.resolve(json)
            } catch (e: Exception) {
                Log.e(logTag, "Unexpected exception on handling result for logout request!")
                call.reject(ERR_GENERAL, ex = e)
            }
        }
    }

    internal fun handleAuthorizationRequestActivity(intent: Intent?, savedCall: PluginCall) {
        // there are valid situation when the Intent is null, but
        if (intent == null) {
            // the intent is null because the provider send the redirect to the server, which would be valid
            // the intent is null because the plugin user configured sth wrong incl.
            // the provider does not support redirecting to a android app, which would be invalid
            savedCall.reject(ERR_ANDROID_RESULT_NULL)
            return
        }

        // Callers only get here after authenticate() or logout() stored the options
        val options = oauth2Options ?: return

        val authorizationResponse: AuthorizationResponse?
        val error: AuthorizationException?
        val state: AuthState
        try {
            authorizationResponse = AuthorizationResponse.fromIntent(intent)
            error = AuthorizationException.fromIntent(intent)
            // A logout in between discards the state; the NullPointerException has always been reported as ERR_GENERAL
            state = authState!!
            state.update(authorizationResponse, error)
        } catch (e: Exception) {
            savedCall.reject(ERR_GENERAL, ex = e)
            return
        }

        if (error != null) {
            if (error.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code) {
                savedCall.reject(USER_CANCELLED)
            } else if (error.code == AuthorizationException.AuthorizationRequestErrors.STATE_MISMATCH.code) {
                if (options.isLogsEnabled) {
                    Log.i(logTag, "State from web options: " + options.state)
                    if (authorizationResponse != null) {
                        Log.i(logTag, "State returned from provider: " + authorizationResponse.state)
                    }
                }
                savedCall.reject(ERR_STATES_NOT_MATCH)
            } else {
                savedCall.reject(ERR_GENERAL, ex = error)
            }
            return
        }

        // this response may contain the authorizationCode but also idToken and accessToken depending on the flow chosen by responseType
        if (authorizationResponse == null) {
            savedCall.reject(ERR_NO_AUTHORIZATION_CODE)
            return
        }

        if (options.isLogsEnabled) {
            Log.i(logTag, "Authorization response:\n" + authorizationResponse.jsonSerializeString())
        }
        // if there is a tokenEndpoint configured try to get the accessToken from it.
        // it might be already in the authorizationResponse but tokenEndpoint might deliver other tokens.
        if (options.accessTokenEndpoint == null) {
            resolveAuthorizationResponse(savedCall, authorizationResponse)
            return
        }

        val service = AuthorizationService(context)
        authService = service
        try {
            val tokenExchangeRequest = authorizationResponse.createTokenExchangeRequest()
            service.performTokenRequest(tokenExchangeRequest) { accessTokenResponse, exception ->
                state.update(accessTokenResponse, exception)
                if (exception != null) {
                    savedCall.reject(ERR_AUTHORIZATION_FAILED, exception.code.toString(), exception)
                } else if (accessTokenResponse != null) {
                    if (options.isLogsEnabled) {
                        Log.i(logTag, "Access token response:\n" + accessTokenResponse.jsonSerializeString())
                    }
                    state.performActionWithFreshTokens(service) { accessToken, _, _ ->
                        @Suppress("DEPRECATION")
                        ResourceUrlAsyncTask(savedCall, options, logTag, authorizationResponse, accessTokenResponse).execute(accessToken)
                    }
                } else {
                    resolveAuthorizationResponse(savedCall, authorizationResponse)
                }
            }
        } catch (e: Exception) {
            savedCall.reject(ERR_NO_AUTHORIZATION_CODE, ex = e)
        }
    }

    private fun resolveAuthorizationResponse(savedCall: PluginCall, authorizationResponse: AuthorizationResponse) {
        val json = JSObject()
        OAuth2Utils.assignResponses(json, null, authorizationResponse, null)
        savedCall.resolve(json)
    }

    internal fun buildAuthenticateOptions(callData: JSObject): OAuth2Options {
        val o = OAuth2Options()
        // required
        o.appId = overwrittenString(callData, PARAM_APP_ID)
        o.authorizationBaseUrl = overwrittenString(callData, PARAM_AUTHORIZATION_BASE_URL)
        o.responseType = overwrittenString(callData, PARAM_RESPONSE_TYPE)
        o.redirectUrl = overwrittenString(callData, PARAM_REDIRECT_URL)

        // optional
        o.isLogsEnabled = ConfigUtils.getOverwrittenAndroidParam(Boolean::class.javaObjectType, callData, PARAM_LOGS_ENABLED) == true
        o.resourceUrl = overwrittenString(callData, PARAM_RESOURCE_URL)
        o.accessTokenEndpoint = overwrittenString(callData, PARAM_ACCESS_TOKEN_ENDPOINT)
        o.isPkceEnabled = ConfigUtils.getOverwrittenAndroidParam(Boolean::class.javaObjectType, callData, PARAM_PKCE_ENABLED) == true
        if (o.isPkceEnabled) {
            o.pkceCodeVerifier = ConfigUtils.getRandomString(64)
        }

        o.scope = overwrittenString(callData, PARAM_SCOPE)
        o.state = overwrittenString(callData, PARAM_STATE) ?: ConfigUtils.getRandomString(20)

        val additionalParameters = ConfigUtils.getOverwrittenAndroidParamMap(callData, PARAM_ADDITIONAL_PARAMETERS)
        for ((key, value) in additionalParameters) {
            when (key) {
                PARAM_DISPLAY -> o.display = value
                PARAM_LOGIN_HINT -> o.loginHint = value
                PARAM_PROMPT -> o.prompt = value
                PARAM_RESPONSE_MODE -> o.responseMode = value
                else -> o.addAdditionalParameter(key, value)
            }
        }
        o.additionalResourceHeaders = ConfigUtils.getOverwrittenAndroidParamMap(callData, PARAM_ADDITIONAL_RESOURCE_HEADERS)
        // android only
        o.customHandlerClass = ConfigUtils.trimToNull(ConfigUtils.getParamString(callData, PARAM_ANDROID_CUSTOM_HANDLER_CLASS))
        o.isHandleResultOnNewIntent =
            ConfigUtils.getParam(Boolean::class.javaObjectType, callData, PARAM_ANDROID_HANDLE_RESULT_ON_NEW_INTENT, false) == true
        o.isHandleResultOnActivityResult =
            ConfigUtils.getParam(Boolean::class.javaObjectType, callData, PARAM_ANDROID_HANDLE_RESULT_ON_ACTIVITY_RESULT, false) == true
        if (!o.isHandleResultOnNewIntent && !o.isHandleResultOnActivityResult) {
            o.isHandleResultOnActivityResult = true
        }
        return o
    }

    internal fun buildRefreshTokenOptions(callData: JSObject): OAuth2RefreshTokenOptions {
        val o = OAuth2RefreshTokenOptions()
        o.appId = overwrittenString(callData, PARAM_APP_ID)
        o.accessTokenEndpoint = overwrittenString(callData, PARAM_ACCESS_TOKEN_ENDPOINT)
        o.scope = overwrittenString(callData, PARAM_SCOPE)
        o.refreshToken = overwrittenString(callData, PARAM_REFRESH_TOKEN)
        return o
    }

    override fun handleOnStop() {
        super.handleOnStop()
        disposeAuthService()
    }

    private fun overwrittenString(callData: JSObject, key: String): String? =
        ConfigUtils.trimToNull(ConfigUtils.getOverwrittenAndroidParam(String::class.java, callData, key))

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    private fun newCustomHandler(className: String): OAuth2CustomHandler =
        (Class.forName(className) as Class<OAuth2CustomHandler>).newInstance()

    private fun disposeAuthService() {
        authService?.dispose()
        authService = null
    }

    private fun discardAuthState() {
        authState = null
    }

    private companion object {
        private const val PARAM_APP_ID = "appId"
        private const val PARAM_AUTHORIZATION_BASE_URL = "authorizationBaseUrl"
        private const val PARAM_RESPONSE_TYPE = "responseType"
        private const val PARAM_REDIRECT_URL = "redirectUrl"
        private const val PARAM_SCOPE = "scope"
        private const val PARAM_STATE = "state"
        private const val PARAM_ACCESS_TOKEN_ENDPOINT = "accessTokenEndpoint"
        private const val PARAM_PKCE_ENABLED = "pkceEnabled"
        private const val PARAM_RESOURCE_URL = "resourceUrl"
        private const val PARAM_ADDITIONAL_RESOURCE_HEADERS = "additionalResourceHeaders"
        private const val PARAM_ADDITIONAL_PARAMETERS = "additionalParameters"
        private const val PARAM_ANDROID_CUSTOM_HANDLER_CLASS = "android.customHandlerClass"
        private const val PARAM_ANDROID_HANDLE_RESULT_ON_NEW_INTENT = "android.handleResultOnNewIntent"
        private const val PARAM_ANDROID_HANDLE_RESULT_ON_ACTIVITY_RESULT = "android.handleResultOnActivityResult"
        private const val PARAM_REFRESH_TOKEN = "refreshToken"
        private const val PARAM_DISPLAY = "display"
        private const val PARAM_LOGIN_HINT = "login_hint"
        private const val PARAM_PROMPT = "prompt"
        private const val PARAM_RESPONSE_MODE = "response_mode"
        private const val PARAM_LOGS_ENABLED = "logsEnabled"
        private const val PARAM_LOGOUT_URL = "logoutUrl"
        private const val PARAM_ID_TOKEN = "id_token"
        private const val USER_CANCELLED = "USER_CANCELLED"
        private const val ERR_PARAM_NO_APP_ID = "ERR_PARAM_NO_APP_ID"
        private const val ERR_PARAM_NO_AUTHORIZATION_BASE_URL = "ERR_PARAM_NO_AUTHORIZATION_BASE_URL"
        private const val ERR_PARAM_NO_REDIRECT_URL = "ERR_PARAM_NO_REDIRECT_URL"
        private const val ERR_PARAM_NO_RESPONSE_TYPE = "ERR_PARAM_NO_RESPONSE_TYPE"
        private const val ERR_PARAM_NO_ACCESS_TOKEN_ENDPOINT = "ERR_PARAM_NO_ACCESS_TOKEN_ENDPOINT"
        private const val ERR_PARAM_NO_REFRESH_TOKEN = "ERR_PARAM_NO_REFRESH_TOKEN"
        private const val ERR_AUTHORIZATION_FAILED = "ERR_AUTHORIZATION_FAILED"
        private const val ERR_NO_ACCESS_TOKEN = "ERR_NO_ACCESS_TOKEN"
        private const val ERR_ANDROID_NO_BROWSER = "ERR_ANDROID_NO_BROWSER"
        private const val ERR_ANDROID_RESULT_NULL = "ERR_ANDROID_NO_INTENT"
        private const val ERR_CUSTOM_HANDLER_LOGIN = "ERR_CUSTOM_HANDLER_LOGIN"
        private const val ERR_CUSTOM_HANDLER_LOGOUT = "ERR_CUSTOM_HANDLER_LOGOUT"
        private const val ERR_GENERAL = "ERR_GENERAL"
        private const val ERR_STATES_NOT_MATCH = "ERR_STATES_NOT_MATCH"
        private const val ERR_NO_AUTHORIZATION_CODE = "ERR_NO_AUTHORIZATION_CODE"
    }
}
