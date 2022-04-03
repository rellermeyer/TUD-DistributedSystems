package logic.wall

/**
 * a trait to represent the file in communication
 */
trait File/* {
  val fileName: String
  val fileType: FileType.FileType
  val content: String
}*/

/**
 * FileType
 */
object FileType extends Enumeration {
  type FileType = Value
  val Index, List, FriendList, FirstName, LastName, BirthDay, City, WallIndex, WallEntry = Value
}
