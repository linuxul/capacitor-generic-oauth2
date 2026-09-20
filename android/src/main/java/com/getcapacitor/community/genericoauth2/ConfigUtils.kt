package com.getcapacitor.community.genericoauth2

import com.getcapacitor.JSObject
import java.util.Random
import java.util.regex.Pattern
import org.json.JSONException

public object ConfigUtils {
    // Pattern.split drops trailing empty parts and keeps an input without a match, like String.split in Java
    private val KEY_SEPARATOR = Pattern.compile("\\.")

    private const val RANDOM_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    @JvmStatic
    public fun getParamString(data: JSObject, key: String): String? = getParam(String::class.java, data, key)

    @JvmStatic
    @JvmOverloads
    @Suppress("UNCHECKED_CAST")
    public fun <T> getParam(clazz: Class<T>, data: JSObject, key: String, defaultValue: T? = null): T? {
        val k = getDeepestKey(key)
        if (k != null) {
            try {
                var value: Any? = null
                val o = getDeepestObject(data, key) ?: return defaultValue

                // #109
                if (o.has(k)) {
                    if (clazz.isAssignableFrom(String::class.java)) {
                        value = o.getString(k)
                    } else if (clazz.isAssignableFrom(Boolean::class.javaObjectType)) {
                        value = o.optBoolean(k)
                    } else if (clazz.isAssignableFrom(Double::class.javaObjectType)) {
                        value = o.getDouble(k)
                    } else if (clazz.isAssignableFrom(Int::class.javaObjectType)) {
                        value = o.getInt(k)
                    } else if (clazz.isAssignableFrom(Long::class.javaObjectType)) {
                        value = o.getLong(k)
                    } else if (clazz.isAssignableFrom(Float::class.javaObjectType)) {
                        value = o.getDouble(k).toFloat()
                    }
                }
                if (value == null) {
                    return defaultValue
                }
                return value as T
            } catch (ignore: Exception) {
            }
        }
        return defaultValue
    }

    @JvmStatic
    public fun getParamMap(data: JSObject, key: String): MutableMap<String, String> {
        val map = HashMap<String, String>()
        val k = getDeepestKey(key)
        if (k != null) {
            try {
                val jsonObject = getDeepestObject(data, key)?.getJSONObject(k) ?: return map
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val mapKey = keys.next()
                    if (mapKey != null && mapKey.trim { it <= ' ' }.isNotEmpty()) {
                        try {
                            map[mapKey] = jsonObject.getString(mapKey)
                        } catch (ignore: JSONException) {
                        }
                    }
                }
            } catch (ignore: Exception) {
            }
        }
        return map
    }

    @JvmStatic
    public fun getDeepestKey(key: String): String? = KEY_SEPARATOR.split(key).lastOrNull()

    /** Returns null when one of the objects on the way to the key is missing. */
    @JvmStatic
    public fun getDeepestObject(o: JSObject, key: String): JSObject? {
        // Split on periods
        val parts = KEY_SEPARATOR.split(key)
        // Search until the second to last part of the key
        var current: JSObject? = o
        for (i in 0 until parts.size - 1) {
            current = current?.getJSObject(parts[i])
        }
        return current
    }

    @JvmStatic
    public fun <T> getOverwrittenAndroidParam(clazz: Class<T>, data: JSObject, key: String): T? =
        getParam(clazz, data, "android.$key") ?: getParam(clazz, data, key)

    @JvmStatic
    public fun getOverwrittenAndroidParamMap(data: JSObject, key: String): MutableMap<String, String> {
        val mergedParam = HashMap(getParamMap(data, key))
        mergedParam.putAll(getParamMap(data, "android.$key"))
        return mergedParam
    }

    @JvmStatic
    public fun getRandomString(len: Int): String {
        val random = Random()
        val c = CharArray(len) { RANDOM_CHARS[random.nextInt(RANDOM_CHARS.length)] }
        return String(c)
    }

    @JvmStatic
    public fun trimToNull(value: String?): String? {
        if (value != null && value.trim { it <= ' ' }.isEmpty()) {
            return null
        }
        return value
    }
}
