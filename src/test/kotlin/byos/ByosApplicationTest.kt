package byos

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

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

        assertEqualsIgnoringWhitespace(expectedResult, result)
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

        assertEqualsIgnoringWhitespace(expectedResult, result)
    }

    // Note: this also ignores whitespaces inside strings
    private fun assertEqualsIgnoringWhitespace(expectedResult: String, result: String) =
        assertEquals(
            expectedResult.filterNot { it.isWhitespace() },
            result.filterNot { it.isWhitespace() }
        )

}
