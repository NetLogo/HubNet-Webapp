package util

import play.Play
import play.jobs.Job

import java.io.{InputStreamReader, BufferedReader, PrintStream, File}

import collection.JavaConversions._

import org.apache.tools.ant.{BuildException, DemuxOutputStream, DefaultLogger, Project}
import org.apache.tools.ant.taskdefs.Java
import org.apache.tools.ant.types.{FileSet, Path}

import models.Activity
import java.lang.IllegalStateException


// Play Framework job for launching a new activity. This can be used to avoid blocking a
// request while launching the activity (which can take a couple seconds).  See the Play
// Framework documentation on Asynchronous Jobs.
class ActivityLaunchJob(val activity: Activity) extends Job {
  override def doJob() {
    activity.launch()
  }
}

object ActivityLauncher {

  /**
   * Launches an activity. This relies on an external source file called RunHeadless.java
   * (currently in the "netlogo" directory, which is in the application root directory), which must
   * be compiled beforehand. This uses Apache Ant to launch RunHeadless.java in an external process
   * in what is hopefully a platform-independent manner.
   *
   * Launching the activity in a separate process is beneficial because:
   *  - NetLogo and Play depend on different versions of Scala (can't put both on the classpath
   *    and expect things to work).
   *  - It decreases the chance that a problem in one activity would affect another activity.
   *
   * Issue: This doesn't automatically pick up changes to RunHeadless.java. It will continue to
   * use the compiled RunHeadless.class file, even if RunHeadless.java changed. For now, you
   * can build RunHeadless.java using the makefile in the application root directory.
   */
  def launch(activity: Activity) {

    // There's probably no harm in re-launching an activity, but at the same time it might
    // indicate some sort of error elsewhere.  (To re-connect to an activity that's already
    // running, use the controllers.Activities.manage() action instead).
    if (activity.status != ActivityStatus.NotStarted) {
      throw new IllegalStateException("ActivityLauncher.launch() assumes that the activity's "
        + "status is NotStarted. However, the activity " + activity.name + " (ID " + activity.id
        + ") has status " + activity.status + ".")
    }

    activity.status = ActivityStatus.Loading.Initializing(progress = 25)

    println("** Launching activity (ID=" + activity.id + "): " + activity.file.getAbsolutePath)

    val project: Project = new Project
    project.setBaseDir(new File(ActivityLauncher.netlogoPath))
    project.init()

    val logger: DefaultLogger = new DefaultLogger
    project.addBuildListener(logger)
    logger.setOutputPrintStream(System.out)
    logger.setErrorPrintStream(System.err)
    logger.setMessageOutputLevel(Project.MSG_INFO)
    System.setOut(new PrintStream(new DemuxOutputStream(project, false)))
    System.setErr(new PrintStream(new DemuxOutputStream(project, true)))

    activity.status = ActivityStatus.Loading.BuildingLauncher(progress = 50)

    val errorMessageBuilder = new StringBuilder()

    project.fireBuildStarted()
    var caught: Throwable = null
    try {
      val javaTask = new Java
      javaTask.setTaskName("RunHeadless")
      javaTask.setProject(project)
      javaTask.setFork(true)
      javaTask.setFailonerror(true)
      javaTask.setDir(new File(ActivityLauncher.runHeadlessPath))
      javaTask.setClassname("RunHeadless")

      // Supply the activity filename as an argument to RunHeadless.java
      val arg = javaTask.createArg()
      arg.setValue(activity.file.getAbsolutePath)

      javaTask.setClasspath(ActivityLauncher.buildClassPath(project))

      javaTask.init()

      activity.status = ActivityStatus.Loading.Launching(progress = 75)

      // We could now call javaTask.executeJava(), but instead we'll use
      // ProcessBuilder because it makes redirection easier.  We are interested
      // in reading the output of RunHeadless.java to determine whether it
      // was successful or if an error occurred.

      val pb = new ProcessBuilder(asJavaList(javaTask.getCommandLine.getCommandline))
      pb.redirectErrorStream(true)
      activity.proc = pb.start()

      val reader = new BufferedReader(new InputStreamReader(activity.proc.getInputStream))
      var line:String = null
      var currentlyReadingErrorMessage = false

      while({line = reader.readLine; line != null}) {
        println(line)
        if (currentlyReadingErrorMessage) {
          errorMessageBuilder.append(line + "\n")
        }

        if (line.contains("Running on port:")) {            // This has to appear in RunHeadless output first...
          activity.assignPort(line.split(" ").last.toInt)
        }
        else if (line.contains("The model is running")) {   // ... and then this.
          activity.status = ActivityStatus.Running(activity.port)
        }
        else if (line.contains("** Error")) {
          currentlyReadingErrorMessage = true
          errorMessageBuilder.append(line + "\n")
        }
      }
      reader.close()

      //val ret = javaTask.executeJava
      //System.out.println("RunHeadless return code: " + ret)
    }
    catch {
      case e: BuildException => {
        caught = e
        println("** Error: RunHeadless failed to build.")
        activity.status = ActivityStatus.Error.LaunchFailure("The launcher failed to build: " + caught.getMessage)
      }
    }
    project.log("RunHeadless finished")
    println(activity.status)
    project.fireBuildFinished(caught)

    try {
      activity.proc.exitValue match {
        // For error code values, see RunHeadless.java
        case 0 =>
          println("RunHeadless exited with code 0")
          activity.status = ActivityStatus.Finished
        case 102 =>
          println("** File not found: " + activity.file.getAbsolutePath)
          activity.status = ActivityStatus.Error.FileNotFound(activity.file.getAbsolutePath)
        case 103 =>
          println("** Model error: " + errorMessageBuilder.toString())
          activity.status = ActivityStatus.Error.ModelError(errorMessageBuilder.toString())
        case exit: Int =>
          println("** Error: RunHeadless returned " + exit)
          activity.status = ActivityStatus.Error.LaunchFailure("The launcher failed with code " + exit)
      }
    } catch {
      case e:IllegalThreadStateException =>
        // This generally shouldn't happen, but if it does, we should probably assume the activity failed
        // to launch.
        println("** Warning: RunHeadless output stream is closed, but process is still not finished.")
        println("** An error may have occurred, but we can't know about it.")
        activity.status = ActivityStatus.Error.LaunchFailure("An unknown error occurred while launching the activity.")
      case e:Exception =>
        activity.status = ActivityStatus.Error.LaunchFailure("The launcher failed with error: " + e.getMessage)
        println("** Error: Exception occurred while executing RunHeadless:")
        e.printStackTrace()
    }
  }

