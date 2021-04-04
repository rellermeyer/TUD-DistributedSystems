import com.typesafe.config.{Config, ConfigFactory}

package object experiments {
  val config: Config = ConfigFactory.load()
}
