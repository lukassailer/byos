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
            edges {
              node {
                id
                title
                publishedin
              }
            }
          }
        }
        """

        val result = graphQLService.executeGraphQLQuery(query)

        val expectedResult = """
            {
              "data": {
                "allBooks": {
                  "edges": [
                    {
                      "node": {
                        "id": 1,
                        "title": "1984",
                        "publishedin": 1948
                      }
                    },
                    {
                      "node": {
                        "id": 2,
                        "title": "Animal Farm",
                        "publishedin": 1945
                      }
                    },
                    {
                      "node": {
                        "id": 3,
                        "title": "O Alquimista",
                        "publishedin": 1988
                      }
                    },
                    {
                      "node": {
                        "id": 4,
                        "title": "Brida",
                        "publishedin": 1990
                      }
                    }
                  ]
                }
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
              edges {
                node {
                  title
                }
              }
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
                "books": {
                  "edges": [
                    {
                      "node": {
                        "title": "1984"
                      }
                    },
                    {
                      "node": {
                        "title": "Animal Farm"
                      }
                    }
                  ]
                }
              },
              {
                "lastName": "Coelho",
                "books": {
                  "edges": [
                    {
                      "node": {
                        "title": "O Alquimista"
                      }
                    },
                    {
                      "node": {
                        "title": "Brida"
                      }
                    }
                  ]
                }
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
            edges {
              node {
                order_id
                user {
                  user_id
                }
              }
            }
          }
        }
        """

        val result = graphQLService.executeGraphQLQuery(query)

        val expectedResult = """
            {
              "data": {
                "allOrders": {
                  "edges": [
                    {
                      "node": {
                        "order_id": 1,
                        "user": null
                      }
                    },
                    {
                      "node": {
                        "order_id": 2,
                        "user": {
                          "user_id": 1
                        }
                      }
                    },
                    {
                      "node": {
                        "order_id": 3,
                        "user": {
                          "user_id": 1
                        }
                      }
                    },
                    {
                      "node": {
                        "order_id": 4,
                        "user": {
                          "user_id": 2
                        }
                      }
                    }
                  ]
                }
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
            edges {
              node {
                label
                parent {
                  label
                }
                children {
                  edges {
                    node {
                      label
                    }
                  }
                }
              }
            }
          }
        }
        """

        val result = graphQLService.executeGraphQLQuery(query)

        val expectedResult = """
            {
              "data": {
                "allTrees": {
                  "edges": [
                    {
                      "node": {
                        "label": "A",
                        "parent": null,
                        "children": {
                            "edges": [
                                { "node": { "label": "B" } },
                                { "node": { "label": "C" } }
                            ]
                        }
                      }
                    },
                    {
                      "node": {
                        "label": "B",
                        "parent": {
                          "label": "A"
                        },
                        "children": {
                            "edges": [
                                { "node": { "label": "D" } },
                                { "node": { "label": "E" } }
                            ]
                        }
                      }
                    },
                    {
                      "node": {
                        "label": "C",
                        "parent": {
                          "label": "A"
                        },
                        "children": {
                            "edges": [
                                { "node": { "label": "F" } }
                            ]
                        }
                      }
                    },
                    {
                      "node": {
                        "label": "D",
                        "parent": {
                          "label": "B"
                        },
                        "children": {
                          "edges": []
                        }
                      }
                    },
                    {
                      "node": {
                        "label": "E",
                        "parent": {
                          "label": "B"
                        },
                        "children": {
                          "edges": []
                        }
                      }
                    },
                    {
                      "node": {
                        "label": "F",
                        "parent": {
                          "label": "C"
                        },
                        "children": {
                          "edges": []
                        }
                      }
                    }
                  ]
                }
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
            edges {
              node {
                nid: id
                id
                writer: author {
                  id: id
                }
              }
            }
          }
        }
        """

        val result = graphQLService.executeGraphQLQuery(query)

        val expectedResult = """
            {
              "data": {
                "novel": {
                  "edges": [
                    {
                      "node": {
                        "nid": 1,
                        "id": 1,
                        "writer": {
                          "id": 1
                        }
                      }
                    },
                    {
                      "node": {
                        "nid": 2,
                        "id": 2,
                        "writer": {
                          "id": 1
                        }
                      }
                    },
                    {
                      "node": {
                        "nid": 3,
                        "id": 3,
                        "writer": {
                          "id": 2
                        }
                      }
                    },
                    {
                      "node": {
                        "nid": 4,
                        "id": 4,
                        "writer": {
                          "id": 2
                        }
                      }
                    }
                  ]
                }
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
            edges {
              node {
                name
                books {
                  edges {
                    node {
                      title
                    }
                  }
                }
                b2b {
                  edges {
                    node {
                      stock
                      book {
                        title
                      }
                    }
                  }
                }
              }
            }
          }
        }
        """

        val result = graphQLService.executeGraphQLQuery(query)

        val expectedResult = """
        {
          "data": {
            "allBookStores": {
              "edges": [
                {
                  "node": {
                    "name": "Orell Füssli",
                    "books": {
                      "edges": [
                        {
                          "node": {
                            "title": "1984"
                          }
                        },
                        {
                          "node": {
                            "title": "Animal Farm"
                          }
                        },
                        {
                          "node": {
                            "title": "O Alquimista"
                          }
                        }
                      ]
                    },
                    "b2b": {
                      "edges": [
                        {
                          "node": {
                            "stock": 10,
                            "book": {
                              "title": "1984"
                            }
                          }
                        },
                        {
                          "node": {
                            "stock": 10,
                            "book": {
                              "title": "Animal Farm"
                            }
                          }
                        },
                        {
                          "node": {
                            "stock": 10,
                            "book": {
                              "title": "O Alquimista"
                            }
                          }
                        }
                      ]
                    }
                  }
                },
                {
                  "node": {
                    "name": "Ex Libris",
                    "books": {
                      "edges": [
                        {
                          "node": {
                            "title": "1984"
                          }
                        },
                        {
                          "node": {
                            "title": "O Alquimista"
                          }
                        }
                      ]
                    },
                    "b2b": {
                      "edges": [
                        {
                          "node": {
                            "stock": 1,
                            "book": {
                              "title": "1984"
                            }
                          }
                        },
                        {
                          "node": {
                            "stock": 2,
                            "book": {
                              "title": "O Alquimista"
                            }
                          }
                        }
                      ]
                    }
                  }
                },
                {
                  "node": {
                    "name": "Buchhandlung im Volkshaus",
                    "books": {
                      "edges": [
                        {
                          "node": {
                            "title": "O Alquimista"
                          }
                        }
                      ]
                    },
                    "b2b": {
                      "edges": [
                        {
                          "node": {
                            "stock": 1,
                            "book": {
                              "title": "O Alquimista"
                            }
                          }
                        }
                      ]
                    }
                  }
                }
              ]
            }
          }
        }
        """

        assertJsonEquals(expectedResult, result)
    }

    @Test
    fun `query with first limit`() {
        val query = """
            query {
              allBooks(first: 1) {
                edges {
                  node {
                    id
                    author {
                      books(publishedin: 1945, first: 1) {
                        edges {
                          node {
                            id
                          }
                        }
                      }
                    }
                  }
                  cursor
                }
                totalCount
              }
            }
        """

        val result = graphQLService.executeGraphQLQuery(query)

        val expectedResult = """
            {
              "data": {
                "allBooks": {
                  "edges": [
                    {
                      "node": {
                        "id": 1,
                        "author": {
                          "books": {
                            "edges": [
                              {
                                "node": {
                                  "id": 2
                                }
                              }
                            ]
                          }
                        }
                      },
                      "cursor" : "{\"id\" : 1}"
                    }
                  ],
                  "totalCount": 4
                }
              }
            }
        """

        assertJsonEquals(expectedResult, result)
    }

    @Test
    fun `query with custom order`() {
        val query = """
            query {
              allBooks(orderBy:{title: ASC } ){
                edges{
                   node{
                    id
                    title
                  }
                  cursor
                }
              }
            }
        """

        val result = graphQLService.executeGraphQLQuery(query)

        val expectedResult = """
            {
               "data":{
                  "allBooks":{
                     "edges":[
                        {
                           "node":{
                              "id":1,
                              "title":"1984"
                           },
                           "cursor":"{\"title\" : \"1984\", \"id\" : 1}"
                        },
                        {
                           "node":{
                              "id":2,
                              "title":"Animal Farm"
                           },
                           "cursor":"{\"title\" : \"Animal Farm\", \"id\" : 2}"
                        },
                        {
                           "node":{
                              "id":4,
                              "title":"Brida"
                           },
                           "cursor":"{\"title\" : \"Brida\", \"id\" : 4}"
                        },
                        {
                           "node":{
                              "id":3,
                              "title":"O Alquimista"
                           },
                           "cursor":"{\"title\" : \"O Alquimista\", \"id\" : 3}"
                        }
                     ]
                  }
               }
            }
        """

        assertJsonEquals(expectedResult, result)
    }

    @Test
    fun `order by multiple fields and use cursor`() {
        val query = """
            query {
              allProducts(
                orderBy: {category: ASC, price: DESC}
                after: "{\"category\" : \"Category 1\", \"price\" : 10.99, \"id\" : 1}"
              ) {
                edges {
                  node {
                    id
                    name
                    price
                    category
                  }
                  cursor
                }
                totalCount
              }
            }
        """

        val result = graphQLService.executeGraphQLQuery(query)

        val expectedResult = """
            {
              "data": {
                "allProducts": {
                  "edges": [
                    {
                      "node": {
                        "id": 2,
                        "name": "Product B",
                        "price": 10.99,
                        "category": "Category 1"
                      },
                      "cursor": "{\"category\" : \"Category 1\", \"price\" : 10.99, \"id\" : 2}"
                    },
                    {
                      "node": {
                        "id": 5,
                        "name": "Product E",
                        "price": 9.99,
                        "category": "Category 1"
                      },
                      "cursor": "{\"category\" : \"Category 1\", \"price\" : 9.99, \"id\" : 5}"
                    },
                    {
                      "node": {
                        "id": 3,
                        "name": "Product C",
                        "price": 10.99,
                        "category": "Category 2"
                      },
                      "cursor": "{\"category\" : \"Category 2\", \"price\" : 10.99, \"id\" : 3}"
                    },
                    {
                      "node": {
                        "id": 6,
                        "name": "Product F",
                        "price": 7.99,
                        "category": "Category 2"
                      },
                      "cursor": "{\"category\" : \"Category 2\", \"price\" : 7.99, \"id\" : 6}"
                    }
                  ],
                  "totalCount": 6
                }
              }
            }
        """

        assertJsonEquals(expectedResult, result)
    }
}
