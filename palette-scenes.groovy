/*
    Palette Scenes
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/


definition(
    name: "Palette Scenes",
    namespace: "evequefou",
    author: "Mike Bishop",
    description: "Create a scene (with activator device) that rotates through a color palette",
    category: "Convenience",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")


preferences {
     page name: "mainPage", title: "", install: true, uninstall: true
}


def installed() {
    log.info "Installed with settings: ${settings}"
    initialize()
}

def uninstalled() {
    log.info "Uninstalled"
    def childIds = childApps*.getId();
    childIds.each { deleteChildApp(it) }
    def switchGroup = getChildDevices().find();
    if (switchGroup) {
        switchGroup.removeExcess([]);
        deleteChildDevice(switchGroup.getDeviceNetworkId());
    }
}

def removeChild(childId) {
    log.debug "Removing child ${childId}"
    def currentIds = childApps*.getId().collect{ "${it}" }
    def newIds = currentIds.findAll{ it != childId }
    getParentSwitch().removeExcess(newIds);
}

def updated() {
    log.info "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}


def initialize() {
    log.info "There are ${childApps.size()} child apps"
    childApps.each { child ->
    	log.info "Child app: ${child.label}"
        child.initialize();
    }
    getParentSwitch().removeExcess(childApps*.getId().collect{ "${it}" })
    state.deviceIndices = state.deviceIndices ?: [];
    state.nextDeviceIndex = state.nextDeviceIndex ?: 0;
}

def getParentSwitch() {
    // Should only be one
    def switchGroup = getChildDevices().find();
    if (switchGroup == null) {
        log.info "No parent switch found; creating"
        def name = thisName ? "${thisName} Palettes" : "Palettes"
        switchGroup = addChildDevice("evequefou", "Single Active Switch", "${app.id}-switches", [name: name, isComponent: true]);
    }
    return switchGroup;
}


def getFormat(type, myText=""){
	if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

def getRgbDevices() {
    return state.deviceIndices.collect{ settings["device${it}"] };
}

def mainPage() {
    dynamicPage(name: "mainPage", install: true, uninstall: true) {
        def appInstalled = app.getInstallationState();

        if (appInstalled != 'COMPLETE') {
    		section{paragraph "Please hit 'Done' to install '${app.label}' parent app "}
  	    }
        else {
			section() {
                input "thisName", "text", title: "Name this instance", submitOnChange: true
                if(thisName) {
                    app.updateLabel("$thisName")
                    getParentSwitch().setLabel("$thisName Palettes")
                }

				paragraph "Select a collection of RGB devices to control. Each child instance will apply a different collection of colors to the selected devices."

                deviceSelector();
                input "debugSpew", "bool", title: "Log debug messages?",
                    submitOnChange: true, defaultValue: false;
            }
  			section("<b>Palettes:</b>") {
				app(name: "anyOpenApp", appName: "Palette Scene Instance", namespace: "evequefou", title: "<b>Create a new palette</b>", multiple: true)
			}
		}
	}
}

def debugSpew() {
    return debugSpew;
}

#include evequefou.color-tools
