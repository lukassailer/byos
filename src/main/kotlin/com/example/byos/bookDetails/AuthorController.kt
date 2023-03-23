package com.example.byos.bookDetails

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class AuthorController {
    @QueryMapping
    fun authorById(@Argument id: String?): Author? {
        return Author.getById(id!!)
    }

    @QueryMapping
    fun allAuthors(): List<Author> {
        return Author.getAll()
    }

    @SchemaMapping
    fun books(author: Author): List<Book> {
        return Book.getAll().stream().filter { book: Book? ->
            book!!.authorId == author.id
        }.toList()
    }
}
