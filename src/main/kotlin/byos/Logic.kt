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
        val arguments: List<Argument>
    ) : InternalQueryNode(graphQLFieldName, graphQLAlias)

    class Attribute(graphQLFieldName: String, graphQLAlias: String) : InternalQueryNode(graphQLFieldName, graphQLAlias)
}

data class FieldTypeInfo(private val fieldName: String, val isList: Boolean) {
    val relationName = fieldName.lowercase()
}

fun buildInternalQueryTree(queryDefinition: OperationDefinition): List<InternalQueryNode.Relation> =
    getChildrenFromSelectionSet(queryDefinition.selectionSet).map { it as InternalQueryNode.Relation }

private fun getChildrenFromSelectionSet(selectionSet: SelectionSet): List<InternalQueryNode> =
    selectionSet.selections
        .filterIsInstance<GraphQLField>()
        .map { selection ->
            val subSelectionSet = selection.selectionSet
            if (subSelectionSet == null) {
                InternalQueryNode.Attribute(
                    graphQLFieldName = selection.name,
                    graphQLAlias = selection.alias ?: selection.name
                )
            } else {
                InternalQueryNode.Relation(
                    graphQLFieldName = selection.name,
                    graphQLAlias = selection.alias ?: selection.name,
                    sqlAlias = "${selection.name}-${UUID.randomUUID()}",
                    fieldTypeInfo = getFieldTypeInfo(schema, selection.name),
                    children = getChildrenFromSelectionSet(subSelectionSet),
                    arguments = selection.arguments
                )
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

    return if (relation.fieldTypeInfo.isList) {
        DSL.field(
            DSL.select(
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
            ).from(
                DSL.select(attributeNames)
                    .select(subSelects)
                    .from(outerTable)
                    .where(relation.arguments.map { WhereCondition.getForArgument(it, outerTable) })
                    .and(joinCondition)
                    .orderBy(outerTable.primaryKey?.fields?.map { outerTable.field(it) })
            )
        ).`as`(relation.graphQLAlias)
    } else {
        DSL.field(
            DSL.select(
                DSL.jsonObject(
                    *attributeNames.toTypedArray(),
                    *subSelects.toTypedArray()
                )
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
}

private fun getTableWithAlias(relation: InternalQueryNode.Relation) =
    PUBLIC.getTable(relation.fieldTypeInfo.relationName)?.`as`(relation.sqlAlias) ?: error("Table not found")

fun getFieldTypeInfo(schema: GraphQLSchema, fieldName: String): FieldTypeInfo {
    // TODO: search only on specific type
    schema.allTypesAsList
        .filterIsInstance<GraphQLObjectType>()
        .flatMap { it.fieldDefinitions }
        .find { it.name == fieldName }
        ?.type
        ?.let { return getTypeInfo(it, false) }
        ?: error("Field '$fieldName' not found in schema")
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
