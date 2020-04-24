package hyperdex

import play.api.libs.json.{Json, Reads, Writes}
import sttp.model.StatusCode
import sttp.tapir.json.play._
import sttp.tapir.{EndpointInput, _}

object API {

  type Key = Int
  type Attribute = Int
  type AttributeMapping = Map[String, Attribute]

  sealed trait ErrorInfo
  case class InternalError(message: String) extends ErrorInfo
  case class BadRequestError(message: String) extends ErrorInfo

  implicit val internalErrorReads: Reads[InternalError] = Json.reads[InternalError]
  implicit val internalErrorWrites: Writes[InternalError] = Json.writes[InternalError]
  implicit val badRequestErrorReads: Reads[BadRequestError] = Json.reads[BadRequestError]
  implicit val badRequestErrorWrites: Writes[BadRequestError] = Json.writes[BadRequestError]

  object Create {
    case class Input(table: String, attributes: Seq[String])

    val endpointInput: EndpointInput[Input] =
      ("create" / path[String]("table"))
        .and(jsonBody[Seq[String]])
        .mapTo(Input)

    val endp: Endpoint[Input, ErrorInfo, String, Nothing] = endpoint.post
      .in(endpointInput)
      .out(stringBody)
      .errorOut(
        oneOf[ErrorInfo](
          statusMapping(StatusCode.BadRequest, jsonBody[BadRequestError]),
          statusMapping(StatusCode.InternalServerError, jsonBody[InternalError])
        )
      )
  }

  object Get {
    case class Input(table: String, key: Key)

    val endpointInput: EndpointInput[Input] =
      ("get" / path[String]("table") / path[Key]("key"))
        .mapTo(Input)

    val endp: Endpoint[Input, ErrorInfo, Option[AttributeMapping], Nothing] = endpoint.get
      .in(endpointInput)
      .out(jsonBody[Option[AttributeMapping]])
      .errorOut(
        oneOf[ErrorInfo](
          statusMapping(StatusCode.BadRequest, jsonBody[BadRequestError]),
          statusMapping(StatusCode.InternalServerError, jsonBody[InternalError])
        )
      )

  }

  object Put {
    case class Input(table: String, key: Key, value: AttributeMapping)

    val endpointInput: EndpointInput[Input] =
      ("put" / path[String]("table") / path[Key]("key"))
        .and(jsonBody[AttributeMapping])
        .mapTo(Input)

    val endp: Endpoint[Input, ErrorInfo, String, Nothing] = endpoint.post
      .in(endpointInput)
      .out(stringBody)
      .errorOut(
        oneOf[ErrorInfo](
          statusMapping(StatusCode.BadRequest, jsonBody[BadRequestError]),
          statusMapping(StatusCode.InternalServerError, jsonBody[InternalError])
        )
      )
  }

  object Search {
    case class Input(table: String, query: AttributeMapping)

    val endpointInput: EndpointInput[Input] =
      ("search" / path[String]("table"))
        .and(jsonBody[AttributeMapping])
        .mapTo(Input)

    // return set of tuples instead of map because the keys are integers (json only has string keys)
    val endp: Endpoint[Input, ErrorInfo, Set[(Key, AttributeMapping)], Nothing] =
      endpoint.get
        .in(endpointInput)
        .out(jsonBody[Set[(Key, AttributeMapping)]])
        .errorOut(
          oneOf[ErrorInfo](
            statusMapping(StatusCode.BadRequest, jsonBody[BadRequestError]),
            statusMapping(StatusCode.InternalServerError, jsonBody[InternalError])
          )
        )
  }

}
