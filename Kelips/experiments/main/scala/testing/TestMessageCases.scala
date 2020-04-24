package testing

object TestMessageCases {
  case class WriteInsertion(text: String)
  case class WriteLookup(text: String)
  case class WriteText(text: String)
  case class Close()
}
