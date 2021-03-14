import FileSystem.{Container, DistributedSystem, Representative}

object Temp {
  def main(args: Array[String]): Unit = {

    //Create a test "distributed" file system
    val fileSystem = DistributedSystem(0.0)
    fileSystem.createContainers(Seq(0, 2, 1, 4, 3))
    fileSystem.createSuite(1, 1, 1, Seq(0, 2, 1, 4, 3))

    //This demonstrates that you can gather a correct response,
    //and that find latest picks the first correct option in the list
    //Note that response also contains the prefixes, but those are not printed
    var response = fileSystem.collectSuite(1)
    println(response)
    println(response.findLatest())
    println(response.findReadQuorum(30, 0))

    //This demonstrates that findLatest correctly returns the rep that has been updated
    fileSystem.writeSuite(Seq(2), 1, 1)
    response = fileSystem.collectSuite(1)
    println(response.findLatest())

    println(response.findReadQuorum(30, 1))
  }
}
