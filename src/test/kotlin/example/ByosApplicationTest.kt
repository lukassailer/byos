package example

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [GraphQLService::class])
internal class ByosApplicationTest {
    @Autowired
    private lateinit var graphQLService: GraphQLService

    @Test
    fun simpleQuery() {
        val query = """
            query {
              allFilms(first: 3) {
                edges {
                  node {
                    film_id
                    title
                    release_year
                  }
                }
              }
            }
            """.trimIndent()
        val expectedResult = """
            {
              "data": {
                "allFilms": {
                  "edges": [
                    {
                      "node": {
                        "film_id": 1,
                        "title": "ACADEMY DINOSAUR",
                        "release_year": 2006
                      }
                    },
                    {
                      "node": {
                        "film_id": 2,
                        "title": "ACE GOLDFINGER",
                        "release_year": 2006
                      }
                    },
                    {
                      "node": {
                        "film_id": 3,
                        "title": "ADAPTATION HOLES",
                        "release_year": 2006
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        assertJsonEquals(expectedResult, graphQLService.executeGraphQLQuery(query))
    }

    @Test
    fun simpleQueryWithMoreDepth() {
        val query = """
            query {
              allFilms(first: 2) {
                edges {
                  node {
                    title
                    actors {
                      actor_id
                    }
                  }
                }
              }
            }
            """.trimIndent()
        val expectedResult = """
            {
              "data": {
                "allFilms": {
                  "edges": [
                    {
                      "node": {
                        "title": "ACADEMY DINOSAUR",
                        "actors": [
                          {
                            "actor_id": 1
                          },
                          {
                            "actor_id": 10
                          },
                          {
                            "actor_id": 20
                          },
                          {
                            "actor_id": 30
                          },
                          {
                            "actor_id": 40
                          },
                          {
                            "actor_id": 53
                          },
                          {
                            "actor_id": 108
                          },
                          {
                            "actor_id": 162
                          },
                          {
                            "actor_id": 188
                          },
                          {
                            "actor_id": 198
                          }
                        ]
                      }
                    },
                    {
                      "node": {
                        "title": "ACE GOLDFINGER",
                        "actors": [
                          {
                            "actor_id": 19
                          },
                          {
                            "actor_id": 85
                          },
                          {
                            "actor_id": 90
                          },
                          {
                            "actor_id": 160
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        assertJsonEquals(expectedResult, graphQLService.executeGraphQLQuery(query))
    }

    @Test
    fun queryReturningObject() {
        val query = """
            query {
              filmById(film_id: 1) {
                title
              }
            }

            """.trimIndent()
        val expectedResult = """
            {
              "data": {
                "filmById": {
                  "title": "ACADEMY DINOSAUR"
                }
              }
            }

            """.trimIndent()
        assertJsonEquals(expectedResult, graphQLService.executeGraphQLQuery(query))
    }

    @Test
    fun queryReturningNull() {
        val query = """
            query {
              filmById(film_id: 1) {
                original_language {
                  name
                }
              }
            }
            """.trimIndent()
        val expectedResult = """
            {
              "data": {
                "filmById": {
                  "original_language": null
                }
              }
            }
            """.trimIndent()
        assertJsonEquals(expectedResult, graphQLService.executeGraphQLQuery(query))
    }

    @Test
    fun queryWithSelfRelation() {
        val query = """
            {
              allCategories(first: 3) {
                edges {
                  node {
                    name
                    parent_category {
                      name
                    }
                    subcategories {
                      edges {
                        node {
                          name
                        }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()
        val expectedResult = """
            {
              "data": {
                "allCategories": {
                  "edges": [
                    {
                      "node": {
                        "name": "Action",
                        "parent_category": null,
                        "subcategories": {
                          "edges": [
                            {
                              "node": {
                                "name": "Sci-Fi"
                              }
                            }
                          ]
                        }
                      }
                    },
                    {
                      "node": {
                        "name": "Animation",
                        "parent_category": null,
                        "subcategories": {
                          "edges": [
                            {
                              "node": {
                                "name": "Children"
                              }
                            },
                            {
                              "node": {
                                "name": "Classics"
                              }
                            }
                          ]
                        }
                      }
                    },
                    {
                      "node": {
                        "name": "Children",
                        "parent_category": {
                          "name": "Animation"
                        },
                        "subcategories": {
                          "edges": [
                            {
                              "node": {
                                "name": "Family"
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
            """.trimIndent()
        assertJsonEquals(expectedResult, graphQLService.executeGraphQLQuery(query))
    }

    @Test
    fun queryWithAlias() {
        val query = """
            query {
              movie2: filmById(film_id: 2) {
                id: film_id
                film_id
                star: actors {
                  aid: actor_id
                }
              }
            }
            """.trimIndent()
        val expectedResult = """
            {
              "data": {
                "movie2": {
                  "id": 2,
                  "film_id": 2,
                  "star": [
                    {
                      "aid": 19
                    },
                    {
                      "aid": 85
                    },
                    {
                      "aid": 90
                    },
                    {
                      "aid": 160
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        assertJsonEquals(expectedResult, graphQLService.executeGraphQLQuery(query))
    }

    @Test
    fun multipleQueries() {
        val query = """
            {
              a1: actorById(actor_id: 1) {
                last_name
              }
              a2: actorById(actor_id: 2) {
                last_name
              }
            }
            """.trimIndent()
        val expectedResult = """
            {
              "data": {
                "a1": {
                  "last_name": "GUINESS"
                },
                "a2": {
                  "last_name": "WAHLBERG"
                }
              }
            }
            """.trimIndent()
        assertJsonEquals(expectedResult, graphQLService.executeGraphQLQuery(query))
    }

    @Test
    fun queryWithArgument() {
        val query = """
            query {
              actorById(actor_id: 1) {
                actor_id
                last_name
              }
            }
            
            """.trimIndent()
        val expectedResult = """
            {
              "data": {
                "actorById": {
                  "actor_id": 1,
                  "last_name": "GUINESS"
                }
              }
            }
            """.trimIndent()
        assertJsonEquals(expectedResult, graphQLService.executeGraphQLQuery(query))
    }

    @Test
    fun nToMRelationTwoWays() {
        val query = """
            {
              allStores {
                edges {
                  node {
                    store_id
                    films(first: 2) {
                      edges {
                        node {
                          film_id
                        }
                      }
                    }
                    inventories(first: 2) {
                      edges {
                        node {
                          film {
                            film_id
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()
        val expectedResult = """
            {
              "data": {
                "allStores": {
                  "edges": [
                    {
                      "node": {
                        "store_id": 1,
                        "films": {
                          "edges": [
                            {
                              "node": {
                                "film_id": 1
                              }
                            },
                            {
                              "node": {
                                "film_id": 4
                              }
                            }
                          ]
                        },
                        "inventories": {
                          "edges": [
                            {
                              "node": {
                                "film": {
                                  "film_id": 1
                                }
                              }
                            },
                            {
                              "node": {
                                "film": {
                                  "film_id": 1
                                }
                              }
                            }
                          ]
                        }
                      }
                    },
                    {
                      "node": {
                        "store_id": 2,
                        "films": {
                          "edges": [
                            {
                              "node": {
                                "film_id": 1
                              }
                            },
                            {
                              "node": {
                                "film_id": 2
                              }
                            }
                          ]
                        },
                        "inventories": {
                          "edges": [
                            {
                              "node": {
                                "film": {
                                  "film_id": 1
                                }
                              }
                            },
                            {
                              "node": {
                                "film": {
                                  "film_id": 1
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
        """.trimIndent()
        assertJsonEquals(expectedResult, graphQLService.executeGraphQLQuery(query))
    }

    @Test
    fun queryWithFirstLimit() {
        val query = """
            query {
              allFilms(first: 1) {
                edges {
                  node {
                    film_id
                    actors {
                    actor_id
                      films(release_year: 2006, first: 1) {
                        edges {
                          node {
                            film_id
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
            """.trimIndent()
        val expectedResult = """
            {
              "data": {
                "allFilms": {
                  "edges": [
                    {
                      "node": {
                        "film_id": 1,
                        "actors": [
                          {
                            "actor_id": 1,
                            "films": {
                              "edges": [
                                {
                                  "node": {
                                    "film_id": 1
                                  }
                                }
                              ]
                            }
                          },
                          {
                            "actor_id": 10,
                            "films": {
                              "edges": [
                                {
                                  "node": {
                                    "film_id": 1
                                  }
                                }
                              ]
                            }
                          },
                          {
                            "actor_id": 20,
                            "films": {
                              "edges": [
                                {
                                  "node": {
                                    "film_id": 1
                                  }
                                }
                              ]
                            }
                          },
                          {
                            "actor_id": 30,
                            "films": {
                              "edges": [
                                {
                                  "node": {
                                    "film_id": 1
                                  }
                                }
                              ]
                            }
                          },
                          {
                            "actor_id": 40,
                            "films": {
                              "edges": [
                                {
                                  "node": {
                                    "film_id": 1
                                  }
                                }
                              ]
                            }
                          },
                          {
                            "actor_id": 53,
                            "films": {
                              "edges": [
                                {
                                  "node": {
                                    "film_id": 1
                                  }
                                }
                              ]
                            }
                          },
                          {
                            "actor_id": 108,
                            "films": {
                              "edges": [
                                {
                                  "node": {
                                    "film_id": 1
                                  }
                                }
                              ]
                            }
                          },
                          {
                            "actor_id": 162,
                            "films": {
                              "edges": [
                                {
                                  "node": {
                                    "film_id": 1
                                  }
                                }
                              ]
                            }
                          },
                          {
                            "actor_id": 188,
                            "films": {
                              "edges": [
                                {
                                  "node": {
                                    "film_id": 1
                                  }
                                }
                              ]
                            }
                          },
                          {
                            "actor_id": 198,
                            "films": {
                              "edges": [
                                {
                                  "node": {
                                    "film_id": 1
                                  }
                                }
                              ]
                            }
                          }
                        ]
                      },
                      "cursor": "{\"film_id\" : 1}"
                    }
                  ],
                  "totalCount": 1000
                }
              }
            }
            """.trimIndent()
        assertJsonEquals(expectedResult, graphQLService.executeGraphQLQuery(query))
    }

    @Test
    fun queryWithCustomOrder() {
        val query = """
            {
              allFilms(first: 3, orderBy: {title: ASC}) {
                edges {
                  node {
                    film_id
                    title
                  }
                  cursor
                }
              }
            }
            """.trimIndent()
        val expectedResult = """
            {
              "data": {
                "allFilms": {
                  "edges": [
                    {
                      "node": {
                        "film_id": 1,
                        "title": "ACADEMY DINOSAUR"
                      },
                      "cursor": "{\"title\" : \"ACADEMY DINOSAUR\", \"film_id\" : 1}"
                    },
                    {
                      "node": {
                        "film_id": 2,
                        "title": "ACE GOLDFINGER"
                      },
                      "cursor": "{\"title\" : \"ACE GOLDFINGER\", \"film_id\" : 2}"
                    },
                    {
                      "node": {
                        "film_id": 3,
                        "title": "ADAPTATION HOLES"
                      },
                      "cursor": "{\"title\" : \"ADAPTATION HOLES\", \"film_id\" : 3}"
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        assertJsonEquals(expectedResult, graphQLService.executeGraphQLQuery(query))
    }

    @Test
    fun orderByMultipleFieldsAndUseCursor() {
        val query = """
            {
              allFilms (orderBy: {release_year: ASC, title: DESC, film_id: ASC}, after: "{\"release_year\" : 2006, \"title\" : \"AFFAIR PREJUDICE\", \"film_id\" : 4}") {
                edges {
                  node {
                    title
                  }
                  cursor
                }
                totalCount
              }
            }
            """.trimIndent()
        val expectedResult = """
            {
              "data": {
                "allFilms": {
                  "edges": [
                    {
                      "node": {
                        "title": "ADAPTATION HOLES"
                      },
                      "cursor": "{\"release_year\" : 2006, \"title\" : \"ADAPTATION HOLES\", \"film_id\" : 3}"
                    },
                    {
                      "node": {
                        "title": "ACE GOLDFINGER"
                      },
                      "cursor": "{\"release_year\" : 2006, \"title\" : \"ACE GOLDFINGER\", \"film_id\" : 2}"
                    },
                    {
                      "node": {
                        "title": "ACADEMY DINOSAUR"
                      },
                      "cursor": "{\"release_year\" : 2006, \"title\" : \"ACADEMY DINOSAUR\", \"film_id\" : 1}"
                    }
                  ],
                  "totalCount": 1000
                }
              }
            }
            """.trimIndent()
        assertJsonEquals(expectedResult, graphQLService.executeGraphQLQuery(query))
    }
}
