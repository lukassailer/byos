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
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.File
import java.util.stream.Collectors

@Component
class GraphiQLFilter : OncePerRequestFilter() {
    private val schemaFile = File("src/main/resources/graphql/schema.graphqls")
    private val schema = SchemaGenerator().makeExecutableSchema(SchemaParser().parse(schemaFile), RuntimeWiring.newRuntimeWiring().build())
    private val graphQL = GraphQL.newGraphQL(schema).build()

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val query = getQueryFromRequest(request)
        if (query.isNullOrBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        val ast = parseASTFromQuery(query)
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        if (ast.name == "IntrospectionQuery") {
            val executionInput = ExecutionInput.newExecutionInput()
                .query(IntrospectionQuery.INTROSPECTION_QUERY)
                .context(request)
                .build()
            val result = graphQL.execute(executionInput)
            response.writer.write(ObjectMapper().writeValueAsString(result.toSpecification()))
            return
        }

        val tree = buildTree(ast)
        val result = executeJooqQuery { ctx ->
            ctx.select(resolveTree(tree)).fetch()
        }
        println(result)
        response.writer.write(result.formatGraphQLResponse())
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
