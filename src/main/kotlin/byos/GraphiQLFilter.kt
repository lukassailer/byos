package byos

import com.fasterxml.jackson.databind.ObjectMapper
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.introspection.IntrospectionQuery
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.validation.Validator
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.File
import java.util.Locale
import java.util.stream.Collectors

@Component
class GraphiQLFilter : OncePerRequestFilter() {
    companion object {
        private val schemaFile = File("src/main/resources/graphql/schema.graphqls")
        private val schema = SchemaGenerator().makeExecutableSchema(SchemaParser().parse(schemaFile), RuntimeWiring.newRuntimeWiring().build())
        private val graphQL = GraphQL.newGraphQL(schema).build()
        private val objectMapper = ObjectMapper()
    }
    
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val query = extractQueryFromRequest(request)
        if (query.isNullOrBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"

        val document = Parser().parseDocument(query)
        val errors = Validator().validateDocument(schema, document, Locale.ENGLISH)
        if (errors.isNotEmpty()) {
            response.writer.write(
                objectMapper.writeValueAsString(
                    mapOf("errors" to errors.map { it.toSpecification() })
                )
            )
            return
        }

        val ast = parseASTFromQuery(query)

        if (ast.name == "IntrospectionQuery") {
            val executionInput = ExecutionInput.newExecutionInput()
                .query(IntrospectionQuery.INTROSPECTION_QUERY)
                .context(request)
                .build()
            val result = graphQL.execute(executionInput)
            response.writer.write(objectMapper.writeValueAsString(result.toSpecification()))
            return
        }

        val queryTrees = buildInternalQueryTree(ast)
        val results =
            queryTrees.map { tree ->
                executeJooqQuery { ctx ->
                    ctx.select(resolveInternalQueryTree(tree)).fetch()
                }
            }
        results.map(::println)
        response.writer.write(results.formatGraphQLResponse())
    }

    private fun extractQueryFromRequest(request: HttpServletRequest): String? {
        val requestBody = request.reader.lines().collect(Collectors.joining())
        val jsonNode = objectMapper.readTree(requestBody)
        return jsonNode["query"]?.textValue()
    }
}

fun parseASTFromQuery(query: String): OperationDefinition {
    val parser = Parser()
    val document = parser.parseDocument(query)
    return document.definitions[0] as OperationDefinition
}
