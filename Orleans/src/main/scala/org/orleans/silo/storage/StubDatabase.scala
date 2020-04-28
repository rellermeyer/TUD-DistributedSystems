package org.orleans.silo.storage
import org.orleans.silo.services.grain.Grain

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.reflect.runtime.universe

class StubDatabase extends GrainDatabase {

  override def store[T <: Grain with AnyRef : ClassTag : universe.TypeTag](grain: T): Future[Option[T]] = Future {None}

  override def load[T <: Grain with AnyRef : ClassTag : universe.TypeTag](id: String): Future[T] = Future {
    throw new Exception("Can't use load when not using a database")
  }

  override def load[T <: Grain with AnyRef : ClassTag : universe.TypeTag](fieldName: String, value: Any): Future[T] = {
    throw new Exception("Can't use load when not using a database")
  }

  override def delete(id: String): Future[Boolean] = Future(false)

  override def contains(id: String): Boolean = false

  override def get[T <: Grain with AnyRef : ClassTag : universe.TypeTag](id: String): Option[T] = None
}
