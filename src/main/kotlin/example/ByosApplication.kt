package example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ByosApplication

fun main(args: Array<String>) {
    runApplication<ByosApplication>(*args)
}
