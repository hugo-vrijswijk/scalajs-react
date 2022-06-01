package japgolly.scalajs.react.test.emissions.util

import scala.Console._

object Props {

  def moduleName = "testEmissions"

  private object Prop {
    def get(property: String): Option[String] = {
      val o = Option(System.getProperty(property))
      println(s"$CYAN[$moduleName] $property$RESET = $YELLOW${o.getOrElse("")}$RESET")
      o
    }

    def get(property: String, default: String): String =
      get(property).getOrElse(default)

    def need(property: String): String =
      get(property).getOrElse(throw new RuntimeException("Property not defined: " + property))
  }

  val jsOutputDir = Prop.need("jsOutputDir")
  val tempDir     = Prop.need("tempDir")
  val testResDir  = Prop.need("testResDir")
  val testRootDir = Prop.need("testRootDir")
}
