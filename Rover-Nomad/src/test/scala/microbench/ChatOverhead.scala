package microbench

import java.io.{BufferedWriter, FileWriter}

import au.com.bytecode.opencsv.CSVWriter
import chatapp.{ChatAppClient, ChatServer, ChatUser}
import chatapp.model.{Chat, ChatMessage}
import utilities.{Results, Utilities}

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Random

trait BenchmarkableChat {
    def send(message: ChatMessage): Future[Unit]
}

class PrimitiveChat extends BenchmarkableChat {
    
    private var chat = List[ChatMessage]()
    
    override def send(message: ChatMessage): Future[Unit] = {
        return Future({chat = chat :+ message})
    }
}

class RdObjectChat(val chat: Chat) extends BenchmarkableChat {
    override def send(message: ChatMessage): Future[Unit] = {
        return chat.send(message)
    }
}

class ChatOverheadMicroBench(val numRepetitions: Int,
                             val maxMessageLength: Int) {
    
    def rountripTimesBenchmark(testSet: List[ChatMessage]): Results = {
        var results = new Results()
        
        // start server
        val server = ChatServer.start()
        
        // start client
        val client = new ChatAppClient("http://localhost:8080")
        Await.ready(client.login(ChatUser.Steffan), Duration.Inf)
        
        
        var iteration = 0
        for (msg <- testSet) {
            iteration += 1
            println(s"Roundtrip iteration ${iteration}")
            
            val tick = System.nanoTime()
            
            Await.ready(client.send(msg.body), Duration.Inf)
            
            val tock = System.nanoTime()
    
            val timeTakenForMessage = tock - tick
            results = results.withAddedResult(timeTakenForMessage)
        }
        
        return results
    }
    
    def rountripTimesConcurrentUsersBenchmark(testSet: List[ChatMessage]): Results = {
        var resultsSteffan = new Results()
        var resultsGiannis = new Results()
        
        // start server
        val server = ChatServer.start()
        
        // start client
        val clientSteffan = new ChatAppClient("http://localhost:8080")
        Await.ready(clientSteffan.login(ChatUser.Steffan), Duration.Inf)
    
        val clientGiannis = new ChatAppClient("http://localhost:8080")
        Await.ready(clientGiannis.login(ChatUser.Giannis), Duration.Inf)
        
        
        var iteration = 0
        for (msg <- testSet) {
            iteration += 1
            println(s"Roundtrip iteration ${iteration}")
    
            // this client sends something
            val giannisSendAction = clientGiannis.send(msg.body + "1")
            
            val tick = System.nanoTime()

                Await.ready(clientSteffan.send(msg.body + "1"), Duration.Inf)

            val tock = System.nanoTime()
            
            Await.ready(giannisSendAction, Duration.Inf)

            val timeTakenForMessage = tock - tick
            resultsSteffan = resultsSteffan.withAddedResult(timeTakenForMessage)
        }
        
        return resultsSteffan
    }

    def generateRandomMessages(numMessages: Int, maxMessageLength: Int): List[ChatMessage] = {
        var messageLength : Int = 0
        var messageBody: String = null
        var randomMessages = List[ChatMessage]()

        for (i <- Range.inclusive(1, numMessages)) {
            messageLength = Random.nextInt(maxMessageLength)
            messageBody = Random.alphanumeric.take(messageLength).mkString
            randomMessages = randomMessages :+ new ChatMessage(messageBody, ChatUser.System)
        }

        return randomMessages
    }
    
    def benchmarkChatPerformance(impl: BenchmarkableChat, testSet: List[ChatMessage]): Results = {
        var results = new Results()
    
        for (msg <- testSet) {
            val tick = System.nanoTime()
        
            Await.ready(impl.send(msg), Duration.Inf)
        
            val tock = System.nanoTime()
            
            val timeTakenForMessage = tock - tick
            results = results.withAddedResult(timeTakenForMessage)
        }
    
        return results
    }
    
    def runTimeOverhead(testSet: List[ChatMessage]): Unit = {
        println("Benchmark time overhead")
        
        // warmup
        benchmarkChatPerformance(new PrimitiveChat, testSet)
    
        // real
        val primitiveChatDurationPerMsg = benchmarkChatPerformance(new PrimitiveChat, testSet)
    
        // warmup
        benchmarkChatPerformance(new RdObjectChat(Chat.initial()), testSet)
    
        // real
        val rdoChatDurationPerMsg = benchmarkChatPerformance(new RdObjectChat(Chat.initial()), testSet)
    
    
        println("Duration per adding msg")
        println(s"   RdObject: ${rdoChatDurationPerMsg}")
        println(s"   Primitive: ${primitiveChatDurationPerMsg}")
    
        val csv = toCSV(testSet, primitiveChatDurationPerMsg, rdoChatDurationPerMsg)
    
    
        println(s"RdoChat: mean time in nano: ${rdoChatDurationPerMsg.mean}, std: ${rdoChatDurationPerMsg.stdDev}")
        println(s"    Ops/s: ${Utilities.oneSecInNano/rdoChatDurationPerMsg.mean}\n\n")
    
        println(s"PrimiveChat: mean time in nano ${primitiveChatDurationPerMsg.mean}, std: ${primitiveChatDurationPerMsg.stdDev}")
        println(s"    Ops/s: ${Utilities.oneSecInNano/primitiveChatDurationPerMsg.mean}\n\n")
    
        println("\n")
        println(f"Overhead: ${Utilities.getOverhead(primitiveChatDurationPerMsg.mean, rdoChatDurationPerMsg.mean)}%1.4f")
    }

    
    def runSizeOverhead(testSet: List[ChatMessage]): Unit = {
    
        println("\n\n")
        println("Benchmark Size overhead")
        
        /* scenarios under test: */
        // The RdObject (its atomic state is measured, not the whole thing)
        val rdObject = Chat.initial()
    
        // Primitive chat (justa list of ChatMessages)
        var primitiveObject = List[ChatMessage]()
    
        /* For results */
        var rdoSizes = new Results
        var primitiveSizes = new Results
    
        for (msg <- testSet) {
        
            Await.ready(rdObject.send(msg), Duration.Inf)
            primitiveObject = primitiveObject :+ msg
        
            // todo: get delta? As new object will probably contain the previous?
            val memorySizeInBytesOfAtomicObjectState = Utilities.sizeOf(rdObject.state)
            val memorySizeInBytesOfPrimitveObjectState = Utilities.sizeOf(primitiveObject)
        
            rdoSizes = rdoSizes.withAddedResult(memorySizeInBytesOfAtomicObjectState)
            primitiveSizes = primitiveSizes.withAddedResult(memorySizeInBytesOfPrimitveObjectState)
        
            //println(s"rover-size: ${rdoSizes.last}")
            //println(s"non-rover-size: ${primitiveSizes.last}")
        }
    
        println(s"Mean rover size: ${rdoSizes.mean}, with std: ${rdoSizes.stdDev}")
        println(s"Mean primitive size: ${primitiveSizes.mean}, with std: ${primitiveSizes.stdDev}")
    
        println(s"Size-overhead: ${Utilities.getOverhead(primitiveSizes.mean, rdoSizes.mean)}")
    }
    
    def runRoundtripTime(testSet: List[ChatMessage]): Unit = {
        println("\n\n")
        println("Benchmark rountrip times")
        val roundtripTimes = rountripTimesBenchmark(testSet)
        
        println(roundtripTimes)
    }

    def runAll: Unit = {
        val randomMessages = generateRandomMessages(numRepetitions, maxMessageLength)
    
        runTimeOverhead(randomMessages)
        runSizeOverhead(randomMessages)
        runRoundtripTime(randomMessages.slice(0, 19))
        rountripTimesConcurrentUsersBenchmark(randomMessages.slice(0, 19))
    }

    def toCSV(messages: List[ChatMessage], baselineDurations: Results, compareDurations: Results): Unit = {
    
        val nowInEpochSeconds = java.time.Instant.now.getEpochSecond
        
        val outputFile = new BufferedWriter(new FileWriter(s"./results_chat_${nowInEpochSeconds}.csv"))
        val csvWriter = new CSVWriter(outputFile)
        
        val csvHeader = Array("id", "messages", "nonRoverDurations", "roverDurations")
        var listOfRecords = new ListBuffer[Array[String]]() :+ csvHeader
        
        for (i <- Range.inclusive(1, messages.length)) {
            listOfRecords += Array(
                i.toString,
                messages(i-1).toString,
                baselineDurations(i-1).toString,
                compareDurations(i-1).toString)
        }
        
        csvWriter.writeAll(listOfRecords.toList)
        outputFile.close()
    }

}

object ChatOverheadMicroBench {
    def main(args: Array[String]): Unit = {

        val microBench = new ChatOverheadMicroBench(100, 100)
        microBench.runAll
    }
}
