package com.example.byos

import db.jooq.generated.Tables.BOOKS
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class GraphiQLFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        if (request.servletPath == "/graphql") {

            val userName = "postgres"
            val password = ""
            val url = "jdbc:postgresql://localhost:5432/byos_schema_names"

            val books = DSL.using(url, userName, password).use { ctx ->
                ctx.select(BOOKS.TITLE)
                    .from(BOOKS)
                    .fetch()
                    .map { it[BOOKS.TITLE] }
            }

            response.writer.write("""{"data": [{"request": "${request}"}, {"books": "${books}"}]}""")
            return
        }
        filterChain.doFilter(request, response)
    }
}
