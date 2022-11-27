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
        log.debug "Activator: ${activator.label}"
        unsubscribe(activator, "switch.on")
        subscribe(activator, "switch.on", "activatePalette");
    }
    settings["debugSpew"] = getParent().debugSpew();
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



void activatePalette(evt) {
    log.debug "Activating palette ${paletteName}: ${alignment ? "different" : "same"} colors, ${rotation} pattern";
    subscribe(getControlSwitch(), "switch.off", "deactivatePalette");

    // We're going to start the display; unless it's static,
    // schedule the updates.
    def handlerName = "doLightUpdate";
    scheduleHandler(handlerName, frequency, rotation != STATIC);
}

private doLightUpdate() {
    debug("Do light update");

    // Assemble the list of devices to use.
    def devices = parent.getRgbDevices();
    if( alignment ) {
        // Multiple colors displayed simultaneously.
        devices = devices.collect{ [it] };
    }
    else {
        // Single color displayed at a time.
        devices = [devices];
    }

    // Assemble the list of colors to apply.
    def colors = getColors(state.colorIndices, devices.size());

    // Apply the colors to the devices.
    applyColors(colors, devices);
}

void deactivatePalette(evt) {
    log.debug "Deactivating palette ${paletteName}"
    unschedule("doLightUpdate");
    unsubscribe(getControlSwitch(), "switch.off");
    parent.getRgbDevices()*.off();
}

#include evequefou.color-tools
