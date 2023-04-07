package byos

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean

@SpringBootApplication
class ByosApplication

fun main(args: Array<String>) {
    runApplication<ByosApplication>(*args)
}

@Bean
fun graphiQLFilter(): FilterRegistrationBean<GraphiQLFilter> {
    val registrationBean = FilterRegistrationBean<GraphiQLFilter>()
    registrationBean.filter = GraphiQLFilter()
    registrationBean.addUrlPatterns("/graphql")
    return registrationBean
}
