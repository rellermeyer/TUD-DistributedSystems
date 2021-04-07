package CRUSH.OSD.util

import CRUSH.utils.crushmap.CrushObject
import org.apache.commons.math3.distribution.NormalDistribution
import org.json4s._
import org.json4s.native.Serialization

import scala.annotation.tailrec
import scala.io.Source
import scala.reflect.io.Path
import scala.util.Random

case class ObjectDistribution(objectNameSeed: Int, objectSeed: Int, mean: Int, std: Int)

case class Config(numberOSDs: Int, numberObjects: Int, objectDistribution: ObjectDistribution)


class TestInitialization(configPath: Path) {


  // Needed for the parse to implicitly convert the objects to an Unmarshalled Config object.
  implicit val formats: AnyRef with Formats = Serialization.formats(NoTypeHints)


  /**
   * Filename generator, using printable characters. Recursively calls itself in order to generate the file name.
   * This implementation is based on the implementation by Alvin Alexander, found online:
   * https://alvinalexander.com/scala/creating-random-strings-in-scala/
   *
   * @param n      Length of the file to be generated
   * @param list   Accumulator for the generated file name (when called should be Nil)
   * @param random Implicit random, to allow for pseudorandom number generation.
   * @return A list representation of a random file name.
   */
  @tailrec
  private def recursiveNameGeneration(n: Int, list: List[Char] = Nil)(implicit random: Random): List[Char] = {
    if (n == 1) random.nextPrintableChar() :: list
    else recursiveNameGeneration(n - 1, util.Random.nextPrintableChar :: list)
  }


  /**
   * Function to unmarshal the json file into case classes to generate the objects.
   *
   * @return Config case class representing the contents of the path that the initialization was configured with.
   */
  private def parse(): Config = {
    Serialization.read[Config](Source.fromResource(configPath.toString()).getLines().mkString)
  }

  /**
   * Function to generate in a pseudorandom fashion a list of files, s.t. the Nodes can initialize themselves without
   * having to have a warm-up process with dumping objects to the system.
   *
   * @return A tuple containing the number of OSDs started up and a List of Crush Objects to consider during the
   *         evaluation.
   */
  def generateFiles(): (Int, List[CrushObject]) = {
    val Config(numberOSDs, numberObjects, objectDistribution) = this.parse()
    val sizeGenerator = new NormalDistribution(objectDistribution.mean, objectDistribution.std)
    //     Pseudo randomly generate files
    Random.setSeed(objectDistribution.objectNameSeed)
    val files = (1 to numberObjects).map(_ => {
      val name = recursiveNameGeneration(10, Nil)(Random).mkString
      CrushObject(name.hashCode, sizeGenerator.sample().toInt, name)
    }).toList

    (numberOSDs, files)
  }
}
