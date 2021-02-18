import HelloWorld.cube
import org.scalatest.FunSuite

class HelloWorldTest extends  FunSuite {
  test("Cube test") {
    assert(cube(3) === 27)
  }
}
