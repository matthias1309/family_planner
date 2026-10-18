package de.familyplanner.app

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.SimpleRequest
import com.atlassian.oai.validator.model.SimpleResponse
import com.atlassian.oai.validator.report.LevelResolver
import com.atlassian.oai.validator.report.ValidationReport
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import java.io.File
import java.net.URLDecoder
import kotlin.text.Charsets.UTF_8

/** Validates real request/response pairs against api/openapi.yaml. */
class ContractValidator {
    private val validator: OpenApiInteractionValidator =
        OpenApiInteractionValidator
            .createForSpecificationUrl(File(System.getProperty("openapi.spec")).toURI().toString())
            // Missing Authorization headers are exercised on purpose (401 tests); auth itself is tested separately.
            .withLevelResolver(
                LevelResolver.create().withLevel("validation.request.security.missing", ValidationReport.Level.IGNORE).build(),
            ).build()

    fun report(
        method: String,
        path: String,
        status: Int,
        contentType: String?,
        body: String,
        requestBody: String? = null,
    ): ValidationReport {
        val request =
            SimpleRequest
                .Builder(method, path.substringBefore('?'))
                .withContentType("application/json")
                .apply { queryParameters(path).forEach { (name, value) -> withQueryParam(name, value) } }
                .apply { requestBody?.let { withBody(it) } }
                .build()
        val response =
            SimpleResponse.Builder
                .status(status)
                .apply { contentType?.let { withContentType(it) } }
                .withBody(body)
                .build()
        return validator.validate(request, response)
    }

    /** With [invalidRequestExpected], violations of the *request* schema are ignored (negative tests send bad input on purpose). */
    suspend fun assertValid(
        method: String,
        path: String,
        response: HttpResponse,
        requestBody: String? = null,
        invalidRequestExpected: Boolean = false,
    ) {
        val report =
            report(
                method = method,
                path = path,
                status = response.status.value,
                contentType = response.headers[HttpHeaders.ContentType],
                body = response.bodyAsText(),
                requestBody = requestBody,
            )
        val errors =
            report.messages
                .filter { it.level == ValidationReport.Level.ERROR }
                .filterNot { invalidRequestExpected && it.key.startsWith("validation.request.") }
        check(errors.isEmpty()) {
            "Contract violation for $method $path (${response.status}): ${errors.joinToString("; ") { message -> message.message }}"
        }
    }
}

/** Decoded `name=value` pairs of the query part of [path]; empty if there is none. */
private fun queryParameters(path: String): List<Pair<String, String>> =
    path
        .substringAfter('?', "")
        .split('&')
        .filter { it.isNotEmpty() }
        .map { URLDecoder.decode(it.substringBefore('='), UTF_8) to URLDecoder.decode(it.substringAfter('=', ""), UTF_8) }

fun ValidationReport.text(): String = messages.joinToString(separator = "; ") { message -> message.message }
