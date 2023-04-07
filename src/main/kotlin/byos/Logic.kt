package byos

import db.jooq.generated.Tables.AUTHORS
import db.jooq.generated.Tables.BOOKS
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import org.jooq.Condition
import org.jooq.Record
import org.jooq.Select
import org.jooq.impl.DSL

sealed interface QueryNode {
    data class Relation(val value: String, val children: List<QueryNode>) : QueryNode
    data class Attribute(val value: String) : QueryNode
}

fun buildTree(queryDefinition: OperationDefinition): QueryNode.Relation =
    getOperationTree(queryDefinition.selectionSet)[0] as QueryNode.Relation

private fun getOperationTree(selectionSet: SelectionSet): List<QueryNode> =
    selectionSet.selections.mapNotNull { selection ->
        val subSelectionSet = (selection as Field).selectionSet
        if (subSelectionSet == null) {
            QueryNode.Attribute(selection.name)
        } else {
            QueryNode.Relation(selection.name, getOperationTree(subSelectionSet))
        }
    }

fun resolveTree(relation: QueryNode.Relation, condition: Condition = DSL.noCondition()): Select<Record> {
    val (relations, attributes) = relation.children.partition { it is QueryNode.Relation }
    val attributeNames = attributes.map { (it as QueryNode.Attribute).value }.map { DSL.field(it) }

    val newCond = DSL.condition(BOOKS.AUTHORID.eq(AUTHORS.ID))

    val subSelects = relations.map {
        DSL.multiset(
            resolveTree(it as QueryNode.Relation, newCond)
        ).`as`(it.value)
    }

    return DSL.select(attributeNames)
        .select(subSelects)
        .from(relation.value)
        .where(condition)
}
