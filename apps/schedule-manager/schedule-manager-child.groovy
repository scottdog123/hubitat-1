/**
 * ==========================  Schedule Manager (Child App) ==========================
 *
 *  DESCRIPTION:
 *  This app allows users to configure a time table, per device, and schedule the desired state for each configured time. 
 *  Users can select any number of switches/dimmers/buttons, schedule them based on a set time, hub variable or sunrise/set (with offset), 
 *  and configure the desired state/action for that time. Additionally, users can pause the schedule for individual times. 
 *  Advanced options include only running for desired modes or when a specific switch is set in addition to the ability 
 *  to manually pause all schedules.
 *
 *  For feature details and installation instructions, see README at 
 *  https://github.com/evcallia/hubitat/tree/main/apps/schedule-manager
 *
 *  Community Forum: https://community.hubitat.com/t/release-schedule-manager-app/145646
 *
 *  Copyright 2024 Evan Callia
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * =======================================================================================
 *
 *  NOTICE: This file has been modified by *Evan Callia* under compliance with
 *  the Apache 2.0 License from the original work of *Switch Scheduler and More*.
 *
 * Below link is for original source (@kampto)
 * https://github.com/kampto/Hubitat/blob/main/Apps/Switch%20Scheduler%20and%20More
 *
 * =======================================================================================
 *
 *  Changelog:
 *
 *  1.0.0 - 2024-11-18 - Initial release
 *  1.0.1 - 2024-11-25 - Fix typo when logging
 *                     - Fix sunrise/set offset not accounted for
 *                     - Fix issue where sunrise/set wasn't updated daily
 *  1.1.0 - 2024-12-31 - BREAKING CHANGE - App name will be reset
 *                     - Show "(paused)" in app name when app is paused
 *                     - Additional error handling for default schedules and when new devices are added to a schedule
 *  2.0.0 - 2025-04-01 - BREAKING CHANGE - Sunrise/set times will not update unless app is re-saved. It will still run, just using the sunrise/set values at the time the app was updated.
 *                     - Additional error handling for default schedules and when new devices are added to a schedule
 *                     - Support for Hub Variables (datetime) used as start time
 *                       - This allows users to select a Hub Variable to be used as the start time for schedules
 *                       - Can use datetime, time or date (at midnight)
 *                       - Supports variable renaming
 *                       - Registers the use of a Hub Variable with the Hub
 *                       - Update schedules when the Hub Variable changes
 *                     - Fix null pointer that happened occasionally when a new device was added
 *  2.0.1 - 2025-04-03 - Fix app version not showing properly
 *  2.0.2 - 2025-04-21 - Fix error when offset is applied to "time" or "date" only Hub Variables
 *  3.0.0 - 2025-08-12 - BREAKING CHANGE - OAuth required to edit schedules
 *                     - Schedules will continue to run even if oauth is not enabled
 *                     - Modernize UI - Better looking table, popup system for inputs
 *                     - Add ability to manually refresh OAuth token
 *                     - Add support for button devices - push, doubleTap, hold, release
 *                     - Refresh schedules when hub restarts
 *  3.1.0 - 2025-08-13 - Advanced option to restore device state to most recent schedule after hub reboot
 *                     - Advanced option to manually restore device state to most recent schedule
 *  3.2.0 - 2025-08-18 - Add option to set hub restore functionality (when enabled) to be configured on a per-schedule basis
 *                       - New column in table will appear exposing this setting
 *                       - Note that the manual restore also respects these settings, even if the column is hidden
 *  3.2.1 - 2025-09-23 - Automatically stagger the daily sunrise/sunset refresh away from user schedules in the 1 AM hour
 *                     - Allow for configuring debug log duration
 *  3.3.0 - 2025-10-08 - Add advanced option to configure dual times and run at the earlier or later value
 *                     - Update cron generation and UI to support dual-time schedules
 *  3.4.0 - 2025-11-04 - Add advanced option to configure schedules not to run if device is already above/below scheduled level
 *                     - Bug fix for c-5 hub: prevent page refresh when popup is opened
 *  3.5.0 - 2025-11-17 - Add advanced option to turn off devices when mode changes to an unselected mode
 *                     - Add advanced option to restore devices to latest schedule when mode changes to a selected mode
 *  3.6.0 - 2025-02-04 - Add support for restoring button devices after hub reboot and mode changes
 *                     - Add support for lock devices (lock/unlock actions)
 *                     - Add support for garage door/gate devices (open/close actions)
 *                     - Advanced option for locks to auto-lock and doors to auto-close when mode changes to unselected mode
 *                     - Bug fix for curent level is greater/less than desired level - Runs were skipped if switch was off
 *  3.6.1 - 2026-06-09 - Sort hub variable options alphabetically
 *                     - Bug fix for restoring state when "current level is greater/less than desired level" is set
 *                     - Add safeguard for when "only run schedules during selected mode" is set but no modes are selected
 */

import groovy.json.JsonOutput
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Calendar
import org.quartz.CronExpression

def titleVersion() {
    state.name = "Schedule Manager"
    state.version = "3.6.1"
}

definition(
    name: "Schedule Manager (Child App)",
    label: "Schedule Manager Instance",
    namespace: "evcallia",
    author: "Evan Callia",
    description: "Child app for schedule manager",
    category: "Control",
    parent: "evcallia:Schedule Manager",
    iconUrl: "",
    iconX2Url: "",
    oauth: true
)
preferences { page(name: "mainPage") }


//****  Main Page Inputs/Set-Up ****//

def mainPage() {
    if (app.getInstallationState() != "COMPLETE") {
        hide = false
    } else {
        hide = true
    }

    if (state.devices == null) state.devices = [:]

    if (logEnableBool) { logDebug "Main Page Refresh. Devices: ${devices}" }

    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        displayTitle()

        section(getFormat("header", "Initial Set-Up"), hideable: true, hidden: hide) {
            input name: "appName", type: "string", title: "<b>Name this App</b>", required: true, submitOnChange: true, width: 3
            input "devices", "capability.switch, capability.SwitchLevel, capability.doubleTapableButton, capability.holdableButton, capability.pushableButton, capability.releasableButton, capability.lock, capability.garageDoorControl", title: "<b>Select Devices</b>", required: true, multiple: true, submitOnChange: true, width: 6

            devices.each { dev ->
                if (!state.devices["$dev.id"]) {
                    def isButton = dev.capabilities.find { it.name in ["PushableButton", "HoldableButton", "DoubleTapableButton", "ReleasableButton"] } != null
                    def isLock = dev.capabilities.find { it.name == "Lock" } != null
                    def isDoor = dev.capabilities.find { it.name == "GarageDoorControl" } != null

                    // Determine initial capability: Dimmer > Button > Lock > Door > Switch
                    def initialCapability = "Switch"
                    if (dev.capabilities.find { it.name == "SwitchLevel" }) {
                        initialCapability = "Dimmer"
                    } else if (isButton) {
                        initialCapability = "Button"
                    } else if (isLock) {
                        initialCapability = "Lock"
                    } else if (isDoor) {
                        initialCapability = "Door"
                    }

                    state.devices["$dev.id"] = [
                        zone: 0,
                        capability: initialCapability,
                        schedules: [
                            (UUID.randomUUID().toString()): generateDefaultSchedule()
                        ],
                    ]
                }
            }

            if (appName) {
                String label = appName
                if (pauseBool) {
                    label += " <span style='color:red'>(Paused)</span>"
                }
                app.updateLabel(label)
                updated()
            }
        }

        // Needs to happen before table is rendered so 'Restore' column is conditionally rendered properly
        if (!modeBool) {
            if (turnOffUnselectedModesBool) {
                modeSettingsCleared = true
                app?.updateSetting("turnOffUnselectedModesBool", [value: "false", type: "bool"])
            }
            if (restoreAfterModeChangeBool) {
                modeSettingsCleared = true
                app?.updateSetting("restoreAfterModeChangeBool", [value: "false", type: "bool"])
            }
        }

        section {
            if (devices) {
                def devicesToRemove = []
                state.devices.each { deviceId, deviceConfig ->
                    // If the device ID is not in the currently selected devices, mark it for removal
                    if (!devices.find { it.id == deviceId }) {
                        devicesToRemove << deviceId
                    }
                }

                // Remove the devices from state.devices
                devicesToRemove.each { deviceId ->
                    state.devices.remove(deviceId)
                }

                paragraph displayTable()
            }
        }


//***  Advanced Inputs ***//

        section(getFormat("header", "Advanced Options"), hideable: true, hidden: false) {
            input "updateButton", "button", title: "Update/Store schedules without hitting Done"
            input "refreshOAuthToken", "button", title: "Refresh OAuth Token"
            input "restoreStateButton", "button", title: "Restore state of all devices to most recent schedule"
            input name: "modeBool", type: "bool", title: getFormat("important2", "<b>Only run schedules during a selected mode?</b><br><small>Home, Away,.. Applies to all Devices</small>"), defaultValue: false, submitOnChange: true, style: 'margin-left:10px'
            if (modeBool) {
                input name: "mode", type: "mode", title: getFormat("important2", "<b>Select mode(s) for schedules to run</b>"), defaultValue: false, submitOnChange: true, style: 'margin-left:70px', multiple: true
                input name: "turnOffUnselectedModesBool", type: "bool", title: getFormat("important2", "<b>Turn off all devices in unselected modes?</b><br><small>When the mode changes to an unselected option, all controllable devices will be turned off.</small><br><small>Additionally, locks will be locked and doors will be closed.</small>"), defaultValue: false, submitOnChange: true, style: 'margin-left:70px'
                input name: "restoreAfterModeChangeBool", type: "bool", title: getFormat("important2", "<b>Restore to latest schedule after mode change?</b><br><small>When the mode changes to a selected option, devices will be restored using the latest schedule.<br>Adds \"Restore\" column.</small>"), defaultValue: false, submitOnChange: true, style: 'margin-left:70px'
            }
            input name: "switchActivationBool", type: "bool", title: getFormat("important2", "<b>Only run schedules when a specific switch is set</b>"), defaultValue: false, submitOnChange: true, style: 'margin-left:10px'
            if (switchActivationBool) {
                input name: "activationSwitch", type: "capability.switch", title: getFormat("important2", "Select required switch for activation"), submitOnChange: true, style: 'margin-left:70px', multiple: false, required: true
                input name: "activationSwitchOnOff", type: "enum", title: getFormat("important2", "on or off?"), submitOnChange: true, style: 'margin-left:70px', multiple: false, required: true, options: ["on", "off"]
            }
            input name: "activateOnBeforeLevelBool", type: "bool", title: getFormat("important2", "<b>Set 'on' before 'level'?</b><br><small>Use this option if a device does not turn on with a 'setLevel' command, but first needs to be turned on</small>"), defaultValue: false, submitOnChange: true, style: 'margin-left:10px'
            input name: "onlyRunWhenNotAlreadySetBool", type: "bool", title: getFormat("important2", "<b>Only run when not already set?</b><br><small>When enabled, schedules can skip running if the device level already matches the desired value.<br>Adds 'Don't Run If Value Is' column.</small>"), defaultValue: false, submitOnChange: true, style: 'margin-left:10px'
            input name: "dualTimeBool", type: "bool", title: getFormat("important2", "<b>Enable earlier/later dual times?</b><br><small>Allows each schedule to configure two times and run at the earlier or later value.<br>Adds 'Earlier or Later' column.</small>"), defaultValue: false, submitOnChange: true, style: 'margin-left:10px'
            input name: "restoreAfterBootBool", type: "bool", title: getFormat("important2", "<b>Restore device states after hub reboot?</b><br><small>When enabled, devices will be set to their last scheduled state after hub restart.<br>Adds \"Restore\" column.<br>The most recent run for a schedule must be within 7 days or it will be ignored.<br>If 'modes' or 'activation switch' are selected, restore will only take place if those conditions are met.</small>"), defaultValue: false, submitOnChange: true, style: 'margin-left:10px'
            input name: "pauseBool", type: "bool", title: getFormat("important2","<b>Pause all schedules</b>"), defaultValue:false, submitOnChange:true, style: 'margin-left:10px'
            input name: "logEnableBool", type: "bool", title: getFormat("important2", "<b>Enable Logging of App based device activity and refreshes?</b><br><small>Auto disables after configured duration</small>"), defaultValue: true, submitOnChange: true, style: 'margin-left:10px'
            if (logEnableBool) {
                input name: "logEnableMinutes", type: "number", title: getFormat("important2", "<b>How many minutes should logging stay enabled?</b><br><small>0 = Indefinite</small>"), defaultValue: 60, submitOnChange: true, style: 'margin-left:70px'
            }
        }


//****  Notes Section ****//

        section(getFormat("header", "Usage Notes"), hideable: true, hidden: hide) {
            paragraph """
                <ul>
                    <li>Use for any switch, outlet, dimmer, button, lock, or garage door/gate. This may also include shades or others depending on your driver. Add as many as you want to the table.</li>
                    <li>In order to use this app, you must enable OAuth. This can be done by opening the Hubitat sidenav and clicking 'Apps Code'. Find 'Schedule Manager (Child App)' and click it. This opens code editor. On the top right, click the three stacked dots to open the menu and select 'OAuth' > 'Enable OAuth in App'.</li>
                    <li>If you ever update your OAuth token, you must click 'Refresh OAuth Token' in the 'Advanced Options' of this instance in order for the app to get the new token.</li>
                    <li>Enter Start time in 24 hour format.</li>
                    <li>To use Sunset/Sunrise with/- offset, check 'Use Sun Set/Rise' box, check sunrise or sunset icon, click number to enter offset.</li>
                    <li>To use Hub Variables with/- offset, check 'Use Hub Variable' box, select your variable, click number to enter offset.</li>
                    <li>If you make/change a schedule, it wont take unless you hit 'Done' or 'Update/Store'.</li>
                    <li>Optional: Select which modes to run schedules for.</li>
                    <ul>
                        <li>Optional: Turn off devices when mode changes to an unselected mode. Locks will auto-lock and garage doors/gates will auto-close.</li>
                        <li>Optional: Restore devices to latest schedule when mode changes to a selected mode. When this option is selected, a new column called "Restore" will appear in the table where you can manage this setting for individual times.</li>
                    </ul>
                    <li>Optional: Select a switch that needs to be set on/off in order for schedules to run. This can be used as an override switch or essentially a pause button for all schedules.</li>
                    <li>Optional: Select whether you want device to first receive an 'on' command before a 'setLevel' command. Useful if a device does not turn on via a 'setLevel' command.</li>
                    <li>Optional: Select whether you want to restore device state to last known schedule after hub reboot. When this option is selected, a new column called "Restore" will appear in the table where you can manage this setting for individual times. This respects the options for 'modes' and 'activation switches'. If there is not a schedule for the device in the last 7 days then the restore will be ignored. This is common if you're using hub variables and the value changed to some time in the future.</li>
                    <li>Optional: Enable dual times. This allows you to select 2 times for a schedule and have it run at the earlier/later of them. Adds 'Earlier or Later' column.</li>
                    <li>Optional: Configure schedules not to run if device is already above/below scheduled level. Adds 'Don't Run If Value Is' column.</li>
                    <li>Optional: Select whether you want to pause all schedules.</li>
                </ul>"""
        }

        section(getFormat("header", "Support")) {
            paragraph """
                <ul>
                    <li>Donations are welcome and appreciated! Support the project using <a href='https://www.paypal.com/donate/?hosted_button_id=P38WNJK735N9N'>PayPal</a> or <a href='https://venmo.com/u/Evan-Callia'>Venmo</a>.</li>
                    <li><b>Need help?</b> Visit the <a href='https://community.hubitat.com/t/release-schedule-manager-app/145646' target='_blank'>Hubitat Community</a> for support.</li>
                    <li><b>Found a bug?</b> Please post it on the community thread or report it on the <a href='https://github.com/evcallia/hubitat/tree/main/apps/schedule-manager'>GitHub Repository</a> by opening an issue.</li>
                </ul>"""
        }
    }
}

