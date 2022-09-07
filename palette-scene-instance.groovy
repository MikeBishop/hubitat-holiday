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
            input "alignment", "bool", title: "Different colors on different lights?",
                width: 5, submitOnChange: true, defaultValue: true
            input "rotation", "enum", title: "How to rotate colors",
                width: 5, options: [
                    (RANDOM): "Random",
                    (STATIC):  "Static",
                    (SEQUENTIAL): "Sequential"
                ], submitOnChange: true
            if( !settings["alignment"] && settings["rotation"] == STATIC) {
                paragraph "Note: With this combination, only the first color will ever be used!"
            }
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

            def inputKey = "color${color}"
            debug("Color ${index+1} is ${settings[inputKey]}")

            // For each existing color slot, display four things:
            section("Color ${index+1}") {

                // Map, picker, and presets displayed here
                drawPicker(inputKey, color);

                // And finally a delete button.
                def delete = "<img src='${trashIcon}' width='30' style='float: left; width: 30px; margin: 5px 5px 0 -8px;'>"
                input "deleteColor${color}", "button", title: "${delete} Delete", submitOnChange: true, width: 3
            }
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

@Field final static String PICKER_JS = '''
<script type="text/javascript">
function syncColors(pickerId, inputId) {
    debugger;
    let colorMap = document.getElementById(inputId);
    let picker = document.getElementById(pickerId);
    let hueStr = '0', satStr = '0', valStr = '0';
    let colorStr = colorMap.value;

    if (colorStr) {
        try {
            colorStr = colorStr.replace(/(\\w+):/g, '"$1":');
            colorStr = colorStr.slice(1).slice(0, -1);
            const parsedColor = JSON.parse(`{${colorStr}}`);
            hueStr = `${parsedColor.hue}`;
            satStr = `${parsedColor.saturation}`;
            levStr = `${parsedColor.level}`;

            let hue = parseFloat(hueStr)/100;
            let sat = parseFloat(satStr)/100;
            let level = parseFloat(levStr)/100;

            let RGB = HSVtoRGB(hue, sat, level);

            picker.value = "#" + ((1 << 24) + (RGB.r << 16) + (RGB.g << 8) + RGB.b).toString(16).slice(1);
            return;
        } catch (e) {
            // ignore
        }
    }

    // Otherwise, read the picker and populate the Map input
    let hexString = picker.value;

    let rgb = {
        r: parseInt(hexString.substring(1, 3), 16),
        g: parseInt(hexString.substring(3, 5), 16),
        b: parseInt(hexString.substring(5, 7), 16)
    };
    let hsv = RGBtoHSV(rgb);
    colorMap.value = `[hue:${hsv.h*100}, saturation:${hsv.s*100}, level:${hsv.v*100}]`;
}

function HSVtoRGB(h, s, v) {
    var r, g, b, i, f, p, q, t;
    if (arguments.length === 1) {
        s = h.s, v = h.v, h = h.h;
    }
    i = Math.floor(h * 6);
    f = h * 6 - i;
    p = v * (1 - s);
    q = v * (1 - f * s);
    t = v * (1 - (1 - f) * s);
    switch (i % 6) {
        case 0: r = v, g = t, b = p; break;
        case 1: r = q, g = v, b = p; break;
        case 2: r = p, g = v, b = t; break;
        case 3: r = p, g = q, b = v; break;
        case 4: r = t, g = p, b = v; break;
        case 5: r = v, g = p, b = q; break;
    }
    return {
        r: Math.round(r * 255),
        g: Math.round(g * 255),
        b: Math.round(b * 255)
    };
}

function RGBtoHSV(r, g, b) {
    if (arguments.length === 1) {
        g = r.g, b = r.b, r = r.r;
    }
    var max = Math.max(r, g, b), min = Math.min(r, g, b),
        d = max - min,
        h,
        s = (max === 0 ? 0 : d / max),
        v = max / 255;

    switch (max) {
        case min: h = 0; break;
        case r: h = (g - b) + d * (g < b ? 6: 0); h /= 6 * d; break;
        case g: h = (b - r) + d * 2; h /= 6 * d; break;
        case b: h = (r - g) + d * 4; h /= 6 * d; break;
    }

    return {
        h: h,
        s: s,
        v: v
    };
}
</script>
''';

private drawPicker(inputKey, pickerSuffix = "") {
    def inputId = "settings[${inputKey}]";
    def colorOptions = COLORS.
        collect{ "<option value=\"${it.value}\">${it.key}</option>" }.
        join("\n");

    // First, the actual ColorMap input for a literal selection
    // Everything else transfers its value here.
    input inputKey, "COLOR_MAP", title: "", required: true, defaultValue: COLORS["White"]

    // Next, inject a color picker (and its scripts) to help with setting
    // the map:
    def pickerId = "colorPicker${pickerSuffix}"
    paragraph """
<input type="color" id="${pickerId}" style="width: 95%;" onChange="
    let mapElement = document.getElementById('${inputId}');
    mapElement.value = '';
    syncColors('${pickerId}', '${inputId}');
">
<script type="text/javascript">
\$(document).ready(function() {
syncColors("${pickerId}", "${inputId}");
document.getElementById("${inputId}").addEventListener("change", function () {
        syncColors("${pickerId}", "${inputId}");
    });
})
</script>
                """, width: 5

    // Then the preset options
    paragraph """
<select onChange="
    debugger;
    let mapElement = document.getElementById('${inputId}');
    mapElement.value = this.value;
    syncColors('${pickerId}', '${inputId}');">
${colorOptions}
</select>
    """,width: 4
}


