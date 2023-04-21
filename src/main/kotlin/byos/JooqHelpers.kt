package byos

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.jooq.DSLContext
import org.jooq.ExecuteContext
import org.jooq.ExecuteListener
import org.jooq.Formattable
import org.jooq.JSONFormat
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultExecuteListenerProvider
import java.sql.DriverManager

private const val userName = "postgres"
private const val password = ""
private const val url = "jdbc:postgresql://localhost:5432/byos_schema_names"

fun <T> executeJooqQuery(withDsl: (dsl: DSLContext) -> T): T {
    val connection = DriverManager.getConnection(url, userName, password)
    val configuration = DefaultConfiguration().set(connection).set(SQLDialect.POSTGRES)
    configuration.set(DefaultExecuteListenerProvider(PrettyPrinter()))

    return withDsl(DSL.using(configuration))
}

val GRAPHQL_FORMAT = JSONFormat()
    .header(false)
    .recordFormat(JSONFormat.RecordFormat.OBJECT)
    .format(true)

// unwrap singleton and wrap in data object
fun Formattable.formatGraphQLResponse(): String {
    val json = this.formatJSON(GRAPHQL_FORMAT)
    val jsonWithoutSingletons = unwrapSingletonArrays(json.substring(1, json.length - 1))
    return "{\"data\":$jsonWithoutSingletons}"
}

// print sql queries (see: https://www.jooq.org/doc/latest/manual/sql-execution/execute-listeners/)
class PrettyPrinter : ExecuteListener {
    override fun executeStart(ctx: ExecuteContext) {
        val create = DSL.using(
            ctx.dialect(),
            Settings().withRenderFormatted(true)
        )

        if (ctx.query() != null) {
            println(create.renderInlined(ctx.query()))
        } else if (ctx.routine() != null) {
            println(create.renderInlined(ctx.routine()))
        }
    }
}

fun unwrapSingletonArrays(json: String): String {
    val mapper = ObjectMapper()
    val rootNode = mapper.readTree(json)
    unwrapSingletonArraysRecursively(rootNode)
    return mapper.writeValueAsString(rootNode)
}

fun unwrapSingletonArraysRecursively(node: JsonNode) {
    if (node.isObject) {
        val objNode = node as ObjectNode
        objNode.fieldNames().forEach { fieldName ->
            val fieldNode = objNode.get(fieldName)
            if (fieldName.endsWith("-singleton") && fieldNode.isArray) {
                if (fieldNode.size() > 1) {
                    error("Expected singleton array, got ${fieldNode.size()} elements")
                } else if (fieldNode.size() == 0) {
                    objNode.set<JsonNode>(fieldName.substringBeforeLast("-singleton"), null)
                } else {
                    val singletonArray = fieldNode.elements().next()
                    objNode.set<JsonNode>(fieldName.substringBeforeLast("-singleton"), singletonArray)
                }
                objNode.remove(fieldName)
            } else {
                unwrapSingletonArraysRecursively(fieldNode)
            }
        }
    } else if (node.isArray) {
        node.forEach { arrayNode ->
            unwrapSingletonArraysRecursively(arrayNode)
        }
    }
}
