import java.net.InetAddress

object Ip {
  def getIp: String = {
    var ret_ip: String = null
    val localhost: InetAddress = InetAddress.getLocalHost
    localhost.getHostAddress
  }
}





