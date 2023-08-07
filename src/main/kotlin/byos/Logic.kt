package byos

import db.jooq.generated.Public.PUBLIC
import graphql.language.Argument
import graphql.language.EnumValue
import graphql.language.IntValue
import graphql.language.ObjectValue
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
import java.math.BigInteger
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

    override fun toString(): String {
        return when (this) {
            is Relation -> "Relation(graphQLFieldName='$graphQLFieldName', graphQLAlias='$graphQLAlias', sqlAlias='$sqlAlias', fieldTypeInfo=$fieldTypeInfo, children=$children, arguments=$arguments, connectionInfo=$connectionInfo)"
            is Attribute -> "Attribute(graphQLFieldName='$graphQLFieldName', graphQLAlias='$graphQLAlias')"
        }
    }
}

data class FieldTypeInfo(val graphQLTypeName: String, val isList: Boolean) {
    val relationName = graphQLTypeName.lowercase()
}

data class ConnectionInfo(
    val cursorGraphQLAliases: List<String>,
    val totalCountGraphQLAliases: List<String>,
    val pageInfos: List<PageInfo>
)

data class PageInfo(
    val graphQLAlias: String,
    val hasNextPageGraphQlAliases: List<String>,
    val endCursorGraphQlAliases: List<String>,
)

fun buildInternalQueryTrees(queryDefinition: OperationDefinition): List<InternalQueryNode.Relation> =
    getChildrenFromSelectionSet(queryDefinition.selectionSet).map { it as InternalQueryNode.Relation }.also { println(it) }

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
                    val pageInfoSelections = subSelectionSet.selections.filterIsInstance<GraphQLField>().filter { it.name == "pageInfo" }

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
                            cursorGraphQLAliases = edgesSelection.selectionSet!!.selections.filterIsInstance<GraphQLField>().filter { it.name == "cursor" }
                                .map { it.alias ?: it.name },
                            totalCountGraphQLAliases = subSelectionSet.selections.filterIsInstance<GraphQLField>().filter { it.name == "totalCount" }
                                .map { it.alias ?: it.name },
                            pageInfos = pageInfoSelections.map { pageInfoSelection ->
                                val pageInfoSelectionSet = pageInfoSelection.selectionSet
                                PageInfo(
                                    graphQLAlias = pageInfoSelection.alias ?: pageInfoSelection.name,
                                    hasNextPageGraphQlAliases = pageInfoSelectionSet!!.selections.filterIsInstance<GraphQLField>()
                                        .filter { it.name == "hasNextPage" }
                                        .map { it.alias ?: it.name },
                                    endCursorGraphQlAliases = pageInfoSelectionSet.selections.filterIsInstance<GraphQLField>().filter { it.name == "endCursor" }
                                        .map { it.alias ?: it.name },
                                )
                            }
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
        .map { attribute ->
            outerTable.field(attribute.graphQLFieldName.lowercase())?.`as`(attribute.graphQLAlias)
                ?: error("Field ${attribute.graphQLFieldName} does not exist on table $outerTable")
        }

    val subSelects = relations.map { subRelation ->
        val innerTable = getTableWithAlias(subRelation as InternalQueryNode.Relation)
        resolveInternalQueryTree(
            subRelation, WhereCondition.getForRelationship(subRelation.graphQLFieldName, outerTable, innerTable)
        )
    }

    val (paginationArgument, otherArguments) = relation.arguments.partition { it.name == "first" }
    val (orderByArgument, otherArguments2) = otherArguments.partition { it.name == "orderBy" }
    val (afterArgument, filterArguments) = otherArguments2.partition { it.name == "after" }

    val limit = (paginationArgument.firstOrNull()?.value as IntValue?)?.value

    val providedOrderCriteria =
        (orderByArgument.firstOrNull()?.value as ObjectValue?)?.objectFields?.associate {
            outerTable.field(it.name.lowercase())!! to
                    (it.value as EnumValue).name
        }.orEmpty()
    val primaryKeyFields = outerTable.primaryKey?.fields?.map { outerTable.field(it)!! }.orEmpty()

    val orderByFields = providedOrderCriteria.keys + (primaryKeyFields - providedOrderCriteria.keys).toSet()
    val orderBy = orderByFields
        .map {
            when (providedOrderCriteria[it]) {
                "DESC" -> it.desc()
                else -> it.asc()
            }
        }
    val cursor = DSL.jsonObject(*orderByFields.toTypedArray()).cast(String::class.java).`as`("cursor")

    val afterCondition = afterArgument.firstOrNull()?.let { WhereCondition.getForAfterArgument(it, orderBy, outerTable) } ?: DSL.noCondition()

    val argumentConditions = filterArguments.map { WhereCondition.getForArgument(it, outerTable) }

    val cte =
        DSL.name("cte").`as`(
            DSL.select(attributeNames)
                .select(subSelects)
                .select(cursor)
                .select(DSL.count().over().`as`("count_after_cursor"))
                .from(outerTable)
                .where(argumentConditions)
                .and(joinCondition)
                .and(afterCondition)
                .orderBy(orderBy)
                .apply { if (limit != null) limit(limit) }
        )

    val totalCountSubquery =
        DSL.selectCount()
            .from(outerTable)
            .where(argumentConditions)
            .and(joinCondition)

    val endCursorSubquery =
        DSL.select(DSL.lastValue(DSL.field(cursor.name)).over())
            .from(cte)
            .limit(1)

    return DSL.field(
        DSL.with(cte).select(
            when {
                relation.connectionInfo != null -> {
                    DSL.jsonObject(
                        DSL.key("edges").value(
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
                                            DSL.key(it).value(DSL.field(cursor.name))
                                        }.toTypedArray()
                                    )
                                ),
                                DSL.jsonArray()
                            )

                        ),
                        *relation.connectionInfo.totalCountGraphQLAliases.map {
                            DSL.key(it).value(totalCountSubquery)
                        }.toTypedArray(),
                        *relation.connectionInfo.pageInfos.map { pageInfo ->
                            DSL.key(pageInfo.graphQLAlias).value(
                                DSL.jsonObject(
                                    *pageInfo.hasNextPageGraphQlAliases.map {
                                        DSL.key(it).value(
                                            when (limit) {
                                                null -> false
                                                BigInteger.ZERO -> true
                                                else -> DSL.max(DSL.field("count_after_cursor")).greaterThan(limit)
                                            }
                                        )
                                    }.toTypedArray(),
                                    *pageInfo.endCursorGraphQlAliases.map {
                                        DSL.key(it).value(endCursorSubquery)
                                    }.toTypedArray()
                                )
                            )
                        }.toTypedArray()
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
        ).from(cte)
    ).`as`(relation.graphQLAlias)
}

private fun getTableWithAlias(relation: InternalQueryNode.Relation) =
    PUBLIC.getTable(relation.fieldTypeInfo.relationName)?.`as`(relation.sqlAlias) ?: error("Table ${relation.fieldTypeInfo.relationName} not found")

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
