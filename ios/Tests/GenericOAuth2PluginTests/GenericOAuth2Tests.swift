import XCTest
@testable import CapacitorCommunityGenericOauth2

class GenericOAuth2Tests: XCTestCase {
    func testPluginRegistration() {
        let plugin = GenericOAuth2Plugin()

        XCTAssertEqual("GenericOAuth2Plugin", plugin.identifier)
        XCTAssertEqual("GenericOAuth2", plugin.jsName)
        XCTAssertEqual(["refreshToken", "authenticate", "logout"], plugin.pluginMethods.map { $0.name })
        for method in plugin.pluginMethods {
            XCTAssertTrue(plugin.responds(to: method.selector), "\(method.name) is not exposed to Objective-C")
        }
    }
}
