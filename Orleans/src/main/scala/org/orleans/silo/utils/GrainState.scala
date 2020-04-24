package org.orleans.silo.utils

object GrainState extends Enumeration {
  type GrainState = Value
  val InMemory, Persisted, Activating, Deactivating, Unknown = Value

  def withNameWithDefault(name: String): Value =
    values.find(_.toString.toLowerCase == name.toLowerCase()).getOrElse(Unknown)
}
