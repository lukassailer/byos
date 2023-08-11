package byos

import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType

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
