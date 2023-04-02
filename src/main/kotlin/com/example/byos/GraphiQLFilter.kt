package com.example.byos

import com.fasterxml.jackson.databind.ObjectMapper
import graphql.language.OperationDefinition
import graphql.parser.Parser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.stream.Collectors

@Component
class GraphiQLFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val query = getQueryFromRequest(request)
        if (!query.isNullOrBlank()) {
            val ast = parseASTFromQuery(query)
            val tree = buildTree(ast)
            // TODO execute after building tree

            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"data": "Hello World"}""")
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun getQueryFromRequest(request: HttpServletRequest): String? {
        val requestBody = request.reader.lines().collect(Collectors.joining())
        val jsonNode = ObjectMapper().readTree(requestBody)
        return jsonNode["query"]?.textValue()
    }
}

fun parseASTFromQuery(query: String): OperationDefinition {
    val parser = Parser()
    val document = parser.parseDocument(query)
    return document.definitions[0] as OperationDefinition
}
