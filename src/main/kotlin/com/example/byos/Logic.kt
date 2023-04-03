package com.example.byos

import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import org.jooq.Record
import org.jooq.Select
import org.jooq.impl.DSL

sealed interface QueryNode {
    data class Relation(val value: String, val children: List<QueryNode>) : QueryNode
    data class Attribute(val value: String) : QueryNode
}

val userName = "postgres"
val password = ""
val url = "jdbc:postgresql://localhost:5432/byos_schema_names"


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

fun resolveTree(relation: QueryNode.Relation): Select<Record> {
    val (relations, attributes) = relation.children.partition { it is QueryNode.Relation }
    val attributeNames = attributes.map { (it as QueryNode.Attribute).value }.map { DSL.field(it) }

    val subSelects = relations.map {
        DSL.multiset(
            resolveTree(it as QueryNode.Relation)
        )
    }

    return DSL.select(attributeNames)
        .select(subSelects)
        .from(relation.value)
}
