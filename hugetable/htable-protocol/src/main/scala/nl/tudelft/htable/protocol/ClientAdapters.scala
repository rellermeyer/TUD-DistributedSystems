package nl.tudelft.htable.protocol

import akka.util.ByteString
import nl.tudelft.htable.core._
import nl.tudelft.htable.protocol
import nl.tudelft.htable.protocol.client.Mutation.{DeleteFromColumn, DeleteFromRow, SetCell}
import nl.tudelft.htable.protocol.internal.AssignRequest
import nl.tudelft.htable.protocol.internal.AssignRequest.ActionType

import scala.language.implicitConversions

/**
 * Conversions between core classes and Protobuf objects.
 */
object ClientAdapters {

  /**
   * Translate a core [Tablet] to Protobuf [Tablet].
   */
  implicit def tabletToProtobuf(tablet: Tablet): protocol.Tablet =
    protocol.Tablet(tableName = tablet.table, startKey = tablet.range.start, endKey = tablet.range.end, id = tablet.id)

  /**
   * Translate a core [Tablet] to Protobuf [Tablet].
   */
  implicit def tabletToCore(tablet: protocol.Tablet): Tablet =
    Tablet(tablet.tableName, RowRange(tablet.startKey, tablet.endKey), id = tablet.id)

  /**
   * Convert the specified [Query] to a [protocol.client.Query].
   */
  private[protocol] def queryToProtobuf(query: Query): protocol.client.Query = {
    query match {
      case Get(table, key) => protocol.client.Query(table, rows = protocol.client.Query.Rows.RowKey(key))
      case Scan(table, range, reversed) =>
        protocol.client.Query(table, reversed = reversed, rows = protocol.client.Query.Rows.RowRange(range))
    }
  }

  /**
   * Convert the specified [protocol.client.Query] to a [Query].
   */
  private[protocol] def queryToCore(request: protocol.client.Query): Query = {
    request.rows match {
      case protocol.client.Query.Rows.RowKey(key)                         => Get(request.tableName, key)
      case protocol.client.Query.Rows.RowRange(range) => Scan(request.tableName, range, request.reversed)
      case protocol.client.Query.Rows.Empty                               => throw new IllegalArgumentException("Empty query")
    }
  }

  /**
   * Convert the specified [Row] to a [protocol.Row].
   */
  private[protocol] def rowToProtobuf(row: Row): protocol.Row = protocol.Row(rowKey = row.key, cells = row.cells)

  /**
   * Convert the specified [protocol.Row] to a [Row].
   */
  private[protocol] def rowToCore(res: protocol.Row): Row = Row(res.rowKey, res.cells)

  /**
   * Convert the specified [RowCell] to a [protocol.RowCell].
   */
  private[protocol] def cellToProtobuf(cell: RowCell): protocol.RowCell = protocol.RowCell(qualifier = cell.qualifier, timestamp = cell.timestamp, value = cell.value)

  /**
   * Convert the specified [protocol.RowCell] to a [RowCell].
   */
  private[protocol] def cellToCore(cell: protocol.RowCell): RowCell = RowCell(cell.qualifier, cell.timestamp, cell.value)

  /**
   * Convert the specified [RowRange] to a [protocol.RowRange].
   */
  private[protocol] def rowRangeToProtobuf(range: RowRange): protocol.RowRange = protocol.RowRange(range.start, range.end)

  /**
   * Convert the specified [protocol.RowRange] to a [RowRange].
   */
  private[protocol] def rowRangeToCore(range: protocol.RowRange): RowRange = RowRange(range.startKey, range.endKey)

  /**
   * Convert the specified [RowMutation] to a [protocol.client.RowMutation].
   */
  private[protocol] def mutationToProtobuf(mutation: RowMutation): protocol.client.RowMutation = {
    val mutations: List[protocol.client.Mutation] = mutation.mutations.reverse
      .map {
        case Mutation.PutCell(cell) =>
          protocol.client.Mutation.Mutation.SetCell(SetCell(cell.qualifier, cell.timestamp, cell.value))
        case Mutation.DeleteCell(cell) =>
          protocol.client.Mutation.Mutation.DeleteFromColumn(DeleteFromColumn(cell.qualifier, cell.timestamp))
        case Mutation.Delete =>
          protocol.client.Mutation.Mutation.DeleteFromRow(DeleteFromRow())
      }
        .map(mut => protocol.client.Mutation(mut))
    protocol.client.RowMutation(tableName = mutation.table, rowKey = mutation.key, mutations)
  }

  /**
   * Convert a [protocol.client.RowMutation] to [RowMutation]
   */
  private[protocol] def mutationToCore(rowMutation: protocol.client.RowMutation): RowMutation = {
    val mutations = rowMutation.mutations.flatMap[Mutation] { mutation =>
      if (mutation.mutation.isSetCell) {
        val cell = mutation.mutation.setCell.head
        Some(Mutation.PutCell(RowCell(cell.qualifier, cell.timestamp, cell.value)))
      } else if (mutation.mutation.isDeleteFromColumn) {
        val cell = mutation.mutation.deleteFromColumn.head
        Some(Mutation.DeleteCell(RowCell(cell.qualifier, cell.timestamp, ByteString())))
      } else if (mutation.mutation.isDeleteFromRow) {
        Some(Mutation.Delete)
      } else {
        None
      }
    }

    RowMutation(rowMutation.tableName, rowMutation.rowKey)
      .copy(mutations = mutations.toList)
  }

  /**
   * Convert an action.
   */
  private[protocol] def actionToCore(action: AssignRequest.Action): AssignAction = {
    AssignAction(action.tablet.get, actionTypeToCore(action.actionType))
  }

  /**
   * Convert an action.
   */
  private[protocol] def actionToProtobuf(action: AssignAction): AssignRequest.Action = {
    AssignRequest.Action(actionTypeToProtobuf(action.action), Some(action.tablet))
  }


  /**
   * Convert an action type.
   */
  private[protocol] def actionTypeToCore(actionType: AssignRequest.ActionType): AssignType.Type = {
    actionType match {
      case ActionType.ADD => AssignType.Add
      case ActionType.REMOVE => AssignType.Remove
      case ActionType.CREATE => AssignType.Create
      case ActionType.DELETE => AssignType.Delete
      case ActionType.CLEAR => AssignType.Clear
      case _ => throw new IllegalArgumentException()
    }
  }

  /**
   * Convert an action type.
   */
  private[protocol] def actionTypeToProtobuf(actionType: AssignType.Type): AssignRequest.ActionType = {
    actionType match {
      case AssignType.Add => AssignRequest.ActionType.ADD
      case AssignType.Remove => AssignRequest.ActionType.REMOVE
      case AssignType.Create => AssignRequest.ActionType.CREATE
      case AssignType.Delete => AssignRequest.ActionType.DELETE
      case AssignType.Clear => AssignRequest.ActionType.CLEAR
    }
  }
}
