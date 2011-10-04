package util

import java.io._

import play.Play
import play.jobs._
import play.exceptions._

@OnApplicationStart class Bootstrap extends Job {
  override def doJob() {
    checkConfig()
  }

  /**
   * Detect any problems with the configuration (e.g., invalid directories, missing files).
   * See: conf/application.conf.
   */
  def checkConfig() {

    // Define a couple utility methods

    def invalidConfigMessage(configVar: String, moreDetails: String) = {
      "The path specified for the configuration variable " + configVar + " is invalid. " +
      "Please edit the file conf/application.conf and modify the value for this property. " +
      "\n\n" + "More details follow: \n" + moreDetails
    }

    def checkValidConfigVar(configVar: String) {
      if (!Play.configuration.containsKey(configVar)) {
        throw new ConfigurationException("The configuration variable " + configVar + " has not been " +
          "defined in conf/application.conf.")
      }
    }

    def checkValidFile(configVar: String) {
      checkValidConfigVar(configVar)
      val file = new File(Play.configuration.get(configVar).toString)
      if (!file.exists) {
        throw new ConfigurationException(
          invalidConfigMessage(configVar, "The file " + file + " does not exist."))
      }
      if (!file.isFile) {
        throw new ConfigurationException(
          invalidConfigMessage(configVar, "Expected a file, but " + file.getAbsolutePath + " is not a file."))
      }
    }

    def checkValidDirectory(configVar: String) {
      checkValidConfigVar(configVar)
      val path = new File(Play.configuration.get(configVar).toString)
      if (!path.exists) {
        throw new ConfigurationException(
          invalidConfigMessage(configVar, "The path " + path + " does not exist."))
      }
      if (!path.isDirectory) {
        throw new ConfigurationException(
          invalidConfigMessage(configVar, "Expected a directory, but " + path.getAbsolutePath + " is not a directory."))
      }
    }

    // Perform the configuration checks

    checkValidDirectory("netlogo.path")
    checkValidFile("netlogo.jar.path")
    checkValidDirectory("netlogo.models.path")
    checkValidDirectory("netlogo.dependencies.path")
    checkValidFile("netlogo.scala.path")
    checkValidDirectory("netlogo.RunHeadless.path")

    // Check that netlogo.models.path contains some models
    val path = new File(Play.configuration.get("netlogo.models.path").toString)
    val activities = path.listFiles().filter(file => file.isFile && file.getName.endsWith(".nlogo"))
    if (activities.length == 0) {
      throw new ConfigurationException(
        invalidConfigMessage("netlogo.models.path", "The directory " + path.getAbsolutePath +
          " does not contain any HubNet activities. Please check that this directory is valid."))
    }
  }
}