package org.orleans.silo.storage

import com.typesafe.scalalogging.LazyLogging
import org.orleans.silo.services.grain.Grain

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object GrainDatabase extends LazyLogging {
  private var applicationName: String = _
  var disableDatabase = false
  lazy val instance: GrainDatabase =
    if (disableDatabase) {
      new StubDatabase()
    } else {
      new MongoGrainDatabase(applicationName)
    }
  def setApplicationName(name: String) = {
    if (applicationName != null) {
      logger.error("Can't set application name twice")
    } else {
      applicationName = name
    }
  }
}

trait GrainDatabase {

  def store[T <: Grain with AnyRef : ClassTag : TypeTag](grain: T): Future[Option[T]]
  def load[T <: Grain with AnyRef : ClassTag : TypeTag](id: String): Future[T]
  def load[T <: Grain with AnyRef : ClassTag : TypeTag](fieldName: String, value: Any): Future[T]
  def delete(id: String): Future[Boolean]

  def contains(id: String): Boolean

  def get[T <: Grain with AnyRef : ClassTag : TypeTag](id: String): Option[T]

}
