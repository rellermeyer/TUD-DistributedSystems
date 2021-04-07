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
import org.scalatest.funsuite.AnyFunSuite

import java.io.PrintWriter
import scala.collection.mutable.ListBuffer
import scala.util.Random

class CrushExperiments extends AnyFunSuite {
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

  def removeRandomOSD(map: CrushMap, r: Random): CrushMap = {
    var node: Node = OSD("", 2)
    map.root match {
      case None       => return map
      case Some(root) => node = root
    }
    node match {
      case b: Bucket => CrushMap(Some(removeRandomOSD(b, r)), map.levels)
      case _         => map
    }
  }

  def removeRandomOSD(b: Bucket, r: Random): Bucket = {
    val newBL   = ListBuffer[Node]()
    val changeI = r.nextInt(b.children.length)
    var currI   = 0
    b.children.foreach(obj => {
      if (changeI == currI) {
        obj match {
          case bucket: Bucket => newBL += removeRandomOSD(bucket, r)
          case _              => // it's an OSD, so do nothing
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

  test("test functions") {
    println(createRandomObjects(100))
    println(createCrushMap(List[Int](1, 2, 3), List[BucketType](Straw(), Straw(), Uniform()), 8, randomW = true))
    assert(calculateDiff(List(OSD("", 2, 2, 2)), List(OSD("", 2, 2, 2))) == 0)
  }

  test("experiment removal straw 1/100") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](100), List(Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves straw 1/100:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }

  test("experiment removal straw 5/100") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](100), List(Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(5, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves straw 5/100:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }

  test("experiment removal uniform 1/100") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](100), List(Uniform()), i, randomW = false)
      val rules           = PlacementRule(List(Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves uni 1/100:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment removal uniform 2/100") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](100), List(Uniform()), i, randomW = false)
      val rules           = PlacementRule(List(Select(2, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves uni 2/100:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment removal straw 2/100") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](100), List(Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(2, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves straw 2/100:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)

  }
  test("experiment removal straw 2*1/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Straw(), Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(2, Rack()), Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves straw 2*1/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment removal uniform 2*1/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Uniform(), Uniform()), i, randomW = false)
      val rules           = PlacementRule(List(Select(2, Rack()), Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves straw 2*1/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment removal straw 2*2/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Straw(), Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(2, Rack()), Select(2, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves straw 2*2/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment removal uniform 2*2/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Uniform(), Uniform()), i, randomW = false)
      val rules           = PlacementRule(List(Select(2, Rack()), Select(2, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves uni 2*2/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }

  test("experiment removal uniform straw 1*1/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Uniform(), Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(1, Rack()), Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves uni 1*1/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment removal straw 1*1/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Straw(), Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(1, Rack()), Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves straw 1*1/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment removal uniform 1*1/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Uniform(), Uniform()), i, randomW = false)
      val rules           = PlacementRule(List(Select(1, Rack()), Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves uni 1*1/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }

  test("experiment removal uniform straw 1*1/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Uniform(), Straw()), i, randomW = false)
      val rules           = PlacementRule(List(Select(1, Rack()), Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves uni 1*1/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("experiment removal straw uniform 1*1/10*10") {
    var moves = 0
    0 until 100 foreach (i => {
      println("on iteration " + i + " moves:" + moves)
      val files           = createRandomObjects(100000)
      val crushmap        = createCrushMap(List[Int](10, 10), List(Straw(), Uniform()), i, randomW = false)
      val rules           = PlacementRule(List(Select(1, Rack()), Select(1, Rack()), Emit()))
      val filesLocations  = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))
      val newCrushMap     = removeRandomOSD(crushmap, new Random())
      val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
      val add             = calculateMove(filesLocations, filesLocations2)
      println(add / 100000.0)
      moves += add
    })
    println("Total moves straw 1*1/10*10:" + moves)
    println("Average: " + moves / 100.0 / 100000.0)
  }
  test("test small") {
    val files = createRandomObjects(20000)
    println("done generating files")
    val crushMap = createCrushMap(List[Int](3, 3), List(Straw(), Straw()), 10, randomW = false)
    println("done generating map")
    val rules          = PlacementRule(List(Select(3, Rack()), Select(2, Rack())))
    val filesLocations = files.map(obj => (obj, Crush.crush(obj.hash, crushMap, rules)))
    println("done placing 1")
    val newCrushMap = removeRandomOSD(crushMap, new Random())

    println("done removing node")
    val filesLocations2 = files.map(obj => (obj, Crush.crush(obj.hash, newCrushMap, rules)))
    println("done placing 2")
    println(calculateMove(filesLocations, filesLocations2))
  }

  test("experiment mapping time cost, varying depth") { // use experimentData/benchvaryingdepth.py to visualize
    val iterations = 100000
    val writer     = new PrintWriter("experimentData/mapBenchVaryingDepth.txt")
    writer.println("depth straw uniform")

    1 to 8 foreach (i => {
      val counts   = List.fill(i)(8)
      val straws   = List.fill(i)(Straw())
      val Uniforms = List.fill(i)(Uniform())
      val rule     = PlacementRule(List.fill(i)(Select(1, Rack())))

      val strawCrushMap   = createCrushMap(counts, straws, new Random().nextInt(), randomW = true)
      val UniformCrushMap = createCrushMap(counts, Uniforms, new Random().nextInt(), randomW = true)

      0 until iterations foreach (j => {
        val startS = System.nanoTime()
        Crush.crush(j, strawCrushMap, rule)
        val endS           = System.nanoTime()
        val timeTakenStraw = endS - startS

        val startU = System.nanoTime()
        Crush.crush(j, UniformCrushMap, rule)
        val endU             = System.nanoTime()
        val timeTakenUniform = endU - startU

        writer.println(s"$i $timeTakenStraw $timeTakenUniform")
      })
    })
    writer.flush()
  }

  test("experiment mapping time cost, varying bucket size") { // use experimentData/benchvaryingbucketsize.py to visualize
    val iterations = 100000
    val rule       = PlacementRule(List(Select(1, Rack())))
    val writer     = new PrintWriter("experimentData/mapBenchVaryingBucketSize.txt")
    writer.println("bucketsize straw uniform")

    1 to 50 foreach (i => {
      val strawCrushMap   = createCrushMap(List(i), List(Straw()), new Random().nextInt(), randomW = true)
      val UniformCrushMap = createCrushMap(List(i), List(Uniform()), new Random().nextInt(), randomW = true)

      0 until iterations foreach (j => {
        val startS = System.nanoTime()
        Crush.crush(j, strawCrushMap, rule)
        val endS           = System.nanoTime()
        val timeTakenStraw = endS - startS

        val startU = System.nanoTime()
        Crush.crush(j, UniformCrushMap, rule)
        val endU             = System.nanoTime()
        val timeTakenUniform = endU - startU

        writer.println(s"$i $timeTakenStraw $timeTakenUniform")
      })
    })
    writer.flush()
  }

  test("experiment division Straw") { // use experimentData/division.py to visualize(make sure it uses the correct input file)
    val map    = collection.mutable.Map[String, Int]()
    val writer = new PrintWriter("experimentData/divStraw.txt")
    writer.println("bucket amount")
    0 until 100 foreach (i => {
      val files    = createRandomObjects(100000)
      val crushmap = createCrushMap(List[Int](100), List(Straw()), i, randomW = false)

      val rules          = PlacementRule(List(Select(1, Rack()), Emit()))
      val filesLocations = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))

      filesLocations.foreach(item => {
        val address = item._2.head.asInstanceOf[OSD].address
        if (map.contains(address)) {
          map(address) += 1
        } else {
          map(address) = 1
        }
      })
      0 until 100 foreach (i => {
        val a = i.toString
        if (map.contains(a)) {
          val v = map(a)
          writer.println(s"$a $v")
        } else {
          println(a, 0, "weird")
        }
      })
      writer.flush()
    })

  }

  test("experiment division uniform") { // use experimentData/division.py to visualize(make sure it uses the correct input file)
    val writer = new PrintWriter("experimentData/divUni.txt")
    writer.println("bucket amount")
    val map = collection.mutable.Map[String, Int]()
    0 until 100 foreach (i => {
      val files    = createRandomObjects(100000)
      val crushmap = createCrushMap(List[Int](100), List(Uniform()), i, randomW = false)

      val rules          = PlacementRule(List(Select(1, Rack()), Emit()))
      val filesLocations = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))

      filesLocations.foreach(item => {
        val address = item._2.head.asInstanceOf[OSD].address
        if (map.contains(address)) {
          map(address) += 1
        } else {
          map(address) = 1
        }
      })
      0 until 100 foreach (i => {
        val a = i.toString
        if (map.contains(a)) {
          val v = map(a)
          writer.println(s"$a $v")
        } else {
          println(a, 0, "weird")
        }
      })
    })

  }

  test("experiment division sparse uniform") {
    val map = collection.mutable.Map[String, Int]()

    0 until 100 foreach (i => {
      val files    = createRandomObjects(100)
      val crushmap = createCrushMap(List[Int](100), List(Straw()), i, randomW = false)

      val rules          = PlacementRule(List(Select(1, Rack()), Emit()))
      val filesLocations = files.map(obj => (obj, Crush.crush(obj.hash, crushmap, rules)))

      filesLocations.foreach(item => {
        val address = item._2.head.asInstanceOf[OSD].address
        if (map.contains(address)) {
          map(address) += 1
        } else {
          map(address) = 1
        }
      })
    })
    // print the totals
    0 until 100 foreach (i => {
      val a = i.toString
      if (map.contains(a)) {
        println(a, map(a))
      } else {
        println(a, 0, "should not happen")
      }
    })
  }
}
