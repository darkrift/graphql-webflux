package com.example.demo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import graphql.ExecutionInput.newExecutionInput
import graphql.GraphQL
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters.fromResource
import org.springframework.web.reactive.function.server.*
import org.springframework.web.reactive.function.server.ServerResponse.ok
import reactor.core.publisher.Mono
import java.net.URLDecoder
import java.util.*

val GraphQLMediaType = MediaType.parseMediaType("application/GraphQL")

@Configuration
class Routes {

  @Bean
  fun routesFun(objectMapper: ObjectMapper) = router {
    GET("/", { ok().body(fromResource(ClassPathResource("/graphiql.html"))) })
    POST("/graphql", { req ->
      req.bodyToMono<String>()
        .flatMap { body ->
          val queryParameters = objectMapper.readValue(body, QueryParameters::class.java)
          serveGraphql(queryParameters)
        }
    })
    GET("/graphql", { req ->
      val queryParameters = queryParametersFromRequest(req)
      serveGraphql(queryParameters)
    })
  }
}

fun queryParametersFromRequest(req: ServerRequest): QueryParameters {
  return QueryParameters(
    query = req.queryParam("query").get(),
    operationName = req.queryParam("operationName").orElseGet { null },
    variables = getVariables(req)
  )
}

fun getVariables(req: ServerRequest): Map<String, Any>? {
  return req.queryParam("variables")
    .map { URLDecoder.decode(it, "UTF-8") }
    .map { getJsonAsMap(it) }
    .orElseGet { null }
}

val schema = buildSchema()

fun serveGraphql(queryParameters: QueryParameters): Mono<ServerResponse> {
  val executionInput = newExecutionInput()
    .query(queryParameters.query)
    .operationName(queryParameters.operationName)
    .variables(queryParameters.variables)

  val graphQL = GraphQL
    .newGraphQL(schema)
    .build()

  val executionResult = graphQL.executeAsync(executionInput)
  return ok().body(Mono.fromFuture(executionResult))
}

data class QueryParameters(
  val query: String,
  val operationName: String? = null,
  val variables: Map<String, Any>? = null
)

fun getJsonAsMap(variables: String?): Map<String, Any>? {
  val typeRef =
    TypeFactory.defaultInstance().constructMapType(HashMap::class.java, String::class.java, Any::class.java)
  return jacksonObjectMapper().readValue(variables, typeRef)
}
