package org.orleans.silo.metrics

import java.util.concurrent.ConcurrentHashMap

import com.typesafe.scalalogging.LazyLogging

class RegistryFactory extends LazyLogging{

  // Active registries collecting metrics on different services.
  private val registries: ConcurrentHashMap[String, Registry] = new ConcurrentHashMap[String, Registry]()

  /**
   * Creats and returns registry for new service.
   *
   * @param id Id of the service.
   * @return New registry.
   */
  def getOrCreateRegistry(id: String): Registry = {
    if (registries.containsKey(id))
      registries.get(id)
    else {
      val newRegistry = new Registry()
      registries.put(id, newRegistry)
      newRegistry
    }
  }

  /**
   * Deletes the registry of the service.
   *
   * @param id Id of the service.
   */
  def deleteRegistry(id: String): Unit = {
    registries.remove(id)
  }

  /**
   * Gets the collection of all active registries
   *
   * @return Map of registries per service.
   */
  def getRegistries(): ConcurrentHashMap[String, Registry] = {
    registries
  }


  /**
   * Gets the loads per service id.
   *
   * @return Map of loads per service.
   */
  def getRegistryLoads(): Map[String, (Int, Int)] = {
    var loads: Map[String, (Int, Int)] = Map()
    this.registries.forEach((id, registry) => {
      loads += (id ->  (MetricsExtractor.getPendingRequests(registry), registry.grainsActivated.get()))
    })
    loads
  }

}
