package util

abstract class ActivityState(description: String) {
  override def toString = description
  def toJson = "{\"status\": \"" + description + "\"}"
}

object ActivityState {
  case object NotStarted extends ActivityState("Not started")

  abstract class Loading(progress: Int, subtask: String) extends ActivityState("Loading") {
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

  case object Running extends ActivityState("Running")

  abstract class Error(message: String) extends ActivityState("Error") {
    override def toString = "Error: " + message
    override def toJson = "{\"status\": \"Error\"," +
                           "\"message\": \"" + org.json.simple.JSONObject.escape(message) + "\"}"
  }
  object Error {
    case class ModelError(message: String) extends Error(message)
    case class LaunchFailure(message: String) extends Error(message)
    case class FileNotFound(filename: String)
      extends Error("The model file could not be found: " + filename)
  }

  case object Finished extends ActivityState("Finished")
}