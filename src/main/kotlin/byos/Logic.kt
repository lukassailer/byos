package byos

import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.jooq.Condition
import org.jooq.Field
import org.jooq.Record
import org.jooq.Result
import org.jooq.impl.DSL
import java.io.File
import graphql.language.Field as GraphQLField

sealed interface QueryNode {
    data class Relation(val value: String, val children: List<QueryNode>, val isList: Boolean) : QueryNode
    data class Attribute(val value: String) : QueryNode
}

fun buildTree(queryDefinition: OperationDefinition): QueryNode.Relation =
    getOperationTree(queryDefinition.selectionSet)[0] as QueryNode.Relation

private fun getOperationTree(selectionSet: SelectionSet): List<QueryNode> =
    selectionSet.selections.mapNotNull { selection ->
        val subSelectionSet = (selection as GraphQLField).selectionSet
        if (subSelectionSet == null) {
            QueryNode.Attribute(selection.name)
        } else {
            val schemaFile = File("src/main/resources/graphql/schema.graphqls")
            val schema = SchemaGenerator().makeExecutableSchema(SchemaParser().parse(schemaFile), RuntimeWiring.newRuntimeWiring().build())

            QueryNode.Relation(selection.name, getOperationTree(subSelectionSet), isFieldListType(schema, selection.name))
        }
    }

fun resolveTree(relation: QueryNode.Relation, condition: Condition = DSL.noCondition()): Field<Result<Record>> {
    val (relations, attributes) = relation.children.partition { it is QueryNode.Relation }
    val attributeNames = attributes.map { (it as QueryNode.Attribute).value }.map { DSL.field(it) }

    val subSelects = relations.map {
        val whereCondition = WhereCondition.getFor(relation.value, (it as QueryNode.Relation).value)
        resolveTree(it, whereCondition)
    }

    return DSL.multiset(
        DSL.select(attributeNames)
            .select(subSelects)
            .from(relation.value)
            .where(condition)
    ).`as`(relation.value + if (relation.isList) "" else "-singleton")
}

fun isFieldListType(schema: GraphQLSchema, fieldName: String): Boolean {
    // The type could also be deducted from the position in the tree in the future
    val allTypes = schema.allTypesAsList
    for (type in allTypes) {
        // Interface, Union, Scalar, Enum, InputObject are not supported
        if (type is GraphQLObjectType) {
            val fieldDef = type.getFieldDefinition(fieldName)
            if (fieldDef != null) {
                println(fieldDef.type)
                return when (val fieldType = fieldDef.type) {
                    is GraphQLList -> true
                    is GraphQLNonNull -> fieldType.wrappedType is GraphQLList
                    else -> false
                }
            }
        }
    }
    throw IllegalArgumentException("Field '$fieldName' not found in schema")
}
