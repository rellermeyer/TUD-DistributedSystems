package nl.tudelft.IN4391G4.machines

import java.io._
import java.util
import java.util.UUID

import nl.tudelft.IN4391G4.messages.JobMessages.JobResult
import nl.tudelft.IN4391G4.messages.MachineState


trait Job {
  // binary etc
  val id: UUID

  def execute: JobResult
}

case class JavaJob(id: UUID, binary: Array[Byte], cmdArgs: String) extends Job {
  override def execute: JobResult = {
    val filePath = s"${id}.jar"
    val file = new File(filePath)
    val outputStream = new BufferedOutputStream(new FileOutputStream(file))
    outputStream.write(binary)
    outputStream.close()
    new ProcessBuilder("", "")
    val list = new util.ArrayList[String]()
    list.add("java")
    list.add("-jar")
    list.add(filePath)
    val array = cmdArgs.split(" ")
    array.foreach(list.add)
    list.addAll(util.Arrays.asList())

    val pb = new java.lang.ProcessBuilder(list)
    val start = System.currentTimeMillis()
    val process = pb.start()
    val bufferedInputReader = new BufferedReader(new InputStreamReader(process.getInputStream))
    val bufferedErrorReader = new BufferedReader(new InputStreamReader(process.getErrorStream))
    var line: String = null
    val outputStringBuilder = new StringBuilder()
    val errorStringBuilder = new StringBuilder()
    while ({line = bufferedInputReader.readLine; line != null}) {
      outputStringBuilder.append(line)
    }
    while ({line = bufferedErrorReader.readLine; line != null}) {
      errorStringBuilder.append(line)
    }
    val status = process.waitFor
    val time = System.currentTimeMillis() - start
    file.delete()
    JobResult(time, status, outputStringBuilder.toString(), errorStringBuilder.toString())
  }
}
case class LambdaJob(id: UUID, fn: () => String) extends Job {

  override def execute: JobResult = {
    val start = System.currentTimeMillis()
    val result = fn()
    val time = System.currentTimeMillis() - start
    JobResult(time, 0, result, "")
  }
}

case class JobContext(jobId: UUID, requiredState: MachineState) {
  override def toString(): String = {
    s"JobContext $jobId - $requiredState"
  }
}