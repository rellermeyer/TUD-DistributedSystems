import FileSystem.{Container, DistributedSystem, Representative}

object Temp {
  def main(args: Array[String]): Unit = {
    val fileSystem = DistributedSystem(0.0)
    fileSystem.createContainers(Seq(1, 2, 3, 4, 5))
    fileSystem.createSuite(1, 1, 1, Seq(0, 1, 2, 3, 4))
    fileSystem.createSuite(2, 3, 2, Seq(5, 6, 7, 8, 9))
    println(fileSystem.collectSuite(1))
    println(fileSystem.collectSuite(2))
    fileSystem.writeSuite(Seq(0, 2, 4), 1, 1)
    fileSystem.writeSuite(Seq(1, 3), 2, 2)
    println(fileSystem.collectSuite(1))
    println(fileSystem.collectSuite(2))
  }
}
