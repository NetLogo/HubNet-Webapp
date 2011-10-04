package controllers

import play._
import play.mvc._
import play.mvc.results._

import java.io._

import _root_.util._
import models.Activity

object Activities extends Controller with Secure {

  import views.Activities._

  // Base directory containing the NetLogo models that users are allowed to run
  val modelsDir = new File(Play.configuration.getProperty("netlogo.models.path"))

  def list = {
    val runningActivities = ActivityManager.activities.values

    val path = new File(modelsDir.getAbsolutePath)
    val availableActivities = path.listFiles().filter(file => file.isFile && file.getName.endsWith(".nlogo")).map(_.getName)
    html.list(runningActivities, availableActivities)
  }

  def run(model: String) = {
    val file = new File(modelsDir, model)
    if (file.exists) {
      val activity = new Activity(file)
      activity.launchAsync()
      html.run(activity)
    } else {
      flash += ("error" -> "The specified activity is invalid.  Please try again or select a different activity from the list.")
      Action(list)
    }
  }

  def manage(id: Int) = {
    if (ActivityManager.activities.contains(id) && ActivityManager.activities(id).status.isInstanceOf[ActivityStatus.Running]) {
      val activity = ActivityManager.activities(id)
      html.manage(activity)
    } else {
      flash += ("error" -> "The specified activity is invalid.  Please check that it is still running and try starting it again.")
      Action(list)
    }
  }

  /**
   * (JSON action) Returns the activity's status, encoded in JSON.
   */
  def status(id: Int) = {
    if (ActivityManager.activities.contains(id)) {
      new RenderJson(ActivityManager.activities(id).status.toJson)
    }
    else {
      new RenderJson(ActivityStatus.Error.InvalidActivity().toJson)
    }
  }

  /**
   * (JSON action) Waits until the activity finishes loading, then returns its status encoded in JSON.
   */
  def waitToLoad(id: Int) = {
    if (ActivityManager.activities.contains(id)) {
      ActivityManager.activities(id).waitUntilLoaded()
      status(id)
    }
    else {
      new RenderJson(ActivityStatus.Error.InvalidActivity().toJson)
    }
  }

  /**
   * Launches a local client in the activity with the given ID.
   */
  def local(id: Int) = {
    if (ActivityManager.activities.contains(id) && ActivityManager.activities(id).status.isInstanceOf[ActivityStatus.Running]) {
      val activity = ActivityManager.activities(id)
      html.local(activity.name, activity.port, activity.getNextLocalClientUsername())
    }
    else {
      NotFound("The specified activity is invalid. Please make sure the activity is running and try again.")
    }
  }

  /**
   * Stops an activity with the given ID. This kills the process in which the activity is
   * running.
   */
  def stop(id: Int) = {
    if (ActivityManager.activities.contains(id)) {
      val activity = ActivityManager.activities(id)
      activity.stop()
      flash += ("info" -> ("The activity " + activity.name + " has been stopped successfully."))
    } else {
      flash += ("error" -> "The specified activity is invalid.  Please try again or select a different activity from the list.")
    }
    Action(list)
  }
}
