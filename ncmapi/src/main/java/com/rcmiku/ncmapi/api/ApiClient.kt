package com.rcmiku.ncmapi.api

import android.util.Log
import com.rcmiku.ncmapi.model.ApiCodeResponse
import com.rcmiku.ncmapi.utils.CookieProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

var API_BASE_URL = "https://ncm-api.prod.gbclstudio.cn"
var UNBLOCK_BASE_URL = "https://unlock.depresskid.top"

val apiClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        })
    }
    defaultRequest {
        header("User-Agent", "JetMelo/1.0")
        header("Accept", "application/json")
        val cookie = CookieProvider.cookie
        if (cookie.isNotEmpty()) {
            header("Cookie", cookie)
        }
    }
}

suspend inline fun <reified T> apiGet(path: String, params: Map<String, Any> = emptyMap()): Result<T> {
    return try {
        val response = apiClient.request("$API_BASE_URL$path") {
            method = HttpMethod.Get
            params.forEach { (key, value) ->
                parameter(key, value)
            }
        }
        if (response.status.isSuccess()) {
            val body = response.bodyAsText()
            val result = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }.decodeFromString<T>(body)
            Result.success(result)
        } else {
            Result.failure(Exception("API error: ${response.status}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

data class ApiResponseWithCookie<T>(
    val data: T,
    val cookie: String
)

suspend inline fun <reified T> apiGetWithCookie(path: String, params: Map<String, Any> = emptyMap()): Result<ApiResponseWithCookie<T>> {
    return try {
        val response = apiClient.request("$API_BASE_URL$path") {
            method = HttpMethod.Get
            params.forEach { (key, value) ->
                parameter(key, value)
            }
        }
        if (response.status.isSuccess()) {
            val headerCookies = response.headers.getAll("Set-Cookie").orEmpty()
                .map { it.substringBefore(';').trim() }
                .filter { it.contains('=') }
            val body = response.bodyAsText()
            val allCookies = if (headerCookies.isNotEmpty()) {
                headerCookies.joinToString("; ")
            } else {
                extractBodyCookie(body)
            }
            try {
                val result = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                }.decodeFromString<T>(body)
                Result.success(ApiResponseWithCookie(data = result, cookie = allCookies))
            } catch (e: Exception) {
                Result.success(ApiResponseWithCookie(data = ApiCodeResponse(code = 200) as T, cookie = allCookies))
            }
        } else {
            Result.failure(Exception("API error: ${response.status}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun extractBodyCookie(body: String): String {
    return try {
        val element = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }.parseToJsonElement(body)
        val cookie = (element as? JsonObject)?.get("cookie")
        if (cookie is JsonPrimitive && cookie.content.isNotBlank()) {
            cookie.jsonPrimitive.content
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}

private fun encodeForm(value: Any): String =
    URLEncoder.encode(value.toString(), "UTF-8")

suspend inline fun <reified T> apiPost(path: String, body: Map<String, Any> = emptyMap()): Result<T> {
    return try {
        val response = apiClient.request("$API_BASE_URL$path") {
            method = HttpMethod.Post
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body.map { "${encodeForm(it.key)}=${encodeForm(it.value)}" }.joinToString("&"))
        }
        // CookieProvider.cookie
        if (response.status.isSuccess()) {
            val responseBody = response.bodyAsText()
            val result = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }.decodeFromString<T>(responseBody)
            Result.success(result)
        } else {
            Result.failure(Exception("API error: ${response.status}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
