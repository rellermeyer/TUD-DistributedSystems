package rover.rdo.comms.http

import java.io._
import java.nio.charset.Charset

import org.apache.commons.codec.binary.{Base64, Base64OutputStream}

// keep this import! Otherwise java.io.Serializable is used due to import java.io._
// Even though scala.Serializable is just trait with java.io.Serializable, it will break the app (v2.12.8)
import scala.Serializable

import rover.rdo.state.AtomicObjectState

class SerializedAtomicObjectState[A <: Serializable] (state: AtomicObjectState[A]) {

	private lazy val asByteArrayOutputStream: ByteArrayOutputStream = {
		val byteOutputStream = new ByteArrayOutputStream()

		// write object into byte output stream
		val objectOutputStream = new ObjectOutputStream(byteOutputStream)
		objectOutputStream.writeObject(state)
		objectOutputStream.close()

		// return
		byteOutputStream
	}

	lazy val asBytes: Array[Byte] = {
		// return
		asByteArrayOutputStream.toByteArray
	}

	lazy val asString: String = {
		val encoded = new Base64().encodeToString(asBytes)
		
//		println(encoded)
		
		//return
		encoded
	}
	
	
}


trait DeserializedAtomicObjectState[A <: Serializable] {
	def asAtomicObjectState: AtomicObjectState[A]
}

class AtomicObjectStateAsByteArray[A <: Serializable] (bytes: Array[Byte]) extends DeserializedAtomicObjectState[A] {

	override lazy val asAtomicObjectState: AtomicObjectState[A] = {
		val bytesInputStream = new ByteArrayInputStream(bytes)
		val objectInputStream = new ObjectInputStream(bytesInputStream)

		val state = objectInputStream.readObject().asInstanceOf[AtomicObjectState[A]]
		objectInputStream.close()

		// return
		state
	}
}

class AtomicObjectStateAsInputStream[A <: Serializable] (inputStream: InputStream) extends DeserializedAtomicObjectState[A] {
	override lazy val asAtomicObjectState: AtomicObjectState[A] = {
		val objectInputStream = new ObjectInputStream(inputStream)

		val state = objectInputStream.readObject().asInstanceOf[AtomicObjectState[A]]
		objectInputStream.close()
		inputStream.close()

		// return
		state
	}
}


object DeserializedAtomicObjectState {
	def apply[A <: Serializable](inputStream: InputStream): DeserializedAtomicObjectState[A] = {
		return new AtomicObjectStateAsInputStream[A](inputStream)
	}

	def apply[A <: Serializable](bytes: Array[Byte]): DeserializedAtomicObjectState[A] = {
		return new AtomicObjectStateAsByteArray[A](bytes)
	}
	
	def apply[A <: Serializable](string: String): DeserializedAtomicObjectState[A] = {
//		println(string)
		val decoded = new Base64().decode(string)
		return new AtomicObjectStateAsByteArray[A](decoded)
	}
}
