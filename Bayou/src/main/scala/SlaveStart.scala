import scala.collection.JavaConverters._

// Definition of a simple slave, sends 3 strings
object SlaveStart {
  def main(args: Array[String]): Unit = {

    val parseString = args(0)
    val subString = parseString.substring(1, parseString.length - 1).replaceAll("\\s", "")
    val resultList = subString.split(',').toList
    val slave = new MySlave(resultList.asJava)
    slave.run()
  }
}