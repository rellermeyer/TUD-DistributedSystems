package utilities

import java.io.{ByteArrayOutputStream, ObjectOutputStream}

import scala.util.Random

object Utilities {
    val oneSecInNano = math.pow(10, 9)

    def getMean(durations: List[Long]): Long = {
        return durations.sum / durations.length
    }

    def getStd(durations: List[Long]): Double = {
        val meanBenchTime = getMean(durations)
        val sqrt = math.sqrt(durations.foldLeft(0.asInstanceOf[Long])((total, current) =>
            total + (current - meanBenchTime) * (current - meanBenchTime)) / (durations.length -1))
        return sqrt
    }

    def getOverhead(meanBaselineDuration: Double, meanCompareDuration: Double) : Double = {
        return math.abs(meanBaselineDuration - meanCompareDuration) / meanBaselineDuration
    }

    def generateRandomInts(size: Int, maxValue: Int): List[Int] = {
        var randomInts = List[Int]()

        Range.inclusive(1, size).foreach(_ => {
            randomInts = randomInts :+ Random.nextInt(maxValue)
        })
        return randomInts
    }

    def sizeOf(obj: AnyRef): Long = {
        val byteOutputStream = new ByteArrayOutputStream()
        val objectOutputStream = new ObjectOutputStream(byteOutputStream)

        objectOutputStream.writeObject(obj)
        objectOutputStream.flush()
        objectOutputStream.close()

        return byteOutputStream.toByteArray.length
    }
}

