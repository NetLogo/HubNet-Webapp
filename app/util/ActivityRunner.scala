package util

import play.Play
import play.jobs.Job

import java.io.{InputStreamReader, BufferedReader, PrintStream, File}

import collection.mutable
import concurrent.MailBox
import collection.JavaConversions._

import org.apache.tools.ant.{BuildException, DemuxOutputStream, DefaultLogger, Project}
import org.apache.tools.ant.taskdefs.Java
import org.apache.tools.ant.types.{FileSet, Path}

object ActivityRunner {

  /** The list of all activities currently running. Activities are accessible by ID. */
  val activities = mutable.Map.empty[Int, ActivityRunner]
  private var idCounter = 0

  /**
   * Adds a new HubNet activity to the list of running activities. Returns the activity ID
   * that could be using for managing it.
   */
  def registerNewActivity(activity: ActivityRunner) = {
    idCounter += 1
    val id = idCounter
    activities(id) = activity
    id
  }

  val netlogoPath = new File(Play.configuration.getProperty("netlogo.path")).getAbsolutePath
  val runHeadlessPath = new File(Play.configuration.getProperty("netlogo.RunHeadless.path")).getAbsolutePath

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


class ActivityRunner(val model: File) extends Job {
  private val _id = ActivityRunner.registerNewActivity(this)
  def id = _id

  var port: Option[Int] = None

  /**
   * Anyone who needs to be notified of changes to the activity state
   * can add themselves to this list.
   */
  val listeners = mutable.ListBuffer.empty[MailBox]

  private var _status: ActivityState = ActivityState.NotStarted
  def status = _status
  private def status_= (value: ActivityState) = {
    if (value != _status) {
      _status = value
      listeners.foreach(_ send value)
    }
  }

  // Local clients
  var localClientCounter = 0
  def localClientPrefix = "user "   // NetLogo uses "Local", so we'll use something else to avoid any conflicts.
  def getNextLocalClientUsername() = {
    localClientCounter = localClientCounter + 1
    localClientPrefix + localClientCounter
  }

  /**
   * Blocks until the activity is loaded (or until there is an error).
   */
  def waitToLoad() {
    val listener = new MailBox()
    listeners.add(listener)

    var newState = status

    // TODO: Pattern matching would be nice here, but I ran into an issue similar to this:
    // http://stackoverflow.com/questions/5765311/pattern-matching-on-nested-types-in-scala
    // But using sealed traits means having to duplicate what I've defined in the abstract
    // class ActivityState into all its subclasses.
    // I'm sure there's a cleaner way to do what I'm doing here, I just haven't found it yet.
    while (!(newState.isInstanceOf[ActivityState.Running] || newState.isInstanceOf[ActivityState.Error])) {
      newState = listener receive {
        case state: ActivityState => state
      }
    }

    listeners.remove(listener)
  }

  /**
   * Actually launches the activity. This relies on an external source file called RunHeadless.java
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
  override def doJob() {
    println("** Launching activity (ID=" + id + "): " + model.getAbsolutePath)
    
    status = ActivityState.Loading.Initializing(progress = 25)

    val project: Project = new Project
    project.setBaseDir(new File(ActivityRunner.netlogoPath))
    project.init()

    val logger: DefaultLogger = new DefaultLogger
    project.addBuildListener(logger)
    logger.setOutputPrintStream(System.out)
    logger.setErrorPrintStream(System.err)
    logger.setMessageOutputLevel(Project.MSG_INFO)
    System.setOut(new PrintStream(new DemuxOutputStream(project, false)))
    System.setErr(new PrintStream(new DemuxOutputStream(project, true)))

    status = ActivityState.Loading.BuildingLauncher(progress = 50)

    var proc: java.lang.Process = null
    val errorMessageBuilder = new StringBuilder()

    project.fireBuildStarted()
    var caught: Throwable = null
    try {
      val javaTask = new Java
      javaTask.setTaskName("RunHeadless")
      javaTask.setProject(project)
      javaTask.setFork(true)
      javaTask.setFailonerror(true)
      javaTask.setDir(new File(ActivityRunner.runHeadlessPath))
      javaTask.setClassname("RunHeadless")

      // Supply the model filename as an argument to RunHeadless.java
      val arg = javaTask.createArg()
      arg.setValue(model.getAbsolutePath)

      javaTask.setClasspath(ActivityRunner.buildClassPath(project))

      javaTask.init()

      status = ActivityState.Loading.Launching(progress = 75)

      // We could now call javaTask.executeJava(), but instead we'll use
      // ProcessBuilder because it makes redirection easier.  We are interested
      // in reading the output of RunHeadless.java to determine whether it
      // was successful or if an error occurred.

      val pb = new ProcessBuilder(asJavaList(javaTask.getCommandLine.getCommandline))
      pb.redirectErrorStream(true)
      proc = pb.start()

      val reader = new BufferedReader(new InputStreamReader(proc.getInputStream))
      var line:String = null
      var currentlyReadingErrorMessage = false

      while({line = reader.readLine; line != null}) {
        println(line)
        if (currentlyReadingErrorMessage) {
          errorMessageBuilder.append(line + "\n")
        }

        if (line.contains("Running on port:")) {            // This has to appear in RunHeadless output first...
          port = Some(line.split(" ").last.toInt)
        }
        else if (line.contains("The model is running")) {   // ... and then this.
          status = ActivityState.Running(port.getOrElse(
            error("The port that the activity is running on has not yet been specified.")))
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
        status = ActivityState.Error.LaunchFailure("The launcher failed to build: " + caught.getMessage)
      }
    }
    project.log("RunHeadless finished")
    println(status)
    project.fireBuildFinished(caught)

    try {
      proc.exitValue match {
        // For error code values, see RunHeadless.java
        case 0 =>
          println("RunHeadless exited with code 0")
          status = ActivityState.Finished
        case 102 =>
          println("** File not found: " + model.getName)
          status = ActivityState.Error.FileNotFound(model.getName)
        case 103 =>
          println("** Model error: " + errorMessageBuilder.toString())
          status = ActivityState.Error.ModelError(errorMessageBuilder.toString())
        case exit: Int =>
          println("** Error: RunHeadless returned " + exit)
          status = ActivityState.Error.LaunchFailure("The launcher failed with code " + exit)
      }
    } catch {
      case e:IllegalThreadStateException =>
        println("** Warning: RunHeadless output stream is closed, but process is still not finished.")
        println("** An error may have occurred, but we can't know about it.")
      case e:Exception =>
        status = ActivityState.Error.LaunchFailure("The launcher failed with error: " + e.getMessage)
        println("** Error: Exception occurred while executing RunHeadless:")
        e.printStackTrace()
    }
  }
}