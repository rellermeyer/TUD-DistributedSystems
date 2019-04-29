package main.scala.database

/**
  * Database class containing a map with chars as keys and integers as values.
  */
class Database {

  /** Keys can easily be extended to different data types, but since these are used in the paper
    * and to reduce the amount of possible keys during the project characters are used as key. */
  private var dataBase = Map[Char, Int]()

  /**
    * Reads a value from the database
    *
    * @param key The key in the map. This can be any character
    * @return The value in the map or 0 if the key does not exist.
    */
  def readValue(key: Char): Int = {
    val value = dataBase.get(key)
    value match {
      case Some(x) => x
      case None => 0
    }
  }

  /**
    * Creates a new value in the database. This will overwrite an existing value with the same key
    *
    * @param key   The key in the map. This can be any character
    * @param value The value the new key should have. This must be an integer
    */
  def createValue(key: Char, value: Int): Unit = {
    dataBase += (key -> value)
  }

  /**
    * Updates a value in the database. This will add the new value to the existing value for a given key
    *
    * @param key   The key in the map. This can be any character
    * @param value The value the new key should have. This must be an integer
    */
  def updateValue(key: Char, value: Int): Unit = {
    val oldValue = readValue(key)
    dataBase += (key -> (value + oldValue))
  }

  /**
    * Deletes a key value pair from the database. Returns the value of the deleted pair
    *
    * @param key The key in the map. This can be any character
    * @return The value of the removed key value pair
    */
  def deleteValue(key: Char): Int = {
    val oldValue = readValue(key)
    dataBase -= key
    oldValue
  }
}
