package org.orleans.silo.storage

import ch.qos.logback.classic.{Level, LoggerContext}
import com.typesafe.scalalogging.LazyLogging
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.FindOneAndUpdateOptions
import org.orleans.silo.services.grain.Grain
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.util.{Failure, Success}

object MongoGrainDatabase {

  /**
    * Loads the connection string to a given mongo instance from the MongodbConnection file.
    * @return Mongodb connection string
    */
  def loadConnectionString() = {
    val filename = "/MongodbConnection"
    val inputstream = getClass.getResourceAsStream(filename)
    val connectionString = Source.fromInputStream(inputstream).getLines().next()
    inputstream.close()
    connectionString
  }
}

class MongoGrainDatabase(val connectionString: String, databaseName: String)
    extends GrainDatabase
    with LazyLogging {

  def this(databaseName: String) {
    this(MongoGrainDatabase.loadConnectionString(), databaseName);
  }

  LoggerFactory.getILoggerFactory
    .asInstanceOf[LoggerContext]
    .getLogger("org.mongodb.driver")
    .setLevel(Level.ERROR)

  lazy private val client = MongoClient(connectionString)
  lazy private val database: MongoDatabase = client.getDatabase(databaseName)
  lazy private val grainCollection: MongoCollection[Document] = database.getCollection("grains")


  /**
    * Sets the log level of the mongo driver
    * @param level Log Level
    */
  def setMongoLogLevel(level: Level): Unit = {
    LoggerFactory.getILoggerFactory
      .asInstanceOf[LoggerContext]
      .getLogger("org.mongodb.driver")
      .setLevel(level)
  }

  /**
    * Stores a grain to persistant storage. In case the grain is already stored (based on the _id field) it will overwrite it.
    * @param grain Grain to be stored
    * @tparam T Specific subtype of the grain
    * @return Returns a Future that contains the old stored grain if it was successfully stored or else an exception
    */
  override def store[T <: Grain with AnyRef: ClassTag: TypeTag](
      grain: T): Future[Option[T]] = {
    val jsonString = GrainSerializer.serialize(grain)
    logger.debug(s"Inserting or updating grain: $grain")
    val result = grainCollection.findOneAndUpdate(
      equal("_id", grain._id),
      Document("$set" -> Document(jsonString)),
      FindOneAndUpdateOptions().upsert(true))

    result.toFuture().transform {
      case Success(document) =>
        logger.debug("Succesfully stored grain! Now deserializing...")
        if (document != null) {
          logger.debug(document.toJson())
          Success(Some(GrainSerializer.deserialize[T](document.toJson())))
        } else {
          Success(None)
        }

      case Failure(e) =>
        logger.debug("Something went wrong when storing the grain.")
        Failure(e)
    }
  }

  /**
    * Loads a grain based on its id
    * @param id Grain id.
    * @tparam T Type of the grain that is being loaded.
    * @return The loaded grain
    */
  override def load[T <: Grain with AnyRef: ClassTag: TypeTag](
      id: String): Future[T] = {
    load("_id", id)
  }

  /**
    * Loads a grain based on its id
    * @param fieldName Name of the field to search on.
    * @param value Value that needs to be found
    * @tparam T Type of the grain that is being loaded.
    * @return The loaded grain
    */
  override def load[T <: Grain with AnyRef: ClassTag: TypeTag](
      fieldName: String,
      value: Any): Future[T] = {
    val observable: SingleObservable[Document] =
      grainCollection.find(equal(fieldName, value)).first()

    observable.toFuture().transform {
      case Success(document) =>
        logger.debug(
          s"Succesfully loaded grain: ${document.toJson()}! Now deserializing...")
        Success(GrainSerializer.deserialize(document.toJson()))
      case Failure(e) =>
        logger.debug("Something went wrong when storing the grain.")
        Failure(e)
    }
  }

  /**
    * Deletes the grain with the given id from the storage and returns a future with a boolean indicating whether or not this was succesful
    * @param id Id of the grain that has to be deleted
    * @return Future indicating whether or not deletion was succesful
    */
  override def delete(id: String): Future[Boolean] = {
    val observable: SingleObservable[Document] = grainCollection.findOneAndDelete(equal("_id", id))

    observable.toFuture().transform {
      case Success(document) => document match {
        case null => Failure(new Exception(s"Grain deletion from mongodb failed, since grain with id $id not found"))
        case _ => Success(true)
      }
      case Failure(e) =>
        logger.error("Something went wrong when deleting the grain.")
        Failure(e)
    }
  }

  /**
    * Gets the grain synchronously
    * @param id Id of the grain
    * @tparam T Type of the grain
    * @return The grain with the given id of the given type
    */
  override def get[T <: Grain with AnyRef: ClassTag: TypeTag](
      id: String): Option[T] = {
    val observable: SingleObservable[Document] =
      grainCollection.find(equal("_id", id)).first()

    val result: Future[Option[T]] = observable.toFuture().transform {
      case Success(document) =>
        if (document == null) {
          logger.debug(s"Document with id: $id was not found")
          Success(None)
        } else {
          logger.debug(s"Succesfully found grain: ${document.toJson()}")
          Success(Some(GrainSerializer.deserialize[T](document.toJson())))
        }

      case Failure(e) =>
        logger.error(s"Couldn't get grain because of exception: $e")
        Failure(e)
    }

    Await.result(result, 5 seconds) match {
      case Some(grain: T) => Some(grain)
      case _              => None
    }
  }

  override def contains(id: String): Boolean = {
    val observable: SingleObservable[Document] =
      grainCollection.find(equal("_id", id)).first()

    Await.result(observable.toFuture(), 5 seconds) match {
      case null                                   => false
      case document: Document if document != null => true
    }
  }

  /**
    * Closes the connection with the database.
    */
  def close(): Unit = {
    logger.debug("Closing the connection with mongodb.")
    client.close()
  }
}
