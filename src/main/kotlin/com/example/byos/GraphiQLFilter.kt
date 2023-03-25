package com.example.byos

import com.fasterxml.jackson.databind.ObjectMapper
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
            // TODO obtain response from query
            println(query)
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
