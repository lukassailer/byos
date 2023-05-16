package byos

import db.jooq.generated.Public.PUBLIC
import graphql.language.Argument
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.jooq.Condition
import org.jooq.Field
import org.jooq.JSON
import org.jooq.impl.DSL
import java.io.File
import java.util.UUID
import graphql.language.Field as GraphQLField


private val schemaFile = File("src/main/resources/graphql/schema.graphqls")
private val schema: GraphQLSchema = SchemaGenerator().makeExecutableSchema(SchemaParser().parse(schemaFile), RuntimeWiring.newRuntimeWiring().build())

sealed class InternalQueryNode(val graphQLFieldName: String, val graphQLAlias: String) {
    class Relation(
        graphQLFieldName: String,
        graphQLAlias: String,
        val sqlAlias: String,
        val fieldTypeInfo: FieldTypeInfo,
        val children: List<InternalQueryNode>,
        val arguments: List<Argument>,
        val isPaginated: Boolean
    ) : InternalQueryNode(graphQLFieldName, graphQLAlias)

    class Attribute(graphQLFieldName: String, graphQLAlias: String) : InternalQueryNode(graphQLFieldName, graphQLAlias)
}

data class FieldTypeInfo(val graphQLTypeName: String, val isList: Boolean) {
    val relationName = graphQLTypeName.lowercase()
}

fun buildInternalQueryTree(queryDefinition: OperationDefinition): List<InternalQueryNode.Relation> =
    getChildrenFromSelectionSet(queryDefinition.selectionSet).map { it as InternalQueryNode.Relation }

private fun getChildrenFromSelectionSet(selectionSet: SelectionSet, parentGraphQlTypeName: String = schema.queryType.name): List<InternalQueryNode> =
    selectionSet.selections
        .filterIsInstance<GraphQLField>()
        .map { selection ->
            val subSelectionSet = selection.selectionSet
            when {
                subSelectionSet == null -> {
                    InternalQueryNode.Attribute(
                        graphQLFieldName = selection.name,
                        graphQLAlias = selection.alias ?: selection.name
                    )
                }

                subSelectionSet.selections.any { it is GraphQLField && it.name == "edges" } -> {
                    val edgesSelection = subSelectionSet.selections.filterIsInstance<GraphQLField>().single { it.name == "edges" }
                    val nodeSelection = edgesSelection.selectionSet!!.selections.filterIsInstance<GraphQLField>().single { it.name == "node" }
                    val nodeSubSelectionSet = nodeSelection.selectionSet

                    // TODO alias
                    val queryTypeInfo = getFieldTypeInfo(schema, selection.name, parentGraphQlTypeName)
                    val edgesTypeInfo = getFieldTypeInfo(schema, edgesSelection.name, queryTypeInfo.graphQLTypeName)
                    val nodeTypeInfo = getFieldTypeInfo(schema, nodeSelection.name, edgesTypeInfo.graphQLTypeName)

                    InternalQueryNode.Relation(
                        graphQLFieldName = selection.name,
                        graphQLAlias = selection.alias ?: selection.name,
                        sqlAlias = "${selection.name}-${UUID.randomUUID()}",
                        fieldTypeInfo = nodeTypeInfo,
                        children = getChildrenFromSelectionSet(nodeSubSelectionSet, nodeTypeInfo.graphQLTypeName),
                        arguments = selection.arguments,
                        isPaginated = true
                    )
                }

                else -> {
                    val fieldTypeInfo = getFieldTypeInfo(schema, selection.name, parentGraphQlTypeName)
                    InternalQueryNode.Relation(
                        graphQLFieldName = selection.name,
                        graphQLAlias = selection.alias ?: selection.name,
                        sqlAlias = "${selection.name}-${UUID.randomUUID()}",
                        fieldTypeInfo = fieldTypeInfo,
                        children = getChildrenFromSelectionSet(subSelectionSet, fieldTypeInfo.graphQLTypeName),
                        arguments = selection.arguments,
                        isPaginated = false
                    )
                }
            }
        }

fun resolveInternalQueryTree(relation: InternalQueryNode.Relation, joinCondition: Condition = DSL.noCondition()): Field<JSON> {
    val (relations, attributes) = relation.children.partition { it is InternalQueryNode.Relation }
    val attributeNames = attributes.map { attribute -> DSL.field(attribute.graphQLFieldName).`as`(attribute.graphQLAlias) }
    // TODO DEFAULT_CATALOG.schemas durchsuchen anstatt PUBLIC?
    val outerTable = getTableWithAlias(relation)

    val subSelects = relations.map { subRelation ->
        val innerTable = getTableWithAlias(subRelation as InternalQueryNode.Relation)
        resolveInternalQueryTree(
            subRelation, WhereCondition.getForRelationship(subRelation.graphQLFieldName, outerTable, innerTable)
        )
    }

    return DSL.field(
        DSL.select(
            when {
                relation.isPaginated -> {
                    DSL.jsonObject(
                        "edges",
                        DSL.coalesce(
                            DSL.jsonArrayAgg(
                                DSL.jsonObject(
                                    "node",
                                    DSL.jsonObject(
                                        *attributeNames.toTypedArray(),
                                        *subSelects.toTypedArray()
                                    )
                                )
                            ),
                            DSL.jsonArray()
                        )
                    )
                }

                relation.fieldTypeInfo.isList -> {
                    DSL.coalesce(
                        DSL.jsonArrayAgg(
                            DSL.jsonObject(
                                *attributeNames.toTypedArray(),
                                *subSelects.toTypedArray()
                            )
                        ),
                        DSL.jsonArray()
                    )
                }

                else -> {
                    DSL.jsonObject(
                        *attributeNames.toTypedArray(),
                        *subSelects.toTypedArray()
                    )
                }
            }
        ).from(
            DSL.select(attributeNames)
                .select(subSelects)
                .from(outerTable)
                .where(relation.arguments.map { WhereCondition.getForArgument(it, outerTable) })
                .and(joinCondition)
                .orderBy(outerTable.primaryKey?.fields?.map { outerTable.field(it) })
        )
    ).`as`(relation.graphQLAlias)
}

private fun getTableWithAlias(relation: InternalQueryNode.Relation) =
    PUBLIC.getTable(relation.fieldTypeInfo.relationName)?.`as`(relation.sqlAlias) ?: error("Table not found")

fun getFieldTypeInfo(schema: GraphQLSchema, fieldName: String, typeName: String): FieldTypeInfo {
    val type = schema.getType(typeName) as? GraphQLObjectType ?: error("Type '$typeName' not found in schema")
    val field = type.getFieldDefinition(fieldName) ?: error("Field '$fieldName' not found on type $typeName")
    return getTypeInfo(field.type, false)
}

private fun getTypeInfo(type: GraphQLType, inList: Boolean): FieldTypeInfo {
    return when (type) {
        is GraphQLObjectType -> FieldTypeInfo(type.name, inList)
        is GraphQLNonNull -> getTypeInfo(type.wrappedType, inList)
        is GraphQLList -> {
            if (inList) {
                error("Nested lists are not supported")
            }
            getTypeInfo(type.wrappedType, true)
        }

        else -> {
            error("Unsupported type '$type'")
        }
    }
}
