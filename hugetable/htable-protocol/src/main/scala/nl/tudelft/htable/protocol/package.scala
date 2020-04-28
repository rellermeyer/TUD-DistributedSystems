package nl.tudelft.htable

import akka.util.ByteString
import com.google.protobuf
import nl.tudelft.htable.core.{Query, RowMutation}
import nl.tudelft.htable.protocol.ClientAdapters._
import scalapb.TypeMapper

/**
 * Adapters for the Protobuf protocol
 */
package object protocol {
  /**
   * A [TypeMapper] for the Akka [ByteString] type.
   */
  implicit val byteStringType: TypeMapper[protobuf.ByteString, ByteString] =
    TypeMapper[protobuf.ByteString, ByteString](bs => ByteString(bs.asReadOnlyByteBuffer()))(bs => protobuf.ByteString.copyFrom(bs.toByteBuffer))

  /**
   * A [TypeMapper] for the [Query] type.
   */
  implicit val queryType: TypeMapper[protocol.client.Query, Query] =
    TypeMapper[protocol.client.Query, Query](queryToCore)(queryToProtobuf)

  /**
   * A [TypeMapper] for the [RowMutation] type.
   */
  implicit val mutateType: TypeMapper[protocol.client.RowMutation, RowMutation] =
    TypeMapper[protocol.client.RowMutation, RowMutation](mutationToCore)(mutationToProtobuf)

  /**
   * A [TypeMapper] for the [Row] type.
   */
  implicit val tabletType: TypeMapper[protocol.Tablet, core.Tablet] =
    TypeMapper[protocol.Tablet, core.Tablet](tabletToCore)(tabletToProtobuf)

  /**
   * A [TypeMapper] for the [Row] type.
   */
  implicit val rowType: TypeMapper[protocol.Row, core.Row] =
    TypeMapper[protocol.Row, core.Row](rowToCore)(rowToProtobuf)

  /**
   * A [TypeMapper] for the [RowCell] type.
   */
  implicit val cellType: TypeMapper[protocol.RowCell, core.RowCell] =
    TypeMapper[protocol.RowCell, core.RowCell](cellToCore)(cellToProtobuf)

  /**
   * A [TypeMapper] for the [RowRange] type.
   */
  implicit val rowRangeType: TypeMapper[protocol.RowRange, core.RowRange] =
    TypeMapper[protocol.RowRange, core.RowRange](rowRangeToCore)(rowRangeToProtobuf)

  /**
   * A [TypeMapper] for the [AssignType] type.
   */
  implicit val actionType: TypeMapper[protocol.internal.AssignRequest.Action, core.AssignAction] =
    TypeMapper[protocol.internal.AssignRequest.Action, core.AssignAction](actionToCore)(actionToProtobuf)
}
