import com.typesafe.config.{Config, ConfigFactory}

package object sgrub {
  val config: Config = ConfigFactory.load()
}
