package byos

import db.jooq.generated.Public.PUBLIC
import graphql.language.Argument
import graphql.language.IntValue
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
        val connectionInfo: ConnectionInfo?,
    ) : InternalQueryNode(graphQLFieldName, graphQLAlias)

    class Attribute(graphQLFieldName: String, graphQLAlias: String) : InternalQueryNode(graphQLFieldName, graphQLAlias)
}

data class FieldTypeInfo(val graphQLTypeName: String, val isList: Boolean) {
    val relationName = graphQLTypeName.lowercase()
}

data class ConnectionInfo(val cursorGraphQLAliases: List<String>)

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
                        connectionInfo = ConnectionInfo(
                            edgesSelection.selectionSet!!.selections.filterIsInstance<GraphQLField>().filter { it.name == "cursor" }.map { it.alias ?: it.name }
                        )
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
                        connectionInfo = null
                    )
                }
            }
        }

fun resolveInternalQueryTree(relation: InternalQueryNode.Relation, joinCondition: Condition = DSL.noCondition()): Field<JSON> {
    val outerTable = getTableWithAlias(relation)

    val (relations, attributes) = relation.children.partition { it is InternalQueryNode.Relation }
    val attributeNames = attributes.distinctBy { it.graphQLAlias }
        .map { attribute -> outerTable.field(attribute.graphQLFieldName.lowercase())!!.`as`(attribute.graphQLAlias) }

    val subSelects = relations.map { subRelation ->
        val innerTable = getTableWithAlias(subRelation as InternalQueryNode.Relation)
        resolveInternalQueryTree(
            subRelation, WhereCondition.getForRelationship(subRelation.graphQLFieldName, outerTable, innerTable)
        )
    }

    val (paginationArgument, filterArguments) = relation.arguments.partition { it.name == "first" }
    val argumentConditions = filterArguments.map { WhereCondition.getForArgument(it, outerTable) }
    val limit = (paginationArgument.firstOrNull()?.value as IntValue?)?.value

    val primaryKeyFields = outerTable.primaryKey?.fields?.map { outerTable.field(it) } ?: emptyList()
    val orderCriteria = DSL.row(primaryKeyFields).`as`("order_criteria")

    return DSL.field(
        DSL.select(
            when {
                relation.connectionInfo != null -> {
                    DSL.jsonObject(
                        "edges",
                        DSL.coalesce(
                            DSL.jsonArrayAgg(
                                DSL.jsonObject(
                                    DSL.key("node").value(
                                        DSL.jsonObject(
                                            *attributeNames.toTypedArray(),
                                            *subSelects.toTypedArray()
                                        )
                                    ),
                                    *relation.connectionInfo.cursorGraphQLAliases.map {
                                        DSL.key(it).value(DSL.field(orderCriteria.name))
                                    }.toTypedArray()
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
                .select(orderCriteria)
                .from(outerTable)
                .where(argumentConditions)
                .and(joinCondition)
                .orderBy(primaryKeyFields)
                .apply { if (limit != null) limit(limit) }
        )
    ).`as`(relation.graphQLAlias)
}

// TODO DEFAULT_CATALOG.schemas durchsuchen anstatt PUBLIC?
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