//****  API Routes ****//
mappings {
    path("/updateTime") {
        action: [POST: "updateTime"]
    }

    path("/updateOffset") {
        action: [POST: "updateOffset"]
    }

    path("/updateDesiredLevel") {
        action: [POST: "updateDesiredLevel"]
    }

    path("/updateHubVariable") {
        action: [POST: "updateHubVariable"]
    }

    path("/getHubVariableOptions") {
        action: [GET: "getHubVariableOptions"]
    }

    path("/updateButtonConfig") {
        action: [POST: "updateButtonConfig"]
    }

    path("/updateLockAction") {
        action: [POST: "updateLockAction"]
    }

    path("/updateDoorAction") {
        action: [POST: "updateDoorAction"]
    }

    path("/getScheduleTable") {
        action: [GET: "getScheduleTable"]
    }
}

//****  API Route Handlers ****//
def updateTime() {
    logDebug "updateTime called with args ${request.JSON}"
    def json = request.JSON
    def deviceId = json.deviceId
    def scheduleId = json.scheduleId
    def timeType = json.timeType
    def newStartTime = json.startTime
    def zdt = ZonedDateTime.now().withHour(newStartTime.split(':')[0].toInteger())
                                .withMinute(newStartTime.split(':')[1].toInteger())
                                .withSecond(0)
                                .withNano(0)
    def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    def schedule = state.devices[deviceId].schedules[scheduleId]
    if (timeType == "secondary") {
        def secondary = ensureSecondaryTimeConfig(schedule)
        secondary.startTime = zdt.format(formatter)
    } else {
        schedule.startTime = zdt.format(formatter)
    }
}

def updateOffset() {
    logDebug "updateOffset called with args ${request.JSON}"
    def json = request.JSON
    def deviceId = json.deviceId
    def scheduleId = json.scheduleId
    def timeType = json.timeType
    def newOffset = json.offset.toInteger()
    def schedule = state.devices[deviceId].schedules[scheduleId]

    if (timeType == "secondary") {
        def secondary = ensureSecondaryTimeConfig(schedule)
        secondary.offset = newOffset
    } else {
        schedule.offset = newOffset
    }

    refreshVariables()

    if (timeType == "secondary") {
        def secondary = ensureSecondaryTimeConfig(schedule)
        if (secondary.useVariableTime) {
            render contentType: "application/json", data: JsonOutput.toJson([startTime: formatHubVariableNameWithTime(secondary.variableTime, secondary.startTime), offset: newOffset, varType: "hubVariable", timeType: "secondary"])
        } else {
            def newStartTime = getTimeFromDateTimeString(secondary.startTime)
            render contentType: "application/json", data: JsonOutput.toJson([startTime: newStartTime, offset: newOffset, varType: "sun", timeType: "secondary"])
        }
    } else {
        if (schedule.useVariableTime) {
            render contentType: "application/json", data: JsonOutput.toJson([startTime: formatHubVariableNameWithTime(schedule.variableTime, schedule.startTime), offset: newOffset, varType: "hubVariable", timeType: "primary"])
        } else {
            def newStartTime = getTimeFromDateTimeString(schedule.startTime)
            render contentType: "application/json", data: JsonOutput.toJson([startTime: newStartTime, offset: newOffset, varType: "sun", timeType: "primary"])
        }
    }
}

def updateDesiredLevel() {
    logDebug "updateDesiredLevel called with args ${request.JSON}"
    def json = request.JSON
    def deviceId = json.deviceId
    def scheduleId = json.scheduleId
    def newLevel = json.level.toInteger()
    state.devices[deviceId].schedules[scheduleId].desiredLevel = newLevel
}

def updateHubVariable() {
    logDebug "updateHubVariable called with args ${request.JSON}"
    def json = request.JSON
    def deviceId = json.deviceId
    def scheduleId = json.scheduleId
    def timeType = json.timeType
    def newVariable = json.hubVariable
    def schedule = state.devices[deviceId].schedules[scheduleId]

    if (timeType == "secondary") {
        def secondary = ensureSecondaryTimeConfig(schedule)
        secondary.variableTime = newVariable
    } else {
        schedule.variableTime = newVariable
    }

    updateVariableTimes()

    if (timeType == "secondary") {
        def secondary = ensureSecondaryTimeConfig(schedule)
        render contentType: "application/json", data: JsonOutput.toJson([startTime: formatHubVariableNameWithTime(newVariable, secondary.startTime), timeType: "secondary"])
    } else {
        render contentType: "application/json", data: JsonOutput.toJson([startTime: formatHubVariableNameWithTime(newVariable, schedule.startTime), timeType: "primary"])
    }
}

def getHubVariableOptions() {
    logDebug "getHubVariableOptions called"
    render contentType: "application/json", data: JsonOutput.toJson(getHubVariableList())
}

def updateButtonConfig() {
    logDebug "updateButtonConfig called with args ${request.JSON}"
    def json = request.JSON
    def deviceId = json.deviceId
    def scheduleId = json.scheduleId
    def buttonNumber = json.buttonNumber?.toInteger()
    def buttonAction = json.buttonAction
    if (buttonNumber != null) {
        state.devices[deviceId].schedules[scheduleId].buttonNumber = buttonNumber
    }
    if (buttonAction) {
        state.devices[deviceId].schedules[scheduleId].buttonAction = buttonAction
    }
    render contentType: "application/json", data: JsonOutput.toJson([success: true])
}

def updateLockAction() {
    logDebug "updateLockAction called with args ${request.JSON}"
    def json = request.JSON
    def deviceId = json.deviceId
    def scheduleId = json.scheduleId
    def lockAction = json.lockAction
    if (lockAction && ["lock", "unlock"].contains(lockAction)) {
        state.devices[deviceId].schedules[scheduleId].lockAction = lockAction
    }
    render contentType: "application/json", data: JsonOutput.toJson([success: true])
}

def updateDoorAction() {
    logDebug "updateDoorAction called with args ${request.JSON}"
    def json = request.JSON
    def deviceId = json.deviceId
    def scheduleId = json.scheduleId
    def doorAction = json.doorAction
    if (doorAction && ["open", "close"].contains(doorAction)) {
        state.devices[deviceId].schedules[scheduleId].doorAction = doorAction
    }
    render contentType: "application/json", data: JsonOutput.toJson([success: true])
}

def getScheduleTable() {
    render contentType: "text/html", data: renderScheduleTableMarkup()
}

//****  JS for Table  ****//
String loadCSS() {
    return """
        <style>
            .mdl-data-table {
                width: 100%;
                border-collapse: collapse;
                border: 1px solid #E0E0E0;
                box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                border-radius: 4px;
                overflow: hidden;
                margin-bottom: 20px;
            }
            .mdl-data-table thead {
                background-color: #F5F5F5;
            }
            .mdl-data-table th {
                font-size: 14px !important;
                font-weight: 500;
                color: #424242;
                padding: 8px !important;
                text-align: center;
                border-bottom: 2px solid #E0E0E0;
                border-right: 1px solid #E0E0E0;
            }
            th.prominent-border-right,
            td.prominent-border-right {
                border-right: 1px solid gray !important;
            }
            td.prominent-border-bottom {
                border-bottom: 1px solid gray !important;
            }
            .mdl-data-table td {
                font-size: 14px !important;
                padding: 6px 4px !important;
                text-align: center;
                border-bottom: 1px solid #EEEEEE;
                border-right: 1px solid #EEEEEE;
            }
            td.no-group-highlight {
                background-color: inherit !important;
            }
            .mdl-data-table tbody tr:hover {
                background-color: inherit !important;
            }
            .device-section {
                font-weight: 500;
            }
            .device-link a {
                color: #2196F3;
                text-decoration: none;
                font-weight: 500;
            }
            .device-link a:hover {
                text-decoration: underline;
            }
            tr.schedule-group-primary td.group-highlight {
                background-color: rgba(33, 150, 243, 0.12);
                border-top: 2px solid rgba(33, 150, 243, 0.45) !important;
            }
            tr.schedule-group-secondary td.group-highlight {
                background-color: rgba(33, 150, 243, 0.08);
                border-bottom: 2px solid rgba(33, 150, 243, 0.45) !important;
            }
            .schedule-time-wrapper {
                display: flex;
                flex-direction: column;
                gap: 4px;
                align-items: center;
            }
            .schedule-badge {
                display: inline-block;
                padding: 2px 8px;
                border-radius: 12px;
                font-size: 11px;
                font-weight: 600;
                color: #0D47A1;
                background-color: rgba(13, 71, 161, 0.15);
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }
            .schedule-badge-active {
                color: #1B5E20;
                background-color: rgba(76, 175, 80, 0.25);
            }
            .schedule-badge-secondary {
                color: #004D40;
                background-color: rgba(0, 77, 64, 0.18);
            }
            .schedule-run-indicator {
                display: inline-flex;
                align-items: center;
                gap: 4px;
                padding: 2px 6px;
                border-radius: 10px;
                font-size: 10px;
                font-weight: 600;
                color: #1B5E20;
                background-color: rgba(76, 175, 80, 0.2);
                text-transform: uppercase;
                letter-spacing: 0.4px;
            }
            .schedule-run-indicator iconify-icon {
                font-size: 14px;
            }
            .mdl-cell .mdl-textfield div {
              white-space: normal !important;
            }

            /* Popup styles */
            .popup-overlay {
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background-color: rgba(0, 0, 0, 0.5);
                display: none;
                justify-content: center;
                align-items: center;
                z-index: 1000;
            }
            .popup-container {
                background-color: #FFFFFF;
                border-radius: 8px;
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
                padding: 20px;
                max-width: 400px;
                width: 90%;
                position: relative;
            }
            .popup-title {
                font-size: 18px;
                font-weight: 500;
                color: #212121;
                margin-bottom: 15px;
                padding-bottom: 10px;
                border-bottom: 1px solid #EEEEEE;
            }
            .popup-content {
                margin-bottom: 20px;
            }
            .popup-buttons {
                display: flex;
                justify-content: flex-end;
                gap: 10px;
            }
            .popup-btn {
                padding: 8px 16px;
                border-radius: 4px;
                font-size: 14px;
                font-weight: 500;
                cursor: pointer;
                border: none;
                outline: none;
            }
            .popup-btn-primary {
                background-color: #2196F3;
                color: white;
            }
            .popup-btn-secondary {
                background-color: #E0E0E0;
                color: #424242;
            }
            .popup-close {
                position: absolute;
                top: 15px;
                right: 15px;
                font-size: 20px;
                cursor: pointer;
                color: #9E9E9E;
            }
            .popup-input {
                width: 100%;
                padding: 10px;
                border-radius: 4px;
                border: 1px solid #E0E0E0;
                font-size: 14px;
                margin-bottom: 10px;
            }
            .popup-label {
                display: block;
                font-size: 14px;
                color: #616161;
                margin-bottom: 5px;
            }
        </style>
    """
}

