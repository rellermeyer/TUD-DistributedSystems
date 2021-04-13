package main

import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import java.net.{ServerSocket, Socket}
import java.nio.charset.StandardCharsets

import collection.JavaConversions._
import scala.util.control._
import net.{GatedEmbeddingUnit, TemporalAggregation}
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j

object Main extends App {

  val dim1 = 256
  val window_size = 12

  // socket server part settings
  val loop = new Breaks
  val inner_loop = new Breaks
  var msg = new String
  val FRAME_RECEIVE_PORT = 10002
  val listener: ServerSocket = new ServerSocket(FRAME_RECEIVE_PORT)

  println("Socket server is starting...")
  loop.breakable{
    while (true) {
      // create socket
      val socket: Socket = listener.accept()
      var arrays: Array[INDArray] = Array()
      val in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
      val out = new PrintWriter(socket.getOutputStream(), true)
      var input_line: String = in.readLine()
      var query_flag: Boolean = true
      var query_feature: INDArray = null
      var count: Int = 0
      inner_loop.breakable{
        while (input_line != null) {

          count += 1
          if (query_flag) {
            val query_feature_array: Array[Double] = input_line.split(",").map(_.toDouble)
            query_feature = Nd4j.create(query_feature_array).reshape(Array(1, dim1))
            query_flag = false
          } else {
            val frames_array: Array[Double] = input_line.split(",").map(_.toDouble)
            val frames = Nd4j.create(frames_array).reshape(Array(1, dim1))
            arrays :+= frames
          }

          if (count == window_size+1) {
            println("Has received 12 frames...")
            val array_collection: java.util.Collection[INDArray] = arrays.toSeq
            var frames = Nd4j.pile(array_collection)

            // Temporal Aggregation Layer
            val averagePool = new TemporalAggregation
            val temporal_agg = averagePool.run(frames)

            // GEU
            val geu = new GatedEmbeddingUnit
            val embedding1 = geu.run(temporal_agg)
            val embedding2 = geu.run(query_feature)

            // Calculate cosine distance
            val cos_dis = embedding1.mmul(embedding2.reshape(-1)).div(embedding1.norm2().mul(embedding2.norm2()))
            println("cosine dis: ", cos_dis)

            // Send back cosine distance
            out.println(cos_dis)

            in.close()
            out.close()
            socket.close()
            inner_loop.break()
          }

          input_line = in.readLine()
        }
      }
    }
  }
}
