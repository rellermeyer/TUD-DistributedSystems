package rover.rdo.comms.http

import kong.unirest.Unirest
import rover.rdo.ObjectId
import rover.rdo.comms.Client
import rover.rdo.state.AtomicObjectState

/**
  * Provides a simple to use API to communicate to a state/object server
  * over HTTP.
  *
  * @note This class could have extended the ServerHttpInterface "interface",
  *       but  doing so has potential to cause confusion. As well as forcing
  *       server-side method naming onto the client (accept vs push). Can be
  *       overcome in the future with better naming (both interface as API)
  * @param endpointPaths
  * @tparam A The state itself
  */
class ClientForServerOverHttp[A <: Serializable](
	private val endpointPaths: ServerHttpEndpointPaths
) extends Client[A]{

	override def created(): AtomicObjectState[A] = {
		val createResponse = Unirest.get(endpointPaths.createEndpoint).asString()
		val received = DeserializedAtomicObjectState[A](createResponse.getBody).asAtomicObjectState

		return received
	}

	override def fetch(objectId: ObjectId): AtomicObjectState[A] = {
//		println(s"Fetching: ${objectId.asString}")
		
		val fetchResponse = Unirest.get(endpointPaths.getEndpoint + "/{objectId}")
    		.routeParam("objectId", objectId.asString)
			.asString()
		
		val deserialized = DeserializedAtomicObjectState[A](fetchResponse.getBody)

		return deserialized.asAtomicObjectState
	}

	override def push(state: AtomicObjectState[A]): Unit = {
//		println(s"          Pushing: ${state.immutableState}")
		
		val serialized = new SerializedAtomicObjectState[A](state)
		val body = Unirest.post(endpointPaths.acceptEndpoint)
    		.header("Content-Type", "application/octet-stream")
			.body(serialized.asString)
    		.asEmpty()

//		val henk = new Base64().decode(body.get().uniPart().getValue)

//		println(henk)

		// TODO: ?
	}
}
