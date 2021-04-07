package net

import org.deeplearning4j.nn.conf.{MultiLayerConfiguration, NeuralNetConfiguration}
import org.deeplearning4j.nn.conf.layers.{GlobalPoolingLayer, PoolingType}
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.nd4j.linalg.api.ndarray.INDArray
import java.util

class TemporalAggregation {

  // Model configuration
  val seed = 6
  var averagePoolConf = new GlobalPoolingLayer()
  averagePoolConf.setCollapseDimensions(true)
  averagePoolConf.setPoolingType(PoolingType.AVG)
  averagePoolConf.setPoolingDimensions(Array(0))

  // Create builder
  var conf: NeuralNetConfiguration = new NeuralNetConfiguration.Builder()
    .seed(seed)
    .layer(averagePoolConf)
    .build()

  // Deploy the configuration
  val confs: util.List[NeuralNetConfiguration] = new util.LinkedList()
  confs.add(conf)
  val builder = new MultiLayerConfiguration.Builder()
  builder.setConfs(confs)

  // Create model
  val model: MultiLayerNetwork = new MultiLayerNetwork(builder.build())

  // Feed input to the model
  def run(input: INDArray): INDArray = {
    model.init()
    val output = model.output(input)

    output
  }
}
