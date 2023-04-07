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
    fun graphQLTest() {
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
            ctx.selectFrom(resolveTree(tree)).fetch()
        }.formatJSON() // TODO format correctly

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
        """.filterNot { it.isWhitespace() }

        assertEquals(expectedResult, result)
    }

}