//****  CSS for Table  ****//
String loadScript() {
    return """
        <script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>
        <script>
            // Remove some extra whitespace around Hubitat 'paragraph'
            document.querySelectorAll('.mdl-cell.mdl-textfield > div').forEach(el => {
                el.style.removeProperty('white-space');
            });

            // Popup system
            function showPopup(title, content, submitCallback) {
                // Create popup elements
                const overlay = document.createElement('div');
                overlay.className = 'popup-overlay';

                const container = document.createElement('div');
                container.className = 'popup-container';

                const closeBtn = document.createElement('div');
                closeBtn.className = 'popup-close';
                closeBtn.innerHTML = '&times;';
                closeBtn.onclick = hidePopup;

                const titleEl = document.createElement('div');
                titleEl.className = 'popup-title';
                titleEl.innerText = title;

                const contentEl = document.createElement('div');
                contentEl.className = 'popup-content';
                contentEl.innerHTML = content;

                const buttonsEl = document.createElement('div');
                buttonsEl.className = 'popup-buttons';

                const cancelBtn = document.createElement('button');
                cancelBtn.className = 'popup-btn popup-btn-secondary';
                cancelBtn.innerText = 'Cancel';
                cancelBtn.onclick = hidePopup;

                const submitBtn = document.createElement('button');
                submitBtn.className = 'popup-btn popup-btn-primary';
                submitBtn.innerText = 'Submit';
                submitBtn.onclick = function() {
                    submitCallback(overlay);
                };

                // Build popup structure
                buttonsEl.appendChild(cancelBtn);
                buttonsEl.appendChild(submitBtn);

                container.appendChild(closeBtn);
                container.appendChild(titleEl);
                container.appendChild(contentEl);
                container.appendChild(buttonsEl);

                overlay.appendChild(container);

                // Add to document and show
                document.body.appendChild(overlay);
                setTimeout(() => {
                    overlay.style.display = 'flex';
                    // Focus the first input if exists
                    const firstInput = overlay.querySelector('input, select');
                    if (firstInput) firstInput.focus();
                }, 10);

                return overlay;
            }

            function hidePopup() {
                const overlays = document.querySelectorAll('.popup-overlay');
                overlays.forEach(overlay => {
                    overlay.style.display = 'none';
                    setTimeout(() => {
                        overlay.remove();
                    }, 300);
                });
            }

            function refreshScheduleTable() {
                var xhr = new XMLHttpRequest();
                var cacheBuster = new Date().getTime();
                xhr.open('GET', '/apps/api/${app.id}/getScheduleTable?access_token=${state.accessToken}&_=' + cacheBuster, true);
                xhr.onreadystatechange = function() {
                    if (xhr.readyState === 4) {
                        if (xhr.status === 200) {
                            var wrapper = document.getElementById('schedule-table-wrapper');
                            if (wrapper) {
                                wrapper.innerHTML = xhr.responseText;
                                if (window.Iconify && typeof window.Iconify.scan === 'function') {
                                    window.Iconify.scan(wrapper);
                                }
                            } else {
                                window.location.reload();
                            }
                        } else {
                            console.error('Failed to refresh schedule table', xhr.status);
                            window.location.reload();
                        }
                    }
                };
                xhr.send();
            }

            // Time input popup
            function editStartTimePopup(deviceId, scheduleId, currentValue, timeType) {
                const effectiveTimeType = timeType || null;
                const content = `
                    <label class="popup-label">Enter Desired Time</label>
                    <input type="time" class="popup-input" id="timeInput" value="\${currentValue || ''}">
                    <p style="font-size:12px;color:#757575;margin-top:5px;">Applies to all checked days for this device.</p>
                `;

                showPopup("Set Schedule Time", content, (popup) => {
                    const input = popup.querySelector('#timeInput');
                    if (input.value) {
                        // Send request to hubitat app api to handle state change
                        var xhr = new XMLHttpRequest();
                        xhr.open('POST', '/apps/api/${app.id}/updateTime?access_token=${state.accessToken}', true);
                        xhr.setRequestHeader('Content-Type', 'application/json');

                        var data = JSON.stringify({
                            deviceId: deviceId,
                            scheduleId: scheduleId,
                            startTime: input.value,
                            timeType: effectiveTimeType
                        });

                        xhr.onreadystatechange = function() {
                            if (xhr.readyState === 4 && xhr.status === 200) {
                                // When successful, update the input field in the table so we don't need to page refresh
                                const suffix = effectiveTimeType ? "|" + effectiveTimeType : "";
                                refreshScheduleTable();
                            }
                        };

                        xhr.send(data);
                    }
                    hidePopup();
                });
            }

            // Offset input popup
            function newOffsetPopup(deviceId, scheduleId, currentValue, timeType) {
                const effectiveTimeType = timeType || null;
                const content = `
                    <label class="popup-label">Enter +/- Offset time (in minutes)</label>
                    <input type="number" class="popup-input" id="offsetInput" value="\${currentValue || '0'}">
                    <p style="font-size:12px;color:#757575;margin-top:5px;">Examples: 30, -30, or -90. Applied to Sunrise/Sunset or Hub Variable time.</p>
                `;

                showPopup("Set Time Offset", content, (popup) => {
                    const input = popup.querySelector('#offsetInput');
                    if (input.value !== '') {
                        // Send request to hubitat app api to handle state change
                        var xhr = new XMLHttpRequest();
                        xhr.open('POST', '/apps/api/${app.id}/updateOffset?access_token=${state.accessToken}', true);
                        xhr.setRequestHeader('Content-Type', 'application/json');

                        var data = JSON.stringify({
                            deviceId: deviceId,
                            scheduleId: scheduleId,
                            offset: input.value,
                            timeType: effectiveTimeType
                        });

                        xhr.onreadystatechange = function() {
                            if (xhr.readyState === 4 && xhr.status === 200) {
                                // When successful, update the input field in the table so we don't need to page refresh
                                const jsonResponse = JSON.parse(xhr.responseText);
                                const newStartTime = jsonResponse.startTime;
                                const varType = jsonResponse.varType;
                                const responseTimeType = jsonResponse.timeType || effectiveTimeType;

                                const suffix = responseTimeType ? "|" + responseTimeType : "";

                                var idPrefix = "";
                                if (varType === "hubVariable") {
                                    idPrefix = "selectVariableStartTime|";
                                } else {
                                    idPrefix = "editStartTime|";
                                }

                                refreshScheduleTable();
                            }
                        };

                        xhr.send(data);
                    }
                    hidePopup();
                });
            }
            // Dimmer level popup
            function desiredLevelPopup(deviceId, scheduleId, currentValue) {
                const content = `
                    <label class="popup-label">Enter Dimmer Level (1-100)</label>
                    <input type="number" class="popup-input" id="levelInput" value="\${currentValue || '100'}" min="0" max="100">
                    <p style="font-size:12px;color:#757575;margin-top:5px;">Set the desired brightness level when the device turns on.</p>
                `;

                showPopup("Set Dimmer Level", content, (popup) => {
                    const input = popup.querySelector('#levelInput');
                    if (input.value !== '' && input.value >= 0 && input.value <= 100) {
                        // Send request to hubitat app api to handle state change
                        var xhr = new XMLHttpRequest();
                        xhr.open('POST', '/apps/api/${app.id}/updateDesiredLevel?access_token=${state.accessToken}', true);
                        xhr.setRequestHeader('Content-Type', 'application/json');

                        var data = JSON.stringify({
                            deviceId: deviceId,
                            scheduleId: scheduleId,
                            level: input.value
                        });

                        xhr.onreadystatechange = function() {
                            if (xhr.readyState === 4 && xhr.status === 200) {
                                // When successful, update the input field in the table so we don't need to page refresh
                                document.getElementById("desiredLevel|" + deviceId + "|" + scheduleId).innerHTML = input.value;
                            }
                        };

                        xhr.send(data);
                    }
                    hidePopup();
                });
            }

            // Hub Variable popup
            function selectVariableStartTimePopup(deviceId, scheduleId, currentValue, timeType) {
                const effectiveTimeType = timeType || null;
                const fetchOptions = () => {
                    return fetch('/apps/api/${app.id}/getHubVariableOptions?access_token=${state.accessToken}')
                        .then(response => response.json())
                        .catch(error => {
                            console.error('Error fetching variable options:', error);
                            return {};
                        });
                };

                fetchOptions().then(options => {
                    console.log("fetched options: " + options)
                    // Create a select dropdown from the options
                    let optionsHtml = '<label class="popup-label">Select Hub Variable</label>';
                    optionsHtml += '<select class="popup-input" id="variableSelect">';

                    // Add options from the server
                    for (const [key, value] of Object.entries(options)) {
                        const selected = currentValue === key ? 'selected' : '';
                        optionsHtml += '<option value="' + key + '" ' + selected + '>' + value + '</option>';
                    }

                    optionsHtml += '</select>';
                    optionsHtml += '<p style="font-size:12px;color:#757575;margin-top:5px;">Select a Hub Variable to use as the schedule time.</p>';

                    showPopup("Select Hub Variable", optionsHtml, (popup) => {
                        const input = popup.querySelector('#variableSelect');
                        if (input.value) {
                            // Send request to hubitat app api to handle state change
                            var xhr = new XMLHttpRequest();
                            xhr.open('POST', '/apps/api/${app.id}/updateHubVariable?access_token=${state.accessToken}', true);
                            xhr.setRequestHeader('Content-Type', 'application/json');

                            var data = JSON.stringify({
                                deviceId: deviceId,
                                scheduleId: scheduleId,
                                hubVariable: input.value,
                                timeType: effectiveTimeType
                            });

                            xhr.onreadystatechange = function() {
                                if (xhr.readyState === 4 && xhr.status === 200) {
                                    // When successful, update the input field in the table so we don't need to page refresh
                                    const jsonResponse = JSON.parse(xhr.responseText);
                                    const newStartTime = jsonResponse.startTime;
                                    const responseTimeType = jsonResponse.timeType || effectiveTimeType;
                                    const suffix = responseTimeType ? "|" + responseTimeType : "";

                                    refreshScheduleTable();
                                }
                            };

                            xhr.send(data);
                        }
                        hidePopup();
                    });
                });
            }
            // Button number change
            function buttonNumberChange(deviceId, scheduleId, value) {
                var xhr = new XMLHttpRequest();
                xhr.open('POST', '/apps/api/${app.id}/updateButtonConfig?access_token=${state.accessToken}', true);
                xhr.setRequestHeader('Content-Type', 'application/json');
                var data = JSON.stringify({
                    deviceId: deviceId,
                    scheduleId: scheduleId,
                    buttonNumber: value
                });
                xhr.send(data);
            }

            // Button action change
            function buttonActionChange(deviceId, scheduleId, value) {
                var xhr = new XMLHttpRequest();
                xhr.open('POST', '/apps/api/${app.id}/updateButtonConfig?access_token=${state.accessToken}', true);
                xhr.setRequestHeader('Content-Type', 'application/json');
                var data = JSON.stringify({
                    deviceId: deviceId,
                    scheduleId: scheduleId,
                    buttonAction: value
                });
                xhr.send(data);
            }

            // Lock action change
            function lockActionChange(deviceId, scheduleId, value) {
                var xhr = new XMLHttpRequest();
                xhr.open('POST', '/apps/api/${app.id}/updateLockAction?access_token=${state.accessToken}', true);
                xhr.setRequestHeader('Content-Type', 'application/json');
                var data = JSON.stringify({
                    deviceId: deviceId,
                    scheduleId: scheduleId,
                    lockAction: value
                });
                xhr.send(data);
            }

            // Door action change (garage door/gate)
            function doorActionChange(deviceId, scheduleId, value) {
                var xhr = new XMLHttpRequest();
                xhr.open('POST', '/apps/api/${app.id}/updateDoorAction?access_token=${state.accessToken}', true);
                xhr.setRequestHeader('Content-Type', 'application/json');
                var data = JSON.stringify({
                    deviceId: deviceId,
                    scheduleId: scheduleId,
                    doorAction: value
                });
                xhr.send(data);
            }
        </script>
    """
}

//****  Main Table  ****//

String displayTable() {
    // Sunday - Saturday Check Boxes
    if (state.sunCheckedBox) {
        def (deviceId, scheduleId) = state.sunCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sun = true
        state.remove("sunCheckedBox")
    } else if (state.sunUnCheckedBox) {
        def (deviceId, scheduleId) = state.sunUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sun = false
        state.remove("sunUnCheckedBox")
    }

    if (state.monCheckedBox) {
        def (deviceId, scheduleId) = state.monCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].mon = true
        state.remove("monCheckedBox")
    } else if (state.monUnCheckedBox) {
        def (deviceId, scheduleId) = state.monUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].mon = false
        state.remove("monUnCheckedBox")
    }

    if (state.tueCheckedBox) {
        def (deviceId, scheduleId) = state.tueCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].tue = true
        state.remove("tueCheckedBox")
    } else if (state.tueUnCheckedBox) {
        def (deviceId, scheduleId) = state.tueUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].tue = false
        state.remove("tueUnCheckedBox")
    }

    if (state.wedCheckedBox) {
        def (deviceId, scheduleId) = state.wedCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].wed = true
        state.remove("wedCheckedBox")
    } else if (state.wedUnCheckedBox) {
        def (deviceId, scheduleId) = state.wedUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].wed = false
        state.remove("wedUnCheckedBox")
    }

    if (state.thuCheckedBox) {
        def (deviceId, scheduleId) = state.thuCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].thu = true
        state.remove("thuCheckedBox")
    } else if (state.thuUnCheckedBox) {
        def (deviceId, scheduleId) = state.thuUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].thu = false
        state.remove("thuUnCheckedBox")
    }

    if (state.friCheckedBox) {
        def (deviceId, scheduleId) = state.friCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].fri = true
        state.remove("friCheckedBox")
    } else if (state.friUnCheckedBox) {
        def (deviceId, scheduleId) = state.friUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].fri = false
        state.remove("friUnCheckedBox")
    }

    if (state.satCheckedBox) {
        def (deviceId, scheduleId) = state.satCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sat = true
        state.remove("satCheckedBox")
    } else if (state.satUnCheckedBox) {
        def (deviceId, scheduleId) = state.satUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sat = false
        state.remove("satUnCheckedBox")
    }

    if (state.pauseCheckedBox) {
        def (deviceId, scheduleId) = state.pauseCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].pause = true
        state.remove("pauseCheckedBox")
    } else if (state.pauseUnCheckedBox) {
        def (deviceId, scheduleId) = state.pauseUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].pause = false
        state.remove("pauseUnCheckedBox")
    }

    // Sunrise/Sunset
    if (state.sunTimeCheckedBox) {
        def (deviceId, scheduleId) = state.sunTimeCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sunTime = true
        state.remove("sunTimeCheckedBox")
    } else if (state.sunTimeUnCheckedBox) {
        def (deviceId, scheduleId) = state.sunTimeUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sunTime = false
        state.remove("sunTimeUnCheckedBox")
    }

    if (state.sunsetCheckedBox) {
        def (deviceId, scheduleId) = state.sunsetCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sunset = true
        state.remove("sunsetCheckedBox")
    } else if (state.sunsetUnCheckedBox) {
        def (deviceId, scheduleId) = state.sunsetUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sunset = false
        state.remove("sunsetUnCheckedBox")
    }

    // Variable time
    if (state.useVariableTimeChecked) {
        def (deviceId, scheduleId) = state.useVariableTimeChecked.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].useVariableTime = true
        state.remove("useVariableTimeChecked")
    } else if (state.useVariableTimeUnChecked) {
        def (deviceId, scheduleId) = state.useVariableTimeUnChecked.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].useVariableTime = false
        state.remove("useVariableTimeUnChecked")
    }

    if (state.toggleEarlierLater) {
        def (deviceId, scheduleId) = state.toggleEarlierLater.tokenize('|')
        def schedule = state.devices[deviceId].schedules[scheduleId]
        def current = (schedule.earlierLater ?: "select").toString().toLowerCase()

        String nextValue
        if (current == "select") {
            nextValue = "earlier"
        } else if (current == "earlier") {
            nextValue = "later"
        } else {
            nextValue = "select"
        }
        schedule.earlierLater = nextValue
        ensureSecondaryTimeConfig(schedule)
        state.remove("toggleEarlierLater")
    }

    if (state.sunTimeSecondaryChecked) {
        def (deviceId, scheduleId) = state.sunTimeSecondaryChecked.tokenize('|')
        def schedule = state.devices[deviceId].schedules[scheduleId]
        def secondary = ensureSecondaryTimeConfig(schedule)
        secondary.sunTime = true
        secondary.useVariableTime = false
        state.remove("sunTimeSecondaryChecked")
    } else if (state.sunTimeSecondaryUnChecked) {
        def (deviceId, scheduleId) = state.sunTimeSecondaryUnChecked.tokenize('|')
        def schedule = state.devices[deviceId].schedules[scheduleId]
        ensureSecondaryTimeConfig(schedule).sunTime = false
        state.remove("sunTimeSecondaryUnChecked")
    }

    if (state.sunsetSecondaryChecked) {
        def (deviceId, scheduleId) = state.sunsetSecondaryChecked.tokenize('|')
        def schedule = state.devices[deviceId].schedules[scheduleId]
        ensureSecondaryTimeConfig(schedule).sunset = true
        state.remove("sunsetSecondaryChecked")
    } else if (state.sunsetSecondaryUnChecked) {
        def (deviceId, scheduleId) = state.sunsetSecondaryUnChecked.tokenize('|')
        def schedule = state.devices[deviceId].schedules[scheduleId]
        ensureSecondaryTimeConfig(schedule).sunset = false
        state.remove("sunsetSecondaryUnChecked")
    }

    if (state.useVariableTimeSecondaryChecked) {
        def (deviceId, scheduleId) = state.useVariableTimeSecondaryChecked.tokenize('|')
        def schedule = state.devices[deviceId].schedules[scheduleId]
        def secondary = ensureSecondaryTimeConfig(schedule)
        secondary.useVariableTime = true
        secondary.sunTime = false
        state.remove("useVariableTimeSecondaryChecked")
    } else if (state.useVariableTimeSecondaryUnChecked) {
        def (deviceId, scheduleId) = state.useVariableTimeSecondaryUnChecked.tokenize('|')
        def schedule = state.devices[deviceId].schedules[scheduleId]
        ensureSecondaryTimeConfig(schedule).useVariableTime = false
        state.remove("useVariableTimeSecondaryUnChecked")
    }

    // Add/Remove Run Times
    if (state.addRunTime) {
        def deviceId = state.addRunTime
        state.devices[deviceId].schedules[UUID.randomUUID().toString()] = generateDefaultSchedule()
        state.remove("addRunTime")
    }

    if (state.removeRunTime){
        def (deviceId, scheduleId) = state.removeRunTime.tokenize('|')
        state.devices[deviceId].schedules.remove(scheduleId)

        if (state.devices[deviceId].schedules.size() == 0) {
            state.devices[deviceId].schedules[UUID.randomUUID().toString()] = generateDefaultSchedule()
        }
        state.remove("removeRunTime")
    }

    if (state.toggleSkipComparison) {
        def (deviceId, scheduleId) = state.toggleSkipComparison.tokenize('|')
        def schedule = state.devices[deviceId]?.schedules?.get(scheduleId)
        if (schedule) {
            String current = (schedule.skipComparison ?: "select").toString().toLowerCase()
            if (current == "-") {
                current = "select"
            }
            List<String> options = ["select", "greater", "less"]
            int currentIndex = options.indexOf(current)
            if (currentIndex == -1) {
                currentIndex = 0
            }
            int nextIndex = (currentIndex + 1) % options.size()
            schedule.skipComparison = options[nextIndex]
        }
        state.remove("toggleSkipComparison")
    }

    if (state.restoreToggle){
        def (deviceId, scheduleId) = state.restoreToggle.tokenize('|')

        if (state.devices[deviceId].schedules[scheduleId].restore) {
            state.devices[deviceId].schedules[scheduleId].restore = false
        } else {
            state.devices[deviceId].schedules[scheduleId].restore = true
        }

        state.remove("restoreToggle")
    }

    // Type/Capability
    if (state.setCapabilityDimmer) {
        def deviceId = state.setCapabilityDimmer
        state.devices[deviceId].capability = "Dimmer"
        state.devices[deviceId].schedules.each { _, sched ->
            if (!sched.containsKey('skipComparison') || sched.skipComparison == null || sched.skipComparison == "-") {
                sched.skipComparison = "select"
            }
        }
        state.remove("setCapabilityDimmer")
    }

    if (state.setCapabilitySwitch) {
        def deviceId = state.setCapabilitySwitch
        state.devices[deviceId].capability = "Switch"
        state.devices[deviceId].schedules.each { _, sched ->
            sched.skipComparison = "-"
        }
        state.remove("setCapabilitySwitch")
    }

    if (state.setCapabilityButton) {
        def deviceId = state.setCapabilityButton
        state.devices[deviceId].capability = "Button"
        state.devices[deviceId].schedules.each { _, sched ->
            sched.skipComparison = "-"
        }
        state.remove("setCapabilityButton")
    }

    if (state.setCapabilityLock) {
        def deviceId = state.setCapabilityLock
        state.devices[deviceId].capability = "Lock"
        state.devices[deviceId].schedules.each { _, sched ->
            sched.skipComparison = "-"
        }
        state.remove("setCapabilityLock")
    }

    if (state.setCapabilityDoor) {
        def deviceId = state.setCapabilityDoor
        state.devices[deviceId].capability = "Door"
        state.devices[deviceId].schedules.each { _, sched ->
            sched.skipComparison = "-"
        }
        state.remove("setCapabilityDoor")
    }

    // Desired State
    if (state.desiredState) {
        def (deviceId, scheduleId) = state.desiredState.tokenize('|')
        logDebug "$deviceId|$scheduleId; ${state.devices[deviceId].schedules[scheduleId]}"
        if (state.devices[deviceId].schedules[scheduleId].desiredState == "on") {
            state.devices[deviceId].schedules[scheduleId].desiredState = "off"
        } else {
            state.devices[deviceId].schedules[scheduleId].desiredState = "on"
        }
        state.remove("desiredState")
    }

    // Configure table
    String tableMarkup = renderScheduleTableMarkup()
    return loadCSS() + loadScript() + "<div id='schedule-table-wrapper'>" + tableMarkup + "</div>"
}

