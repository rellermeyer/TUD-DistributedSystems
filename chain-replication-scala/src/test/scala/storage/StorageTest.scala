package storage

import org.scalatest._

class StorageTest extends FunSuite {

  val fileName = "test"

  val rawJson: String =
    """{"name":"Watership Down","location":{"lat":51.235685,"long":-1.309197},"residents":[{"name":"Fiver","age":4,"role":null},{"name":"Bigwig","age":6,"role":"Owsla"}]}"""

  val rawJsonNameFieldOnly: String =
    """{"name":"Watership Down"}"""

  val listJson: String =
    """[1, 2, 3, 4, 5]"""

  test("should initialize without errors") {
    new Storage(fileName)
  }

  test("should update object and receive it back successfully") {
    val storage = new Storage(fileName)
    val updateResult = storage.update(1, rawJson, None)

    updateResult match {
      case Some(jsonBack) =>
        assert(jsonBack == rawJson)
      case None => fail()
    }
  }

  test("should update object and receive it back successfully after query") {
    val storage = new Storage(fileName)
    storage.update(1, rawJson, None)

    val query = storage.query(1, None)

    query match {
      case Some(jsonBack) =>
        assert(jsonBack == rawJson)
      case None => fail()
    }
  }

  test("should only receive the given field after query") {
    val storage = new Storage(fileName)
    storage.update(1, rawJson, None)

    val options = Some(List("name"))
    val query = storage.query(1, options)

    query match {
      case Some(jsonBack) =>
        assert(jsonBack == rawJsonNameFieldOnly)
      case None => fail()
    }
  }

  test("should update two objects which are both defined") {
    val storage = new Storage(fileName)
    val updateResult = storage.update(1, rawJson, None)
    val updateResult2 = storage.update(2, rawJson, None)

    (updateResult, updateResult2) match {
      case (Some(jsonBack), Some(jsonBack2)) =>
        assert(jsonBack == rawJson && jsonBack2 == rawJson)
      case _ => fail()
    }
  }

  test("should deny any non object updates") {
    val storage = new Storage(fileName)
    val updatedResult = storage.update(1, listJson, None)

    updatedResult match {
      case Some(a) =>
        println(a)
        fail()
      case None => succeed
    }
  }

  test("should deny any non valid json updates") {
    val storage = new Storage(fileName)
    val updatedResult = storage.update(1, "{abc; 12}", None)

    updatedResult match {
      case Some(a) =>
        println(a)
        fail()
      case None => succeed
    }
  }

}

