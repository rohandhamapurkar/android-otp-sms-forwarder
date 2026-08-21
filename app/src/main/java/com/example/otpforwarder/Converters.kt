package com.example.otpforwarder

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

class Converters {

    companion object {
        // Gson is thread-safe and not cheap to build; one instance serves every conversion.
        private val gson = Gson()
        private val stringListType = object : TypeToken<List<String>>() {}.type
    }

    /**
     * Returns an empty list rather than null for absent or corrupt data: the entity field is
     * non-null, so a null here would surface as a confusing failure far from the real cause.
     */
    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<String>>(value, stringListType) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromList(list: List<String>): String = gson.toJson(list)
}
