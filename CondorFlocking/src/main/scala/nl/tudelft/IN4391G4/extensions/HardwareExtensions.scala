package nl.tudelft.IN4391G4.extensions

import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean

object HardwareExtensions {

  val os = ManagementFactory.getOperatingSystemMXBean.asInstanceOf[OperatingSystemMXBean]

  def getCpuLoad(): Double = {
    return os.getSystemCpuLoad
  }
}