package byos

import db.jooq.generated.tables.Author
import db.jooq.generated.tables.Book
import db.jooq.generated.tables.Shoporder
import db.jooq.generated.tables.Shopuser
import db.jooq.generated.tables.Tree
import org.jooq.Condition
import org.jooq.Table
import org.jooq.impl.DSL

object WhereCondition {
    fun getFor(relationshipName: String, left: Table<*>, right: Table<*>): Condition =
        when {
            relationshipName == "author" && left is Book && right is Author -> DSL.condition(left.AUTHORID.eq(right.ID))
            relationshipName == "books" && left is Author && right is Book -> DSL.condition(right.AUTHORID.eq(left.ID))
            relationshipName == "user" && left is Shoporder && right is Shopuser -> DSL.condition(right.USER_ID.eq(left.USER_ID))
            relationshipName == "orders" && left is Shopuser && right is Shoporder -> DSL.condition(right.USER_ID.eq(left.USER_ID))
            relationshipName == "children" && left is Tree && right is Tree -> DSL.condition(left.ID.eq(right.PARENT_ID))
            relationshipName == "parent" && left is Tree && right is Tree -> DSL.condition(right.ID.eq(left.PARENT_ID))
            else -> error("No relationship called $relationshipName found for tables $left and $right")
        }
}