  val netlogoPath = new File(Play.configuration.getProperty("netlogo.path")).getAbsolutePath
  val runHeadlessPath = new File(Play.configuration.getProperty("netlogo.RunHeadless.path")).getAbsolutePath

  /**
   * Returns the classpath required for building RunHeadless.jar and running NetLogo headlessly.
   */
  def buildClassPath(project: Project): Path = {
    val netlogoJar = new File(Play.configuration.getProperty("netlogo.jar.path")).getAbsolutePath
    val netlogoDependenciesDir = new File(Play.configuration.getProperty("netlogo.dependencies.path")).getAbsolutePath
    val scalaLibrary = new File(Play.configuration.getProperty("netlogo.scala.path")).getAbsolutePath

    val path= new Path(project);

    // Add directory containing the compiled RunHeadless.class file to the classpath
    path.add(new Path(project, runHeadlessPath))

    // Add directory containing NetLogo.jar to the classpath
    // TODO: This might not be necessary
    path.add(new Path(project, netlogoPath))

    // Add NetLogo.jar to the classpath
    path.add(new Path(project, netlogoJar))

    // Add NetLogo's dependencies to the classpath (asm-3.3.1.jar, gluegen-rt-1.1.1.jar, picocontainer-2.11.1.jar, etc...)
    val netlogoDeps = new FileSet()
    netlogoDeps.setDir(new File(netlogoDependenciesDir))
    netlogoDeps.setIncludes("**/*.jar")
    path.addFileset(netlogoDeps)

    // Add scala-library.jar to the classpath
    path.add(new Path(project, scalaLibrary))

    // Return the constructed classpath
    path
  }
}