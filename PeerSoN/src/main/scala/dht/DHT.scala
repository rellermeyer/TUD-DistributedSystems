package dht

trait DHT {
  /**
   * put a new key on the DHT
   * create a data list for this key
   * add the data to the list
   */
  def put(key: String, data: Any): Unit

  /**
   * retrieves the head of data list by key
   * if key not existed, return None
   */
  def get(key: String, callback: Option[Any] => Unit): Unit

  /**
   * check if the DHT contains a certain key
   */
  def contains(key: String, callback: Boolean => Unit): Unit

  /**
   * update the data list with a new item under a key
   */
  def append(key: String, data: Any): Unit

  /**
   * retrieves all values stored in a data list under a key
   */
  def getAll(key: String, callback: Option[List[Any]] => Unit): Unit

  /**
   * remove a key-value pair
   */
  def remove(key: String): Unit
}
