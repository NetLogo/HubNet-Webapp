package controllers

import play._
import play.mvc._
import play.data.validation._

import play.db.anorm._
import play.db.anorm.SqlParser._
import models.User

object Authentication extends Controller {

  import views.Authentication._

  def login = {
    val username = params.get("username")
    val password = params.get("password")
    if (username != null && password != null) {
      // If username/password are not null, then this request represents a login attempt (the login
      // form has been submitted). Make sure all of the required fields were provided.
      Validation.required("username", username).message("The username field is required.")
      Validation.required("password", password).message("The password field is required.")
      if (Validation.hasErrors) {
        // Redirect the user back to the login page (the validation errors will be displayed there).
        html.login()
      } else {
        // Check that the username and password are correct.
        val count = SQL(
          "Select count(*) from Users where username = {username} and password = {password}"
        ).on("username" -> username, "password" -> password).as(scalar[Long])
        if (count < 1) {
          flash += ("error" -> "Invalid username or password.")
          html.login()
        } else {
          // Login request is successful - store the username in the session cookie. This is how
          // we determine that the user is logged in.
          session.put("username", username)
          // Redirect to the home page. (TODO: Redirect back to where the user came from).
          Application.index
        }
      }
    } else {
      // If username/password are null, then this is merely a request to view the login page - the
      // login form has not been submitted yet. Simply render the login page.
      html.login()
    }
  }

  def logout = {
    session.remove("username")
    Application.index
  }

  def register = {
    val username = params.get("username")
    val password = params.get("password")
    if (username != null && password != null) {
      // If username/password are not null, then this request represents a registration attempt (the
      // registration form has been submitted). Make sure all of the required fields were provided.
      Validation.required("username", username).message("The username field is required.")
      Validation.required("password", password).message("The password field is required.")
      if (Validation.hasErrors) {
        //params.flash()
        //Validation.keep()
        // Redirect the user back to the registration page (the validation errors will be displayed there).
        html.register()
      } else {
        // Make sure that the username is not taken already
        val count = SQL(
          "Select count(*) from Users where username = {username}"
        ).on("username" -> username).as(scalar[Long])
        if (count >= 1) {
          flash += ("error" -> "This username is taken already. Please pick a different username.")
          html.register()
        } else {
          // Create the new user and store him in the database.
          val result = SQL(
            "Insert into Users (username, password) values ({username}, {password})"
          ).on("username" -> username, "password" -> password).executeUpdate().fold(
            e => "An error occurred" ,
            c => "User created successfully"
          )
          if (result == "User created successfully") {
            // Registration attempt is successful - automatically log the user in by storing the username
            // in the session cookie
            session.put("username", username)
            // Redirect to the home page.
            Application.index
          }
          else {
            flash += ("error" -> "A database error occurred while attempting to create a new user. Please try submitting the form again.")
            html.register()
          }
        }
      }
    } else {
      // If username/password are null, then this is merely a request to view the registration page - the
      // registration form has not been submitted yet.  Simply render the registration page.
      html.register()
    }
  }

}
