package storage

import scala.concurrent.Future

trait Database {

    /**
     * Retrieves object with ID from database.
     *
     * @param objectId objectID
     * @return None when object does not exist, else Some(String)
     *         where the string is the object in JSON format
     */
    def get(objectId: Int): Future[Option[String]]

    /**
     * Inserts or updates: if the object does not exist, it will be inserted,
     * if it does exist, it will be updated with the new value
     *
     * @param objectId objectID
     * @param value    the JSON value, must be a valid JSON object
     *                 (e.g. not a list or a single value)
     * @return None when the object was not updated/inserted, else Some(String)
     *         where the string is the object in JSON format
     */
    def upsert(objectId: Int, value: String): Future[Option[String]]

    /**
     * Gets all objects in the database.
     *
     * @return A Nil sequence when there are no objects in the database
     *         otherwise, a sequence of (Int, String) pairs for (id, object)
     */
    def getAllObjects: Future[Seq[(Int, String)]]

    /**
     * Closes the database.
     */
    def close(): Unit

    /**
     * Clears the database.
     */
    def clear(): Unit

}
