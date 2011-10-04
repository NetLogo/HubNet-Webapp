package util

import play._
import play.mvc._

import controllers._
import models._

trait Secure {
  self:Controller =>

  @Before def check = {
    session("username") match {
      case Some(username) =>
        Continue
      case None =>
        flash += ("error" -> "This action requires that you are logged in.")
        Action(Authentication.login)
    }
  }

  def connectedUser = renderArgs("user").get
}