import scala.collection.JavaConverters._

// Definition of a master

object MasterStart {
  def main(args: Array[String]): Unit = {

    val parseString = args(0)
    val subString = parseString.substring(1, parseString.length - 1).replaceAll("\\s", "")
    val resultList = subString.split(',').toList
    val master = new MyMaster(resultList.asJava)
    master.run()
  }
}
