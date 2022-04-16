package Communication

@SerialVersionUID(1L)
class SocketData(val ID: Int,
                 var address: String,
                 val port: Int) extends Serializable