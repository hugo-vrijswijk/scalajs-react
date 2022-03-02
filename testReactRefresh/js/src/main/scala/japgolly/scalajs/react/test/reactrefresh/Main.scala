package japgolly.scalajs.react.test.reactrefresh

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

import org.scalajs.dom

object Main {

  def main(): Unit = {
    val app  = Component()
    val cont = dom.document.getElementById("root")
    app.renderIntoDOM(cont)
  }

  private val Component = ScalaFnComponent[Unit] { _ =>
    <.div(
      UseState1.Component(),
      UseState2.Component(),
      UseStateMulti.Component(),
    )
  }
}
