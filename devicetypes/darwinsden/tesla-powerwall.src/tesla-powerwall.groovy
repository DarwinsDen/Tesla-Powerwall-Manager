/**
 *  Tesla Powerwall 
 *
 *  Copyright 2019-2022 DarwinsDen.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.

 *  02-Feb-2022 >>> v0.2.30.20220202 - Add child device creation option for additional SmartThings and Hubitat control/display capability
 *  29-Dec-2021 >>> v0.2.20.20211229 - Merge from @x10send: Added Off Grid and Refresh Token Support
 *	24-Oct-2021 >>> v0.2.10.20211024 - Added argument for setBackupReservePercent
 *	25-May-2020 >>> v0.2.0e.20200525 - Updated reserve +/- adjust for Hubitat
 *	02-Jul-2020 >>> v0.1.5e.20200702 - Added attribute Tile 
 *	22-Jan-2020 >>> v0.1.4e.20200122 - Added stormwatch enable/disable commands
 *	12-Aug-2019 >>> v0.1.3e.20190812 - Added grid/outage status/display
 *	29-Jul-2019 >>> v0.1.2e.20190729 - Disable reserve percent controls in backup-only mode
 *	23-Jul-2019 >>> v0.1.1e.20190723 - Initial beta release
 *
 */

metadata {
      definition(name: "Tesla Powerwall", namespace: "darwinsden", author: "eedwards") {
               
        capability "Battery"
        capability "Power Meter"
        capability "Power Source"
        capability "Actuator"
        capability "Switch Level"
        capability "Polling"
        capability "Sensor"
        capability "Health Check"
        capability "Refresh"
        capability "Switch"
        
        attribute "reservePercent", "number"
        attribute "reserve_pending", "number"
        attribute "solarPower", "number"
        attribute "loadPower", "number"
        attribute "gridPower", "number"
        attribute "powerwallPower", "number"
        attribute "currentOpState", "string"
        attribute "currentStrategy", "string"
        attribute "siteName", "string"
        attribute "pwVersion", "string"
        attribute "stormwatch", "enum", ["true", "false"]
        attribute "gridStatus", "enum", ["offGrid", "onGrid"]
        attribute "pwTile", "string"
        attribute "stormwatchActive", "enum", ["true", "false"]

        command "setBackupReservePercent", ["number"]
        command "raiseBackupReserve"
        command "lowerBackupReserve"
        command "setBackupOnlyMode"
        command "setTimeBasedControlMode"
        command "setSelfPoweredMode"
        command "setTbcBalanced"
        command "setTbcCostSaving"
        command "enableStormwatch"
        command "disableStormwatch"
        command "goOffGrid"
        command "goOnGrid"
        command "refreshAuth"
    }

    preferences {}

    simulator {}
    
    preferences {
        input name: "createChildStateDevices", type: "bool", title: "Create child switch devices for Powerwall states (Self-Powered, Time-Based Control", 
                    defaultValue: false, submitOnChange: true
        input name: "createStormwatchDevices", type: "bool", title: "Create child switch devices for Storm Watch status (Storm Watch Enabled, Storm Watch Active)", 
                    defaultValue: false, submitOnChange: true
        input name: "createChildMeterDevices", type: "bool", title: "Create child meter devices for Powerwall power levels (Solar, Grid, Home, Powerwall)", 
                    defaultValue: false, submitOnChange: true
	}
}

def setLevel(level, rate=null) {
    String desc = "setting ${device.displayName}: level to ${level}%"
    Map evt = [name: "level", value: level, type: "%", descriptionText: desc]
    setBackupReservePercent(level.toInteger())
    sendEvent(evt)
}

def setBackupOnlyMode() {
    parent.setBackupOnlyMode(this)
}

def setSelfPoweredMode() {
    parent.setSelfPoweredMode(this)
}

def setTimeBasedControlMode() {
    parent.setTimeBasedControlMode(this)
}

def setTbcBalanced() {
    parent.setTbcBalanced(this)
}

def setTbcCostSaving() {
    parent.setTbcCostSaving(this)
}

def goOffGrid(){
     parent.goOffGrid(this)   
}

def goOnGrid(){
     parent.goOnGrid(this)   
}

def refreshAuth(){
     parent.refreshAccessToken()   
}

def enableStormwatch() {
    parent.enableStormwatch(this)
}

