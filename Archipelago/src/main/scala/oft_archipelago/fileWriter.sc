import java.nio.file.{Files, Paths}
import scala.collection.mutable.ArrayBuffer
import scala.io.Source


val filename = "C:\\Users\\Th3o0\\Desktop\\University\\MSC\\IN4391 - Distributed Systems - Q3 2022\\Assignment\\DS\\project\\adopt_commit_max\\file.txt"
val fileSource = Source.fromFile(filename)
var failures = 0.0
var successes = 0.0
var rounds : ArrayBuffer[Double] = new ArrayBuffer[Double]()
var rounds_sq : ArrayBuffer[Double] = new ArrayBuffer[Double]()
var mean = 0.0
var variance = 0.0
for(line<-fileSource.getLines){
  println(line)
  var k = line.split(" ")
  if(k(0).toInt == 1){
    successes += 1.0
    rounds  +=  k(1).toInt

  }
  else{
    failures += 1.0
  }
}
println(rounds)
mean = rounds.sum/(rounds.length)
for(i <- 0 until rounds.length){
  rounds_sq += (rounds(i) - mean)*(rounds(i) - mean)
  variance = rounds_sq.sum/(rounds.length-1)
}
println("failures: ",failures)
println("successes: ",successes)
println("success %: ", successes * 100/(successes + failures))
println("mean rounds: ",mean)
println("variance: ",variance)
fileSource.close()
Files.deleteIfExists(Paths.get(filename))