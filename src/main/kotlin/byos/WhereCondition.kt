package byos

import db.jooq.generated.Tables.BOOK_TO_BOOKSTORE
import db.jooq.generated.tables.Author
import db.jooq.generated.tables.Book
import db.jooq.generated.tables.BookToBookstore
import db.jooq.generated.tables.Bookstore
import db.jooq.generated.tables.Shoporder
import db.jooq.generated.tables.Shopuser
import db.jooq.generated.tables.Tree
import graphql.language.Argument
import graphql.language.BooleanValue
import graphql.language.EnumValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.StringValue
import org.jooq.Condition
import org.jooq.Field
import org.jooq.Table
import org.jooq.impl.DSL

object WhereCondition {
    fun getForRelationship(relationshipName: String, left: Table<*>, right: Table<*>): Condition =
        when {
            relationshipName == "author" && left is Book && right is Author -> left.AUTHORID.eq(right.ID)
            relationshipName == "books" && left is Author && right is Book -> right.AUTHORID.eq(left.ID)
            relationshipName == "user" && left is Shoporder && right is Shopuser -> right.USER_ID.eq(left.USER_ID)
            relationshipName == "orders" && left is Shopuser && right is Shoporder -> right.USER_ID.eq(left.USER_ID)
            relationshipName == "children" && left is Tree && right is Tree -> left.ID.eq(right.PARENT_ID)
            relationshipName == "parent" && left is Tree && right is Tree -> right.ID.eq(left.PARENT_ID)
            relationshipName == "books" && left is Bookstore && right is Book -> DSL.exists(
                DSL.selectOne().from(BOOK_TO_BOOKSTORE).where(left.NAME.eq(BOOK_TO_BOOKSTORE.NAME).and(BOOK_TO_BOOKSTORE.BOOKID.eq(right.ID)))
            )

            relationshipName == "b2b" && left is Bookstore && right is BookToBookstore -> left.NAME.eq(right.NAME)
            relationshipName == "book" && left is BookToBookstore && right is Book -> left.BOOKID.eq(right.ID)

            else -> error("No relationship called $relationshipName found for tables $left and $right")
        }

    // TODO: Use GraphQL Schema to get the type of the field
    fun getForArgument(argument: Argument, table: Table<*>): Condition {
        val field = table.field(argument.name)
        if (field == null) {
            error("No field called ${argument.name} found for table $table")
        } else {
            return when (val value = argument.value) {
                is IntValue -> (field as Field<Any>).eq(value.value)
                is FloatValue -> (field as Field<Any>).eq(value.value)
                is BooleanValue -> (field as Field<Any>).eq(value.isValue)
                is StringValue -> (field as Field<Any>).eq(value.value)
                is EnumValue -> (field as Field<Any>).eq(value.name)
                is NullValue -> (field as Field<Any>).isNull
                else -> error("Unsupported argument type ${argument.value.javaClass}")
            }
        }
    }

}
