package CRUSH

import CRUSH.controller.Rootstore
import CRUSH.utils.crushmap._
import org.scalatest.funsuite.AnyFunSuite
import org.slf4j.{ Logger, LoggerFactory }

import java.time.Instant
import scala.collection.mutable.ListBuffer

class CrushTest extends AnyFunSuite {
  implicit val logger: Logger = LoggerFactory.getLogger("testLogger")
  test("basic") {
    val osd   = OSD("address", 1.0, 2)
    val map   = CrushMap(Some(osd), List(Leaf()))
    val steps = List(Emit())
    val rule  = PlacementRule(steps)

    val output = Crush.crush(123, map, rule)
    assert(output == List(osd))
  }

  test("one layer select one") {
    val osd   = OSD("address", 1.0, 2)
    val rack  = Bucket(List(osd), Uniform(), 1)
    val map   = CrushMap(Some(rack), List(Rack(), Leaf()))
    val steps = List(Select(1, Leaf()), Emit())
    val rule  = PlacementRule(steps)

    val output = Crush.crush(123, map, rule)
    assert(output == List(osd))
  }

  test("one layer select two") {
    val osd1  = OSD("address1", 1.0, 2)
    val osd2  = OSD("address2", 1.0, 2)
    val rack  = Bucket(List(osd1, osd2), Uniform(), 1)
    val map   = CrushMap(Some(rack), List(Rack(), Leaf()))
    val steps = List(Select(2, Leaf()), Emit())
    val rule  = PlacementRule(steps)

    val output = Crush.crush(123, map, rule)
    assert(output == List(osd1, osd2))
  }

  test("multi layer multi nodes") {
    val osd11 = OSD("osd11", 1.0, 11)
    val osd12 = OSD("osd12", 1.0, 12)
    val osd13 = OSD("osd13", 1.0, 13)

    val osd21 = OSD("osd21", 2.0, 21)
    val osd22 = OSD("osd22", 2.0, 22)
    val osd23 = OSD("osd23", 2.0, 23)

    val osd31 = OSD("osd31", 3.0, 31)
    val osd32 = OSD("osd32", 3.0, 32)
    val osd33 = OSD("osd33", 3.0, 33)

    val bucket1    = Bucket(List(osd11, osd12, osd13), Uniform(), 1)
    val bucket2    = Bucket(List(osd21, osd22, osd23), Uniform(), 1)
    val bucket3    = Bucket(List(osd31, osd32, osd33), Uniform(), 1)
    val rootBucket = Bucket(List(bucket1, bucket2, bucket3), Straw(), 1)
    val map        = CrushMap(Some(rootBucket), List(Rack(), Rack(), Leaf()))
    val steps      = List(Select(2, Rack()), Select(2, Rack()), Emit())
    val rule       = PlacementRule(steps)
    val selected   = Crush.crush(123, map, rule)
    print(selected)
    assert(selected.length == 4) // 2**2 selected nodes

  }
  test("multi layer multi nodes empty bucket") {
    val osd11 = OSD("osd11", 0.0, 11)
    val osd12 = OSD("osd12", 0.0, 12)
    val osd13 = OSD("osd13", 0.0, 13)

    val osd21 = OSD("osd21", 2.0, 21)
    val osd22 = OSD("osd22", 2.0, 22)
    val osd23 = OSD("osd23", 2.0, 23)

    val osd31 = OSD("osd31", 3.0, 31)
    val osd32 = OSD("osd32", 3.0, 32)
    val osd33 = OSD("osd33", 3.0, 33)

    val bucket1 = Bucket(List(osd11, osd12, osd13), Uniform(), 1)
    println(bucket1.weight)
    println("bobs")
    val bucket2    = Bucket(List(osd21, osd22, osd23), Uniform(), 1)
    val bucket3    = Bucket(List(osd31, osd32, osd33), Uniform(), 1)
    val rootBucket = Bucket(List(bucket1, bucket2, bucket3), Straw(), 1)
    val map        = CrushMap(Some(rootBucket), List(Rack(), Rack(), Leaf()))
    val steps      = List(Select(2, Rack()), Select(2, Rack()), Emit())
    val rule       = PlacementRule(steps)
    val selected   = Crush.crush(22, map, rule)
    assert(!selected.contains(osd11)) // these cannot be in the output, because the bucket has weight 0
    assert(!selected.contains(osd12))
    assert(!selected.contains(osd13))
  }

