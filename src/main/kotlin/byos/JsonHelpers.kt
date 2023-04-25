package byos

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

const val OBJECT_SUFFIX = "-object"

fun unwrapSingletonArrays(json: String): String {
    val mapper = ObjectMapper()
    val rootNode = mapper.readTree(json)
    unwrapSingletonArraysRecursively(rootNode)
    return mapper.writeValueAsString(rootNode)
}

private fun unwrapSingletonArraysRecursively(node: JsonNode): JsonNode =
    when (node) {
        is ObjectNode -> {
            node.fieldNames().asSequence()
                .associateBy(
                    { fieldName -> fieldName.substringBeforeLast(OBJECT_SUFFIX) },
                    { fieldName ->
                        val fieldNode = node.get(fieldName)
                        if (fieldName.endsWith(OBJECT_SUFFIX) && fieldNode.isArray) {
                            unwrapSingletonArray(fieldNode)
                        } else {
                            unwrapSingletonArraysRecursively(fieldNode)
                        }
                    })
                .let { node.removeAll().setAll(it) }
        }

        is ArrayNode -> {
            node.map { unwrapSingletonArraysRecursively(it) }.let {
                node.removeAll().addAll(it)
            }
        }

        else -> node
    }

private fun unwrapSingletonArray(fieldNode: JsonNode) =
    when (fieldNode.size()) {
        0 -> null
        1 -> fieldNode.elements().next()
        else -> error("Expected singleton or empty array, got ${fieldNode.size()} elements")
    }
