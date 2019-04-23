package nl.tudelft.IN4391G4.gui

import nl.tudelft.IN4391G4.messages.MachineState

object UILauncher {

  def launchWorkstationUI(machineName: String, state: MachineState): WorkstationUI = {
    val ui: WorkstationUI = new WorkstationUI(machineName, state)

    Thread.currentThread().synchronized {
      while (ui == null || !ui.initCompleted) { // busy-wait for UI to have initialised
        Thread.currentThread().wait(250)
      }
    }

    ui
  }
}