  test("uniform selecting") {
    val osd11      = OSD("osd11", 0.0, 11)
    val osd12      = OSD("osd12", 0.0, 12)
    val osd13      = OSD("osd13", 1000000000.0, 13)
    val rootBucket = Bucket(List(osd11, osd12, osd13), Uniform(), 1)
    val map        = CrushMap(Some(rootBucket), List(Rack(), Leaf()))
    val steps      = List(Select(2, Rack()), Emit())
    val rule       = PlacementRule(steps)

    val selected = Crush.crush(123, map, rule)
    assert(selected.exists(p => p.weight == 0.0))
  }

  test("straw selecting") {
    val osd11      = OSD("osd11", 0.0, 11)
    val osd12      = OSD("osd12", 0.0, 12)
    val osd13      = OSD("osd13", 1000000000.0, 13)
    val osd14      = OSD("osd14", 0.5, 13)
    val rootBucket = Bucket(List(osd11, osd12, osd13, osd14), Straw(), 1)
    val map        = CrushMap(Some(rootBucket), List(Rack(), Leaf()))
    val steps      = List(Select(2, Rack()), Emit())
    val rule       = PlacementRule(steps)

    val selected = Crush.crush(123, map, rule)
    assert(
      !selected.exists(p => p.weight == 0.0)
    ) // straw should only select nodes that have at least some weight when there are enough
  }

  test("Test filtering of nodes single") {
    val osd11 = OSD("osd11", 11.0, 11)
    val osd12 = OSD("osd12", 12.0, 12)
    val osd13 = OSD("osd13", 13.0, 13)

    val osd21 = OSD("osd21", 21.0, 21)
    val osd22 = OSD("osd22", 22.0, 22)
    val osd23 = OSD("osd23", 23.0, 23)

    val osd31 = OSD("osd31", 31.0, 31)
    val osd32 = OSD("osd32", 32.0, 32)
    val osd33 = OSD("osd33", 33.0, 33)

    val bucket1    = Bucket(List(osd11, osd12, osd13), Uniform(), 1)
    val bucket2    = Bucket(List(osd21, osd22, osd23), Uniform(), 1)
    val bucket3    = Bucket(List(osd31, osd32, osd33), Uniform(), 1)
    val rootBucket = Bucket(List(bucket1, bucket2, bucket3), Straw(), 2)
    val map        = CrushMap(Some(rootBucket), List(Rack(), Leaf()))
    Rootstore.configMap = map
    val curr = Instant.now().getEpochSecond
    Rootstore.aliveNodes = ListBuffer((11, curr, "addres123", 10))
    Rootstore.filterMap()
    val newMap = Rootstore.map
    assert(newMap.root.get.weight == 11) // only osd11 is alive
  }

  test("Test filtering of nodes none") {
    val osd11 = OSD("osd11", 11.0, 11)
    val osd12 = OSD("osd12", 12.0, 12)
    val osd13 = OSD("osd13", 13.0, 13)

    val osd21 = OSD("osd21", 21.0, 21)
    val osd22 = OSD("osd22", 22.0, 22)
    val osd23 = OSD("osd23", 23.0, 23)

    val osd31 = OSD("osd31", 31.0, 31)
    val osd32 = OSD("osd32", 32.0, 32)
    val osd33 = OSD("osd33", 33.0, 33)

    val bucket1    = Bucket(List(osd11, osd12, osd13), Uniform(), 1)
    val bucket2    = Bucket(List(osd21, osd22, osd23), Uniform(), 1)
    val bucket3    = Bucket(List(osd31, osd32, osd33), Uniform(), 1)
    val rootBucket = Bucket(List(bucket1, bucket2, bucket3), Straw(), 1)
    val map        = CrushMap(Some(rootBucket), List(Rack(), Leaf()))
    Rootstore.configMap = map
    Rootstore.aliveNodes = ListBuffer()
    Rootstore.filterMap()
    val newMap = Rootstore.map
    assert(newMap.root.isEmpty) // no nodes alive
  }

  test("Test filtering of nodes double") {
    val osd11 = OSD("osd11", 11.0, 11)
    val osd12 = OSD("osd12", 12.0, 12)
    val osd13 = OSD("osd13", 13.0, 13)

    val osd21 = OSD("osd21", 21.0, 21)
    val osd22 = OSD("osd22", 22.0, 22)
    val osd23 = OSD("osd23", 23.0, 23)

    val osd31 = OSD("osd31", 31.0, 31)
    val osd32 = OSD("osd32", 32.0, 32)
    val osd33 = OSD("osd33", 33.0, 33)

    val bucket1    = Bucket(List(osd11, osd12, osd13), Uniform(), 1)
    val bucket2    = Bucket(List(osd21, osd22, osd23), Uniform(), 1)
    val bucket3    = Bucket(List(osd31, osd32, osd33), Uniform(), 1)
    val rootBucket = Bucket(List(bucket1, bucket2, bucket3), Straw(), 1)
    val map        = CrushMap(Some(rootBucket), List(Rack(), Leaf()))
    Rootstore.configMap = map
    val curr = Instant.now().getEpochSecond
    Rootstore.aliveNodes = ListBuffer((11, curr, "addres123", 10), (32, curr, "123", 10))
    Rootstore.filterMap()
    val newMap = Rootstore.map
    assert(newMap.root.get.weight == 11 + 32) // only osd11 and osd32 are alive
  }

