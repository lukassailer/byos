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

            val fieldTypeInfo = getFieldTypeInfo(schema, selection.name)

            QueryNode.Relation(fieldTypeInfo.typeName, getOperationTree(subSelectionSet), fieldTypeInfo.isList)
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

fun getFieldTypeInfo(schema: GraphQLSchema, fieldName: String): FieldTypeInfo {
    // The type could also be deducted from the position in the tree in the future
    val allTypes = schema.allTypesAsList
    for (type in allTypes) {
        // Interface, Union, Scalar, Enum, InputObject are not supported
        if (type is GraphQLObjectType) {
            val fieldDef = type.getFieldDefinition(fieldName)
            if (fieldDef != null) {
                return when (val fieldType = fieldDef.type) {
                    // nested lists are not supported
                    is GraphQLList -> FieldTypeInfo((fieldType.wrappedType as GraphQLObjectType).name, true)
                    is GraphQLNonNull -> {
                        when (val wrappedType = fieldType.wrappedType) {
                            is GraphQLList -> FieldTypeInfo((wrappedType.wrappedType as GraphQLObjectType).name, true)
                            is GraphQLObjectType -> FieldTypeInfo(wrappedType.name, false)
                            else -> throw IllegalArgumentException("Field '$fieldName' has unsupported type '$wrappedType'")
                        }
                    }

                    is GraphQLObjectType -> FieldTypeInfo(fieldType.name, false)
                    else -> throw IllegalArgumentException("Field '$fieldName' has unsupported type '$fieldType'")
                }
            }
        }
    }
    throw IllegalArgumentException("Field '$fieldName' not found in schema")
}

data class FieldTypeInfo(val typeName: String, val isList: Boolean)
