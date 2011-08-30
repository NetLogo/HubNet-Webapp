package util

abstract class ActivityStatus(description: String) {
  override def toString = description
  def toJson = "{\"status\": \"" + description + "\"}"
}

object ActivityStatus {

  /***** NotStarted *****/

  case object NotStarted extends ActivityStatus("Not started")


  /***** Loading *****/

  abstract class Loading(progress: Int, subtask: String) extends ActivityStatus("Loading") {
    override def toString = "Loading [" + progress + "%] (" + subtask + ")"
    override def toJson = "{\"status\": \"Loading\"," +
                           "\"progress\": \"" + progress + "\"," +
                           "\"task\": \"" + subtask + "\"}"
  }
  object Loading {
    case class Initializing(progress: Int) extends Loading(progress, "Initializing")
    case class BuildingLauncher(progress: Int) extends Loading(progress, "Building launcher")
    case class Launching(progress: Int) extends Loading(progress, "Launching")
  }


  /***** Running *****/

  case class Running(port: Int) extends ActivityStatus("Running") {
    override def toString = "Running on port " + port
    override def toJson = " { \"status\": \"Running\"," +
                              "\"port\": \"" + port + "\"}"
  }


  /***** Error *****/

  abstract class Error(message: String) extends ActivityStatus("Error") {
    override def toString = "Error: " + message
    override def toJson = "{\"status\": \"Error\"," +
                           "\"message\": \"" + org.json.simple.JSONObject.escape(message) + "\"}"
  }
  object Error {
    case class ModelError(message: String) extends Error(message)
    case class LaunchFailure(message: String) extends Error(message)
    case class FileNotFound(filename: String)
      extends Error("The model file could not be found: " + filename)
    case class InvalidActivity() extends Error("Invalid activity.")
  }


  /***** Finished *****/

  case object Finished extends ActivityStatus("Finished")
}