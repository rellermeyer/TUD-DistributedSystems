package net

import org.deeplearning4j.nn.conf.layers.DenseLayer
import org.deeplearning4j.nn.conf.{MultiLayerConfiguration, NeuralNetConfiguration}
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.nn.weights.WeightInit
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.learning.config.Nesterovs

class GatedEmbeddingUnit {

  // Model configurations
  val seed = 6
  val dim1 = 256
  val dim2 = 256

  // Model builders
  val multiLayerConf_layer1: MultiLayerConfiguration = new NeuralNetConfiguration.Builder()
    .seed(seed)
    .updater(new Nesterovs(0.1, 0.9))
    .list()
    .layer(0, new DenseLayer.Builder().nIn(dim1).nOut(dim2).weightInit(WeightInit.XAVIER).build())
    .build()

  val multiLayerConf_layer2: MultiLayerConfiguration = new NeuralNetConfiguration.Builder()
    .seed(seed)
    .updater(new Nesterovs(0.1, 0.9))
    .list()
    .layer(0, new DenseLayer.Builder().nIn(dim2).nOut(dim2).weightInit(WeightInit.XAVIER).activation(Activation.SOFTMAX).build())
    .build()

  // Create two models
  val model1 = new MultiLayerNetwork(multiLayerConf_layer1)
  val model2 = new MultiLayerNetwork(multiLayerConf_layer2)

  // Feed input to the model
  def run(input: INDArray): INDArray = {
    model1.init()
    model2.init()

    val embedding1 = model1.output(input)

    val output = model2.output(embedding1)
    val embedding2 = output.mul(embedding1)

    val embedding = embedding2.div(embedding2.norm2())

    embedding
  }
}
