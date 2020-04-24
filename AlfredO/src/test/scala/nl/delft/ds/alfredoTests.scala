package nl.delft.ds

import org.scalatra.test.scalatest._

class alfredoTests extends ScalatraFunSuite {

  addServlet(classOf[alfredo], "/*")

  test("GET / on alfredo should return status 200") {
    get("/") {
      status should equal (200)
    }
  }

}
