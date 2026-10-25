package com.beoffline.app.accountability

import com.google.gson.JsonParser
import retrofit2.HttpException

/**
 * The reason the server gave, if it gave one.
 *
 * Every 4xx from this API carries a `{"message": "..."}` body saying what
 * actually went wrong — which limit was hit, how many of what, what to do
 * instead. The client used to throw all of that away and render a status code
 * as a stock line, so "You are in 10 groups already, which is the limit"
 * reached the user as "Not possible", and every single 409 in group chat came
 * out as "Already done."
 *
 * The body can only be read once, so this is for the failure path only.
 */
fun HttpException.serverMessage(): String? = try {
    response()?.errorBody()?.string()
        ?.let { JsonParser.parseString(it) }
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
        ?.get("message")
        ?.takeIf { it.isJsonPrimitive }
        ?.asString
        ?.takeIf { it.isNotBlank() }
} catch (_: Exception) {
    null
}
