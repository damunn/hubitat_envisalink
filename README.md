# Hubitat Envisalink Integration

Hubitat Envisalink Connection driver and Integration application, provides Hubitat integration with Envisalink.
Visit http://www.eyez-on.com/ for more information on Envisalink. 

<b>NOTE: the original author of this has left the HE platform, thus this code is not supported.

**Prerequisites**

* [Envisalink v3 or v4](http://www.eyez-on.com)
* [Hubitat](https://www.hubitat.com)
* The Envisalink system password that you setup when installing Envisalink. 
* Master Disarming Code
* The static IP address of the Envisalink device.
* Knowledge of how the Zones are laid out in your DSC or Vista/Honeywell Alarm system configuration. 

_At this time, I only support a single partition configuration._

## Installation
<b>New App installation:</b><br>
* Copy the Envisalink Integration Application URL from [here](https://raw.githubusercontent.com/bdwilson/hubitat_envisalink/master/hubitat_envisalink_integration_application.groovy)
* In Hubitat, select <b>Apps Code</b>, <b>New App</b>
* Click the <b>Import</b> button and paste the URL and click 'Ok' to the warning. 
* Save the file.

<b>Update App to the newest version:</b><br>
* In Hubitat, select <b>Apps Code</b>, locate the application to update.
* Click the <b>Import</b> button - the URL should already exist.
* Click 'Ok' to the warning.
* Save the file. 

<b>New Driver(s) Installation:</b>
* Follow the same instructions as above to copy these URL's: 
  * [Connection Driver](https://raw.githubusercontent.com/bdwilson/hubitat_envisalink/master/hubitat_envisalink_connection_driver.groovy)
  * [Glass Break Sensor - Optional](https://raw.githubusercontent.com/bdwilson/hubitat_envisalink/master/hubitat_virtual_glassbreak_driver.groovy)
* In Hubitat, select <b>Drivers Code</b>, <b>New Driver</b>
* Click the <b>Import</b> button and paste one of the URL's above and click 'Ok' to the warning.
* Be sure to click 'Save' each time you add or replace the code
* Do this for 'both' of the drivers above if you have a Glassbreak Device

<b>Update Driver(s) to the newest version:</b><br>
* In Hubitat, select <b>Drivers Code</b>, locate the driver to update.
* Click the <b>Import</b> button - the URL should already exist.
* Click 'Ok' to the warning.
* Save the file.

## Activate the Application
* Note: This is only required to be done the 1st time and not required for
updates. Removing and re-activating the app will cause your zones to be
recreated.
1. Go to Apps and Add a new <b>User App</b>
2. Load Envisalink Integration application
3. Using the Envisalink Integration application, configure your IP, Password and Code to Envisalink and your Zone layout and select which type of Alarm (DSC or Vista/Honeywell). 
