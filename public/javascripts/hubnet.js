/*
 * This file contains functions for launching and interacting with the HubNet client applet.
 *
 * The HTML page that includes this script file must contain the following:
 *          <div id="activity-container">
 *              <span id="activity-status">Loading...</span>
 *          </div>
 * There should be no <applet> tag in the page. The applet tag gets inserted dynamically when the launchApplet()
 * function gets called.
 */

// For debugging purposes only: show an alert box for poorly-formed JSON responses from the server.
$.ajaxSetup({"error":function(XMLHttpRequest,textStatus, errorThrown) {
    alert(textStatus);
    alert(errorThrown);
    alert(XMLHttpRequest.responseText);
}});

function launchApplet(port, role, user) {
    $("#activity-container").width(1600);
    $("#activity-container").height(1200);
    $("#activity-container").append(
        '<applet code="org.nlogo.hubnet.client.ClientApplet.class" '
              + 'archive="/public/jars/NetLogo.jar,/public/jars/scala-library.jar" '
              + 'width="100%" '
              + 'height="100%" '
              + 'hspace="0" '
              + 'vspace="0" '
              + 'id="hubnet-client" '
              + 'mayscript>'
            + '<param name="port" value="' + port + '">'
            + '<param name="role" value="' + role + '">'
            + (user ? ('<param name="user" value="' + user + '">') : "")
            + '<param name="notify" value="true">'
            + 'Java must be installed and applets must be enabled in order to run HubNet activities in the browser.'
            + '</applet>');
}

function handleActivityEvent(event, eventArgs) {
    switch(event) {
        case 'login complete':
            $("#activity-status").text("Running");
            // Keep the "Running" text visible for a bit, long enough to be seen, but remove it afterward
            // so it's not distracting.
            window.setTimeout(function () {
                $("#activity-status").html('&nbsp;');
            }, 800);
            break;
        case 'size changed':
            var activityWidth = eventArgs[0];
            var activityHeight = eventArgs[1];
            $("#activity-container").width(activityWidth);
            $("#activity-container").height(activityHeight);
            break;
    }
}

function errorAsHtml(error) {
    return '<div id="error"><p><b>An error occurred while launching the model:</b></p>'
        + '<pre>' + error.message + '</pre>'
        + '</div>';
}

window.setInterval(function() {
    $("#activity-status:contains('Loading...')").append('.');
    $("#activity-status:contains('Launching applet...')").append('.');
}, 1000);