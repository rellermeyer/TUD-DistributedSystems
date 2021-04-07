package l.tudelft.distribted.ec

import scala.collection.concurrent.TrieMap
import java.util.Map

class HashMapDatabase(val hashMap: TrieMap[String, Map[String, AnyRef]] = new TrieMap()) {
  def store(key: String, data: Map[String, AnyRef]): Option[Map[String, AnyRef]] = hashMap.put(key, data)

  def retrieve(key: String): Option[Map[String, AnyRef]] = hashMap.get(key)

  def remove(key: String): Option[Map[String, AnyRef]] = hashMap.remove(key)
}
