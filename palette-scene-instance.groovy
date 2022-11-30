/*
    Palette Scene Instance
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field


definition(
    parent: "evequefou:Palette Scenes",
    name: "Palette Scene Instance",
    namespace: "evequefou",
    author: "Mike Bishop",
    description: "A scene (with activator device) that rotates through a color palette",
    category: "Convenience",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")


preferences {
     page(name: "pageColorSelect")
}


def installed() {
    log.info "Installed with settings: ${settings}"
    initialize()
}

def uninstalled() {
    // Parent needs to notice we're gone
    parent.removeChild("${app.id}")
}

def updated() {
    log.info "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    def activator = getControlSwitch();
    if (activator != null) {
        debug("Activator: ${activator.label}")
        subscribe(activator, "switch.on", "activatePalette");
    }
    if( activator.currentValue("switch") == "on" ) {
        unschedule("relayLightUpdate")
        activatePalette()
    }
}

def pageColorSelect() {
    def name = settings["paletteName"] ?: "New Palette";
    dynamicPage(name: "pageColorSelect", title: "Colors for ${name}", install: true, uninstall: true) {

        section("Display Options") {
            input "paletteName", "text", title: "Palette Name", required: true, submitOnChange: true
            if( paletteName ) {
                app.updateLabel(paletteName);
                getControlSwitch().setLabel("${paletteName} Activator");
            }
            displayOptions()
            input "frequency", "enum", title: "Update Frequency",
                options: [
                    1: "1 minute",
                    5: "5 minutes",
                    10: "10 minutes",
                    15: "15 minutes",
				    30: "30 minutes",
				    60: "1 hour",
				    180: "3 hours"
                ], required: true
            input "debugSpew", "bool", title: "Log debug messages?",
                submitOnChange: true, defaultValue: getParent().debugSpew();


            paragraph PICKER_JS, width: 1
        }

        def colors = state.colorIndices;
        if( !colors ) {
            colors = [];
            state.colorIndices = colors;
        }
        debug("colors ${colors.inspect()}")

        colors.eachWithIndex { color, index ->
            drawColorSection("", color, index);
        }

        section("") {
            input "addColorToPalette", "button", title: "Add Color", submitOnChange: true
        }
        debug("Finished with pageColorSelect");
    }
}

void appButtonHandler(btn) {
    debug("Button ${btn} pressed");
    if (btn.startsWith("addColor")) {
        AddColor();
    }
    else if( btn.startsWith("deleteColor") ) {
        // Extract index
        def index = Integer.parseInt(btn.minus("deleteColor"));
        DeleteColor(index);
    }
}

private AddColor() {
    debug("Adding color");
    if( !state.nextColorIndex ) {
        state.nextColorIndex = 0;
    }

    def nextColor = state.nextColorIndex ?: 0;
    def indices = state.colorIndices;

    if( indices ) {
        indices.add(nextColor);
        state.colorIndices = indices;
    }
    else {
        state.colorIndices = [nextColor];
    }
    debug("colorIndices is now ${state.colorIndices.inspect()}")
    state.nextColorIndex = nextColor + 1;
    return nextColor;
}

private DeleteColor(int colorIndex) {
    debug("Deleting color ${colorIndex}");
    state.colorIndices.removeElement(colorIndex);
    app.removeSetting("color${colorIndex}")
}

private getControlSwitch() {
    def switchGroup = parent.getParentSwitch();
    return switchGroup.fetchChild("${app.id}");
}

void activatePalette(evt = null) {
    debug("Activating palette ${paletteName}: ${settings[ALIGNMENT] ? "different" : "same"} colors, ${settings[ROTATION]} pattern");
    subscribe(getControlSwitch(), "switch.off", "deactivatePalette");

    // We're going to start the display; unless it's static,
    // schedule the updates.
    scheduleHandler("relayLightUpdate", frequency, settings[ROTATION] != STATIC);
}

private relayLightUpdate() {
    debug("Do light update");
    doLightUpdate(parent.getRgbDevices(), state.colorIndices);
}

void deactivatePalette(evt) {
    debug("Deactivating palette ${paletteName}");
    unschedule("relayLightUpdate");
    unsubscribe(getControlSwitch(), "switch.off");
    parent.getRgbDevices()*.off();
}

#include evequefou.color-tools
