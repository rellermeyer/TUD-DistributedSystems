/**
  * State of a fail-stop component.
  */
object ComponentState extends Enumeration {
    type ComponentState = Value
    val Running, Failed = Value
}
