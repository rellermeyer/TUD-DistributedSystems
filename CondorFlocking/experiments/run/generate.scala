import java.io.File
import java.nio.file.Files
import java.util.{Random, UUID}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

import scala.io.Source



object bags {

  def generate(file: String, jobs: Int, min: Int, max: Int): Unit ={
    val r = new Random()
    val jar = "job.jar"
    val sb = StringBuilder.newBuilder
    for(i <- 0 until jobs){
      val id = new UUID(0, i)
      val time: Long = min + r.nextInt(max - min) + 1
      sb.append(f"${id};${jar};${time}\n")

    }
    println(sb)
    Files.write(Paths.get(file), sb.toString().getBytes(StandardCharsets.UTF_8))
  }

  def main(args: Array[String]): Unit = {
    generate("many-short.bag", 800, 1000, 5000)
    generate("few-long.bag", 200, 30000, 60000)
    generate("few-short.bag", 200, 1000, 5000)
  }

}