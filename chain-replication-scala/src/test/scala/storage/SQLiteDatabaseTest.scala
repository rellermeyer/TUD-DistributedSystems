package storage

import org.scalatest._
import storage.database.SQLiteDatabase

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class SQLiteDatabaseTest extends FunSuite {

  val fileName = "test"

  test("should setup and return select statement that is empty") {
    val storageSqlite: SQLiteDatabase = new SQLiteDatabase(fileName)
    val result = Await.result(storageSqlite.get(999), 20 seconds)

    result match {
      case Some(_) => fail()
      case None => succeed
    }

    storageSqlite.close()
  }
}
