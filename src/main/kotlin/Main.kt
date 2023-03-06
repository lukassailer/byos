import db.jooq.generated.tables.Student.STUDENT
import org.jooq.impl.DSL

fun main(args: Array<String>) {
    val userName = "postgres"
    val password = ""
    val url = "jdbc:postgresql://localhost:5432/byos"

    DSL.using(url, userName, password).use { ctx ->
        ctx.select(STUDENT.NAME, STUDENT.AGE)
            .from(STUDENT)
            .fetch()
            .forEach { println("${it[STUDENT.NAME]} is ${it[STUDENT.AGE]} years old") }
    }
}
