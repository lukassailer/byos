package byos

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions


fun assertEqualsIgnoringOrder(expected: String, actual: String) {
    val mapper = ObjectMapper()
    val expectedJson = mapper.readTree(expected)
    val actualJson = mapper.readTree(actual)
    Assertions.assertTrue(
        compareJsonNodes(expectedJson, actualJson),
        "Expected: ${expectedJson.toPrettyString()}\nActual: ${actualJson.toPrettyString()}"
    )
}

// compare two json nodes ignoring order of elements in arrays
fun compareJsonNodes(node1: JsonNode, node2: JsonNode): Boolean {
    if (node1.isArray && node2.isArray) {
        if (node1.size() != node2.size()) {
            return false
        }
        val visited = mutableSetOf<Int>()
        for (i in 0 until node1.size()) {
            var found = false
            for (j in 0 until node2.size()) {
                if (j in visited) {
                    continue
                }
                if (compareJsonNodes(node1[i], node2[j])) {
                    visited.add(j)
                    found = true
                    break
                }
            }
            if (!found) {
                return false
            }
        }
        return true
    } else if (node1.isObject && node2.isObject) {
        if (node1.size() != node2.size()) {
            return false
        }
        for ((key, value) in node1.fields()) {
            if (!node2.has(key) || !compareJsonNodes(value, node2[key])) {
                return false
            }
        }
        return true
    } else {
        return node1 == node2
    }
}
