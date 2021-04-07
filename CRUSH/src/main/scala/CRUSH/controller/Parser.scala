package CRUSH.controller

import CRUSH.utils.crushmap._
import com.typesafe.config.{ ConfigList, ConfigObject }

import scala.collection.mutable.ListBuffer

object Parser {
  def parse(obj: ConfigObject): Node = {
    val bucketType = obj.get("bucketType").unwrapped()
    val id         = obj.get("id").unwrapped().asInstanceOf[Int]
    if (bucketType == "osd") {
      OSD(
        "address",
        obj.get("weight").unwrapped().asInstanceOf[Int],
        id
      ) // address needs to come from osd when connecting
    } else {
      val bucketItems = obj.get("items").asInstanceOf[ConfigList]
      val bucketList  = ListBuffer[Node]()
      0 until bucketItems.size() foreach (index => {
        val bucketItem = bucketItems.get(index).asInstanceOf[ConfigObject]
        bucketList += parse(bucketItem)
      })
      var bucketMode: BucketType = Straw()
      if (bucketType == "uniform") {
        bucketMode = Uniform()
      }
      Bucket(bucketList.toList, bucketMode, id)
    }

  }

  def parseRule(obj: ConfigObject): PlacementRule = {
    val ruleList = obj.get("placement").asInstanceOf[ConfigList]
    val stepList = ListBuffer[PlacementRuleStep]()
    0 until ruleList.size() foreach (index => {
      val item = ruleList.get(index).asInstanceOf[ConfigObject]
      stepList += parseRuleItem(item)
    })
    PlacementRule(stepList.toList)
  }
  def parseRuleItem(obj: ConfigObject): PlacementRuleStep = {
    obj.get("type").unwrapped().asInstanceOf[String] match {
      case "select" =>
        Select(obj.get("amount").unwrapped().asInstanceOf[Int], Rack()) // the hierarchy level does not matter anywhere
      case "emit" => Emit()
    }
  }

}
