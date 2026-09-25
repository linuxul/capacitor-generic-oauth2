import XCTest
import Capacitor
@testable import CapacitorCommunityGenericOauth2

class GenericOAuth2Tests: XCTestCase {
    func testPluginRegistration() {
        let plugin = GenericOAuth2Plugin()

        XCTAssertEqual("GenericOAuth2Plugin", plugin.identifier)
        XCTAssertEqual("GenericOAuth2", plugin.jsName)
        XCTAssertEqual(["refreshToken", "authenticate", "logout"], plugin.pluginMethods.map { $0.name })
        for method in plugin.pluginMethods {
            XCTAssertEqual(.promise, method.returnType, "\(method.name) is not a promise method")
        }
    }

    /// A call that fails the test if the method answers it itself: validation answers by throwing, and the bridge
    /// rejects the call with the error.
    private func call(_ methodName: String, _ options: Capacitor.JSObject = [:]) -> CAPPluginCall {
        CAPPluginCall(callbackId: "test", methodName: methodName, options: options, success: { _, _ in
            XCTFail("\(methodName) must not resolve")
        }, error: { _ in
            XCTFail("\(methodName) answers by throwing")
        })
    }

    func testRefreshTokenRejectsMissingOptionsInOrder() {
        let plugin = GenericOAuth2Plugin()

        XCTAssertThrowsError(try plugin.refreshToken(call("refreshToken"))) { error in
            XCTAssertEqual((error as? CAPPluginError)?.message, "ERR_PARAM_NO_APP_ID")
        }
        XCTAssertThrowsError(try plugin.refreshToken(call("refreshToken", ["appId": "id"]))) { error in
            XCTAssertEqual((error as? CAPPluginError)?.message, "ERR_PARAM_NO_ACCESS_TOKEN_ENDPOINT")
        }
        XCTAssertThrowsError(try plugin.refreshToken(call("refreshToken", ["appId": "id", "accessTokenEndpoint": "https://example.com/token"]))) { error in
            XCTAssertEqual((error as? CAPPluginError)?.message, "ERR_PARAM_NO_REFRESH_TOKEN")
        }
    }

    func testAuthenticateRejectsMissingOptionsInOrder() {
        let plugin = GenericOAuth2Plugin()

        XCTAssertThrowsError(try plugin.authenticate(call("authenticate", ["appId": ""]))) { error in
            XCTAssertEqual((error as? CAPPluginError)?.message, "ERR_PARAM_NO_APP_ID")
        }
        XCTAssertThrowsError(try plugin.authenticate(call("authenticate", ["appId": "id"]))) { error in
            XCTAssertEqual((error as? CAPPluginError)?.message, "ERR_PARAM_NO_AUTHORIZATION_BASE_URL")
        }
        let base: Capacitor.JSObject = ["appId": "id", "authorizationBaseUrl": "https://example.com/auth"]
        XCTAssertThrowsError(try plugin.authenticate(call("authenticate", base))) { error in
            XCTAssertEqual((error as? CAPPluginError)?.message, "ERR_PARAM_NO_RESPONSE_TYPE")
        }
        XCTAssertThrowsError(try plugin.authenticate(call("authenticate", base.merging(["responseType": "code"]) { $1 }))) { error in
            XCTAssertEqual((error as? CAPPluginError)?.message, "ERR_PARAM_NO_REDIRECT_URL")
        }
    }
}
