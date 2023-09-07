package example

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions


// Order and WhiteSpace are not important
fun assertJsonEquals(expected: String, actual: String) {
    val mapper = ObjectMapper()
    val expectedJson = mapper.readTree(expected)
    val actualJson = mapper.readTree(actual)
    Assertions.assertEquals(expectedJson, actualJson)
}
