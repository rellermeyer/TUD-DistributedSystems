package org.orleans.silo.storage

import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, FieldSerializer, Formats}
import org.orleans.silo.services.grain.Grain

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object GrainSerializer {

  /**
    * Serializes a grain to a json string.
    * @param grain Grain to be serialized.
    * @tparam T Subtype of the grain that is being serialized.
    * @return JSON String serialization of the grain
    */
  def serialize[T <: Grain with AnyRef : ClassTag : TypeTag](grain: T): String = {
    implicit val format: Formats = DefaultFormats + FieldSerializer[T]()

    Serialization.write(grain)(format)
  }

  /**
    * Deserializes a JSON string to a grain
    * @param jsonString JSON string to be deserialized.
    * @tparam T Subtype of the grain that should be deserialized to.
    * @return Deserialized grain.
    */
  def deserialize[T <: Grain with AnyRef : ClassTag : TypeTag](jsonString: String): T = {
    implicit val format: Formats = DefaultFormats + FieldSerializer[T]()

    Serialization.read[T](jsonString)
  }
}