String renderScheduleTableMarkup() {
    // Configure table
    String str = """
        <div style='overflow-x:auto'><table class='mdl-data-table'>
        <thead><tr><th>#</th>
        <th>Device</th>
        <th>Current<br>State</th>
        <th>Type</th>
        <th class='prominent-border-right'>Add<br>Run</th>
        <th style='width: 60px !important'>Run<br>Time</th>
        <th>Use Hub<br>Variable?</th>
        <th>Use Sun<br>Set/Rise?</th>
        <th>Rise or<br>Set?</th>
        <th>Offset<br>+/-Min</th>
    """

    if (dualTimeBool) {
        str += "<th>Earlier<br>or Later</th>"
    }

    str += """
        <th>Sun</th>
        <th>Mon</th>
        <th>Tue</th>
        <th>Wed</th>
        <th>Thu</th>
        <th>Fri</th>
        <th>Sat</th>
        <th>Pause<br>Schedule</th>
        <th>Desired<br>State</th>
"""
    if (onlyRunWhenNotAlreadySetBool) {
        str += "<th>Desired<br>Level</th><th class='prominent-border-right'>Don't Run<br>If Value Is</th>"
    } else {
        str += "<th class='prominent-border-right'>Desired<br>Level</th>"
    }

    str += """
        <th>Remove<br>Run</th>
    """

    if (restoreAfterBootBool || restoreAfterModeChangeBool) {
        str += "<th>Restore</th>"
    }

    str += """
        </tr></thead>
    """

    def buildCellAttr = { boolean highlight, boolean includeRightBorder, boolean includeBottomBorder ->
        List<String> classes = []
        if (highlight) {
            classes << "group-highlight"
        }
        if (includeBottomBorder) {
            classes << "prominent-border-bottom"
        }
        if (includeRightBorder) {
            classes << "prominent-border-right"
        }
        classes ? "class='${classes.join(' ')}'" : ""
    }

    int zone = 0
    devices.sort { it.displayName.toLowerCase() }.each { dev ->
        zone += 1

        //**** Setup 'Device' Section of Table ****//

        String deviceLink = "<a href='/device/edit/$dev.id' target='_blank' title='Open Device Page for $dev'>$dev</a>"
        String addNewRunButton = buttonLink("addNew|$dev.id", "<iconify-icon icon='material-symbols:add-circle-outline-rounded'></iconify-icon>", "#4CAF50", "24px")
        int thisZone = state.devices["$dev.id"].zone = zone
        def deviceSchedules = state.devices["$dev.id"].schedules

        deviceSchedules.each { entry ->
            if (!entry.value.earlierLater) {
                entry.value.earlierLater = "select"
            } else {
                entry.value.earlierLater = entry.value.earlierLater.toString().toLowerCase()
            }
            if (!entry.value.containsKey('skipComparison') || entry.value.skipComparison == null) {
                entry.value.skipComparison = state.devices["$dev.id"].capability == "Dimmer" ? "select" : "-"
            } else if (state.devices["$dev.id"].capability == "Dimmer" && entry.value.skipComparison == "-") {
                entry.value.skipComparison = "select"
            } else if (state.devices["$dev.id"].capability != "Dimmer") {
                entry.value.skipComparison = "-"
            }
            ensureSecondaryTimeConfig(entry.value)
        }

        int scheduleCount = deviceSchedules.collect { entry ->
            (dualTimeBool && ["earlier", "later"].contains(entry.value.earlierLater)) ? 2 : 1
        }.sum()

        if (scheduleCount == 0) {
            scheduleCount = 1
        }

        List<String> spanCellClasses = ["no-group-highlight"]
        if (zone != devices.size()) {
            spanCellClasses << "prominent-border-bottom"
        }
        String spanClassAttr = "class='${spanCellClasses.join(' ')}'"

        List<String> deviceCellClasses = ["device-link", "no-group-highlight"]
        if (zone != devices.size()) {
            deviceCellClasses << "prominent-border-bottom"
        }
        String deviceCellClassAttr = "class='${deviceCellClasses.join(' ')}'"

        List<String> addNewCellClasses = ["no-group-highlight", "prominent-border-right"]
        if (zone != devices.size()) {
            addNewCellClasses << "prominent-border-bottom"
        }
        String addNewClassAttr = "class='${addNewCellClasses.join(' ')}'"

        String zoneCell = "<td ${spanClassAttr} rowspan='$scheduleCount'>$thisZone</td>"
        String deviceCell = "<td ${deviceCellClassAttr} rowspan='$scheduleCount'>$deviceLink</td>"

        String statusCell
        def deviceCapability = state.devices["$dev.id"]?.capability

        // Render status icon based on selected capability, not device capabilities
        if (deviceCapability == "Lock") {
            def currentLock = dev.currentValue("lock")
            String lockTitle = currentLock ? "Device is currently ${currentLock}" : "Lock device"
            String lockIcon = currentLock == "locked" ? "lock" : "lock-open"
            String lockColor = currentLock == "locked" ? "#4CAF50" : "#F44336"
            statusCell = "<td ${spanClassAttr} rowspan='$scheduleCount' title='${lockTitle}' style='color:${lockColor};font-weight:bold;text-align:center'>" +
                          "<span style='display:block;font-size:24px'><iconify-icon icon='material-symbols:${lockIcon}'></iconify-icon></span>" +
                          "</td>"
        } else if (deviceCapability == "Door") {
            def currentDoor = dev.currentValue("door")
            String doorTitle = currentDoor ? "Device is currently ${currentDoor}" : "Garage door/gate device"
            String doorIcon = currentDoor == "open" ? "garage-home" : "garage"
            String doorColor = currentDoor == "open" ? "#F44336" : "#4CAF50"
            statusCell = "<td ${spanClassAttr} rowspan='$scheduleCount' title='${doorTitle}' style='color:${doorColor};font-weight:bold;text-align:center'>" +
                          "<span style='display:block;font-size:24px'><iconify-icon icon='material-symbols:${doorIcon}'></iconify-icon></span>" +
                          "</td>"
        } else if (deviceCapability == "Button") {
            // Button device status - buttons don't have on/off state, show neutral icon
            String buttonTitle = "Button device"
            statusCell = "<td ${spanClassAttr} rowspan='$scheduleCount' title='${buttonTitle}' style='color:#2196F3;font-weight:bold;text-align:center'>" +
                          "<span style='display:block;font-size:24px'><iconify-icon icon='material-symbols:radio-button-checked'></iconify-icon></span>" +
                          "</td>"
        } else if (deviceCapability == "Dimmer") {
            String levelDisplay = ""
            String levelTitleSuffix = ""
            def currentLevel = dev.currentValue("level")
            if (currentLevel != null) {
                String currentLevelPercent = "${currentLevel}%"
                levelDisplay = "<span style='display:block;font-size:12px;font-weight:normal;margin-top:2px'>${currentLevelPercent}</span>"
                levelTitleSuffix = " at ${currentLevelPercent}"
            }
            String switchColor = dev.currentSwitch == 'on' ? '#4CAF50' : '#F44336'
            String switchIcon = dev.currentSwitch == 'on' ? 'lightbulb' : 'lightbulb-outline'
            String switchTitle = "Device is currently ${dev.currentSwitch ?: 'unknown'}${levelTitleSuffix}"
            statusCell = "<td ${spanClassAttr} rowspan='$scheduleCount' title='${switchTitle}' style='color:${switchColor};font-weight:bold;text-align:center'>" +
                          "<span style='display:block;font-size:24px'><iconify-icon icon='material-symbols:${switchIcon}'></iconify-icon></span>" +
                          levelDisplay +
                          "</td>"
        } else if (deviceCapability == "Switch") {
            String switchColor = dev.currentSwitch == 'on' ? '#4CAF50' : '#F44336'
            String switchIcon = dev.currentSwitch == 'on' ? 'toggle-on' : 'toggle-off'
            String switchTitle = "Device is currently ${dev.currentSwitch ?: 'unknown'}"
            statusCell = "<td ${spanClassAttr} rowspan='$scheduleCount' title='${switchTitle}' style='color:${switchColor};font-weight:bold;text-align:center'>" +
                          "<span style='display:block;font-size:24px'><iconify-icon icon='material-symbols:${switchIcon}'></iconify-icon></span>" +
                          "</td>"
        } else {
            statusCell = "<td ${spanClassAttr} rowspan='$scheduleCount'></td>"
        }

        def supportedCapabilities = []
        if (dev.capabilities.find { it.name == "SwitchLevel" }) {
            supportedCapabilities.add("Dimmer")
        }
        if (dev.capabilities.find { it.name == "Switch" }) {
            supportedCapabilities.add("Switch")
        }
        if (dev.capabilities.find { it.name in ["PushableButton", "HoldableButton", "DoubleTapableButton", "ReleasableButton"] }) {
            supportedCapabilities.add("Button")
        }
        if (dev.capabilities.find { it.name == "Lock" }) {
            supportedCapabilities.add("Lock")
        }
        if (dev.capabilities.find { it.name == "GarageDoorControl" }) {
            supportedCapabilities.add("Door")
        }

        String capabilityCell
        if (supportedCapabilities.size() > 1) {
            int nextIndex = (supportedCapabilities.indexOf(state.devices["$dev.id"].capability) + 1) % supportedCapabilities.size()
            def capabilityButton = buttonLink("setCapability${supportedCapabilities[nextIndex]}|${dev.id}", state.devices[dev.id].capability, "#2196F3")
            capabilityCell = "<td ${spanClassAttr} rowspan='$scheduleCount' style='font-weight:bold' title='Capability: ${state.devices["$dev.id"].capability}'>$capabilityButton</td>"
        } else {
            capabilityCell = "<td ${spanClassAttr} rowspan='$scheduleCount' title='Capability: ${state.devices["$dev.id"].capability}'>${state.devices["$dev.id"].capability}</td>"
        }

        String addNewCell = "<td ${addNewClassAttr} rowspan='$scheduleCount' title='Click to add new time for this device'>$addNewRunButton</td>"

        //**** Update sunrise/set & hub variable times ****//
        refreshVariables()

        // order schedules so table is sorted by time (using the effective run time)
        Map<String, Long> sortKeyCache = [:]
        Closure<Long> resolveSortKey = { entry ->
            if (!sortKeyCache.containsKey(entry.key)) {
                sortKeyCache[entry.key] = buildScheduleSortKey(entry.value)
            }
            return sortKeyCache[entry.key]
        }

        def sortedSchedules = deviceSchedules.sort { a, b ->
            Long keyA = resolveSortKey(a)
            Long keyB = resolveSortKey(b)
            int comparison = keyA <=> keyB
            if (comparison != 0) {
                return comparison
            }
            return a.key <=> b.key
        }

        int rowsRemaining = scheduleCount
        boolean firstScheduleRow = true

        sortedSchedules.each { scheduleId, schedule ->
            String deviceAndScheduleId = "${dev.id}|$scheduleId"
            boolean hasSecondaryRow = dualTimeBool && ["earlier", "later"].contains(schedule.earlierLater)
            boolean showRunIndicator = hasSecondaryRow
            boolean runsAtSecondary = false
            if (showRunIndicator) {
                def effectiveInfo = getEffectiveTimeConfig(schedule)
                runsAtSecondary = effectiveInfo?.isSecondary ?: false
            }
            boolean isLastRowAfterThis = (!hasSecondaryRow && rowsRemaining == 1)
            boolean addBottomBorder = (isLastRowAfterThis && zone != devices.size())
            String tdAttr = buildCellAttr(hasSecondaryRow, false, addBottomBorder)
            String tdAttrRight = buildCellAttr(hasSecondaryRow, true, addBottomBorder)

            String thisStartTime = getTimeFromDateTimeString(schedule.startTime)
            String thisOffsetTime = (schedule.offset != null) ? schedule.offset.toString() : "0"

            List<String> rowClasses = ["device-section"]
            if (hasSecondaryRow) {
                rowClasses << "schedule-group-primary"
            }
            String rowClassAttr = rowClasses ? " class='${rowClasses.join(' ')}'" : ""
            str += "<tr${rowClassAttr}>"

            if (firstScheduleRow) {
                str += zoneCell + deviceCell + statusCell + capabilityCell + addNewCell
            }

            String startTime
            if (schedule.sunTime) {
                startTime = thisStartTime
            } else if (schedule.useVariableTime) {
                if (schedule.variableTime) {
                    def variableValue = getValueForHubVariable(schedule.variableTime)
                    if (variableValue == null) {
                        startTime = buttonLink("selectVariableStartTime|$deviceAndScheduleId", "Select Variable", "MediumBlue")
                    } else {
                        startTime = buttonLink("selectVariableStartTime|$deviceAndScheduleId", formatHubVariableNameWithTime(schedule.variableTime, schedule.startTime), "MediumBlue")
                    }
                } else {
                    startTime = buttonLink("selectVariableStartTime|$deviceAndScheduleId", "Select<br>Variable", "MediumBlue")
                }
            } else {
                startTime = buttonLink("editStartTime|$deviceAndScheduleId", thisStartTime, "MediumBlue")
            }

            if (hasSecondaryRow) {
                String primaryBadgeClass = runsAtSecondary ? "schedule-badge" : "schedule-badge schedule-badge-active"
                String primaryIndicator = (!runsAtSecondary && showRunIndicator) ? "<span class='schedule-run-indicator' title='Schedule will run at this time'><iconify-icon icon='material-symbols:schedule-rounded'></iconify-icon>Runs At</span>" : ""
                startTime = "<div class='schedule-time-wrapper'><span class='${primaryBadgeClass}'>Time 1</span>${primaryIndicator}${startTime}</div>"
            }

            String sunCheckBoxT = (schedule.sun) ? buttonLink("sunUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("sunChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String monCheckBoxT = (schedule.mon) ? buttonLink("monUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("monChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String tueCheckBoxT = (schedule.tue) ? buttonLink("tueUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("tueChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String wedCheckBoxT = (schedule.wed) ? buttonLink("wedUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("wedChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String thuCheckBoxT = (schedule.thu) ? buttonLink("thuUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("thuChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String friCheckBoxT = (schedule.fri) ? buttonLink("friUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("friChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String satCheckBoxT = (schedule.sat) ? buttonLink("satUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("satChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String pauseCheckBoxT = (schedule.pause) ? buttonLink("pauseUnChecked|$deviceAndScheduleId", "<iconify-icon icon=ic:sharp-pause-circle-filled></iconify-icon>", "red", "23px") : buttonLink("pauseChecked|$deviceAndScheduleId", "<iconify-icon icon=ic:baseline-play-circle></iconify-icon>", "green", "23px")
            String useVariableTimeCheckBoxT = (schedule.useVariableTime) ? buttonLink("useVariableTimeUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "purple", "23px") : buttonLink("useVariableTimeChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String sunTimeCheckBoxT = (schedule.sunTime) ? buttonLink("sunTimeUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "purple", "23px") : buttonLink("sunTimeChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String sunsetCheckBoxT = (schedule.sunset) ? buttonLink("sunsetUnChecked|$deviceAndScheduleId", "<iconify-icon icon=ph:moon-stars-duotone></iconify-icon>", "DodgerBlue", "23px") : buttonLink("sunsetChecked|$deviceAndScheduleId", "<iconify-icon icon='ph:sun-duotone'></iconify-icon>", "orange", "23px")
            String offset = buttonLink("newOffset|$deviceAndScheduleId", thisOffsetTime, "MediumBlue")
            String removeRunButton = buttonLink("removeRunTime|$deviceAndScheduleId", "x", "red", "23px")
            String desiredStateButton = buttonLink("desiredState|$deviceAndScheduleId", schedule.desiredState, "${schedule.desiredState == "on" ? "green" : "red"}", "15px; font-weight:bold")
            String desiredLevelButton = buttonLink("desiredLevel|$deviceAndScheduleId", schedule.desiredLevel.toString(), "MediumBlue")

            // Handle button device specifics
            if (state.devices["$dev.id"].capability == "Button") {
                if (schedule.buttonNumber == null) {
                    schedule.buttonNumber = 1 // Default to button 1 if not set
                }

                // For button devices, show button config instead of desired state
                def buttonCount = dev.currentValue("numberOfButtons") ?: 1
                def buttonNum = schedule.buttonNumber ?: 1
                def buttonOptions = (1..buttonCount).collect { n -> "<option value='${n}' ${n==buttonNum?'selected':''}>button ${n}</option>" }.join('')
                def buttonSelect = "<select id='buttonNumber|${deviceAndScheduleId}' onchange=\"buttonNumberChange('${dev.id}','${scheduleId}',this.value)\">${buttonOptions}</select>"

                def actions = dev.getSupportedCommands()?.collect { it.toString() }.intersect(["doubleTap", "hold", "push", "release"]) ?: ["No commands found"]
                def actionVal = schedule.buttonAction ?: actions[0]
                def actionOptions = actions.collect { a -> "<option value='${a}' ${a==actionVal?'selected':''}>${a}</option>" }.join('')
                def actionSelect = "<select id='buttonAction|${deviceAndScheduleId}' onchange=\"buttonActionChange('${dev.id}','${scheduleId}',this.value)\">${actionOptions}</select>"

                desiredStateButton = "${actionSelect}<br>${buttonSelect}"
                desiredLevelButton = ""
            } else if (state.devices["$dev.id"].capability == "Lock") {
                // For lock devices, show lock/unlock dropdown
                def lockAction = schedule.lockAction ?: "lock"
                def lockOptions = ["lock", "unlock"].collect { a -> "<option value='${a}' ${a==lockAction?'selected':''}>${a}</option>" }.join('')
                def lockSelect = "<select id='lockAction|${deviceAndScheduleId}' onchange=\"lockActionChange('${dev.id}','${scheduleId}',this.value)\">${lockOptions}</select>"

                desiredStateButton = lockSelect
                desiredLevelButton = ""
            } else if (state.devices["$dev.id"].capability == "Door") {
                // For garage door/gate devices, show open/close dropdown
                def doorAction = schedule.doorAction ?: "close"
                def doorOptions = ["open", "close"].collect { a -> "<option value='${a}' ${a==doorAction?'selected':''}>${a}</option>" }.join('')
                def doorSelect = "<select id='doorAction|${deviceAndScheduleId}' onchange=\"doorActionChange('${dev.id}','${scheduleId}',this.value)\">${doorOptions}</select>"

                desiredStateButton = doorSelect
                desiredLevelButton = ""
            } else {
                desiredStateButton = buttonLink("desiredState|$deviceAndScheduleId", schedule.desiredState, "${schedule.desiredState == "on" ? "green" : "red"}", "15px; font-weight:bold")
                desiredLevelButton = buttonLink("desiredLevel|$deviceAndScheduleId", schedule.desiredLevel.toString(), "MediumBlue")
            }

            String skipComparisonButton = "-"
            if (onlyRunWhenNotAlreadySetBool) {
                if (state.devices["$dev.id"].capability == "Dimmer") {
                    String comparison = (schedule.skipComparison ?: "select").toString().toLowerCase()
                    if (comparison == "-") {
                        comparison = "select"
                    }
                    if (!["select", "greater", "less"].contains(comparison)) {
                        comparison = "select"
                    }
                    schedule.skipComparison = comparison
                    String skipLabel
                    String skipColor
                    switch (comparison) {
                        case "greater":
                            skipLabel = "Greater"
                            skipColor = "#1E88E5"
                            break
                        case "less":
                            skipLabel = "Less"
                            skipColor = "#1E88E5"
                            break
                        default:
                            skipLabel = "Select"
                            skipColor = "#757575"
                            break
                    }
                    skipComparisonButton = buttonLink("toggleSkipComparison|$deviceAndScheduleId", skipLabel, skipColor)
                } else {
                    schedule.skipComparison = "-"
                }
            }

            if (schedule.sunTime) {
                str += "<td ${tdAttr} id='editStartTime|${deviceAndScheduleId}' title='Start Time with Sunset or Sunrise +/- offset'>$startTime</td>" +
                        "<td ${tdAttr}>Using Sun<br>Time</td>"
            } else if (schedule.useVariableTime){
                str += "<td ${tdAttr} title='Select Hub Variable'>$startTime</td>" +
                        "<td ${tdAttr} title='Use a hub variable'>$useVariableTimeCheckBoxT</td>"
            } else {
                str += "<td ${tdAttr} style='font-weight:bold !important' title='${thisStartTime ? "Click to Change Start Time" : "Select"}'>$startTime</td>" +
                        "<td ${tdAttr} title='Use a hub variable'>$useVariableTimeCheckBoxT</td>"
            }

            if (schedule.useVariableTime) {
                str += "<td ${tdAttr} colspan=2 title='Variable selected time (not sunset/sunrise)'>Hub Variable</td>" +
                       "<td ${tdAttr} style='font-weight:bold' title='${thisOffsetTime ? "Click to set +/- minutes for Sunset or Sunrise start time" : "Select"}'>$offset</td>"
            } else {
                str += "<td ${tdAttr} title='Use Sunrise or Sunset for Start time'>$sunTimeCheckBoxT</td>"
                if (schedule.sunTime) {
                str += "<td ${tdAttr} title='Sunset start (moon), otherwise Sunrise start(sun)'>$sunsetCheckBoxT</td>" +
                            "<td ${tdAttr} style='font-weight:bold' title='${thisOffsetTime ? "Click to set +/- minutes for Sunset or Sunrise start time" : "Select"}'>$offset</td>"
                } else {
                str += "<td ${tdAttr} colspan=2 title='User Entered time (not sunset/sunrise)'>User Time</td>"
                }
            }

            if (dualTimeBool) {
                String selectionValue = (schedule.earlierLater ?: "select").toString().toLowerCase()
                String displayText = selectionValue.capitalize()
                String toggleColor = selectionValue == "select" ? "#757575" : "#2196F3"
                String earlierLaterButton = buttonLink("toggleEarlierLater|$deviceAndScheduleId", displayText, toggleColor)
                str += "<td ${tdAttr} title='Choose which configured time should run'>$earlierLaterButton</td>"
            }

             str += "<td ${tdAttr} title='Check Box to select Day'>$sunCheckBoxT</td>" +
                    "<td ${tdAttr} title='Check Box to select Day'>$monCheckBoxT</td>" +
                    "<td ${tdAttr} title='Check Box to select Day'>$tueCheckBoxT</td>" +
                    "<td ${tdAttr} title='Check Box to select Day'>$wedCheckBoxT</td>" +
                    "<td ${tdAttr} title='Check Box to select Day'>$thuCheckBoxT</td>" +
                    "<td ${tdAttr} title='Check Box to select Day'>$friCheckBoxT</td>" +
                    "<td ${tdAttr} title='Check Box to select Day'>$satCheckBoxT</td>" +
                    "<td ${tdAttr} title='Check Box to Pause this device schedule, Red is paused, Green is run'>$pauseCheckBoxT</td>" +
                    "<td ${tdAttr} title='${state.devices["$dev.id"].capability == "Button" ? "Button Configuration" : "Click to change desired state"}'>$desiredStateButton</td>"

            boolean showDesiredLevel = !(state.devices["$dev.id"].capability == "Switch" || state.devices["$dev.id"].capability == "Button" || (schedule.desiredState && schedule.desiredState == "off"))
            if (showDesiredLevel) {
                str += "<td ${onlyRunWhenNotAlreadySetBool ? tdAttr : tdAttrRight} style='font-weight:bold' title='Click to set dimmer level'>$desiredLevelButton</td>"
            } else {
                if (state.devices["$dev.id"].capability != "Dimmer") {
                    schedule.skipComparison = "-"
                }
                str += "<td ${onlyRunWhenNotAlreadySetBool ? tdAttr : tdAttrRight}>-</td>"
            }

            if (onlyRunWhenNotAlreadySetBool) {
                if (showDesiredLevel) {
                    str += "<td ${tdAttrRight} title='Skip when current level already meets criteria'>$skipComparisonButton</td>"
                } else {
                    str += "<td ${tdAttrRight}>-</td>"
                }
            }

            str += "<td ${tdAttr} title='Click to remove run'>$removeRunButton</td>"

            if (restoreAfterBootBool || restoreAfterModeChangeBool) {
                if (schedule.restore == null) {
                    schedule.restore = true // Default to true if not set
                }

                def restoreToggle = (schedule.restore) ? buttonLink("restoreToggle|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("restoreToggle|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
                str += "<td ${tdAttr} title='Restore this schedule at boot/mode change'>$restoreToggle</td>"
            }

            str += "</tr>"
            rowsRemaining -= 1
            firstScheduleRow = false

            if (hasSecondaryRow) {
                def secondary = ensureSecondaryTimeConfig(schedule)
                boolean secondaryIsLast = (rowsRemaining == 1)
                boolean secondaryBottomBorder = (secondaryIsLast && zone != devices.size())
                String secondaryAttr = buildCellAttr(true, false, secondaryBottomBorder)
                String secondaryRightAttr = buildCellAttr(true, true, secondaryBottomBorder)

                String secondaryStartTime = getTimeFromDateTimeString(secondary.startTime)
                String secondaryOffsetValue = (secondary.offset != null) ? secondary.offset.toString() : "0"

                String secondaryUseVariableButton = (secondary.useVariableTime) ? buttonLink("useVariableTimeSecondaryUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "purple", "23px") : buttonLink("useVariableTimeSecondaryChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
                String secondarySunTimeButton = (secondary.sunTime) ? buttonLink("sunTimeSecondaryUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "purple", "23px") : buttonLink("sunTimeSecondaryChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
                String secondarySunsetButton = (secondary.sunset) ? buttonLink("sunsetSecondaryUnChecked|$deviceAndScheduleId", "<iconify-icon icon=ph:moon-stars-duotone></iconify-icon>", "DodgerBlue", "23px") : buttonLink("sunsetSecondaryChecked|$deviceAndScheduleId", "<iconify-icon icon='ph:sun-duotone'></iconify-icon>", "orange", "23px")
                String secondaryOffsetButton = buttonLink("newOffset|$deviceAndScheduleId|secondary", secondaryOffsetValue, "MediumBlue")

                String secondaryStartDisplay
                if (secondary.sunTime) {
                    secondaryStartDisplay = secondaryStartTime
                } else if (secondary.useVariableTime) {
                    if (secondary.variableTime) {
                        def variableValue = getValueForHubVariable(secondary.variableTime)
                        if (variableValue == null) {
                            secondaryStartDisplay = buttonLink("selectVariableStartTime|$deviceAndScheduleId|secondary", "Select Variable", "MediumBlue")
                        } else {
                            secondaryStartDisplay = buttonLink("selectVariableStartTime|$deviceAndScheduleId|secondary", formatHubVariableNameWithTime(secondary.variableTime, secondary.startTime), "MediumBlue")
                        }
                    } else {
                        secondaryStartDisplay = buttonLink("selectVariableStartTime|$deviceAndScheduleId|secondary", "Select<br>Variable", "MediumBlue")
                    }
                } else {
                    secondaryStartDisplay = buttonLink("editStartTime|$deviceAndScheduleId|secondary", secondaryStartTime, "MediumBlue")
                }

                String secondaryBadgeClass = runsAtSecondary ? "schedule-badge schedule-badge-secondary schedule-badge-active" : "schedule-badge schedule-badge-secondary"
                String secondaryIndicator = (runsAtSecondary && showRunIndicator) ? "<span class='schedule-run-indicator' title='Schedule will run at this time'><iconify-icon icon='material-symbols:schedule-rounded'></iconify-icon>Runs At</span>" : ""
                secondaryStartDisplay = "<div class='schedule-time-wrapper'><span class='${secondaryBadgeClass}'>Time 2</span>${secondaryIndicator}${secondaryStartDisplay}</div>"

                str += "<tr class='device-section schedule-group-secondary'>"
                if (secondary.sunTime) {
                    str += "<td ${secondaryAttr} id='editStartTime|${deviceAndScheduleId}|secondary' title='Start Time with Sunset or Sunrise +/- offset'>$secondaryStartDisplay</td>" +
                           "<td ${secondaryAttr}>Using Sun<br>Time</td>"
                } else if (secondary.useVariableTime) {
                    str += "<td ${secondaryAttr} title='Select Hub Variable'>$secondaryStartDisplay</td>" +
                           "<td ${secondaryAttr} title='Use a hub variable'>$secondaryUseVariableButton</td>"
                } else {
                    str += "<td ${secondaryAttr} style='font-weight:bold !important' title='${secondaryStartTime ? "Click to Change Start Time" : "Select"}'>$secondaryStartDisplay</td>" +
                           "<td ${secondaryAttr} title='Use a hub variable'>$secondaryUseVariableButton</td>"
                }

                if (secondary.useVariableTime) {
                    str += "<td ${secondaryAttr} colspan=2 title='Variable selected time (not sunset/sunrise)'>Hub Variable</td>" +
                           "<td ${secondaryAttr} style='font-weight:bold' title='${secondaryOffsetValue ? "Click to set +/- minutes for Sunset or Sunrise start time" : "Select"}'>$secondaryOffsetButton</td>"
                } else {
                    str += "<td ${secondaryAttr} title='Use Sunrise or Sunset for Start time'>$secondarySunTimeButton</td>"
                    if (secondary.sunTime) {
                        str += "<td ${secondaryAttr} title='Sunset start (moon), otherwise Sunrise start(sun)'>$secondarySunsetButton</td>" +
                               "<td ${secondaryAttr} style='font-weight:bold' title='${secondaryOffsetValue ? "Click to set +/- minutes for Sunset or Sunrise start time" : "Select"}'>$secondaryOffsetButton</td>"
                    } else {
                        str += "<td ${secondaryAttr} colspan=2 title='User Entered time (not sunset/sunrise)'>User Time</td>"
                    }
                }

                if (dualTimeBool) {
                    str += "<td ${secondaryAttr}></td>"
                }

                // Blank out remaining columns for the secondary row
                7.times { str += "<td ${secondaryAttr}></td>" }
                str += "<td ${secondaryAttr}></td>" // Pause
                str += "<td ${secondaryAttr}></td>" // Desired state
                if (onlyRunWhenNotAlreadySetBool) {
                    str += "<td ${secondaryAttr}></td>" // Desired level
                    str += "<td ${secondaryRightAttr}></td>" // Skip column
                } else {
                    str += "<td ${secondaryRightAttr}></td>" // Desired level
                }
                str += "<td ${secondaryAttr}></td>" // Remove run

                if (restoreAfterBootBool || restoreAfterModeChangeBool) {
                    str += "<td ${secondaryAttr}></td>"
                }

                str += "</tr>"
                rowsRemaining -= 1
            }
        }
    }
    str += "</table></div>"
    return str
}


//**** Handlers ****//

void switchHandler(data) {
    if (logEnableBool) logDebug "switchHandler - data: $data"

    // If this is set, crons should not even be scheduled but we'll check anyways
    if (pauseBool) {
        if (logEnableBool) logDebug "All schedules have been manually paused. Skipping."
        return
    }

    def deviceConfig = state.devices[data.deviceId]
    def schedule = deviceConfig.schedules[data.scheduleId]
    def device = devices.find { it.id == data.deviceId }

    if ((modeBool && mode != null && mode.contains(location.mode)) || !modeBool || mode == null) {
        if ((switchActivationBool && activationSwitch.currentSwitch == activationSwitchOnOff) || (!switchActivationBool)) {
            if (!schedule.pause) { // If schedule is paused it should never be scheduled anyway. We'll still check.
                // Check to make sure the date is correct if we're using Hub Variables
                def effectiveConfig = getEffectiveTimeConfig(schedule).config
                def validDate = true
                if (effectiveConfig.useVariableTime && effectiveConfig.variableTime != null) {
                    def dateStr = getDateFromDateTimeString(effectiveConfig.startTime)
                    if (!dateStr.equals("9999-99-99")){ // this indicates the Hub Variable is only a time, not datetime
                        def parsedDate = Date.parse('yyyy-MM-dd', dateStr).clearTime()
                        validDate = new Date().clearTime() == parsedDate
                    }
                }

                if (validDate) {
                    logDebug "switchHandler - Device: $device; schedule: $schedule"
                    if (onlyRunWhenNotAlreadySetBool && deviceConfig.capability == "Dimmer" && schedule.desiredState == "on") {
                        String comparison = (schedule.skipComparison ?: "select").toString().toLowerCase()
                        if (comparison == "-") {
                            comparison = "select"
                        }
                        if (["greater", "less"].contains(comparison)) {
                            Closure<Integer> toInt = { val ->
                                if (val == null) {
                                    return null
                                }
                                if (val instanceof Number) {
                                    return (val as Number).intValue()
                                }
                                try {
                                    return val.toString().toBigDecimal().intValue()
                                } catch (Exception ignored) {
                                    return null
                                }
                            }

                            Integer currentLevel = toInt(device?.currentValue("level"))
                            Integer desiredLevel = toInt(schedule.desiredLevel)
                            String currentSwitch = device?.currentValue("switch")

                            if (currentLevel != null && desiredLevel != null && currentSwitch != null) {
                                boolean skipRun = currentSwitch == "on" &&
                                                  ((comparison == "greater" && currentLevel > desiredLevel) ||
                                                  (comparison == "less" && currentLevel < desiredLevel))
                                if (skipRun) {
                                    logDebug "Skipping run for Device: $device; schedule: $schedule - current level $currentLevel ${comparison == 'greater' ? '>' : '<'} desired level $desiredLevel"
                                    return
                                }
                            }
                        }
                    }
                    if (deviceConfig.capability == "Button") {
                        if (schedule.buttonNumber && schedule.buttonAction && schedule.buttonAction != "No commands found") {
                            device."$schedule.buttonAction"(schedule.buttonNumber)
                            logDebug "$device $schedule.buttonAction $schedule.buttonNumber triggered"
                        } else {
                            logError "Cannot perform action \"${schedule.buttonAction}\" on button \"${schedule.buttonNumber}\""
                        }
                    } else if (deviceConfig.capability == "Lock") {
                        // Handle lock devices
                        def lockAction = schedule.lockAction ?: "lock"
                        if (lockAction == "lock") {
                            device.lock()
                            logDebug "$device locked"
                        } else if (lockAction == "unlock") {
                            device.unlock()
                            logDebug "$device unlocked"
                        }
                    } else if (deviceConfig.capability == "Door") {
                        // Handle garage door/gate devices
                        def doorAction = schedule.doorAction ?: "close"
                        if (doorAction == "open") {
                            device.open()
                            logDebug "$device opened"
                        } else if (doorAction == "close") {
                            device.close()
                            logDebug "$device closed"
                        }
                    } else if (schedule.desiredState == "on") {
                        if (deviceConfig.capability == "Dimmer") {
                            if (activateOnBeforeLevelBool) {
                                device.on()
                                logDebug "$device turned on"
                            }
                            device.setLevel(schedule.desiredLevel)
                            logDebug "$device set to $schedule.desiredLevel"
                        } else {
                            device.on()
                            logDebug "$device turned on"
                        }
                    } else {
                        device.off()
                        logDebug "$device turned off"
                    }
                } else {
                    logDebug "Scheduled date is in the past, skipping run for Device: $device; schedule: $schedule"
                }
            } else {
                // If schedule is paused it should never be scheduled anyway
                logDebug "Schedule is paused, skipping run for Device: $device; schedule: $schedule"
            }
        } else {
            logDebug "Switch $activationSwitch is not set to $activationSwitchOnOff, skipping run for Device: $device; schedule: $schedule"
        }
    } else {
        logDebug "Mode of $location.mode is not one of $mode, skipping run for Device: $device; schedule: $schedule"
    }
}

// Get new sunrise/sunset and Hub Variable times
def refreshVariables(evt) {
    logDebug "refreshVariables called"
    updateSunriseAndSet()
    updateVariableTimes()
}

def logsOff() {
    logDebug "logsOff() called - All App logging auto disabled"
    app?.updateSetting("logEnableBool", [value: "false", type: "bool"])
}

void appButtonHandler(btn) {
    if (btn == "updateButton") updated()
    if (btn == "refreshOAuthToken") createAccessToken()
    if (btn == "restoreStateButton") restoreState(true)
    else if (btn.startsWith("sunUnChecked|")) state.sunUnCheckedBox = btn.minus("sunUnChecked|")
    else if (btn.startsWith("sunChecked|")) state.sunCheckedBox = btn.minus("sunChecked|")
    else if (btn.startsWith("monUnChecked|")) state.monUnCheckedBox = btn.minus("monUnChecked|")
    else if (btn.startsWith("monChecked|")) state.monCheckedBox = btn.minus("monChecked|")
    else if (btn.startsWith("tueUnChecked|")) state.tueUnCheckedBox = btn.minus("tueUnChecked|")
    else if (btn.startsWith("tueChecked|")) state.tueCheckedBox = btn.minus("tueChecked|")
    else if (btn.startsWith("wedUnChecked|")) state.wedUnCheckedBox = btn.minus("wedUnChecked|")
    else if (btn.startsWith("wedChecked|")) state.wedCheckedBox = btn.minus("wedChecked|")
    else if (btn.startsWith("thuUnChecked|")) state.thuUnCheckedBox = btn.minus("thuUnChecked|")
    else if (btn.startsWith("thuChecked|")) state.thuCheckedBox = btn.minus("thuChecked|")
    else if (btn.startsWith("friUnChecked|")) state.friUnCheckedBox = btn.minus("friUnChecked|")
    else if (btn.startsWith("friChecked|")) state.friCheckedBox = btn.minus("friChecked|")
    else if (btn.startsWith("satUnChecked|")) state.satUnCheckedBox = btn.minus("satUnChecked|")
    else if (btn.startsWith("satChecked|")) state.satCheckedBox = btn.minus("satChecked|")
    else if (btn.startsWith("pauseUnChecked|")) state.pauseUnCheckedBox = btn.minus("pauseUnChecked|")
    else if (btn.startsWith("pauseChecked|")) state.pauseCheckedBox = btn.minus("pauseChecked|")
    else if (btn.startsWith("sunTimeUnChecked|")) state.sunTimeUnCheckedBox = btn.minus("sunTimeUnChecked|")
    else if (btn.startsWith("sunTimeChecked|")) state.sunTimeCheckedBox = btn.minus("sunTimeChecked|")
    else if (btn.startsWith("sunsetUnChecked|")) state.sunsetUnCheckedBox = btn.minus("sunsetUnChecked|")
    else if (btn.startsWith("sunsetChecked|")) state.sunsetCheckedBox = btn.minus("sunsetChecked|")
    else if (btn.startsWith("toggleEarlierLater|")) state.toggleEarlierLater = btn.minus("toggleEarlierLater|")
    else if (btn.startsWith("sunTimeSecondaryUnChecked|")) state.sunTimeSecondaryUnChecked = btn.minus("sunTimeSecondaryUnChecked|")
    else if (btn.startsWith("sunTimeSecondaryChecked|")) state.sunTimeSecondaryChecked = btn.minus("sunTimeSecondaryChecked|")
    else if (btn.startsWith("sunsetSecondaryUnChecked|")) state.sunsetSecondaryUnChecked = btn.minus("sunsetSecondaryUnChecked|")
    else if (btn.startsWith("sunsetSecondaryChecked|")) state.sunsetSecondaryChecked = btn.minus("sunsetSecondaryChecked|")
    else if (btn.startsWith("useVariableTimeSecondaryUnChecked|")) state.useVariableTimeSecondaryUnChecked = btn.minus("useVariableTimeSecondaryUnChecked|")
    else if (btn.startsWith("useVariableTimeSecondaryChecked|")) state.useVariableTimeSecondaryChecked = btn.minus("useVariableTimeSecondaryChecked|")
    else if (btn.startsWith("addNew|")) state.addRunTime = btn.minus("addNew|")
    else if (btn.startsWith("removeRunTime|")) state.removeRunTime = btn.minus("removeRunTime|")
    else if (btn.startsWith("setCapabilityDimmer|")) state.setCapabilityDimmer = btn.minus("setCapabilityDimmer|")
    else if (btn.startsWith("setCapabilitySwitch|")) state.setCapabilitySwitch = btn.minus("setCapabilitySwitch|")
    else if (btn.startsWith("setCapabilityButton|")) state.setCapabilityButton = btn.minus("setCapabilityButton|")
    else if (btn.startsWith("setCapabilityLock|")) state.setCapabilityLock = btn.minus("setCapabilityLock|")
    else if (btn.startsWith("setCapabilityDoor|")) state.setCapabilityDoor = btn.minus("setCapabilityDoor|")
    else if (btn.startsWith("desiredState|")) state.desiredState = btn.minus("desiredState|")
    else if (btn.startsWith("useVariableTimeUnChecked|")) state.useVariableTimeUnChecked = btn.minus("useVariableTimeUnChecked|")
    else if (btn.startsWith("useVariableTimeChecked|")) state.useVariableTimeChecked = btn.minus("useVariableTimeChecked|")
    else if (btn.startsWith("restoreToggle|")) state.restoreToggle = btn.minus("restoreToggle|")
    else if (btn.startsWith("toggleSkipComparison|")) state.toggleSkipComparison = btn.minus("toggleSkipComparison|")
}


//**** Functions ****//

def updateSunriseAndSet() {
    devices.each { dev ->
        state.devices["$dev.id"].schedules.each { scheduleId, schedule ->
            if (schedule.sunTime) {
                logDebug "Updating Sunrise/Sunset for Device: $dev; Schedule: $scheduleId, Offset: ${schedule.offset}"
                def offsetRiseAndSet = getSunriseAndSunset(sunriseOffset: schedule.offset, sunsetOffset: schedule.offset)
                if (schedule.sunset) {
                    schedule.startTime = offsetRiseAndSet.sunset.toString()
                } else {
                    schedule.startTime = offsetRiseAndSet.sunrise.toString()
                }
            }

            def secondary = ensureSecondaryTimeConfig(schedule)
            if (secondary.sunTime) {
                logDebug "Updating Secondary Sunrise/Sunset for Device: $dev; Schedule: $scheduleId, Offset: ${secondary.offset}"
                def secondaryRiseAndSet = getSunriseAndSunset(sunriseOffset: secondary.offset, sunsetOffset: secondary.offset)
                if (secondary.sunset) {
                    secondary.startTime = secondaryRiseAndSet.sunset.toString()
                } else {
                    secondary.startTime = secondaryRiseAndSet.sunrise.toString()
                }
            }
        }
    }
}

def variableChangeHandler(evt) {
    logDebug "Hub Variable Changed: ${evt.name} -> ${evt.value}"
    updated()
}

def updateVariableTimes() {
    devices.each { dev ->
        state.devices["$dev.id"].schedules.each { scheduleId, schedule ->
            if (schedule.useVariableTime && schedule.variableTime) {
                def varTime = getGlobalVar(schedule.variableTime)
                if (varTime != null) {
                    // If the Hub Variable is just a date, then the time will be 99:99:99.999-9999. Replace that with midnight of current timezone.
                    def startTime = varTime.value.replace("99:99:99.999-9999", "00:00:00.000" + new Date().format("XX"))
                    if (schedule.offset && schedule.offset != 0) {
                        dateStr = getDateFromDateTimeString(startTime)
                        def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                        def date = ZonedDateTime.parse(startTime.replace("9999-99-99", "2000-01-01"), formatter) // Random date substitute, doesn't matter
                        date = date.plusMinutes(schedule.offset)

                        if (dateStr.equals("9999-99-99")) {
                            // When adding offsets for time only vars, it messes up the default 9999-99-99 format so we need to reformat it that way
                            formatter = DateTimeFormatter.ofPattern("'9999-99-99T'HH:mm:ss.SSSXX")
                            startTime = date.format(formatter)
                        } else {
                            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX")
                            startTime = date.format(formatter)
                        }
                    }
                    schedule.startTime = startTime
                }
            }

            def secondary = ensureSecondaryTimeConfig(schedule)
            if (secondary.useVariableTime && secondary.variableTime) {
                def varTime = getGlobalVar(secondary.variableTime)
                if (varTime != null) {
                    def startTime = varTime.value.replace("99:99:99.999-9999", "00:00:00.000" + new Date().format("XX"))
                    if (secondary.offset && secondary.offset != 0) {
                        dateStr = getDateFromDateTimeString(startTime)
                        def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                        def date = ZonedDateTime.parse(startTime.replace("9999-99-99", "2000-01-01"), formatter)
                        date = date.plusMinutes(secondary.offset)

                        if (dateStr.equals("9999-99-99")) {
                            formatter = DateTimeFormatter.ofPattern("'9999-99-99T'HH:mm:ss.SSSXX")
                            startTime = date.format(formatter)
                        } else {
                            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX")
                            startTime = date.format(formatter)
                        }
                    }
                    secondary.startTime = startTime
                }
            }
        }
    }
}

def buildCron() {
    logDebug "buildCron called"

    state.devices.each { deviceId, deviceConfigs ->
        deviceConfigs.schedules.each { scheduleId, schedule ->
            def timeConfig = getEffectiveTimeConfig(schedule).config
            if (timeConfig?.startTime) {
                String formattedTime = getTimeFromDateTimeString(timeConfig.startTime)
                String hours = formattedTime.substring(0, formattedTime.length() - 3)
                String minutes = formattedTime.substring(3)

                String days = ""
                if (schedule.sun) days = "SUN,"
                if (schedule.mon) days += "MON,"
                if (schedule.tue) days += "TUE,"
                if (schedule.wed) days += "WED,"
                if (schedule.thu) days += "THU,"
                if (schedule.fri) days += "FRI,"
                if (schedule.sat) days += "SAT,"

                if (days != "") {
                    days = days.substring(0, days.length() - 1)  // Chop off last ","
                    schedule.days = days
                    schedule.cron = "0 ${minutes} ${hours} ? * ${days} *"
                    logDebug "deviceId: $deviceId; scheduleId: $scheduleId; Generated cron: ${schedule.cron}"
                }
            }
        }
    }
}


def handleHubBootUp(evt) {
    logDebug "handleHubBootUp called"
    updated()

    // Check advanced config to see if we should restore states after boot up
    if (restoreAfterBootBool) {
        if (pauseBool) {
            logDebug "Restore after boot skipped - app is paused"
            return
        }

        if (modeBool && !mode.contains(location.mode)) {
            logDebug "Restore after boot skipped - mode is not correct; current mode ${location.mode} not in $mode"
            return
        }

        if (switchActivationBool && activationSwitch.currentSwitch != activationSwitchOnOff) {
            logDebug "Restore after boot skipped - activation switch condition not met"
            return
        }

        logDebug "Restore after boot enabled - restoring device states"
        restoreState()
    } else {
        logDebug "Restore after boot disabled - skipping restore"
    }
}

def handleModeChange(evt) {
    String currentMode = evt?.value ?: location?.mode
    logDebug "Mode changed to ${currentMode}"

    if (!modeBool) {
        logDebug "Mode change handling skipped - mode restriction disabled"
        return
    }

    if (pauseBool) {
        logDebug "Mode change handling skipped - app is paused"
        return
    }

    if (switchActivationBool && activationSwitch?.currentSwitch != activationSwitchOnOff) {
        logDebug "Mode change handling skipped - activation switch condition not met - ${activationSwitch?.currentSwitch} is not ${activationSwitchOnOff}"
        return
    }

    List<String> selectedModes = []
    if (mode instanceof Collection) {
        selectedModes.addAll(mode.findAll { it })
    } else if (mode) {
        selectedModes << mode
    }

    boolean modeIsSelected = selectedModes?.contains(currentMode)

    if (!modeIsSelected) {
        if (turnOffUnselectedModesBool) {
            logDebug "Mode ${currentMode} is not selected - turning off all controllable devices"
            turnOffDevicesForModeRestriction()
        }
        return
    }

    if (restoreAfterModeChangeBool) {
        logDebug "Mode ${currentMode} is selected - restoring devices to latest schedule"
        restoreState()
    }
}

/* * Function to get the last run time from a cron expression.
 * It searches for the most recent time before now, going back up to 7 days.
 */
private Date getPreviousRunFromCron(String cronString) {
    def cron = new CronExpression(cronString)
    def now = new Date()
    def searchStart = new Date(now.time - (7L * 24L * 60L * 60L * 1000L))

    def candidate = cron.getTimeAfter(searchStart)
    def lastRun = null

    while (candidate && candidate.before(now)) {
        lastRun = candidate
        candidate = cron.getTimeAfter(candidate)
    }

    return lastRun
}

/* Function to set devices to last scheduled state */
private restoreState(shouldUpdate = false) {
    logDebug "Restoring device states"
    if (shouldUpdate) {
        updated()
    }

    // Process each device
    devices.each { dev ->
        def deviceConfig = state.devices[dev.id]

        if (deviceConfig) {
            // Find most recent applicable schedule
            def mostRecentSchedule = null
            def mostRecentScheduleTime = null

            // Go through all schedules for this device
            deviceConfig.schedules.each { scheduleId, schedule ->
                // Skip paused schedules
                if (schedule.pause) {
                    return
                }

                def previousRun
                if (schedule.useVariableTime) {
                    // Use schedule.startTime instead of grabbing the variable time directly because we've already applied offsets
                    def dateStr = getDateFromDateTimeString(schedule.startTime)
                    if (dateStr.equals("9999-99-99")){ // this indicates the Hub Variable is only a time, not datetime
                        try {
                            previousRun = getPreviousRunFromCron(schedule.cron)
                        } catch (Exception e) {
                            logWarn "Failed to get previous run from cron for schedule ${scheduleId}, cron ${schedule.cron}: ${e.message}"
                        }
                    } else {
                        def parsedDate = parseDateTime(schedule.startTime)
                        if (parsedDate < new Date()) {
                            previousRun = parsedDate
                        }
                    }
                } else {
                    try {
                        previousRun = getPreviousRunFromCron(schedule.cron)
                    } catch (Exception e) {
                        logWarn "Failed to get previous run from cron for schedule ${scheduleId}, cron ${schedule.cron}: ${e.message}"
                    }
                }

                logDebug "previousRun for ${schedule.cron} is $previousRun"
                if (!mostRecentScheduleTime || previousRun > mostRecentScheduleTime) {
                    mostRecentScheduleTime = previousRun
                    mostRecentSchedule = schedule
                }
            }

            // If schedule was found, apply it
            if (mostRecentSchedule) {
                logDebug "Found most recent schedule for $dev: $mostRecentSchedule.desiredState at $mostRecentScheduleTime"

                if (mostRecentSchedule.restore == null) {
                    mostRecentSchedule.restore = true
                }

                // Skip if restore is not enabled
                if (!mostRecentSchedule.restore) {
                    logDebug "Skipping restore for $dev - restore at boot not enabled for this schedule"
                    return
                }

                // Apply schedule based on device capability
                if (deviceConfig.capability == "Button") {
                    // Trigger button action
                    if (mostRecentSchedule.buttonNumber && mostRecentSchedule.buttonAction && mostRecentSchedule.buttonAction != "No commands found") {
                        dev."$mostRecentSchedule.buttonAction"(mostRecentSchedule.buttonNumber)
                        logDebug "$dev restored: $mostRecentSchedule.buttonAction $mostRecentSchedule.buttonNumber triggered"
                    } else {
                        logError "Cannot restore button action for $dev - invalid button configuration (action: ${mostRecentSchedule.buttonAction}, number: ${mostRecentSchedule.buttonNumber})"
                    }
                } else if (deviceConfig.capability == "Lock") {
                    // Restore lock state
                    def lockAction = mostRecentSchedule.lockAction ?: "lock"
                    if (lockAction == "lock") {
                        dev.lock()
                        logDebug "$dev restored: locked"
                    } else if (lockAction == "unlock") {
                        dev.unlock()
                        logDebug "$dev restored: unlocked"
                    }
                } else if (deviceConfig.capability == "Door") {
                    // Restore garage door/gate state
                    def doorAction = mostRecentSchedule.doorAction ?: "close"
                    if (doorAction == "open") {
                        dev.open()
                        logDebug "$dev restored: opened"
                    } else if (doorAction == "close") {
                        dev.close()
                        logDebug "$dev restored: closed"
                    }
                } else if (mostRecentSchedule.desiredState == "on") {
                    if (deviceConfig.capability == "Dimmer") {
                        if (onlyRunWhenNotAlreadySetBool) {
                            String comparison = (mostRecentSchedule.skipComparison ?: "select").toString().toLowerCase()
                            if (comparison == "-") {
                                comparison = "select"
                            }
                            if (["greater", "less"].contains(comparison)) {
                                Closure<Integer> toInt = { val ->
                                    if (val == null) {
                                        return null
                                    }
                                    if (val instanceof Number) {
                                        return (val as Number).intValue()
                                    }
                                    try {
                                        return val.toString().toBigDecimal().intValue()
                                    } catch (Exception ignored) {
                                        return null
                                    }
                                }

                                Integer currentLevel = toInt(dev?.currentValue("level"))
                                Integer desiredLevel = toInt(mostRecentSchedule.desiredLevel)
                                String currentSwitch = dev?.currentValue("switch")

                                if (currentLevel != null && desiredLevel != null && currentSwitch != null) {
                                    boolean skipRestore = currentSwitch == "on" &&
                                                          ((comparison == "greater" && currentLevel > desiredLevel) ||
                                                          (comparison == "less" && currentLevel < desiredLevel))
                                    if (skipRestore) {
                                        logDebug "Skipping restore for $dev - current level $currentLevel ${comparison == 'greater' ? '>' : '<'} desired level $desiredLevel"
                                        return  // only skps current device
                                    }
                                }
                            }
                        }
                        if (activateOnBeforeLevelBool) {
                            dev.on()
                        }
                        dev.setLevel(mostRecentSchedule.desiredLevel)
                        logDebug "$dev restored to brightness level $mostRecentSchedule.desiredLevel"
                    } else {
                        dev.on()
                        logDebug "$dev restored to ON"
                    }
                } else {
                    dev.off()
                    logDebug "$dev restored to OFF"
                }
            } else {
                logDebug "No applicable schedule found for $dev to restore state after reboot"
            }
        }
    }
}

private void turnOffDevicesForModeRestriction() {
    devices?.each { dev ->
        def deviceConfig = state.devices[dev.id]

        if (!deviceConfig) {
            return
        }

        // Skip buttons - they don't have an "off" state
        if (deviceConfig.capability == "Button") {
            logDebug "Skipping turn off for $dev - device is configured as a button"
            return
        }

        // Handle locks - lock them when mode changes to unselected
        if (deviceConfig.capability == "Lock") {
            if (dev.hasCommand("lock")) {
                dev.lock()
                logDebug "$dev locked due to mode restriction"
            }
            return
        }

        // Handle garage doors/gates - close them when mode changes to unselected
        if (deviceConfig.capability == "Door") {
            if (dev.hasCommand("close")) {
                dev.close()
                logDebug "$dev closed due to mode restriction"
            }
            return
        }

        // Handle switches and dimmers
        if (dev.hasCommand("off")) {
            dev.off()
            logDebug "$dev turned off due to mode restriction"
        } else {
            logDebug "$dev does not support off() command - skipping mode restriction shutdown"
        }
    }
}

private getHubVariableList() {
    def variables = [:]

    // Create a map of variable names to their name & value concatenation
    def allVariables = getGlobalVarsByType("datetime")
    allVariables.keySet().toList().sort { a, b ->
        a.toString().toLowerCase() <=> b.toString().toLowerCase()
    }.each { key ->
        def value = allVariables[key]
        variables[key] = "$key: ${value.value}"
    }
    return variables
}

private getValueForHubVariable(name) {
    def val = getGlobalVar(name)
    if (val != null) {
        return val.value
    }
    return null
}

private formatHubVariableNameWithTime(hubVariable, startTime) {
    def dtString = ""
    def date = getDateFromDateTimeString(startTime)
    if (!date.equals("9999-99-99")) {
        dtString += "$date"
    }
    def time = getTimeFromDateTimeString(startTime)
    if (!time.equals("99:99")) {
        if (dtString.length() > 0) {
            dtString += "<br>"
        }
        dtString += time
    }
    return "$hubVariable<br>($dtString)"
}

private parseDateTime (String dateStr) {
    if (!dateStr) {
        return null
    }

    if (dateStr == "00000000000Select000000000000") {
        return null
    }

    try {
        // Try ISO format first
        def todayDateString = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        dateStr = dateStr.replace("yyyy-mm-dd", todayDateString).replace("9999-99-99", todayDateString).replace("sss-zzzz", "000-0000")
        return new Date().parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", dateStr)
    } catch (Exception e1) {
        try {
            // Try the other format
            return new Date().parse("EEE MMM dd HH:mm:ss zzz yyyy", dateStr)
        } catch (Exception e2) {
            logError "Failed to parse date: ${dateStr}. Exception 1: ${e1.message}, Exception 2: ${e2.message}"
            return null
        }
    }
}

private Long buildScheduleSortKey(Map schedule) {
    if (!schedule) {
        return Long.MAX_VALUE
    }

    try {
        def effectiveInfo = getEffectiveTimeConfig(schedule)
        Map config = effectiveInfo?.config ?: [:]

        List<String> candidates = []
        if (config.startTime) {
            candidates << config.startTime.toString()
        }
        if (schedule.startTime) {
            candidates << schedule.startTime.toString()
        }

        def secondary = schedule.secondaryTime
        if (secondary instanceof Map && secondary.startTime) {
            candidates << secondary.startTime.toString()
        }

        for (String candidate : candidates) {
            Long minutes = extractMinutesFromStartTime(candidate)
            if (minutes != null) {
                return minutes
            }
        }
    } catch (Exception e) {
        logError "Error determining schedule sort key: ${e.message}"
    }

    return Long.MAX_VALUE
}

private Long extractMinutesFromStartTime(String startTime) {
    if (!startTime) {
        return null
    }

    def matcher = startTime =~ /(\d{1,2}):(\d{2})/
    if (matcher.find()) {
        Integer hours = matcher.group(1).toInteger()
        Integer minutes = matcher.group(2).toInteger()
        return (hours * 60L) + minutes
    }

    def parsed = parseDateTime(startTime)
    if (parsed) {
        Integer hours = (parsed.format("HH") as Integer)
        Integer minutes = (parsed.format("mm") as Integer)
        return (hours * 60L) + minutes
    }

    return null
}

private getTimeFromDateTimeString(dt) {
    return dt.substring(11, dt.length() - 12)
}

private getDateFromDateTimeString(dt) {
    return dt.tokenize("T")[0]
}

// Formating function for consistency and ease of changing style
def getFormat(type, myText = "") {
    if (type == "title") return "<h3 style='color:#2196F3; font-weight: bold; margin-bottom: 15px;'>${myText}</h3>"
    if (type == "blueRegular") return "<div style='color:#2196F3; font-weight: bold; font-size: 16px; padding: 5px 0;'>${myText}</div>"
    if (type == "noticable") return "<div style='color:#FF5722; font-weight: 500; padding: 3px 0;'>${myText}</div>"
    if (type == "important") return "<div style='color:#00BCD4; font-weight: 500; padding: 3px 0;'>${myText}</div>"
    if (type == "lessImportant") return "<div style='color:#4CAF50; font-weight: normal; padding: 3px 0;'>${myText}</div>"
    if (type == "header") return "<div style='color:#212121; font-weight: bold; font-size: 16px; margin-top: 15px; margin-bottom: 5px;'>${myText}</div>"
    if (type == "important2") return "<div style='color:#424242; font-weight: normal;'>${myText}</div>"
}

// Helper to generate & format a button
String buttonLink(String btnName, String linkText, color = "#2196F3", font = "15px") {
    def tokens = btnName.tokenize('|')
    def action = tokens ? tokens[0] : null
    def deviceId = tokens.size() > 1 ? tokens[1] : null
    def scheduleId = tokens.size() > 2 ? tokens[2] : null
    def extra = tokens.size() > 3 ? tokens[3] : null

    if (["editStartTime", "newOffset", "desiredLevel", "selectVariableStartTime"].contains(action)) {
        String extraParam = extra ? ", \"${extra}\"" : ""
        return """<span role="button" id="${btnName}" onclick='event.preventDefault(); event.stopPropagation(); ${action}Popup("${deviceId}","${scheduleId}", "${linkText}"${extraParam}); return false;' style='color:$color;cursor:pointer;font-size:$font;font-weight:500;padding:2px 4px;border-radius:4px;transition:all 0.3s ease;display:inline-block'>${linkText}</span>"""
    }
    return """<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font;font-weight:500;padding:2px 4px;border-radius:4px;transition:all 0.3s ease;display:inline-block'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"""
}

// Generate a new, empty schedule
static LinkedHashMap<String, Object> generateDefaultSchedule() {
    return [
            sunTime        : false,
            sunset         : true,
            offset         : 0,
            sun            : true,
            mon            : true,
            tue            : true,
            wed            : true,
            thu            : true,
            fri            : true,
            sat            : true,
            startTime      : "00000000000Select000000000000",
            cron           : "",
            days           : "",
            pause          : false,
            desiredState   : "on",
            desiredLevel   : 100,
            useVariableTime: false,
            variableTime   : null,
            buttonNumber   : null,
            buttonAction   : null,
            lockAction     : "lock",      // For lock devices: "lock" or "unlock"
            doorAction     : "close",     // For garage door/gate devices: "open" or "close"
            restore        : true,
            earlierLater   : "select",
            skipComparison : "select",
            secondaryTime  : generateDefaultSecondaryTime()
    ]
}

static LinkedHashMap<String, Object> generateDefaultSecondaryTime() {
    return [
            sunTime        : false,
            sunset         : true,
            offset         : 0,
            startTime      : "00000000000Select000000000000",
            useVariableTime: false,
            variableTime   : null
    ]
}

private Map ensureSecondaryTimeConfig(Map schedule) {
    if (!schedule.secondaryTime) {
        schedule.secondaryTime = generateDefaultSecondaryTime()
    } else {
        if (!schedule.secondaryTime.containsKey('sunTime')) schedule.secondaryTime.sunTime = false
        if (!schedule.secondaryTime.containsKey('sunset')) schedule.secondaryTime.sunset = true
        if (!schedule.secondaryTime.containsKey('offset') || schedule.secondaryTime.offset == null) schedule.secondaryTime.offset = 0
        if (!schedule.secondaryTime.containsKey('startTime') || schedule.secondaryTime.startTime == null) schedule.secondaryTime.startTime = "00000000000Select000000000000"
        if (!schedule.secondaryTime.containsKey('useVariableTime')) schedule.secondaryTime.useVariableTime = false
        if (!schedule.secondaryTime.containsKey('variableTime')) schedule.secondaryTime.variableTime = null
    }
    return schedule.secondaryTime
}

private Map getEffectiveTimeConfig(Map schedule) {
    Map primary = [
            startTime      : schedule.startTime,
            useVariableTime: schedule.useVariableTime,
            variableTime   : schedule.variableTime,
            sunTime        : schedule.sunTime,
            sunset         : schedule.sunset,
            offset         : schedule.offset
    ]

    Map secondary = ensureSecondaryTimeConfig(schedule)
    String selection = (schedule.earlierLater ?: "select").toString().toLowerCase()

    if (!dualTimeBool || !["earlier", "later"].contains(selection)) {
        return [config: primary, isSecondary: false]
    }

    Map primaryInfo = buildTimeComparisonInfo(primary)
    Map secondaryInfo = buildTimeComparisonInfo(secondary)

    if (!primaryInfo.parsedDate && !secondaryInfo.parsedDate) {
        return [config: primary, isSecondary: false]
    }
    if (!primaryInfo.parsedDate) {
        return [config: secondary, isSecondary: true]
    }
    if (!secondaryInfo.parsedDate) {
        return [config: primary, isSecondary: false]
    }

    boolean chooseSecondary = false
    if (selection == "earlier") {
        chooseSecondary = shouldChooseSecondary(primaryInfo, secondaryInfo, true)
    } else if (selection == "later") {
        chooseSecondary = shouldChooseSecondary(primaryInfo, secondaryInfo, false)
    }

    return chooseSecondary ? [config: secondary, isSecondary: true] : [config: primary, isSecondary: false]
}

private Map buildTimeComparisonInfo(Map config) {
    Map result = [
            parsedDate        : null,
            secondsOfDay      : null,
            isDateTimeVariable: false
    ]

    if (!config) {
        return result
    }

    String startTime = config.startTime?.toString()
    if (!startTime) {
        return result
    }

    def parsed = parseDateTime(startTime)
    if (!parsed) {
        return result
    }

    result.parsedDate = parsed

    try {
        Integer hours = parsed.format("HH") as Integer
        Integer minutes = parsed.format("mm") as Integer
        Integer seconds = parsed.format("ss") as Integer
        result.secondsOfDay = (hours * 3600L) + (minutes * 60L) + seconds
    } catch (Exception ignored) {
        // leave secondsOfDay as null when formatting fails
    }

    if (config.useVariableTime && config.variableTime) {
        try {
            String dateToken = getDateFromDateTimeString(startTime)
            if (dateToken && dateToken != "9999-99-99") {
                result.isDateTimeVariable = true
            }
        } catch (Exception ignored) {
            // If we cannot extract the date token, default to treating it as time-only
        }
    }

    return result
}

private boolean shouldChooseSecondary(Map primaryInfo, Map secondaryInfo, boolean chooseEarlier) {
    Date primaryCompare
    Date secondaryCompare

    if (primaryInfo.isDateTimeVariable && secondaryInfo.isDateTimeVariable) {
        primaryCompare = primaryInfo.parsedDate
        secondaryCompare = secondaryInfo.parsedDate
    } else if (primaryInfo.isDateTimeVariable) {
        primaryCompare = primaryInfo.parsedDate
        secondaryCompare = alignToReferenceDate(primaryCompare, secondaryInfo)
    } else if (secondaryInfo.isDateTimeVariable) {
        secondaryCompare = secondaryInfo.parsedDate
        primaryCompare = alignToReferenceDate(secondaryCompare, primaryInfo)
    }

    if (primaryCompare && secondaryCompare) {
        if (chooseEarlier) {
            return secondaryCompare.before(primaryCompare)
        }
        return secondaryCompare.after(primaryCompare)
    }

    Long primarySeconds = primaryInfo.secondsOfDay as Long
    Long secondarySeconds = secondaryInfo.secondsOfDay as Long

    if (primarySeconds == null && secondarySeconds == null) {
        return false
    }
    if (primarySeconds == null) {
        return true
    }
    if (secondarySeconds == null) {
        return false
    }

    if (chooseEarlier) {
        return secondarySeconds < primarySeconds
    }
    return secondarySeconds > primarySeconds
}

private Date alignToReferenceDate(Date reference, Map timeInfo) {
    if (!reference || !timeInfo) {
        return null
    }

    Long secondsOfDay = timeInfo.secondsOfDay as Long
    if (secondsOfDay == null) {
        return null
    }

    Calendar cal = Calendar.getInstance()
    cal.time = reference
    int hours = (int) (secondsOfDay / 3600L)
    int minutes = (int) ((secondsOfDay % 3600L) / 60L)
    int seconds = (int) (secondsOfDay % 60L)
    cal.set(Calendar.HOUR_OF_DAY, hours)
    cal.set(Calendar.MINUTE, minutes)
    cal.set(Calendar.SECOND, seconds)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.time
}

def displayTitle() {
    titleVersion()

    String str = ""
    if (pauseBool) {
        str += getFormat("noticable", "<h3>[App Paused]</h3>")
    }
    str += getFormat("blueRegular", "${appName ? appName + ' - ' : ""}${state.name}: ${"ver " + state.version}")
    section (str) {}
}

void logDebug(msg) {
    if (logEnableBool) {
        log.debug("${app.label} - $msg")
    }
}

void logWarn(msg) {
    if (logEnableBool) {
        log.warn("${app.label} - $msg")
    }
}

void logError(msg) {
    if (logEnableBool) {
        log.error("${app.label} - $msg")
    }
}

private Integer getLoggingDurationMinutes() {
    def minutesSetting = settings?.logEnableMinutes
    Integer minutes = 60

    if (minutesSetting != null) {
        if (minutesSetting instanceof Number) {
            minutes = (minutesSetting as Number).intValue()
        } else {
            String minutesStr = minutesSetting.toString()
            if (minutesStr?.trim()) {
                try {
                    minutes = minutesStr.toInteger()
                } catch (NumberFormatException ignored) {
                    // keep default when conversion fails
                }
            }
        }
    }

    if (minutes < 0) {
        minutes = 60
    }

    return minutes
}


//**** Required Methods ****//

void initialize() {
    logDebug "initialize() called"

    subscribe(location, "systemStart", handleHubBootUp)

    if (modeBool && (turnOffUnselectedModesBool || restoreAfterModeChangeBool)) {
        subscribe(location, "mode", handleModeChange)
        logDebug "Subscribed to mode change events"
    }

    if (logEnableBool) {
        Integer loggingMinutes = getLoggingDurationMinutes()
        if (loggingMinutes == 0) {
            logDebug "Logging enabled indefinitely per configuration"
        } else {
            runIn(loggingMinutes * 60, logsOff)  // Disable Logging after configured time elapsed
        }
    }

    if (pauseBool) {
        logDebug "All schedules have been manually paused. Will skip scheduling"
        return
    }

    logDebug state.devices

    // Set device cron schedules
    if (devices != null) {
        devices.sort { it.displayName.toLowerCase() }.each { dev ->
            deviceConfig = state.devices["$dev.id"]
            if (deviceConfig != null) { // Can happen when adding a new device that doesn't yet have a default config
                zone = deviceConfig.zone
                deviceConfig.schedules.each { scheduleId, sched ->
                    if (sched.cron && !sched.pause && !sched.cron.contains("Sel")) {
                        schedule(sched.cron, switchHandler, [data:[deviceId: dev.id, scheduleId: scheduleId], overwrite:false])
                        logDebug "SCHEDULED Device: $dev; DeviceId: $dev.id; ScheduleId: $scheduleId; deviceSchedule: $sched"

                        // Subscribe to Hub Variable Change Events & Register Variable Use with Hub
                        if (sched.useVariableTime && sched.variableTime != null) {
                            addInUseGlobalVar(sched.variableTime)
                            subscribe(location, "variable:${sched.variableTime}", "variableChangeHandler")
                            logDebug "Subscribed to Hub Variable Change events for variable: ${sched.variableTime}"
                        }

                        def secondary = ensureSecondaryTimeConfig(sched)
                        if (secondary.useVariableTime && secondary.variableTime != null) {
                            if (!(sched.useVariableTime && sched.variableTime == secondary.variableTime)) {
                                addInUseGlobalVar(secondary.variableTime)
                                subscribe(location, "variable:${secondary.variableTime}", "variableChangeHandler")
                                logDebug "Subscribed to Hub Variable Change events for secondary variable: ${secondary.variableTime}"
                            } else {
                                logDebug "Secondary schedule uses same Hub Variable as primary; existing subscription reused for ${secondary.variableTime}"
                            }
                        }
                    }
                }
            }
        }
    }

    def refreshSchedule = determineDailyRefreshCron()
    schedule(refreshSchedule.cron, updated) // Daily refresh for sunrise/sunset and Hub Variables
    logDebug "Daily refresh scheduled for ${refreshSchedule.time} using cron ${refreshSchedule.cron}"
}

private Map determineDailyRefreshCron() {
    Set<String> scheduledTimes = getScheduledTimesForConflictCheck()
    logDebug "Scheduled times at 01:00 hour for conflict check: $scheduledTimes"
    String hourStr = "01"

    for (int minute = 0; minute < 60; minute += 1) {
        String minuteStr = String.format("%02d", minute)
        String candidateTime = "${hourStr}:${minuteStr}"
        
        if (!scheduledTimes.contains(candidateTime)) {
            String cron = "0 ${minuteStr} ${hourStr} ? * * *"
            return [cron: cron, time: candidateTime]
        }

        logDebug "Daily refresh conflict detected at ${candidateTime}; checking next 1-minute slot"
    }

    logWarn "Unable to find available time between 01:00 and 01:59 for daily refresh. Defaulting to 01:00. This may result in a race condition where the schedule at 1:00 does not run."
    return [cron: "0 00 01 ? * * *", time: "01:00"]
}

private Set<String> getScheduledTimesForConflictCheck() {
    Set<String> scheduledTimes = [] as Set

    state.devices?.each { deviceId, deviceConfig ->
        deviceConfig?.schedules?.each { scheduleId, schedule ->
            if (schedule && !schedule.pause) {
                boolean added = false
                String cron = schedule.cron

                if (cron && !cron.contains("Sel")) {
                    List<String> cronParts = cron.tokenize(' ')
                    if (cronParts.size() >= 3) {
                        String minute = cronParts[1].padLeft(2, '0')
                        String hour = cronParts[2].padLeft(2, '0')
                        if (hour == "01") {
                            String time = "${hour}:${minute}"
                            scheduledTimes.add(time)
                            added = true
                        }
                    }
                }

                if (!added) {
                    String startTime = getEffectiveTimeConfig(schedule).config.startTime
                    if (startTime && startTime.contains('T')) {
                        try {
                            String time = getTimeFromDateTimeString(startTime)
                            if (time && time != "99:99" && time.startsWith("01:")) {
                                scheduledTimes.add(time)
                            }
                        } catch (Exception ignored) {
                            // Ignore invalid start times
                        }
                    }
                }
            }
        }
    }

    return scheduledTimes
}

// Required handler for when a hub variable is renamed
def renameVariable(oldName, newName) {
    logDebug "renameVariable called - old: $oldName, new: $newName"
    if (devices != null) {
        devices.each { dev ->
                deviceConfig = state.devices["$dev.id"]
                if (deviceConfig != null) { // Can happen when adding a new device that doesn't yet have a default config
                    zone = deviceConfig.zone
                    deviceConfig.schedules.each { scheduleId, sched ->
                        if (sched != null) {
                            if (sched.variableTime != null && sched.variableTime.equals(oldName)) {
                                sched.variableTime = newName
                            }
                            def secondary = ensureSecondaryTimeConfig(sched)
                            if (secondary.variableTime != null && secondary.variableTime.equals(oldName)) {
                                secondary.variableTime = newName
                            }
                        }
                    }
                }
            }
        }
}

def updated() {  // runs every 'Done' on already installed app
    logDebug "updated called"
    unsubscribe()
    unschedule()  // cancels all (or one) scheduled jobs including runIn, switchHandler, refreshVariables
    removeAllInUseGlobalVar()  // remove all in use global Hub Variables
    refreshVariables()
    buildCron()  // build schedules
    initialize()  // set schedules and subscribes
    if (!state.accessToken) {
        createAccessToken()
    }
}

def installed() {  // only runs once for new app 'Done' or first time open
    logDebug "installed called"
    updated()
}
