package com.getcapacitor.community.genericoauth2

import com.getcapacitor.JSObject
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.TokenResponse

public object OAuth2Utils {
    @JvmStatic
    public fun assignResponses(
        resp: JSObject,
        accessToken: String?,
        authorizationResponse: AuthorizationResponse?,
        accessTokenResponse: TokenResponse?
    ) {
        // #154
        if (authorizationResponse != null) {
            resp.put("authorization_response", authorizationResponse.jsonSerialize())
        }
        if (accessTokenResponse != null) {
            resp.put("access_token_response", accessTokenResponse.jsonSerialize())
        }
        if (accessToken != null) {
            resp.put("access_token", accessToken)
        }
    }
}
