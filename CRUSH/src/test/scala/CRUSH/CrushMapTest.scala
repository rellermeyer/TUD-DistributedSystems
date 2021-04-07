package CRUSH

import CRUSH.controller.RootController
import CRUSH.utils.crushmap.{ Bucket, OSD, Straw, Uniform }
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Random

class CrushMapTest extends AnyFunSuite {
  test("negative weight") {
    assertThrows[IllegalArgumentException](OSD("a", -1.0, 3))
  }

  test("bucket weight for 1 leaf") {
    val rack = Bucket(List(OSD("a", 1.0, 2)), Uniform(), 1)

    assert(rack.weight == 1)
  }

  test("bucket weight for 2 leaves") {
    val rack = Bucket(List(OSD("a1", 1.0, 2), OSD("a2", 2.0, 3)), Uniform(), 2)

    assert(rack.weight == 3)
  }

  test("Uniform select count") {
    val nodes = List(OSD("a", 1.0), OSD("b", 2.0), OSD("c", 3.0))
    val r     = new Random(1)

    val s = Uniform().selectChildren(2, nodes, 1)(r)
    assert(s.length == 2)
  }

  test("Straw select count") {
    val nodes = List(OSD("a", 1.0), OSD("b", 2.0), OSD("c", 3.0))
    val r     = new Random(1)

    val s = Straw().selectChildren(2, nodes, 1)(r)
    assert(s.length == 2)
  }

  test("Uniform select count greater than length") {
    val nodes = List(OSD("a", 1.0), OSD("b", 2.0), OSD("c", 3.0))
    val r     = new Random(1)

    val s = Uniform().selectChildren(4, nodes, 1)(r)
    assert(s.length == 3)
  }

  test("Straw select count greater than length") {
    val nodes = List(OSD("a", 1.0), OSD("b", 2.0), OSD("c", 3.0))
    val r     = new Random(1)

    val s = Straw().selectChildren(4, nodes, 1)(r)
    assert(s.length == 3)
  }

  test("Uniform select repeatable") {
    val nodes = List(OSD("a", 1.0), OSD("b", 2.0), OSD("c", 3.0))
    val r1    = new Random(1)
    val r2    = new Random(1)

    val s1 = Uniform().selectChildren(1, nodes, 1)(r1)
    val s2 = Uniform().selectChildren(1, nodes, 1)(r2)
    assert(s1.head eq s2.head)
  }

  test("Straw select repeatable") {
    val nodes = List(OSD("a", 1.0), OSD("b", 2.0), OSD("c", 3.0))
    val r1    = new Random(1)
    val r2    = new Random(1)

    val s1 = Uniform().selectChildren(1, nodes, 1)(r1)
    val s2 = Uniform().selectChildren(1, nodes, 1)(r2)
    assert(s1.head eq s2.head)
  }

  test("read map") {
    RootController.readMap() // this should just succeed
  }
}
