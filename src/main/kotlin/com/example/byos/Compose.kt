package com.example.byos

import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import org.jooq.impl.DSL

sealed interface QueryNode {
    data class Relation(val value: String, val children: List<QueryNode>) : QueryNode
    data class Attribute(val value: String) : QueryNode
}

val userName = "postgres"
val password = ""
val url = "jdbc:postgresql://localhost:5432/byos_schema_names"


fun buildTree(queryDefinition: OperationDefinition) {
    val operationTree = getOperationTree(queryDefinition.selectionSet)[0]
    println(operationTree)

    // TODO select recursively

    val testTable = (operationTree as QueryNode.Relation).value
    val testField = DSL.field((operationTree.children[0] as QueryNode.Attribute).value)
    val test = DSL.using(url, userName, password).use { ctx ->
        ctx.select(testField)
            .from(testTable)
            .fetch()
    }

    println(test)
}

fun getOperationTree(selectionSets: SelectionSet): List<QueryNode> =
    selectionSets.selections.mapNotNull { selection ->
        val subSelectionSet = (selection as Field).selectionSet
        if (subSelectionSet == null) {
            QueryNode.Attribute(selection.name)
        } else {
            QueryNode.Relation(selection.name, getOperationTree(subSelectionSet))
        }
    }
