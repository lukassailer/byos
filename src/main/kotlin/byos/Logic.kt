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

sealed interface InternalQueryNode {
    data class Relation(val title: String, val children: List<InternalQueryNode>, val fieldTypeInfo: FieldTypeInfo, val alias: String) : InternalQueryNode
    data class Attribute(val title: String) : InternalQueryNode
}

fun buildInternalQueryTree(queryDefinition: OperationDefinition): InternalQueryNode.Relation =
    getChildrenFromSelectionSet(queryDefinition.selectionSet)[0] as InternalQueryNode.Relation

private fun getChildrenFromSelectionSet(selectionSet: SelectionSet): List<InternalQueryNode> =
    selectionSet.selections.mapNotNull { selection ->
        val subSelectionSet = (selection as GraphQLField).selectionSet
        if (subSelectionSet == null) {
            InternalQueryNode.Attribute(selection.name)
        } else {
            val fieldTypeInfo = getFieldTypeInfo(schema, selection.name)
            InternalQueryNode.Relation(selection.name, getChildrenFromSelectionSet(subSelectionSet), fieldTypeInfo, UUID.randomUUID().toString())
        }
    }

fun resolveInternalQueryTree(relation: InternalQueryNode.Relation, condition: Condition = DSL.noCondition()): Field<Result<Record>> {
    val (relations, attributes) = relation.children.partition { it is InternalQueryNode.Relation }
    val attributeNames = attributes.map { (it as InternalQueryNode.Attribute).title }.map { DSL.field(it) }

    val outerTable = (PUBLIC.getTable(relation.fieldTypeInfo.relationName) ?: error("Table not found")).`as`(relation.alias)
    val subSelects = relations.map {
        // TODO DEFAULT_CATALOG.schemas durchsuchen anstatt PUBLIC?

        val innerTable = (PUBLIC.getTable((it as InternalQueryNode.Relation).fieldTypeInfo.relationName) ?: error("Table not found")).`as`(it.alias)
        resolveInternalQueryTree(
            it, WhereCondition.getFor(it.title, outerTable, innerTable)
        )
    }

    return DSL.multiset(
        DSL.select(attributeNames)
            .select(subSelects)
            .from(outerTable)
            .where(condition)
    ).`as`(relation.title + if (relation.fieldTypeInfo.isList) "" else OBJECT_SUFFIX)
}

fun getFieldTypeInfo(schema: GraphQLSchema, fieldName: String): FieldTypeInfo {
    // TODO: search only on specific type
    for (type in schema.allTypesAsList) {
        when (type) {
            is GraphQLObjectType -> {
                val fieldDef = type.getFieldDefinition(fieldName)
                if (fieldDef != null) {
                    return getTypeInfo(fieldDef.type, false)
                }
            }
        }
    }
    error("Field '$fieldName' not found in schema")
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

data class FieldTypeInfo(private val _relationName: String, val isList: Boolean) {
    val relationName = _relationName.lowercase()
}
