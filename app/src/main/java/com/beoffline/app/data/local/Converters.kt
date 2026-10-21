package com.beoffline.app.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room TypeConverters for types not natively supported by SQLite.
 * Converts List<String> and List<Int> to/from JSON strings.
 */
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    /**
     * `activeDays` is the only nullable list, and null means something quite
     * different from empty: "this rule has no schedule" rather than "this
     * schedule runs on no days".
     *
     * This used to hand Room the four characters `null` and read them back as
     * an EMPTY list, which collapsed the two. A block set to Always stored
     * null, came back as no-days-selected, and switching it to Set hours
     * landed on a day row with nothing in it: "Pick at least one day", Save
     * greyed out, and the change impossible to make — while the summary line
     * underneath, which reads empty as "every day", cheerfully disagreed.
     *
     * Null now survives the round trip. Rows already holding the literal
     * string are read as null too, so existing blocks are fixed without a
     * migration. The column was always nullable, so no schema change.
     */
    @TypeConverter
    fun fromIntList(value: List<Int>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toIntList(value: String?): List<Int>? {
        if (value == null || value == "null") return null
        val type = object : TypeToken<List<Int>>() {}.type
        return gson.fromJson(value, type)
    }
}
