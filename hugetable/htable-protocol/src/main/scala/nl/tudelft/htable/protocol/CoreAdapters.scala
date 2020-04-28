package nl.tudelft.htable.protocol

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.net.InetSocketAddress

import akka.util.ByteString

import scala.language.implicitConversions

/**
 * Core adapters used for the conversion between core classes and Protobuf classes.
 */
object CoreAdapters {

  /**
   * Serialize an [InetSocketAddress] to a byte string.
   */
  def serializeAddress(value: InetSocketAddress): Array[Byte] = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(stream)
    oos.writeObject(value)
    oos.close()
    stream.toByteArray
  }

  /**
   * Convert the specified byte string into a socket address.
   */
  def deserializeAddress(bytes: Array[Byte]): InetSocketAddress = {
    val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
    val value = ois.readObject
    ois.close()
    value.asInstanceOf[InetSocketAddress]
  }

  /**
   * Translate an UTF-8 [String] into an Akka [ByteString].
   */
  implicit def stringToAkka(string: String): ByteString = ByteString(string)

  /**
   * Translate a [ByteString] into a UTF-8 string.
   */
  implicit def akkaToString(byteString: ByteString): String = byteString.utf8String

  /**
   * Translate an Akka byte string into a Google Protobuf byte string.
   */
  implicit def akkaToProtobuf(byteString: ByteString): com.google.protobuf.ByteString =
    com.google.protobuf.ByteString.copyFrom(byteString.toByteBuffer)

  /**
   * Translate a Google Protobuf ByteString to Akka ByteString.
   */
  implicit def protobufToAkka(byteString: com.google.protobuf.ByteString): ByteString =
    ByteString(byteString.asReadOnlyByteBuffer())
}
