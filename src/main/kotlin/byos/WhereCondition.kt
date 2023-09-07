package byos

import com.fasterxml.jackson.databind.ObjectMapper
import graphql.language.Argument
import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.EnumValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.StringValue
import graphql.language.Value
import org.jooq.Condition
import org.jooq.Field
import org.jooq.Record
import org.jooq.SortField
import org.jooq.SortOrder
import org.jooq.Table
import org.jooq.impl.DSL

class WhereCondition(private val getConditionForRelationship: (String, Table<*>, Table<*>) -> Condition?) {
    fun getForRelationship(relationshipName: String, left: Table<*>, right: Table<*>): Condition =
        getConditionForRelationship(relationshipName, left, right)
            ?: error("No relationship called $relationshipName found for tables $left and $right")


    fun getForArgument(argument: Argument, table: Table<*>): Condition {
        val field = table.field(argument.name) as Field<Any>?
            ?: error("No field called ${argument.name} found for table $table")

        return when (val value = extractValue(argument.value)) {
            is List<*> -> field.`in`(value).or(if (value.contains(null)) field.isNull else DSL.noCondition())
            null -> field.isNull
            else -> field.eq(value)
        }
    }

    private fun extractValue(value: Value<Value<*>>): Any? =
        when (value) {
            is IntValue -> value.value
            is FloatValue -> value.value
            is BooleanValue -> value.isValue
            is StringValue -> value.value
            is EnumValue -> value.name
            is NullValue -> null
            is ArrayValue -> value.values.map { extractValue(it) }
            else -> error("Unsupported argument type ${value.javaClass}")
        }

    fun getForAfterArgument(afterArgument: Argument, orderByFields: List<SortField<*>>, table: Table<out Record>): Condition {
        val after = (afterArgument.value as StringValue).value
        val json = ObjectMapper().readTree(after)
        val afterValues = json.fields().asSequence().map { it.key to it.value.asText() }.toList()

        return getForAfterArgumentRec(afterValues, orderByFields, table)
    }

    private fun getForAfterArgumentRec(afterValues: List<Pair<String, String>>, orderByFields: List<SortField<*>>, table: Table<out Record>): Condition {
        val head = afterValues.firstOrNull() ?: return DSL.noCondition()

        val cond = head.let { (field, value) ->
            orderByFields.find { it.name == field }!!.let {
                if (it.order == SortOrder.DESC) {
                    (table.field(field) as Field<Any>).lessThan(value)
                } else {
                    (table.field(field) as Field<Any>).greaterThan(value)
                }
            }
        }

        val tail = afterValues.drop(1).ifEmpty { return cond }

        return cond.or(
            head.let { (field, value) ->
                (table.field(field) as Field<Any>).eq(value).and(
                    getForAfterArgumentRec(tail, orderByFields, table)
                )
            }
        )
    }
}
