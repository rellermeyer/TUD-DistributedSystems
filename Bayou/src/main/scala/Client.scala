class Client[D](serverIp: String) {
  val port: Int = 9999
  val ip: String = Ip.getIp

  val cs: ClientSocket[D] = new ClientSocket[D](ip, port)

  def send(message: BayouRequest[D]): BayouResponse[D] = {
    this.cs.send(serverIp, message)
  }
}
