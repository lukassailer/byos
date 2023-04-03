package com.example.byos

import graphql.language.Field
import graphql.language.OperationDefinition
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
              authors {
                lastName
                books {
                  name
                }
              }
            }
        """

        val parser = Parser()
        val document = parser.parseDocument(query)
        val queryDefinition = document.definitions[0] as OperationDefinition
        val selectionSet = queryDefinition.selectionSet
        val selections = selectionSet.selections
        selections.forEach { selection ->
            val field = selection as Field
            println(field.name)

            val subSelectionSet = field.selectionSet
            if (subSelectionSet != null) {
                val subSelections = subSelectionSet.selections
                subSelections.forEach { subSelection ->
                    val subField = subSelection as Field
                    println(subField.name)

                    val subSubSelectionSet = subField.selectionSet
                    if (subSubSelectionSet != null) {
                        val subSubSelections = subSubSelectionSet.selections
                        subSubSelections.forEach { subSubSelection ->
                            val subSubField = subSubSelection as Field
                            println(subSubField.name)
                        }
                    }
                }
            }
        }

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
