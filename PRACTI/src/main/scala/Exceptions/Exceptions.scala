package Exceptions

object Exceptions {
  final case class SecurityException(code: String, message: String, cause: Throwable = null) extends ExceptionWithCode(code, message, cause)

  abstract class ExceptionWithCode(code: String, message: String, cause: Throwable = null) extends Exception(message, cause) {
    override def getMessage: String = {
      "\n  Code: " + code + "\n   " + message
    }
  }
}
