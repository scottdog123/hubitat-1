/**
 * ============================  Laundry Notifications (Child App) ============================
 *
 *  DESCRIPTION:
 *  Monitors a vibration sensor attached to a laundry appliance and notifies the correct
 *  person (based on a hub variable value) when the appliance has been inactive for a
 *  configured period of time. Once a cycle finishes, the notification recipient hub
 *  variable is reset to the string value 'null' after 30 minutes unless the machine
 *  begins running again, in which case the reset timer is cancelled and restarted when
 *  the next cycle completes.
 *
 *  SETUP NOTES:
 *  - Create a hub variable (type: String) that will hold the name of the person to notify.
 *  - Configure mappings between hub variable values (names) and notification devices.
 *  - Choose the vibration/acceleration sensor that indicates the machine is running.
 *  - Define how many minutes of inactivity indicate that the cycle is finished.
 *
 *  Copyright 2024
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License
 *  is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 *  Last modified: 2025-11-04
 */

import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime

definition(
        name: "Laundry Notifications (Child App)",
        namespace: "evcallia",
        author: "Evan Callia",
        parent: "evcallia:Laundry Notifications",
        description: "Notify the correct person when laundry finishes based on hub variable values.",
        category: "Convenience",
        iconUrl: "",
        iconX2Url: "",
        iconX3Url: "",
)

preferences {
    page(name: 'configurationPage', install: true, uninstall: true)
}

Map configurationPage() {
    dynamicPage(name: 'configurationPage') {
        section() {
            input name: "appName", type: "string", title: "Name this App", required: true, submitOnChange: true
            setAppLabel()
        }

        section('Instructions') {
            paragraph 'Select the vibration sensor for your machine and specify how long it must remain inactive before a cycle is considered complete.'
            paragraph 'Choose the hub variable that stores the person to notify. Map each possible hub variable value to the appropriate notification device.'
        }

        section('Configuration') {
            input 'vibrationSensor', 'capability.accelerationSensor', title: 'Laundry vibration/acceleration sensor', required: true
            input 'inactivityMinutes', 'number', title: 'Minutes of inactivity before notifying', required: true, defaultValue: 5
            input 'activeConfirmationSeconds', 'number', title: 'Seconds of continuous activity before confirming cycle start', required: true, defaultValue: 30
            input 'cycleStatusSwitch', 'capability.switch', title: 'Optional switch to reflect cycle status', required: false
        }

        section() {
            Map<String, String> variableOptions = stringHubVariableOptions()
            input 'hubVariableName', 'enum', title: 'Hub variable name', required: true,
                    options: variableOptions, submitOnChange: true, offerAll: false
            if (!variableOptions) {
                paragraph 'No string hub variables were found. Create a string hub variable in Hub Variables and return to select it.'
            }
        }

        section('Recipients') {
            paragraph 'Select all notification devices and enter the hub variable value (name) that should trigger each device.'
            input 'notificationDevices', 'capability.notification', title: 'Notification devices', multiple: true, submitOnChange: true, required: true

            notificationDevices?.each { device ->
                input "recipientName_${device.id}", 'text', title: "Hub variable value for ${device.displayName}", required: false, submitOnChange: false
            }
        }

        section('Notification Message') {
            paragraph 'Customize the message that is sent when the laundry cycle finishes. Use %recipient% as a placeholder for the hub variable value.'
            input 'customNotificationMessage', 'text', title: 'Notification message', required: false,
                    defaultValue: 'Laundry cycle complete for %recipient%.'
        }

        section('Debug Options') {
            input 'enableDebugLogging', 'bool', title: 'Enable debug logging', defaultValue: false
        }
    }
}

void setAppLabel(Boolean start=null) {
    if (appName) {
        String label = appName
        if (start != null && start && state.cycleStartTimestamp != null) {
            label += " <span style='color:green; font-size:smaller'>(Started ${state.cycleStartTimestamp})</span>"
        } else if (start != null && !start && state.cycleStartTimestamp != null) {
            label += " <span style='color:green; font-size:smaller'>(Ended ${state.cycleStartTimestamp})</span>"
        } 
        app.updateLabel(label)
    } else {
        app.updateLabel("App name undefined")
    }
}

void installed() {
    logInfo 'Installed child app.'
    initialize()
}

void updated() {
    logInfo 'Updated child app.'
    unsubscribe()
    unschedule()
    initialize()
}

void initialize() {
    logInfo 'Initializing child app.'
    if (!vibrationSensor) {
        logWarn 'No vibration sensor configured; app will not subscribe to events.'
        return
    }

    state.cycleActive = false
    unschedule(confirmCycleStart)

    removeAllInUseGlobalVar()  // remove all in use global Hub Variables
    if (hubVariableName) {
        addInUseGlobalVar(hubVariableName)
        logDebug "Registering use of hub variable '${hubVariableName}'."
    }

    subscribe(vibrationSensor, 'acceleration', handleAccelerationEvent)
    logDebug "Subscribed to acceleration events for ${vibrationSensor.displayName}."
}

void handleAccelerationEvent(evt) {
    logDebug "Acceleration event received: ${evt.value}"
    if (evt?.value == 'active') {
        if (state.cycleActive) {
            startCycle()
        } else {
            scheduleCycleStartConfirmation()
        }
    } else if (evt?.value == 'inactive') {
        if (state.cycleActive) {
            scheduleCycleCheck()
        } else {
            cancelPendingStartCheck()
        }
    }
}

void startCycle() {
    if (!state.cycleActive) {
        logInfo 'New laundry cycle detected.'
        state.cycleActive = true
        state.cycleStartTimestamp = getFormattedDateTime()
        updateCycleSwitch(true)
        setAppLabel(true)
    }
    unschedule(confirmCycleStart)
    unschedule(resetHubVariable)
    unschedule(confirmCycleComplete)
}

