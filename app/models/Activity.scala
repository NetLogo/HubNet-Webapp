package models

import java.io.File

import collection.mutable
import concurrent.MailBox
import collection.JavaConversions._

import util.{ActivityLauncher, ActivityStatus, ActivityManager}
import play.jobs.Job

/**
 * Represents a HubNet activity which is running headlessly on the server.
 *
 * When a new activity is instantiated, it gets a unique ID assigned to it that may be
 * used to access it from the global ActivityManager.activities map.
 *
 * Initially, the activity's status is ActivityStatus.NotStarted.  You must call the
 * launch() method in order to load it up and make it start running.  This may take a
 * couple of seconds.
 *
 * @param file      The NetLogo model file that this activity will be running.
 * @param username  The username of the user who started this activity.
 *
 */
class Activity(val file: File, val username: String) {
  def id = _id
  private val _id = ActivityManager.registerNewActivity(this)

  def name = file.getName.replace(".nlogo", "")

  // The port doesn't get assigned until the activity is running.
  def port: Int = _port.getOrElse(error("The port for this activity has not yet been assigned."))
  private var _port: Option[Int] = None
  def assignPort(newPort: Int) {
    _port = Some(newPort)
  }

  // The process in which this activity will be running.
  var proc: java.lang.Process = null

  // Activity status denotes whether the activity is running, or if it's
  // still loading, or if an error occurred.
  def status = _status
  private var _status: ActivityStatus = ActivityStatus.NotStarted
  def status_= (value: ActivityStatus) = {
    if (value != _status) {
      _status = value
      statusListeners.foreach(_ send value)
    }
  }

  // Anyone who needs to be notified of changes to the activity state
  // can add themselves to this list.
  val statusListeners = mutable.ListBuffer.empty[MailBox]

  // Local clients: keep track of how many are connected so we can give them
  // unique usernames.
  var localClientCounter = 0
  def localClientPrefix = "user "   // NetLogo uses "Local", so we'll use something else to avoid any conflicts.
  def getNextLocalClientUsername() = {
    localClientCounter = localClientCounter + 1
    localClientPrefix + localClientCounter
  }

  /**
   * Loads the activity and makes it start running.  This is a relatively long-running operation (it may
   * take a couple of seconds), and this call will block.  For an asynchronous version, use launchAsync().
   *
   * If successful, the activity's status at the end will be ActivityStatus.Running. Otherwise, the status will
   * be one of the subclasses of ActivityStatus.Error.
   */
  def launch() {
    ActivityLauncher.launch(this)
  }

  /**
   * Starts loading the activity asynchronously.  This should return pretty much immediately, but the activity
   * will not be fully up and running for a few seconds.
   *
   * To signal that loading the activity finished successfully, the activity's status will change to
   * ActivityStatus.Running.  If an error occurred, the status will be one of the subclasses of
   * ActivityStatus.Error.
   *
   * You may use the waitUntilLoaded() method to block until the activity's status changes to either Running
   * or Error.
   */
  def launchAsync() {
    // Play Framework job for launching a new activity. This can be used to avoid blocking a
    // request while launching the activity (which can take a couple seconds).  See the Play
    // Framework documentation on Asynchronous Jobs.
    class ActivityLaunchJob(val activity: Activity) extends Job {
      override def doJob() {
        activity.launch()
      }
    }
    new ActivityLaunchJob(this).now()
  }

  /**
   * Blocks until the activity is loaded (or until there is an error).  This does NOT cause the activity
   * to actually load - for that, use the launch() or the launchAsync() methods.
   */
  def waitUntilLoaded() {
    // Listen for changes in activity status until we see that it's either running or
    // there's an error.
    val listener = new MailBox()
    statusListeners.add(listener)
    var newStatus = status
    // TODO: Pattern matching would be nice here, but I ran into an issue similar to this:
    // http://stackoverflow.com/questions/5765311/pattern-matching-on-nested-types-in-scala
    // But using sealed traits means having to duplicate what I've defined in the abstract
    // class ActivityStatus into all its subclasses.
    // I'm sure there's a cleaner way to do what I'm doing here, I just haven't found it yet.
    while (!(newStatus.isInstanceOf[ActivityStatus.Running] || newStatus.isInstanceOf[ActivityStatus.Error])) {
      newStatus = listener receive {
        case state: ActivityStatus => state
      }
    }
    statusListeners.remove(listener)
  }

  /**
   * Stops the activity from running by killing the process. This also unregisters the activity from
   * the global activities list.
   */
  def stop() {
    if (proc != null)
      proc.destroy()
    
    if (ActivityManager.activities.contains(id))
      ActivityManager.activities.remove(id)

    status = ActivityStatus.Finished
    _port = None
  }
}