def disableStormwatch() {
    parent.disableStormwatch(this)
}

def setBackupReservePercent(value) {
    parent.setBackupReservePercent(this, value)
}

def setBackupReservePercentHandler(data) {
    setBackupReservePercent(data.value)
}

def lowerBackupReserve(value) {
    if (device.currentValue("currentOpState").toString() != "Backup-Only") {
       Integer brp 
       if (device.currentValue("reserve_pending")) {
            brp = device.currentValue("reserve_pending").toInteger()
       } else {
            brp = device.currentValue("reservePercent").toInteger()
       }
       if (!brp || state.lastReserveSetTime == null || ((now() - state.lastReserveSetTime) > 20 * 1000)) {
           brp = device.currentValue("reservePercent").toInteger()
       }
       if (brp > 0) {
           brp = brp - 1
           runIn(10, setBackupReservePercentHandler, [data: [value: brp]])
           state.lastReserveSetTime = now()
           sendEvent(name: "reserve_pending", value: brp, displayed: false)
       }
    }
}

def raiseBackupReserve(value) {
    if (device.currentValue("currentOpState").toString() != "Backup-Only") {
       Integer brp 
       if (device.currentValue("reserve_pending")) {
            brp = device.currentValue("reserve_pending").toInteger()
       } else {
            brp = device.currentValue("reservePercent").toInteger()
       }
       if (!brp || state.lastReserveSetTime == null || ((now() - state.lastReserveSetTime) > 20 * 1000)) {
           brp = device.currentValue("reservePercent").toInteger()
       }
       if (brp < 100) {
           brp = brp + 1
           runIn(10, setBackupReservePercentHandler, [data: [value: brp]])
           state.lastReserveSetTime = now()
           sendEvent(name: "reserve_pending", value: brp, displayed: false)
       }
    }
}

def installed() {
    log.debug "${device} Installed"
    initialize()
    return []
}

def updated() {
    log.debug "${device} Updated"
    initialize()
    return []
}

def refreshChildDevices() {
    runIn (1, refreshMode)
    runIn (1, refreshMeters)
}

def refresh() {
    refreshMode ()
    refreshMeters ()
    def status = parent.refresh(this)
}

def poll() {
  def status = parent.refresh(this)
}

def initialize() {
    if (settings.createChildStateDevices) {
        createChildSwitch ("Self-Powered")
        createChildSwitch ("Time-Based Control")
    } else {
        removeChild ("Self-Powered")
        removeChild ("Time-Based Control")
    }
    if (settings.createChildMeterDevices) {
        createChildMeter ("Solar Power")
        createChildMeter ("Grid Power")
        createChildMeter ("Powerwall Power")
        createChildMeter ("Home Power")
    } else {
        removeChild ("Solar Power")
        removeChild ("Grid Power")
        removeChild ("Powerwall Power")
        removeChild("Home Power")
    }
    if (settings.createStormwatchDevices) {
        createChildSwitch ("Storm Watch Enabled")
        createChildSwitch ("Storm Watch Active")
    } else {
        removeChild ("Storm Watch Enabled")
        removeChild ("Storm Watch Active")
    }
    refresh()
}

def createChildSwitch (String suffix) {
    def child = getChildDev (suffix)
    if (!child) {
        log.info "Creating ${suffix} Child Switch Device"
        if (hubIsSt()) {
            child = addChildDevice("SmartThings", "Child Switch", getChildDni(suffix), null,
                 [label: "${device.displayName} - ${suffix}", isComponent: false])
        } else {
            child = addChildDevice("hubitat", "Generic Component Switch", getChildDni(suffix),[completedSetup: true, label: "${device.displayName} - ${suffix}",
                    isComponent: false] )
            child.updateSetting("txtEnable",[value:false, type:"bool"]) //turn of logging on child device
        }
    }
}      

def removeChild (String suffix) {
    def child = getChildDev (suffix)
    if (child) {
        removeChildDevice(child)
    }
}     

void removeChildDevice(child) {
    try {
        log.info "Removing ${child.displayName} "
		deleteChildDevice(child.deviceNetworkId)
    } 
    catch (e) {
        log.warn "Issue removing ${child?.displayName}"
    }
}                               
                    
