package byos

import org.jooq.DSLContext
import org.jooq.Formattable
import org.jooq.JSONFormat
import org.jooq.impl.DSL


private const val userName = "postgres"
private const val password = ""
private const val url = "jdbc:postgresql://localhost:5432/byos_schema_names"

fun <T> executeJooqQuery(withDsl: (dsl: DSLContext) -> T): T {
    return DSL.using(url, userName, password).use { ctx ->
        withDsl(ctx)
    }
}

val GRAPHQL_FORMAT = JSONFormat()
    .header(false)
    .recordFormat(JSONFormat.RecordFormat.OBJECT)
    .format(true)

// unwrap singleton and wrap in data object
fun Formattable.formatGraphQLResponse(): String {
    val json = this.formatJSON(GRAPHQL_FORMAT)
    return "{\"data\":" + json.substring(1, json.length - 1) + "}"
}
