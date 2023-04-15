package byos

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.schema.GraphQLList
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLType
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.io.File

@SpringBootTest
class ByosApplicationTest {

    @Test
    fun contextLoads() {
    }

    @Test
    fun `simple query`() {
        val query = """
            query {
              books {
                id
                title
                publishedin
              }
            }
        """

        val ast = parseASTFromQuery(query)
        val tree = buildTree(ast)
        val result = executeJooqQuery { ctx ->
            ctx.select(resolveTree(tree)).fetch()
        }.formatGraphQLResponse()

        val expectedResult = """
            {
              "data": {
                "books": [
                  {
                    "id": 1,
                    "title": "1984",
                    "publishedin": 1948
                  },
                  {
                    "id": 2,
                    "title": "Animal Farm",
                    "publishedin": 1945
                  },
                  {
                    "id": 3,
                    "title": "O Alquimista",
                    "publishedin": 1988
                  },
                  {
                    "id": 4,
                    "title": "Brida",
                    "publishedin": 1990
                  }
                ]
              }
            }
            """

        assertEqualsIgnoringOrder(expectedResult, result)
    }

    @Test
    fun `simple query with more depth`() {
        val query = """
            query {
              authors {
                lastName
                books {
                  title
                }
              }
            }
        """

        val ast = parseASTFromQuery(query)
        val tree = buildTree(ast)
        val result = executeJooqQuery { ctx ->
            ctx.select(resolveTree(tree)).fetch()
        }.formatGraphQLResponse()

        val expectedResult = """
            {
              "data": {
                "authors": [
                  {
                    "lastName": "Orwell",
                    "books": [
                      { 
                        "title": "1984"
                      },
                      {
                        "title": "Animal Farm"
                      }
                    ]
                  },
                  {
                    "lastName": "Coelho",
                    "books": [
                      {
                        "title": "O Alquimista"
                      },
                      {
                        "title": "Brida"
                      }
                    ]
                  }
                ]
              }
            }
            """

        assertEqualsIgnoringOrder(expectedResult, result)
    }

    // NOTE: [Author]! is considered an object, not an array
    @Test
    fun introspection() {
        val schemaFile = File("src/main/resources/graphql/schema.graphqls")
        val schema = SchemaGenerator().makeExecutableSchema(SchemaParser().parse(schemaFile), RuntimeWiring.newRuntimeWiring().build())
        val typeMap: Map<String, GraphQLType> = schema.typeMap

        for ((name, type) in typeMap) {
            println("$name:")
            if (type is GraphQLObjectType) {
                for (field in type.fieldDefinitions) {
                    if (field.type is GraphQLList) {
                        println("Field " + field.name + " returns an array")
                    } else {
                        println("Field " + field.name + " returns an object")
                    }
                }
            }
        }
    }

    fun assertEqualsIgnoringOrder(expected: String, actual: String) {
        val mapper = ObjectMapper()
        val expectedJson = mapper.readTree(expected)
        val actualJson = mapper.readTree(actual)
        assertTrue(compareJsonNodes(expectedJson, actualJson))
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

}