  test("Purge dead nodes") {
    val curr = Instant.now().getEpochSecond
    Rootstore.aliveNodes = ListBuffer((11, curr, "addres123", 10), (32, curr - 31, "123", 10))
    Rootstore.purgeDeadNodes()
    val alives = Rootstore.aliveNodes
    assert(alives.length == 1)
    assert(alives.head._1 == 11)
  }

  test("add alive nodes already") {
    val curr = Instant.now().getEpochSecond
    Rootstore.aliveNodes = ListBuffer((11, curr, "addres123", 10), (32, curr - 10, "123", 10))
    Rootstore.addAlive(32, "bob", 10, initialized = true)
    Rootstore.purgeDeadNodes()
    val aliveNodes = Rootstore.aliveNodes
    assert(aliveNodes.length == 2)
    assert(aliveNodes.exists(_._3 == "bob"))
  }

  test("add alive nodes new") {
    val curr = Instant.now().getEpochSecond
    Rootstore.aliveNodes = ListBuffer((11, curr, "addres123", 10))
    Rootstore.addAlive(32, "bob", 10, initialized = true)
    Rootstore.purgeDeadNodes()
    val alives = Rootstore.aliveNodes
    assert(alives.length == 2)
    assert(alives.exists(_._3 == "bob"))
  }

  test("add alive nodes multiple") {
    val curr = Instant.now().getEpochSecond
    Rootstore.aliveNodes = ListBuffer((11, curr, "addres123", 10), (32, curr - 10, "1234", 10))
    Rootstore.addAlive(32, "bob", 10, initialized = true)
    Rootstore.purgeDeadNodes()
    val alives = Rootstore.aliveNodes
    assert(alives.length == 2)
    assert(alives.exists(_._3 == "bob"))
  }
  test("test check") {
    val osd11 = OSD("osd11", 1.0, 11)
    val osd12 = OSD("osd12", 1.0, 12)
    val osd13 = OSD("osd13", 1.0, 13)

    val osd21 = OSD("osd21", 2.0, 21)
    val osd22 = OSD("osd22", 2.0, 22)
    val osd23 = OSD("osd23", 2.0, 23)

    val osd31 = OSD("osd31", 3.0, 31)
    val osd32 = OSD("osd32", 3.0, 32)
    val osd33 = OSD("osd33", 3.0, 33)

    val bucket1    = Bucket(List(osd11, osd12, osd13), Uniform(), 1)
    val bucket2    = Bucket(List(osd21, osd22, osd23), Uniform(), 1)
    val bucket3    = Bucket(List(osd31, osd32, osd33), Uniform(), 1)
    val rootBucket = Bucket(List(bucket1, bucket2, bucket3), Straw(), 1)
    val map        = CrushMap(Some(rootBucket), List(Rack(), Rack(), Leaf()))
    val steps      = List(Select(2, Rack()), Select(2, Rack()), Emit())
    val rule       = PlacementRule(steps)
    val selected   = Crush.crush(123, map, rule)
    val checkP     = Crush.osdCheckPlacement(123, map, rule, 0, selected)
    assert(!checkP._1) // should not change
  }
  test("test check with space change") {
    val osd11 = OSD("osd11", 1.0, 11)
    val osd12 = OSD("osd12", 1.0, 12)
    val osd13 = OSD("osd13", 1.0, 13)

    val osd21 = OSD("osd21", 2.0, 21)
    val osd22 = OSD("osd22", 2.0, 22)
    val osd23 = OSD("osd23", 2.0, 23)

    val osd31 = OSD("osd31", 3.0, 31)
    val osd32 = OSD("osd32", 3.0, 32)
    val osd33 = OSD("osd33", 3.0, 33)

    val bucket1    = Bucket(List(osd11, osd12, osd13), Uniform(), 1)
    val bucket2    = Bucket(List(osd21, osd22, osd23), Uniform(), 1)
    val bucket3    = Bucket(List(osd31, osd32, osd33), Uniform(), 1)
    val rootBucket = Bucket(List(bucket1, bucket2, bucket3), Straw(), 1)
    val map        = CrushMap(Some(rootBucket), List(Rack(), Rack(), Leaf()))
    val steps      = List(Select(2, Rack()), Select(2, Rack()), Emit())
    val rule       = PlacementRule(steps)
    val selected   = Crush.crush(123, map, rule)
    selected.foreach(node => node.space -= 3) // file is stored there, so remove space
    val checkP = Crush.osdCheckPlacement(123, map, rule, 3, selected)
    assert(!checkP._1) // should not change
  }

