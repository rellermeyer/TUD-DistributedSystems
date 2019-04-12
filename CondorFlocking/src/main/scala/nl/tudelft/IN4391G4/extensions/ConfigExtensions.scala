package nl.tudelft.IN4391G4.extensions

import com.typesafe.config.{Config, ConfigException}

object ConfigExtensions {

  def getString(conf: Config, key: String): String = {
    try {
      conf.getString(key)
    } catch {
      case (e: ConfigException.Missing) => {
        Console.err.println(s"Invalid configuration, a property is not specified. Tried to read: $key")
        throw e
      }
      case (e: ConfigException.WrongType) => {
        Console.err.println(s"Invalid configuration, only string properties are supported. Tried to read: $key as String")
        throw e
      }
    }
  }

  def getStringSafe(conf: Config, key: String, fallbackVal: String): String = {
    if(conf == null){
      return fallbackVal
    }
    try {
      conf.getString(key)
    } catch {
      case _:ConfigException => fallbackVal
    }
  }

  def getBooleanSafe(conf: Config, key: String, fallbackVal: Boolean): Boolean = {
    if(conf == null){
      return fallbackVal
    }
    try {
      conf.getBoolean(key)
    } catch {
      case _:ConfigException => fallbackVal
    }
  }
}