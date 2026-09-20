package com.getcapacitor.community.genericoauth2

import android.util.Log
import com.getcapacitor.JSObject
import java.util.Locale
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenericOAuth2PluginTest {
    private lateinit var plugin: GenericOAuth2Plugin

    @BeforeEach
    fun setup() {
        plugin = GenericOAuth2Plugin()
    }

    @Test
    fun allBooleanValues() {
        val jsObject = loadJson(
            """
            {
                "appId": "CLIENT_ID",
                "authorizationBaseUrl": "https://accounts.google.com/o/oauth2/auth",
                "accessTokenEndpoint": "https://www.googleapis.com/oauth2/v4/token",
                "scope": "email profile",
                "pkceEnabled": true,
                "logsEnabled": true,
                "resourceUrl": "https://www.googleapis.com/userinfo/v2/me",
                "web": {
                    "redirectUrl": "http://localhost:4200",
                    "windowOptions": "height=600,left=0,top=0"
                },
                "android": {
                    "appId": "$CLIENT_ID_ANDROID",
                    "redirectUrl": "com.company.project:/",
                    "handleResultMethod": "TEST",
                    "logsEnabled": false,
                    "handleResultOnNewIntent": true,
                    "handleResultOnActivityResult": false,
                    "responseType": "TOKEN"
                },
                "ios": {
                    "appId":  "CLIENT_ID_IOS",
                    "responseType": "code",
                    "redirectUrl": "com.company.project:/"
                }
            }
            """.trimIndent()
        )
        val options = plugin.buildAuthenticateOptions(jsObject!!)
        Assertions.assertNotNull(options)
        Assertions.assertTrue(options.isPkceEnabled)
        Assertions.assertFalse(options.isLogsEnabled)
        Assertions.assertTrue(options.isHandleResultOnNewIntent)
        Assertions.assertFalse(options.isHandleResultOnActivityResult)
    }

    @Test
    fun responseTypeToken() {
        val jsObject = loadJson(
            """
            {
                "appId": "CLIENT_ID",
                "authorizationBaseUrl": "https://accounts.google.com/o/oauth2/auth",
                "accessTokenEndpoint": "https://www.googleapis.com/oauth2/v4/token",
                "scope": "email profile",
                "pkceEnabled": true,
                "resourceUrl": "https://www.googleapis.com/userinfo/v2/me",
                "web": {
                    "redirectUrl": "http://localhost:4200",
                    "windowOptions": "height=600,left=0,top=0"
                },
                "android": {
                    "appId": "$CLIENT_ID_ANDROID",
                    "redirectUrl": "com.company.project:/",
                    "handleResultMethod": "TEST",
                    "responseType": "TOKEN"
                },
                "ios": {
                    "appId":  "CLIENT_ID_IOS",
                    "responseType": "code",
                    "redirectUrl": "com.company.project:/"
                }
            }
            """.trimIndent()
        )
        val options = plugin.buildAuthenticateOptions(jsObject!!)
        Assertions.assertNotNull(options)
        Assertions.assertEquals(CLIENT_ID_ANDROID, options.appId)
        Assertions.assertEquals("token", options.responseType!!.lowercase(Locale.getDefault()))
        Assertions.assertTrue(options.isHandleResultOnActivityResult)
    }

    @Test
    fun serverAuthorizationHandling() {
        val jsObject = loadJson(
            """
            {
                "appId": "CLIENT_ID",
                "authorizationBaseUrl": "https://accounts.google.com/o/oauth2/auth",
                "responseType": "code id_token",
                "redirectUrl": "https://project.myserver.com/oauth",
                "resourceUrl": "https://www.googleapis.com/userinfo/v2/me",
                "scope": "email profile",
                "web": {
                    "windowOptions": "height=600,left=0,top=0"
                },
                "android": {
                    "appId": "$CLIENT_ID_ANDROID"
                },
                "ios": {
                    "appId":  "CLIENT_ID_IOS"
                }
            }
            """.trimIndent()
        )
        val options = plugin.buildAuthenticateOptions(jsObject!!)
        Assertions.assertNotNull(options.appId)
        Assertions.assertEquals(CLIENT_ID_ANDROID, options.appId)
        Assertions.assertNotNull(options.authorizationBaseUrl)
        Assertions.assertEquals("code id_token", options.responseType)
        Assertions.assertNotNull(options.redirectUrl)
    }

    @Test
    fun buildRefreshTokenOptions() {
        val jsObject = loadJson(
            """
            {
                "appId": "CLIENT_ID",
                "accessTokenEndpoint": "https://www.googleapis.com/oauth2/v4/token",
                "refreshToken": "ss4f6sd5f4",
                "scope": "email profile"
            }
            """.trimIndent()
        )
        val options = plugin.buildRefreshTokenOptions(jsObject!!)
        Assertions.assertNotNull(options)
        Assertions.assertNotNull(options.appId)
        Assertions.assertNotNull(options.accessTokenEndpoint)
        Assertions.assertNotNull(options.refreshToken)
        Assertions.assertNotNull(options.scope)
    }

    private fun loadJson(json: String): JSObject? {
        try {
            return JSObject(json)
        } catch (e: Exception) {
            Log.e("OAuth2", "", e)
        }
        return null
    }

    companion object {
        const val CLIENT_ID_ANDROID = "CLIENT_ID_ANDROID"
    }
}
