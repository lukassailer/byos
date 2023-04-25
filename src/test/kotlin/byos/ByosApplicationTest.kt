package byos

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ByosApplicationTest {

    @Test
    fun `simple query`() {
        val query = """
            query {
              allBooks {
                id
                title
                publishedin
              }
            }
        """

        val ast = parseASTFromQuery(query)
        val tree = buildInternalQueryTree(ast)
        val result = executeJooqQuery { ctx ->
            ctx.select(resolveInternalQueryTree(tree)).fetch()
        }.formatGraphQLResponse()

        val expectedResult = """
            {
              "data": {
                "allBooks": [
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
              allAuthors {
                lastName
                books {
                  title
                }
              }
            }
        """

        val ast = parseASTFromQuery(query)
        val tree = buildInternalQueryTree(ast)
        val result = executeJooqQuery { ctx ->
            ctx.select(resolveInternalQueryTree(tree)).fetch()
        }.formatGraphQLResponse()

        val expectedResult = """
            {
              "data": {
                "allAuthors": [
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

    @Test
    fun `query returning object`() {
        val query = """
            query {
              test {
                value
              }
            }  
        """

        val ast = parseASTFromQuery(query)
        val tree = buildInternalQueryTree(ast)
        val result = executeJooqQuery { ctx ->
            ctx.select(resolveInternalQueryTree(tree)).fetch()
        }.formatGraphQLResponse()

        val expectedResult = """
            {
              "data": {
                "test": {
                  "value": "test"
                }
              }
            }
            """

        assertEqualsIgnoringOrder(expectedResult, result)
    }

    @Test
    fun `query returning null`() {
        val query = """
            query {
              allOrders {
                order_id
                user {
                  user_id
                }
              }
            }
        """

        val ast = parseASTFromQuery(query)
        val tree = buildInternalQueryTree(ast)
        val result = executeJooqQuery { ctx ->
            ctx.select(resolveInternalQueryTree(tree)).fetch()
        }.formatGraphQLResponse()

        val expectedResult = """
            {
              "data": {
                "allOrders": [
                  {
                    "order_id": 1,
                    "user": null
                  },
                  {
                    "order_id": 2,
                    "user": {
                      "user_id": 1
                    }
                  },
                  {
                    "order_id": 3,
                    "user": {
                      "user_id": 1
                    }
                  },
                  {
                    "order_id": 4,
                    "user": {
                      "user_id": 2
                    }
                  }
                ]
              }
            }
            """

        assertEqualsIgnoringOrder(expectedResult, result)
    }

    @Test
    fun `query with self-relation`() {
        /*
               A
             /   \
            B     C
           / \   /
          D   E F

         */
        val query = """
            query {
              allTrees {
                label
                parent {
                  label
                }
                children {
                  label
                }
              }
            }
        """

        val ast = parseASTFromQuery(query)
        val tree = buildInternalQueryTree(ast)
        val result = executeJooqQuery { ctx ->
            ctx.select(resolveInternalQueryTree(tree)).fetch()
        }.formatGraphQLResponse()

        val expectedResult = """
            {
              "data": {
                "allTrees": [
                  {
                    "label": "A",
                    "parent": null,
                    "children": [
                      {
                        "label": "B"
                      },
                      {
                        "label": "C"
                      }
                    ]
                  },
                  {
                    "label": "B",
                    "parent": {
                      "label": "A"
                    },
                    "children": [
                      {
                        "label": "D"
                      },
                      {
                        "label": "E"
                      }
                    ]
                  },
                  {
                    "label": "C",
                    "parent": {
                      "label": "A"
                    },
                    "children": [
                      {
                        "label": "F"
                      }
                    ]
                  },
                  {
                    "label": "D",
                    "parent": {
                      "label": "B"
                    },
                    "children": []
                  },
                  {
                    "label": "E",
                    "parent": {
                      "label": "B"
                    },
                    "children": []
                  },
                  {
                    "label": "F",
                    "parent": {
                      "label": "C"
                    },
                    "children": []
                  }
                ]
              }
            }
            """

        println(result)
        assertEqualsIgnoringOrder(expectedResult, result)
    }

}
