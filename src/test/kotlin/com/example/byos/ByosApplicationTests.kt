package com.example.byos

import graphql.parser.Parser
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ByosApplicationTests {

    @Test
    fun contextLoads() {
    }

    @Test
    fun graphQLTest() {
        val query = """
            query {
              allAuthors {
                lastName
                books {
                  name
                }
              }
            }
        """.trimIndent()

        val parser = Parser()
        val document = parser.parseDocument(query)
        println(document)

//        Document {
//            definitions = [OperationDefinition {
//                name = 'null', operation = QUERY, variableDefinitions = [], directives = [], selectionSet = SelectionSet{
//                selections = [Field {
//                    name = 'allAuthors', alias = 'null', arguments = [], directives = [], selectionSet = SelectionSet{
//                    selections = [Field { name = 'lastName', alias = 'null', arguments = [], directives = [], selectionSet = null }, Field {
//                        name = 'books', alias = 'null', arguments = [], directives = [], selectionSet = SelectionSet{
//                        selections = [Field { name = 'name', alias = 'null', arguments = [], directives = [], selectionSet = null }]
//                    }
//                    }]
//                }
//                }]
//            }
//            }]
//        }
    }

}
