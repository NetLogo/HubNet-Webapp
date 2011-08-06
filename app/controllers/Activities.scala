package controllers

import play._
import play.mvc._
import play.mvc.results._

import java.io._

import _root_.util._

object Activities extends Controller {

  import views.Activities._

  // Base directory containing the NetLogo models that users are allowed to run
  val modelsDir = new File(Play.configuration.getProperty("netlogo.models.path"))

  def list = {
    val path = new File(modelsDir.getAbsolutePath)
    val models = path.listFiles().filter(file => file.isFile && file.getName.endsWith(".nlogo")).map(_.getName)
    html.list(models)
  }

  def run(model: String) = {
    val file = new File(modelsDir, model)
    if (file.exists) {
      val runner = new ActivityRunner(file)
      runner.now()
      html.run(runner.id, model)
    } else {
      throw new IllegalArgumentException    // TODO: Show an error page instead
    }
  }

  def status(id: Int) = {
    new RenderJson(ActivityRunner.activities(id).status.toJson)
  }

  /**
   * Waits until the activity finishes loading, then returns its status.
   */
  def waitToLoad(id: Int) = {
    ActivityRunner.activities(id).waitToLoad()
    status(id)
  }
}
