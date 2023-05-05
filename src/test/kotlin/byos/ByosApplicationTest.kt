package byos

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ByosApplicationTest(
    @Autowired
    private val graphQLService: GraphQLService
) {

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

        val result = graphQLService.executeGraphQLQuery(query)

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

        assertJsonEquals(expectedResult, result)
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

        val result = graphQLService.executeGraphQLQuery(query)

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

        assertJsonEquals(expectedResult, result)
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

        val result = graphQLService.executeGraphQLQuery(query)

        val expectedResult = """
            {
              "data": {
                "test": {
                  "value": "test"
                }
              }
            }
            """

        assertJsonEquals(expectedResult, result)
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

        val result = graphQLService.executeGraphQLQuery(query)

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

        assertJsonEquals(expectedResult, result)
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

        val result = graphQLService.executeGraphQLQuery(query)

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

        assertJsonEquals(expectedResult, result)
    }

    @Test
    fun `query with alias`() {
        val query = """
            query {
              novel: allBooks {
                nid: id
                id
                writer: author{
                  id: id
                }
              }
            }
        """

        val result = graphQLService.executeGraphQLQuery(query)

        val expectedResult = """
            {
              "data": {
                "novel": [
                  {
                    "nid": 1,
                    "id": 1,
                    "writer": {
                      "id": 1
                    }
                  },
                  {
                    "nid": 2,
                    "id": 2,
                    "writer": {
                      "id": 1
                    }
                  },
                  {
                    "nid": 3,
                    "id": 3,
                    "writer": {
                      "id": 2
                    }
                  },
                  {
                    "nid": 4,
                    "id": 4,
                    "writer": {
                      "id": 2
                    }
                  }
                ]
              }
            }
            """

        assertJsonEquals(expectedResult, result)
    }

    @Test
    fun `multiple queries?`() {
        val query = """
            query {
              test {value}
              test2: test {value}
            }
        """

        val result = graphQLService.executeGraphQLQuery(query)

        val expectedResult = """
            {
              "data": {
                "test": {
                  "value": "test"
                },
                "test2": {
                  "value": "test"
                }
              }
            }
            """

        assertJsonEquals(expectedResult, result)
    }

    @Test
    fun `query with argument`() {
        val query = """
            query {
              authorById(id: 1) {
                id
                lastName
              }
            }
        """

        val result = graphQLService.executeGraphQLQuery(query)

        val expectedResult = """
            {
              "data": {
                "authorById": {
                  "id": 1,
                  "lastName": "Orwell"
                }
              }
            }
            """

        assertJsonEquals(expectedResult, result)
    }

    @Test
    fun `n to m relation two ways`() {
        val query = """
            query {
              allBookStores {
                name
                books {
                  title
                }
                b2b {
                  stock
                  book {
                    title
                  }
                }
              }
            }
        """

        val result = graphQLService.executeGraphQLQuery(query)

        val expectedResult = """
            {
              "data": {
                "allBookStores": [
                  {
                    "name": "Orell Füssli",
                    "books": [
                      {
                        "title": "1984"
                      },
                      {
                        "title": "Animal Farm"
                      },
                      {
                        "title": "O Alquimista"
                      }
                    ],
                    "b2b": [
                      {
                        "stock": 10,
                        "book": {
                          "title": "1984"
                        }
                      },
                      {
                        "stock": 10,
                        "book": {
                          "title": "Animal Farm"
                        }
                      },
                      {
                        "stock": 10,
                        "book": {
                          "title": "O Alquimista"
                        }
                      }
                    ]
                  },
                  {
                    "name": "Ex Libris",
                    "books": [
                      {
                        "title": "1984"
                      },
                      {
                        "title": "O Alquimista"
                      }
                    ],
                    "b2b": [
                      {
                        "stock": 1,
                        "book": {
                          "title": "1984"
                        }
                      },
                      {
                        "stock": 2,
                        "book": {
                          "title": "O Alquimista"
                        }
                      }
                    ]
                  },
                  {
                    "name": "Buchhandlung im Volkshaus",
                    "books": [
                      {
                        "title": "O Alquimista"
                      }
                    ],
                    "b2b": [
                      {
                        "stock": 1,
                        "book": {
                          "title": "O Alquimista"
                        }
                      }
                    ]
                  }
                ]
              }
            }
        """

        assertJsonEquals(expectedResult, result)
    }
}