String getFormattedDateTime() {
    def zdt = ZonedDateTime.now()
    def formatter = DateTimeFormatter.ofPattern("MM-dd hh:mm a")
    return zdt.format(formatter)
}

void scheduleCycleCheck() {
    Integer minutes = safeInteger(inactivityMinutes, 5)
    Integer delaySeconds = Math.max(minutes, 0) * 60
    logDebug "Scheduling cycle check in ${delaySeconds} second(s)."
    runIn(delaySeconds, 'confirmCycleComplete', [overwrite: true])
}

void scheduleCycleStartConfirmation() {
    Integer seconds = safeInteger(activeConfirmationSeconds, 30)
    seconds = Math.max(seconds, 0)
    if (seconds <= 0) {
        logDebug 'Active confirmation threshold not set; starting cycle immediately.'
        confirmCycleStart()
        return
    }

    logDebug "Scheduling cycle start confirmation in ${seconds} second(s)."
    runIn(seconds, 'confirmCycleStart', [overwrite: true])
}

void cancelPendingStartCheck() {
    logDebug 'Cancelling pending cycle start confirmation.'
    unschedule(confirmCycleStart)
}

void confirmCycleStart() {
    if (state.cycleActive) {
        logDebug 'Cycle already marked active; skipping confirmation.'
        return
    }

    if (vibrationSensor?.currentValue('acceleration') == 'active') {
        logDebug 'Vibration sensor remained active for required duration; starting cycle.'
        startCycle()
    } else {
        logDebug 'Vibration sensor is no longer active; cycle start aborted.'
    }
}

void confirmCycleComplete() {
    if (vibrationSensor?.currentValue('acceleration') == 'active') {
        logInfo 'Vibration sensor reported active again; cancelling cycle completion.'
        return
    }

    logInfo 'Laundry cycle inactivity threshold reached; preparing notification.'
    state.cycleActive = false
    state.cycleStartTimestamp = getFormattedDateTime()
    setAppLabel(false)
    updateCycleSwitch(false)
    notifyConfiguredRecipient()
    scheduleHubVariableReset()
}

void notifyConfiguredRecipient() {
    String recipientName = currentHubVariableValue()
    if (!recipientName || recipientName?.trim()?.equalsIgnoreCase('null')) {
        logWarn 'Hub variable is empty or set to \"null\"; skipping notification.'
        return
    }

    def device = recipientDeviceMap()[recipientName]
    if (!device) {
        logWarn "No notification device mapping found for hub variable value '${recipientName}'."
        return
    }

    String message = resolveNotificationMessage(recipientName)
    logInfo "Sending notification to ${device.displayName}: ${message}"
    device.deviceNotification(message)
}

void scheduleHubVariableReset() {
    logDebug 'Scheduling hub variable reset in 30 minutes.'
    runIn(30 * 60, 'resetHubVariable', [overwrite: true])
}

void resetHubVariable() {
    if (!hubVariableName) {
        logWarn 'Hub variable name not configured; nothing to reset.'
        return
    }

    try {
        logInfo "Resetting hub variable '${hubVariableName}' to 'null'."
        setGlobalVar(hubVariableName, 'null')
    } catch (Exception ex) {
        log.error "Failed to reset hub variable '${hubVariableName}': ${ex.message}", ex
    }
}

// Build the notification text using the configured template and recipient value.
String resolveNotificationMessage(String recipientName) {
    String template = "${getFormattedDateTime()}: " + customNotificationMessage ?: 'Laundry cycle complete for %recipient%.'
    String safeRecipient = recipientName ?: ''
    return template.replace('%recipient%', safeRecipient)
}

// Toggle the optional switch so dashboards/automations can reflect machine status.
void updateCycleSwitch(Boolean turnOn) {
    if (!cycleStatusSwitch) {
        return
    }

    try {
        if (turnOn) {
            logDebug "Turning on cycle status switch ${cycleStatusSwitch.displayName}."
            cycleStatusSwitch.on()
        } else {
            logDebug "Turning off cycle status switch ${cycleStatusSwitch.displayName}."
            cycleStatusSwitch.off()
        }
    } catch (Exception ex) {
        logWarn "Unable to update cycle status switch: ${ex.message}"
    }
}

String currentHubVariableValue() {
    if (!hubVariableName) {
        logWarn 'Hub variable name not configured.'
        return null
    }

    def val = getGlobalVar(hubVariableName)
    if (val != null) {
        return val.value
    }
    return null
}

Map recipientDeviceMap() {
    Map<String, Object> mapping = [:]
    notificationDevices?.each { device ->
        String key = settings["recipientName_${device.id}"]?.trim()
        if (key) {
            mapping[key] = device
        }
    }
    return mapping
}

// Build a map of available string hub variables so the configuration can present them in a dropdown.
Map<String, String> stringHubVariableOptions() {
    def variables = [:]

    // Create a map of variable names to their name & value concatenation
    def allVariables = getGlobalVarsByType("string")
    allVariables.keySet().toList().sort { a, b ->
        a.toString().toLowerCase() <=> b.toString().toLowerCase()
    }.each { key ->
        def value = allVariables[key]
        variables[key] = "$key: ${value.value}"
    }
    return variables
}

Integer safeInteger(value, Integer defaultValue) {
    try {
        return value != null ? Integer.parseInt(value.toString()) : defaultValue
    } catch (Exception ignored) {
        return defaultValue
    }
}

void logDebug(String message) {
    if (enableDebugLogging) {
        log.debug "[${appName}] ${message}"
    }
}

void logInfo(String message) {
    log.info "[${appName}] ${message}"
}

void logWarn(String message) {
    log.warn "[${appName}] ${message}"
}
