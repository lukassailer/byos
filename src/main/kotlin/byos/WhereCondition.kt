package byos

import com.fasterxml.jackson.databind.ObjectMapper
import db.jooq.generated.Tables.FILM_ACTOR
import db.jooq.generated.Tables.FILM_CATEGORY
import db.jooq.generated.Tables.INVENTORY
import db.jooq.generated.tables.Actor
import db.jooq.generated.tables.Category
import db.jooq.generated.tables.Film
import db.jooq.generated.tables.Inventory
import db.jooq.generated.tables.Language
import db.jooq.generated.tables.Store
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

object WhereCondition {
    fun getForRelationship(relationshipName: String, left: Table<*>, right: Table<*>): Condition =
        when {
            relationshipName == "actors" && left is Film && right is Actor -> DSL.exists(
                DSL.selectOne().from(FILM_ACTOR).where(left.FILM_ID.eq(FILM_ACTOR.FILM_ID).and(FILM_ACTOR.ACTOR_ID.eq(right.ACTOR_ID)))
            )

            relationshipName == "films" && left is Actor && right is Film -> DSL.exists(
                DSL.selectOne().from(FILM_ACTOR).where(left.ACTOR_ID.eq(FILM_ACTOR.ACTOR_ID).and(FILM_ACTOR.FILM_ID.eq(right.FILM_ID)))
            )

            relationshipName == "stores" && left is Film && right is Store -> DSL.exists(
                DSL.selectOne().from(INVENTORY).where(left.FILM_ID.eq(INVENTORY.FILM_ID).and(INVENTORY.STORE_ID.eq(right.STORE_ID)))
            )

            relationshipName == "films" && left is Store && right is Film -> DSL.exists(
                DSL.selectOne().from(INVENTORY).where(left.STORE_ID.eq(INVENTORY.STORE_ID).and(INVENTORY.FILM_ID.eq(right.FILM_ID)))
            )

            relationshipName == "language" && left is Film && right is Language -> left.LANGUAGE_ID.eq(right.LANGUAGE_ID)
            relationshipName == "original_language" && left is Film && right is Language -> left.ORIGINAL_LANGUAGE_ID.eq(right.LANGUAGE_ID)

            relationshipName == "inventories" && left is Store && right is Inventory -> left.STORE_ID.eq(right.STORE_ID)

            relationshipName == "film" && left is Inventory && right is Film -> left.FILM_ID.eq(right.FILM_ID)

            relationshipName == "categories" && left is Film && right is Category -> DSL.exists(
                DSL.selectOne().from(FILM_CATEGORY).where(left.FILM_ID.eq(FILM_CATEGORY.FILM_ID).and(FILM_CATEGORY.CATEGORY_ID.eq(right.CATEGORY_ID)))
            )

            relationshipName == "films" && left is Category && right is Film -> DSL.exists(
                DSL.selectOne().from(FILM_CATEGORY).where(left.CATEGORY_ID.eq(FILM_CATEGORY.CATEGORY_ID).and(FILM_CATEGORY.FILM_ID.eq(right.FILM_ID)))
            )

            relationshipName == "parent_category" && left is Category && right is Category -> left.PARENT_CATEGORY_ID.eq(right.CATEGORY_ID)

            relationshipName == "subcategories" && left is Category && right is Category -> left.CATEGORY_ID.eq(right.PARENT_CATEGORY_ID)

            else -> error("No relationship called $relationshipName found for tables $left and $right")
        }

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
