package nl.tudelft.IN4391G4.messages

trait MachineState

object MachineState {

  case object Busy extends MachineState

  case object Available extends MachineState

}

object MachineStateOrdering extends Ordering[MachineState]{
  def compare(a: MachineState, b: MachineState): Int = (a, b) match{
    case (MachineState.Busy, MachineState.Available) => -1
    case (am, bm) if am == bm => 0
    case _ => 1
  }
}