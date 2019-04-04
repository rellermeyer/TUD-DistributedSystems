/**
  * IDs encoding different wrapper implementations.
  */
object WrapperId extends Enumeration {
    type WrapperId = Value
    val Flask, Postgres, Dummy = Value
    val stringToId: Map[String, WrapperId] = Map(
        "Flask" -> Flask,
        "Postgres" -> Postgres,
        "Dummy" -> Dummy
    )
    val idToPort: Map[WrapperId, String] = Map(
        Flask -> "5000",
        Postgres -> "5432",
        Dummy -> "1234"
    )
}
