package example

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class GraphQLRequestFilter(
    private val graphQLService: GraphQLService
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val requestInfo = graphQLService.extractRequestInfo(request)
        if (requestInfo == null) {
            filterChain.doFilter(request, response)
            return
        }

        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"

        val result = graphQLService.executeGraphQLQuery(requestInfo)
        response.writer.write(result)
    }
}