void activatePalette(evt) {
    log.debug "Activating palette ${paletteName}: ${alignment ? "different" : "same"} colors, ${rotation} pattern";
    subscribe(getControlSwitch(), "switch.off", "deactivatePalette");

    // We're going to start the display; unless it's static,
    // schedule the updates.
    def handlerName = "doLightUpdate";
    unschedule(handlerName);
    if( frequency && rotation != STATIC ) {
        debug("Scheduling ${handlerName} every ${frequency} minutes");
        switch(Integer.parseInt(frequency)) {
            case 1:
                runEvery1Minute(handlerName);
                break;
            case 5:
                runEvery5Minutes(handlerName);
                break;
            case 10:
                runEvery10Minutes(handlerName);
                break;
            case 15:
                runEvery15Minutes(handlerName);
                break;
            case 30:
                runEvery30Minutes(handlerName);
                break;
            case 60:
                runEvery1Hour(handlerName);
                break;
            case 180:
                runEvery3Hours(handlerName);
                break;
            default:
                log.error "Invalid frequency: ${frequency.inspect()}";
        }
    }
    doLightUpdate();
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
    def colors = getColors(devices.size());

    // Apply the colors to the devices.
    debug("Applying colors ${colors.inspect()} to devices ${devices.inspect()}");
    [devices, colors].transpose().each {
        def device = it[0];
        def color = it[1];
        debug("Setting ${device} to ${color}");
        if( color ) {
            device*.setColor(color);
        }
    }
}

private getColors(desiredLength) {
    def colors = state.colorIndices.collect{
        try {
            evaluate(settings["color${it}"])
        }
        catch(Exception ex) {
            error(ex);
            null
        }
    };
    debug("Colors are ${colors.inspect()}");
    colors = colors.findAll{it && it.containsKey("hue") && it.containsKey("saturation") && it.containsKey("level")};

    if( colors.size() <= 0 ) {
        error("No colors found");
        return null;
    }

    def mode = rotation;
    def additional = 0;
    if( mode == SEQUENTIAL ) {
        additional = colors.size();
    }

    def result = [];
    // If we don't have enough colors, we'll need to repeat the colors.
    while( result.size() < desiredLength + additional ) result += colors;

    if( mode == RANDOM ) {
        Collections.shuffle(result);
    }

    debug("Colors selected: ${result.inspect()}");
    def offset = 0;
    if( mode == SEQUENTIAL ) {
        offset = state.sequentialIndex ?: 0;
        state.sequentialIndex = (offset + 1) % (additional ?: 1);
        debug("Starting from offset ${offset} (next is ${state.sequentialIndex})");
    }
    def subList = result[offset..<(offset + desiredLength)];
    debug("Sublist: ${subList.inspect()}");
    return subList;
}

void deactivatePalette(evt) {
    log.debug "Deactivating palette ${paletteName}"
    unschedule("doLightUpdate");
    unsubscribe(getControlSwitch(), "switch.off");
    parent.getRgbDevices()*.off();
}

void debug(String msg) {
    if( getParent().debugSpew() ) {
        log.debug(msg)
    }
}

void error(Exception ex) {
    error("${ex} at ${ex.getStackTrace()}");
}

void error(String msg) {
    log.error(msg);
}

@Field static final Map COLORS = [
    "Choose a Preset": "",
    "White": [hue: 0, saturation: 0, level: 100],
    "Magenta": [hue: 82, saturation: 100, level: 100],
    "Pink": [hue: 90.78, saturation: 67.84, level: 100],
    "Raspberry": [hue: 94, saturation: 100, level: 100],
    "Red": [hue: 0, saturation: 100, level: 100],
    "Brick Red": [hue: 4, saturation: 100, level: 100],
    "Safety Orange": [hue: 7, saturation: 100, level: 100],
    "Orange": [hue: 10, saturation: 100, level: 100],
    "Amber": [hue: 13, saturation: 100, level: 100],
    "Yellow": [hue: 16, saturation: 100, level: 100],
    "Pastel Green": [hue: 23, saturation: 56, level: 100],
    "Green": [hue: 33, saturation: 100, level: 100],
    "Turquoise": [hue: 47, saturation: 100, level: 100],
    "Aqua": [hue: 50, saturation: 100, level: 100],
    "Sky Blue": [hue: 53, saturation: 91, level: 100],
    "Navy Blue": [hue: 61, saturation: 100, level: 100],
    "Blue": [hue: 65, saturation: 100, level: 100],
    "Indigo": [hue: 73, saturation: 100, level: 100],
    "Purple": [hue: 77, saturation: 100, level: 100]
];

// Standardized strings
@Field static final String STATIC = "static";
@Field static final String RANDOM = "random";
@Field static final String SEQUENTIAL = "sequential";

@Field static final String trashIcon = "https://raw.githubusercontent.com/MikeBishop/hubitat-holiday/main/images/trash40.png"
