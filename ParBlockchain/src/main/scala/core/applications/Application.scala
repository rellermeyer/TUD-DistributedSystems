package core.applications

object Application extends Enumeration {
  type Application = Value
  val A, B, C, D, E, F, G,H, I = Value

  def commitThreshold(app: Application): Int = {
    app match {
      case A => 1
      case B => 1
      case C => 1
      case D => 1
      case E => 1
      case F => 1

    }
  }
}
