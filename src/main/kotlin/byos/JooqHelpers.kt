package byos

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
private const val url = "jdbc:postgresql://localhost:5432/sakila"

fun <T> executeJooqQuery(withDsl: (dsl: DSLContext) -> T): T {
    val connection = DriverManager.getConnection(url, userName, password)
    val configuration = DefaultConfiguration().set(connection).set(SQLDialect.POSTGRES)
    configuration.set(DefaultExecuteListenerProvider(PrettyPrinter()))

    return withDsl(DSL.using(configuration))
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

// unwrap singletons and wrap in data object
fun List<Formattable>.formatGraphQLResponse(): String =
    map { it.formatJSON(GRAPHQL_FORMAT) }
        .joinToString(
            separator = ",",
            prefix = "{\"data\":{",
            postfix = "}}",
            transform = { it.substring(2, it.length - 2) }
        )

val GRAPHQL_FORMAT = JSONFormat()
    .header(false)
    .recordFormat(JSONFormat.RecordFormat.OBJECT)