  test("test check with osd space removal") {
    val osd11 = OSD("osd11", 1.0, 11)
    val osd12 = OSD("osd12", 1.0, 12)
    val osd13 = OSD("osd13", 1.0, 13)

    val osd21 = OSD("osd21", 2.0, 21)
    val osd22 = OSD("osd22", 2.0, 22)
    val osd23 = OSD("osd23", 2.0, 23)

    val osd31 = OSD("osd31", 3.0, 31)
    val osd32 = OSD("osd32", 3.0, 32)
    val osd33 = OSD("osd33", 3.0, 33)

    val bucket1    = Bucket(List(osd11, osd12, osd13), Uniform(), 1)
    val bucket2    = Bucket(List(osd21, osd22, osd23), Uniform(), 1)
    val bucket3    = Bucket(List(osd31, osd32, osd33), Uniform(), 1)
    val rootBucket = Bucket(List(bucket1, bucket2, bucket3), Straw(), 1)
    val map        = CrushMap(Some(rootBucket), List(Rack(), Rack(), Leaf()))
    val steps      = List(Select(2, Rack()), Select(2, Rack()), Emit())
    val rule       = PlacementRule(steps)
    val selected   = Crush.crush(123, map, rule)
    selected.head.space -= 10
    selected.foreach(node => node.space -= 3) // file is stored there, so remove space
    val checkP = Crush.osdCheckPlacement(123, map, rule, 3, selected)
    assert(checkP._1) // should change, because a node is 'removed'
  }

  test("test check with osd removal") {
    var selected: List[Node] = Nil
    var map2                 = CrushMap(None, Nil)

    {
      val osd11 = OSD("osd11", 1.0, 11)
      val osd12 = OSD("osd12", 1.0, 12)
      val osd13 = OSD("osd13", 1.0, 13)

      val osd21 = OSD("osd21", 1.0, 21)
      val osd22 = OSD("osd22", 1.0, 22)
      val osd23 = OSD("osd23", 1.0, 23)

      val osd31 = OSD("osd31", 1.0, 31)
      val osd32 = OSD("osd32", 1.0, 32)
      val osd33 = OSD("osd33", 1.0, 33)

      val bucket1    = Bucket(List(osd11, osd12, osd13), Uniform(), 1)
      val bucket2    = Bucket(List(osd21, osd22, osd23), Uniform(), 1)
      val bucket3    = Bucket(List(osd31, osd32, osd33), Uniform(), 1)
      val rootBucket = Bucket(List(bucket1, bucket2, bucket3), Straw(), 1)
      val map        = CrushMap(Some(rootBucket), List(Rack(), Rack(), Leaf()))
      val steps      = List(Select(2, Rack()), Select(2, Rack()), Emit())
      val rule       = PlacementRule(steps)
      selected = Crush.crush(123, map, rule, 3)
    }
    {

      val osd11 = OSD("osd11", 1.0, 11)
      val osd12 = OSD("osd12", 1.0, 12)
      val osd13 = OSD("osd13", 1.0, 13)

      val osd21 = OSD("osd21", 1.0, 21)
      val osd23 = OSD("osd23", 1.0, 23)

      val osd31 = OSD("osd31", 1.0, 31)
      val osd32 = OSD("osd32", 1.0, 32)

      val bucket1    = Bucket(List(osd11, osd12, osd13), Uniform(), 1)
      val bucket2    = Bucket(List(osd21, osd23), Uniform(), 1)
      val bucket3    = Bucket(List(osd31, osd32), Uniform(), 1)
      val rootBucket = Bucket(List(bucket1, bucket2, bucket3), Straw(), 1)
      map2 = CrushMap(Some(rootBucket), List(Rack(), Rack(), Leaf()))

    }

    val steps  = List(Select(2, Rack()), Select(2, Rack()), Emit())
    val rule   = PlacementRule(steps)
    val checkP = Crush.osdCheckPlacement(123, map2, rule, 3, selected)
    assert(checkP._1) // should change, because a node is removed
  }
}
