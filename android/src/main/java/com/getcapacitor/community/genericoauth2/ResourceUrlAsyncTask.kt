@file:Suppress("DEPRECATION")

package com.getcapacitor.community.genericoauth2

import android.os.AsyncTask
import android.util.Log
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.TokenResponse
import org.json.JSONException

public class ResourceUrlAsyncTask(
    private val pluginCall: PluginCall,
    private val options: OAuth2Options,
    private val logTag: String,
    private val authorizationResponse: AuthorizationResponse?,
    private val accessTokenResponse: TokenResponse?
) : AsyncTask<String?, Void?, ResourceCallResult>() {
    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg tokens: String?): ResourceCallResult {
        val result = ResourceCallResult()

        val resourceUrl = options.resourceUrl
        val accessToken = tokens[0]
        if (resourceUrl != null) {
            Log.i(logTag, "Resource url: GET $resourceUrl")
            if (accessToken != null) {
                Log.i(logTag, "Access token:\n$accessToken")

                try {
                    val url = URL(resourceUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.addRequestProperty("Authorization", "Bearer $accessToken")
                    // additional headers
                    options.additionalResourceHeaders?.forEach { (key, value) -> conn.addRequestProperty(key, value) }

                    var inputStream: InputStream? = null
                    try {
                        if (conn.responseCode >= HttpURLConnection.HTTP_OK && conn.responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                            inputStream = conn.inputStream
                        } else {
                            inputStream = conn.errorStream
                            result.isError = true
                        }
                        val resourceResponseBody = readInputStream(inputStream)
                        if (!result.isError) {
                            val resultJson = JSObject(resourceResponseBody)
                            if (options.isLogsEnabled) {
                                Log.i(logTag, "Resource response:\n$resourceResponseBody")
                            }
                            OAuth2Utils.assignResponses(resultJson, accessToken, authorizationResponse, accessTokenResponse)
                            if (options.isLogsEnabled) {
                                Log.i(logTag, MSG_RETURNED_TO_JS + resultJson)
                            }
                            result.response = resultJson
                        } else {
                            result.errorMsg = resourceResponseBody
                        }
                    } catch (e: IOException) {
                        Log.e(logTag, "", e)
                    } catch (e: JSONException) {
                        Log.e(logTag, "Resource response no valid json.", e)
                    } finally {
                        conn.disconnect()
                        inputStream?.close()
                    }
                } catch (e: MalformedURLException) {
                    Log.e(logTag, "Invalid resource url '$resourceUrl'", e)
                } catch (e: IOException) {
                    Log.e(logTag, "Unexpected error", e)
                }
            } else {
                if (options.isLogsEnabled) {
                    Log.i(
                        logTag,
                        "No accessToken was provided although you configured a resourceUrl. Remove the resourceUrl from the config."
                    )
                }
                pluginCall.reject(ERR_NO_ACCESS_TOKEN)
            }
        } else {
            val json = JSObject()
            OAuth2Utils.assignResponses(json, accessToken, authorizationResponse, accessTokenResponse)
            if (options.isLogsEnabled) {
                Log.i(logTag, MSG_RETURNED_TO_JS + json)
            }
            result.response = json
        }
        return result
    }

    @Deprecated("Deprecated in Java")
    override fun onPostExecute(response: ResourceCallResult?) {
        if (response != null) {
            if (!response.isError) {
                pluginCall.resolve(response.response)
            } else {
                // The message is missing when reading the error body failed; Log.e does not take null.
                Log.e(logTag, response.errorMsg.orEmpty())
                pluginCall.reject(ERR_GENERAL, response.errorMsg)
            }
        } else {
            pluginCall.reject(ERR_GENERAL)
        }
    }

    private companion object {
        private const val ERR_GENERAL = "ERR_GENERAL"
        private const val ERR_NO_ACCESS_TOKEN = "ERR_NO_ACCESS_TOKEN"
        private const val MSG_RETURNED_TO_JS = "Returned to JS:\n"

        @Throws(IOException::class)
        private fun readInputStream(input: InputStream?): String {
            BufferedReader(InputStreamReader(input)).use { br ->
                val buffer = CharArray(1024)
                val sb = StringBuilder()
                var readCount: Int
                while (br.read(buffer).also { readCount = it } != -1) {
                    sb.append(buffer, 0, readCount)
                }
                return sb.toString()
            }
        }
    }
}
