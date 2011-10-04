package models

import play.db.anorm.defaults._

object User extends Magic[User](Some("Users"))

case class User(val username: String, val password: String) {

}