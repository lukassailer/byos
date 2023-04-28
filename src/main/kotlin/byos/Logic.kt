package byos

import db.jooq.generated.Public.PUBLIC
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
import org.jooq.Record
import org.jooq.Result
import org.jooq.impl.DSL
import java.io.File
import java.util.UUID
import graphql.language.Field as GraphQLField

private val schemaFile = File("src/main/resources/graphql/schema.graphqls")
private val schema: GraphQLSchema = SchemaGenerator().makeExecutableSchema(SchemaParser().parse(schemaFile), RuntimeWiring.newRuntimeWiring().build())

sealed class InternalQueryNode(val graphQLFieldName: String) {
    class Relation(graphQLFieldName: String, val children: List<InternalQueryNode>, val fieldTypeInfo: FieldTypeInfo, val alias: String) :
        InternalQueryNode(graphQLFieldName)

    class Attribute(graphQLFieldName: String) : InternalQueryNode(graphQLFieldName)
}

data class FieldTypeInfo(private val _relationName: String, val isList: Boolean) {
    val relationName = _relationName.lowercase()
}

fun buildInternalQueryTree(queryDefinition: OperationDefinition): InternalQueryNode.Relation =
    getChildrenFromSelectionSet(queryDefinition.selectionSet)[0] as InternalQueryNode.Relation

private fun getChildrenFromSelectionSet(selectionSet: SelectionSet): List<InternalQueryNode> =
    selectionSet.selections.mapNotNull { selection ->
        val subSelectionSet = (selection as GraphQLField).selectionSet
        if (subSelectionSet == null) {
            InternalQueryNode.Attribute(selection.name)
        } else {
            InternalQueryNode.Relation(
                graphQLFieldName = selection.name,
                children = getChildrenFromSelectionSet(subSelectionSet),
                fieldTypeInfo = getFieldTypeInfo(schema, selection.name),
                alias = "${selection.name}-${UUID.randomUUID()}"
            )
        }
    }

fun resolveInternalQueryTree(relation: InternalQueryNode.Relation, condition: Condition = DSL.noCondition()): Field<Result<Record>> {
    val (relations, attributes) = relation.children.partition { it is InternalQueryNode.Relation }
    val attributeNames = attributes.map { it.graphQLFieldName }.map { DSL.field(it) }
    // TODO DEFAULT_CATALOG.schemas durchsuchen anstatt PUBLIC?
    val outerTable = getTableWithAlias(relation)

    val subSelects = relations.map { subRelation ->
        val innerTable = getTableWithAlias(subRelation as InternalQueryNode.Relation)
        resolveInternalQueryTree(
            subRelation, WhereCondition.getFor(subRelation.graphQLFieldName, outerTable, innerTable)
        )
    }

    return DSL.multiset(
        DSL.select(attributeNames)
            .select(subSelects)
            .from(outerTable)
            .where(condition)
    ).`as`(relation.graphQLFieldName + if (relation.fieldTypeInfo.isList) "" else OBJECT_SUFFIX)
}

private fun getTableWithAlias(relation: InternalQueryNode.Relation) =
    PUBLIC.getTable(relation.fieldTypeInfo.relationName)?.`as`(relation.alias) ?: error("Table not found")

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
