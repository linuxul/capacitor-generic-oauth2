package com.getcapacitor.community.genericoauth2

import android.util.Log
import com.getcapacitor.JSObject
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ConfigUtilsTest {
    private var jsObject: JSObject? = null

    @BeforeEach
    fun setUp() {
        try {
            jsObject = JSObject(BASE_JSON)
        } catch (e: Exception) {
            Log.e("OAuth2", "", e)
        }
    }

    @Test
    fun getParamString() {
        val stringValue = ConfigUtils.getParamString(jsObject!!, "stringValue")
        Assertions.assertNotNull(stringValue)
        Assertions.assertEquals("string", stringValue)
    }

    @Test
    fun getParam() {
        val stringValue = ConfigUtils.getParam(String::class.java, jsObject!!, "stringValue")
        Assertions.assertNotNull(stringValue)
        Assertions.assertEquals("string", stringValue)

        val doubleValue = ConfigUtils.getParam(Double::class.javaObjectType, jsObject!!, "doubleValue")
        Assertions.assertNotNull(doubleValue)
    }

    @Test
    fun getParamMap() {
        val map = ConfigUtils.getParamMap(jsObject!!, "map")
        Assertions.assertNotNull(map)
        Assertions.assertEquals("value1", map["key1"])
    }

    @Test
    fun getDeepestKey() {
        var deepestKey = ConfigUtils.getDeepestKey("com.example.deep")
        Assertions.assertEquals("deep", deepestKey)

        deepestKey = ConfigUtils.getDeepestKey("com")
        Assertions.assertEquals("com", deepestKey)
    }

    @Test
    fun getDeepestObject() {
        val obj = ConfigUtils.getDeepestObject(jsObject!!, "first.second.third")
        Assertions.assertNotNull(obj!!.getJSObject("third"))
    }

    @Test
    fun getOverwrittenAndroidParam() {
        val overwrittenString = ConfigUtils.getOverwrittenAndroidParam(String::class.java, jsObject!!, "stringValue")
        Assertions.assertEquals("stringAndroid", overwrittenString)

        val intValue = ConfigUtils.getOverwrittenAndroidParam(Int::class.javaObjectType, jsObject!!, "intValue")
        Assertions.assertEquals(1, intValue)
    }

    @Test
    fun getOverwrittenAndroidParamMap() {
        val map = ConfigUtils.getOverwrittenAndroidParamMap(jsObject!!, "map")
        Assertions.assertNotNull(map)
        Assertions.assertEquals("value1Android", map["key1"])
        Assertions.assertEquals("value2", map["key2"])
        Assertions.assertEquals("value3Android", map["key3"])
    }

    @Test
    fun overwriteWithEmpty() {
        val accessTokenEndpoint = "accessTokenEndpoint"
        Assertions.assertNotNull(ConfigUtils.getParamString(jsObject!!, accessTokenEndpoint))
        Assertions.assertEquals("", ConfigUtils.getOverwrittenAndroidParam(String::class.java, jsObject!!, accessTokenEndpoint))

        val inMapNullable = "inMapNullable"
        val paramMap = ConfigUtils.getParamMap(jsObject!!, "map")
        Assertions.assertNotNull(paramMap[inMapNullable])
        val androidParamMap = ConfigUtils.getOverwrittenAndroidParamMap(jsObject!!, "map")
        Assertions.assertEquals("", androidParamMap[inMapNullable])
    }

    @ParameterizedTest
    @MethodSource("getBooleanArguments")
    fun getOverwrittenBoolean(json: String, key: String, expected: Boolean?) {
        val jsObject = JSObject(json)
        val actual = ConfigUtils.getOverwrittenAndroidParam(Boolean::class.javaObjectType, jsObject, key)
        if (expected == null) {
            Assertions.assertNull(actual)
        } else {
            Assertions.assertEquals(expected, actual)
        }
    }

    @Test
    fun getRandomString() {
        val randomString = ConfigUtils.getRandomString(8)
        Assertions.assertNotNull(randomString)
        Assertions.assertEquals(8, randomString.length)
    }

    @Test
    fun empty() {
        // make sure the empty value stays empty
        val emptyValue = ConfigUtils.getParamString(jsObject!!, "empty")
        Assertions.assertEquals(0, emptyValue!!.length)
    }

    @Test
    fun blank() {
        // make sure the blank value stays blank
        val blankValue = ConfigUtils.getParamString(jsObject!!, "blank")
        Assertions.assertEquals(" ", blankValue)
    }

    @Test
    fun trimToNull() {
        Assertions.assertNull(ConfigUtils.trimToNull("  "))
        Assertions.assertNull(ConfigUtils.trimToNull(" "))
        Assertions.assertNull(ConfigUtils.trimToNull(""))
        Assertions.assertEquals("a", ConfigUtils.trimToNull("a"))
    }

    companion object {
        private val BASE_JSON =
            """
            {
                "doubleValue": 123.4567,
                "floatValue": 123.4,
                "intValue": 1,
                "stringValue": "string",
                "booleanValue": true,
                "accessTokenEndpoint": "https://byteowls.com",
                "first": {
                    "second": {
                        "third": {
                            "doubleValue": 5.4,
                            "floatValue": 5.9,
                            "intValue": 2,
                            "stringValue": "stringDeep",
                            "booleanValue": false
                        }
                    }
                },
                "map": {
                    "key1": "value1",
                    "key2": "value2",
                    "inMapNullable": "notEmpty"
                },
                "android": {
                    "stringValue": "stringAndroid",
                    "accessTokenEndpoint": "",
                    "map": {
                        "key1": "value1Android",
                        "key3": "value3Android",
                        "inMapNullable": ""
                    }
                },
                "empty": "",
                "blank": " "
            }
            """.trimIndent()

        @JvmStatic
        private fun getBooleanArguments(): Stream<Arguments> = Stream.of(
            Arguments.of("{ \"pkceEnabled\": true, \"android\":{\"pkceEnabled\": false}}", "pkceEnabled", false),
            Arguments.of("{ \"pkceEnabled\": true}", "pkceEnabled", true),
            Arguments.of("{ \"pkceEnabled\": true}", "android.pkceEnabled", null),
            Arguments.of("{ \"pkceEnabled\": true, \"ios\":{\"pkceEnabled\": false}}", "pkceEnabled", true)
        )
    }
}
