package byos

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions


fun assertJsonEquals(expected: String, actual: String) {
    val mapper = ObjectMapper()
    val expectedJson = mapper.readTree(expected)
    val actualJson = mapper.readTree(actual)
    Assertions.assertEquals(expectedJson, actualJson)
}
