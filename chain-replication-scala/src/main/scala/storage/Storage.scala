package storage

import storage.database.SQLiteDatabase

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
 * Storage class, handles database and JSON logic.
 *
 * @param identifier will be used as the name for the file
 */
class Storage(val identifier: String) {

    var storage: Database = new SQLiteDatabase(identifier)

    def query(objectId: Int, options: Option[List[String]]): Option[String] = {
        // Get entry from storage source.
        val databaseEntry: Option[String] = Await.result(storage.get(objectId), 5 seconds)

        databaseEntry match {
            case None => None
            case Some(jsonString) =>
                // Parse to object (fails if it is e.g. a list or invalid)
                val jsonObject = Try(ujson.read(jsonString).obj)

                jsonObject match {
                    case Failure(_) => None
                    case Success(jsonObject) =>
                        options match {
                            case Some(options) =>
                                // Option given, return only requested fields.
                                val filteredObj = jsonObject.filter(x => options.contains(x._1))
                                val jsonString = ujson.write(filteredObj)
                                Some(jsonString)
                            case None =>
                                // No options given, return all fields.
                                val jsonString = ujson.write(jsonObject)
                                Some(jsonString)
                        }
                }

        }
    }

    def update(objectId: Int, newValue: String, options: Option[List[String]]): Option[String] = {
        // Parse new object that the client sent.
        val jsonObject = Try(ujson.read(newValue).obj)

        jsonObject match {
            case Failure(_) => None
            case Success(jsonObject) =>
                // Get entry from storage source.
                val databaseEntry = Await.result(storage.get(objectId), 5 seconds)

                databaseEntry match {
                    case None =>
                        // No object present, create in database.
                        // Parse the json object to string again (formatting reasons).
                        val jsonStringUpdated = ujson.write(jsonObject)
                        val result = Await.result(storage.upsert(objectId, jsonStringUpdated), 5 seconds)

                        result match {
                            case Some(jsonStringUpdated) => Some(jsonStringUpdated)
                            case None => None
                        }
                    case Some(previousValue) =>
                        // Object present, update/merge objects based on options & previous value.
                        // TODO: interpret options and non-deterministic update, current implementation = overwrite.
                        // Parse the json object to string again (formatting reasons).
                        val jsonStringUpdated = ujson.write(jsonObject)
                        val result = Await.result(storage.upsert(objectId, jsonStringUpdated), 5 seconds)

                        result match {
                            case Some(jsonStringUpdated) => Some(jsonStringUpdated)
                            case None => None
                        }
                }
        }

    }

    def getAllObjects: List[(Int, String)] = {
        val objects = Await.result(storage.getAllObjects, 20 seconds)
        objects.toList
    }

    def clear(): Unit = {
        storage.clear()
    }
}
