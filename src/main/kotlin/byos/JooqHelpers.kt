package byos

import org.jooq.DSLContext
import org.jooq.impl.DSL


private const val userName = "postgres"
private const val password = ""
private const val url = "jdbc:postgresql://localhost:5432/byos_schema_names"

fun <T> executeJooqQuery(withDsl: (dsl: DSLContext) -> T): T {
    return DSL.using(url, userName, password).use { ctx ->
        withDsl(ctx)
    }
}