def createChildMeter(String suffix) {
    def child = getChildDev (suffix)
    if (!child) {
        log.info "Creating ${suffix} Child Meter Device"
        if (hubIsSt()) {
            child = addChildDevice("SmartThings", "Child Energy Meter", getChildDni(suffix), null,
                    [label: "${device.displayName} - ${suffix}", isComponent: false])
        } else {
            child = addChildDevice("hubitat", "Generic Component Power Meter", getChildDni(suffix),
			     [completedSetup: true, label: "${device.displayName} - ${suffix}",isComponent: false] )
            child.updateSetting("txtEnable",[value:false, type:"bool"]) //turn of logging on child device
        }
    }
} 

def ping() {
	log.debug "pinged"	
}

def parse(String description) {
    log.debug "${description}"
}

def childOn (dni) {
    if (dni.toString().endsWith("Self-Powered")) {
        setSelfPoweredMode()
    } else if (dni.toString().endsWith("Time-Based Control")) {
        setTimeBasedControlMode()
    } else if (dni.toString().endsWith("Storm Watch Enabled")) {
        enableStormwatch()
    }
}

def childOff (dni) {
  if (dni.toString().endsWith("Self-Powered")) {
     setTimeBasedControlMode()
  } else if (dni.toString().endsWith("Time-Based Control")) {
     setSelfPoweredMode()
  } else if (dni.toString().endsWith("Storm Watch Enabled")) {
     disableStormwatch()
  }
}

def componentOff (dni) { //Hubitat
    childOff (dni) 
}

def componentOn (dni) { //Hubitat
    childOn (dni) 
}

def childRefresh (device) {
    refresh() //ST (not invoked?)
}

def componentRefresh (device) {
    refresh() //Hubitat
}

void sendEventIfChanged(def dev, String name, value, String type=null, units="") {
    if (dev && dev.currentValue(name).toString() != value.toString()) {
        String desc = "${dev.displayName}: ${name} is ${value}${units}"
		Map evt = [name: name, value: value, descriptionText: desc]
		if (type) {
			evt.type = type
		}
		if (unit) {
			evt.unit = units
		}
		dev.sendEvent(evt)  
    }
}

def getChildDni (String suffix) {
    return "${device.deviceNetworkId}-${suffix}".toString()
}

def getChildDev (String suffix) {
    return childDevices?.find {it.deviceNetworkId == getChildDni(suffix)} 
}

def refreshMode (String mode) {
    if (!mode) {
        mode = device.currentValue("currentOpState").toString()
    }
    if (mode == "Self-Powered") {
        sendEventIfChanged(getChildDev("Time-Based Control"), "switch", "off")
        sendEventIfChanged(getChildDev("Self-Powered"), "switch", "on")
    } else if (mode == "Time-Based Control") {
        sendEventIfChanged(getChildDev("Time-Based Control"), "switch", "on")
        sendEventIfChanged(getChildDev("Self-Powered"), "switch", "off")
    }
    if (device.currentValue("stormwatch")?.toBoolean()) {
        sendEventIfChanged(getChildDev("Storm Watch Enabled"), "switch", "on")
    } else {
        sendEventIfChanged(getChildDev("Storm Watch Enabled"), "switch", "off")
    }
    if (device.currentValue("stormwatchActive")?.toBoolean()) {
        sendEventIfChanged(getChildDev("Storm Watch Active"), "switch", "on")
    } else {
        sendEventIfChanged(getChildDev("Storm Watch Active"), "switch", "off")
    }
    if (hubIsSt() && device.currentValue("powerSource") != mode) {
        sendEvent([name: "powerSource", value: mode])
    }
}
  
def refreshMeters () {
    sendEventIfChanged(getChildDev("Solar Power"), "power", device.currentValue("solarPower").toString(), null, "W")
    sendEventIfChanged(getChildDev("Grid Power"), "power", device.currentValue("gridPower").toString(), null, "W")
    sendEventIfChanged(getChildDev("Powerwall Power"), "power", device.currentValue("powerwallPower").toString(), null, "W")
    sendEventIfChanged(getChildDev("Home Power"), "power", device.currentValue("loadPower").toString(), null, "W")
}

private getHubType() {
    String hubType = "SmartThings"
    if (state.hubType == null) {
        try {
            include 'asynchttp_v1'
        } catch (e) {
            hubType = "Hubitat"
        }
        state.hubType = hubType
    }
    return state.hubType
} 

Boolean hubIsSt() {
    return (getHubType() == "SmartThings")
}