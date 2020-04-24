package hyperdex

import akka.actor.typed.ActorRef
import hyperdex.API.AttributeMapping

object MessageProtocol {

  /** messages **/
  trait GatewayMessage extends CBorSerializable

  /** user queries **/
  sealed trait Query extends GatewayMessage
  final case class Create(from: ActorRef[CreateResult], table: String, attributes: Seq[String]) extends Query
  sealed trait TableQuery extends Query
  final case class Lookup(from: ActorRef[LookupResult], table: String, key: Int) extends TableQuery
  final case class Search(from: ActorRef[SearchResult], table: String, mapping: Map[String, Int]) extends TableQuery
  final case class Put(from: ActorRef[PutResult], table: String, key: Int, mapping: Map[String, Int]) extends TableQuery

  /** responses from data nodes **/
  sealed trait DataNodeResponse extends GatewayMessage
  final case class CreateResult(result: Either[CreateError, Boolean]) extends DataNodeResponse
  final case class LookupResult(result: Either[LookupError, Option[AttributeMapping]]) extends DataNodeResponse
  // in order for cbor/json serialization to work a map can only have strings as keys
  final case class PutResult(result: Either[PutError, Boolean]) extends DataNodeResponse
  final case class SearchResult(result: Either[SearchError, Map[String, AttributeMapping]]) extends DataNodeResponse

  /**
    * errors within messages
    */
  sealed trait QueryError

  sealed trait CreateError extends QueryError
  sealed trait LookupError extends QueryError
  sealed trait PutError extends QueryError
  sealed trait SearchError extends QueryError

  final case object TimeoutError extends LookupError with CreateError with PutError with SearchError
  final case object TableNotExistError extends LookupError with PutError with SearchError
  final case class InvalidAttributeError(invalidAttributes: Set[String]) extends PutError with SearchError
  final case class IncompleteAttributesError(missingAttributes: Set[String]) extends PutError
}
