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
    val runningActivities = ActivityRunner.activities.values

    val path = new File(modelsDir.getAbsolutePath)
    val availableActivities = path.listFiles().filter(file => file.isFile && file.getName.endsWith(".nlogo")).map(_.getName)
    html.list(runningActivities, availableActivities)
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

  def manage(id: Int) = {
    if (ActivityRunner.activities.contains(id)) {
      val activity = ActivityRunner.activities(id)
      html.manage(activity)
    } else {
      flash += ("error" -> "The specified activity is invalid.  Please try again or select a different activity from the list.")
      Action(list)
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

  /**
   * Launches a local client in the activity with the given ID.
   */
  def local(id: Int) = {
    val activity = ActivityRunner.activities(id)
    html.local(activity.model.getName, activity.port.get, activity.getNextLocalClientUsername())
  }
}
