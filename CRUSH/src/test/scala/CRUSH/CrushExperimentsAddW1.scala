package CRUSH

import CRUSH.utils.crushmap.{
  Bucket,
  BucketType,
  CrushMap,
  CrushObject,
  Emit,
  Node,
  OSD,
  PlacementRule,
  Rack,
  Select,
  Straw,
  Uniform
}
import org.scalatest.ParallelTestExecution
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable.ListBuffer
import scala.util.Random

class CrushExperimentsAddW1 extends AnyFunSuite with ParallelTestExecution {
  var id = 0

  def createRandomObjects(amount: Int): List[CrushObject] = {
    var objectList = ListBuffer[CrushObject]()
    val r          = new Random()
    0 until amount foreach (i => {
      objectList += CrushObject(r.nextInt(Int.MaxValue), r.nextInt(10), i.toString)
    })
    objectList.toList
  }

  def createCrushMapBucket(map: List[Int], bucketTypes: List[BucketType], r: Random, randomW: Boolean): Bucket = {
    val outList = ListBuffer[Node]()

    if (map.length == 1) {
      0 until map.head foreach (i => {
        id += 1
        outList += OSD(i.toString, if (randomW) r.nextInt(5) + 1 else 10, id)
      })
    } else {
      0 until map.head foreach (_ => {
        outList += createCrushMapBucket(map.tail, bucketTypes.tail, r, randomW)
      })
    }
    id += 1
    Bucket(outList.toList, bucketTypes.head, id)
  }

  def createCrushMap(map: List[Int], bucketTypes: List[BucketType], it: Int, randomW: Boolean): CrushMap = {
    val b = createCrushMapBucket(map, bucketTypes, new Random(it), randomW)
    CrushMap(Some(b), Nil)
  }

  def addRandomOSDOneW(map: CrushMap, r: Random): CrushMap = {
    var node: Node = OSD("", 2)
    map.root match {
      case None       => return map
      case Some(root) => node = root
    }
    node match {
      case b: Bucket => CrushMap(Some(addRandomOSD(b, r)), map.levels)
      case _         => map
    }
  }

  def addRandomOSD(b: Bucket, r: Random): Bucket = {
    val newBL   = ListBuffer[Node]()
    val changeI = r.nextInt(b.children.length)
    var currI   = 0
    b.children.foreach(obj => {
      if (changeI == currI) {
        obj match {
          case bucket: Bucket => newBL += addRandomOSD(bucket, r);
          case o: OSD         => newBL += o; id += 1; newBL += OSD("NEW", 1, id)
        }
      } else {
        newBL += obj
      }
      currI += 1
    })
    Bucket(newBL.toList, b.bucketType, b.id)
  }

  def calculateMove(orig: List[(CrushObject, List[Node])], moved: List[(CrushObject, List[Node])]): Int = {
    orig
      .zip(moved)
      .map(tuple => {
        calculateDiff(tuple._1._2, tuple._2._2)
      })
      .sum
  }

  def calculateDiff(orig: List[Node], moved: List[Node]): Int = {
    moved.diff(orig).length
  }

  test("experiment add 1W straw 1/100") {
    var moves = 0
    0 until 100 foreach (i => {
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](100), List(Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = addRandomOSDOneW(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      moves += add
    })
    print("Total moves add 1W straw 1/100:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }

  test("experiment add 1W straw 5/100") {
    var moves = 0
    0 until 100 foreach (i => {
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](100), List(Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(5, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = addRandomOSDOneW(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      moves += add
    })
    print("Total moves add 1W straw 5/100:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }

  test("experiment add 1W uniform 1/100") {
    var moves = 0
    0 until 100 foreach (i => {
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](100), List(Uniform()), i, randomW = false)
      val rules           = PlacementRule(List(Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = addRandomOSDOneW(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      moves += add
    })
    print("Total moves add 1W uni 1/100:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment add 1W uniform 2/100") {
    var moves = 0
    0 until 100 foreach (i => {
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](100), List(Uniform()), i, randomW = false)
      val rules           = PlacementRule(List(Select(2, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = addRandomOSDOneW(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      moves += add
    })
    print("Total moves add 1W uni 2/100:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)

  }
  test("experiment add 1W straw 2/100") {
    var moves = 0
    0 until 100 foreach (i => {
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](100), List(Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(2, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = addRandomOSDOneW(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      moves += add
    })
    print("Total moves add 1W straw 2/100:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment add 1W straw 2*1/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Straw(), Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(2, Rack()), Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = addRandomOSDOneW(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      moves += add
    })
    print("Total moves add 1W straw 2*1/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment add 1W uniform 2*1/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Uniform(), Uniform()), i, randomW = false)
      val rules           = PlacementRule(List(Select(2, Rack()), Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = addRandomOSDOneW(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      moves += add
    })
    print("Total moves add 1W uni 2*1/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment add 1W straw 2*2/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Straw(), Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(2, Rack()), Select(2, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = addRandomOSDOneW(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      moves += add
    })
    print("Total moves add 1W straw 2*2/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment add 1W uniform 2*2/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Uniform(), Uniform()), i, randomW = false)
      val rules           = PlacementRule(List(Select(2, Rack()), Select(2, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = addRandomOSDOneW(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      moves += add
    })
    print("Total moves add 1W uni 2*2/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment add 1W uniform 1*2/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Uniform(), Uniform()), i, randomW = false)
      val rules           = PlacementRule(List(Select(1, Rack()), Select(2, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = addRandomOSDOneW(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      moves += add
    })
    print("Total moves add 1W uni 1*2/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment add 1W straw 1*2/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Straw(), Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(1, Rack()), Select(2, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = addRandomOSDOneW(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      moves += add
    })
    print("Total moves add 1W straw 1*2/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }

  test("test small 1W") {
    val files = createRandomObjects(20000)
    println("done generating files")
    val crushMap = createCrushMap(List[Int](3, 3), List(Straw(), Straw()), 10, randomW = false)
    println("done generating map")
    val rules          = PlacementRule(List(Select(3, Rack()), Select(2, Rack())))
    val filesLocations = files.map(obj => (obj, Crush.crush(obj.hash, crushMap, rules)))
    println("done placing 1")
    val newCrushMap = addRandomOSDOneW(crushMap, new Random())
    println("done adding node")
    val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
    println("done placing 2")
    println(calculateMove(filesLocations, filesLocations2))
  }

  test("experiment add 1W straw 1*1/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Straw(), Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(1, Rack()), Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = addRandomOSDOneW(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      moves += add
    })
    print("Total moves add 1W straw 1*1/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment add 1W uniform 1*1/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Uniform(), Uniform()), i, randomW = false)
      val rules           = PlacementRule(List(Select(1, Rack()), Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = addRandomOSDOneW(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      moves += add
    })
    print("Total moves add 1W uni 1*1/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }

}
