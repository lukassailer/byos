package com.example.byos

import com.fasterxml.jackson.databind.ObjectMapper
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
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
            val result = resolveQuery(query)
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

fun resolveQuery(query: String): String {
    val parser = Parser()
    val document = parser.parseDocument(query)
    val queryDefinition = document.definitions[0] as OperationDefinition

    var selectionSets = listOf(queryDefinition.selectionSet)
    while (selectionSets.isNotEmpty()) {
        selectionSets = selectionSets.flatMap { getSubFields(it) }
    }

    return "Hello World"
}

fun getSubFields(selectionSet: SelectionSet): List<SelectionSet> {
    val selections = selectionSet.selections
    return selections.mapNotNull { selection ->
        val result = (selection as Field).selectionSet
        println(selection.name)
        result ?: println("(attribute)")
        result
    }
}
