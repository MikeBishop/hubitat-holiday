library (
 author: "Mike Bishop",
 name: "color-tools",
 namespace: "evequefou",
 description: "Shared components between Holiday Lights and Palette Scenes"
)

import groovy.transform.Field

// put methods, etc. here

@Field final static String PICKER_JS = '<script type="text/javascript" src="/local/evequefou_color_picker.js"></script>'

private deviceSelector() {
    def key;
    def displayIndex = 0
    debug("Device indices are ${state.deviceIndices}")
    def deviceIndices = state.deviceIndices.clone();
    for( def index in deviceIndices ) {
        key = "device${index}";
        debug("settings[${key}] is ${settings[key]}")
        if( settings[key] == null ) {
            // User unselected this device -- drop the index
            state.deviceIndices.removeElement(index);
            app.removeSetting(key);
        }
        else {
            displayIndex += 1;
            input key, "capability.colorControl", title: "RGB light ${displayIndex}",
                multiple: false, submitOnChange: true
        }
    }
    def index = state.nextDeviceIndex;
    displayIndex += 1;
    key = "device${index}";
    input key, "capability.colorControl", title: "RGB light ${displayIndex}",
        multiple: false, submitOnChange: true
    if( settings[key] != null ) {
        // User selected device in new slot
        state.deviceIndices.add(index);
        state.nextDeviceIndex += 1;
        index = state.nextDeviceIndex;
        displayIndex += 1;
        key = "device${index}";
        input key, "capability.colorControl", title: "RGB light ${displayIndex}",
            multiple: false, submitOnChange: true
    }
}

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
    paragraph "<input type=\"color\" id=\"${pickerId}\" style=\"width: 95%;\" onChange=\"" +
        "let mapElement = document.getElementById('${inputId}');" +
        "mapElement.value = '';" +
        "syncColors('${pickerId}', '${inputId}');" +
        "\">" +
        "<script type=\"text/javascript\">" +
        "\$(document).ready(function() {" +
        "syncColors(\"${pickerId}\", \"${inputId}\");" +
        "document.getElementById(\"${inputId}\").addEventListener(\"change\", function () {" +
        "        syncColors(\"${pickerId}\", \"${inputId}\");" +
        "    });" +
        "})" +
        "</script>", width: 5

    // Then the preset options
    paragraph "<select onChange=\"" +
        "debugger;" +
        "let mapElement = document.getElementById('${inputId}');" +
        "mapElement.value = this.value;" +
        "syncColors('${pickerId}', '${inputId}');\">" +
        "${colorOptions}" +
        "</select>",width: 4
}

private displayOptions(prefix = "") {
    input "${prefix}Alignment", "bool", title: "Different colors on different lights?",
                width: 5, submitOnChange: true, defaultValue: true
    input "${prefix}Rotation", "enum", title: "How to rotate colors",
        width: 5, options: [
            (RANDOM): "Random",
            (STATIC):  "Static",
            (SEQUENTIAL): "Sequential"
        ], submitOnChange: true
    if( !settings["${prefix}Alignment"] && settings["${prefix}Rotation"] == STATIC) {
        paragraph "Note: With this combination, only the first color will ever be used!"
    }
}

private drawColorSection(prefix, color, index) {
    def inputKey = "${prefix}Color${color}"
    debug("Color ${index+1} is ${settings[inputKey]}")

    // For each existing color slot, display four things:
    section("Color ${index+1}") {

        // Map, picker, and presets displayed here
        drawPicker(inputKey, color);

        // And finally a delete button.
        def delete = "<img src='${trashIcon}' width='30' style='float: left; width: 30px; margin: 5px 5px 0 -8px;'>"
        def capPrefix = prefix ? prefix[0].toUpperCase() + prefix[1..-1] : "";
        input "delete${capPrefix}Color${color}", "button", title: "${delete} Delete", submitOnChange: true, width: 3
    }
}

private scheduleHandler(handlerName, frequency, recurring = true) {
    unschedule(handlerName);
    if( frequency && recurring ) {
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
    this."${handlerName}"()
}

private getColors(colorIndices, desiredLength, prefix = "") {
    def colors = colorIndices.collect{
        try {
            evaluate(settings["${prefix}Color${it}"])
        }
        catch(Exception ex) {
            error(ex);
            null
        }
    };
    debug("Colors${prefix ? " for " + prefix : ""}: ${colors.inspect()}");
    colors = colors.findAll{it && it.containsKey("hue") && it.containsKey("saturation") && it.containsKey("level")};

    if( colors.size() <= 0 ) {
        error("No valid colors found!");
        return null;
    }

    def mode = settings["${prefix}Rotation"];
    def additional = mode == SEQUENTIAL ? colors.size() : 0;

    def result = [];
    // If we don't have enough colors, we'll need to repeat the colors.
    while( result.size() < desiredLength + additional ) result += colors;

    if( mode == RANDOM ) {
        Collections.shuffle(result);
    }

    debug("Selected colors: ${result.inspect()}");
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

def applyColors(colors, devices) {
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

void debug(String msg) {
    if( debugSpew ) {
        log.debug(msg)
    }
}

void error(Exception ex) {
    log.error "${ex} at ${ex.getStackTrace()}"
}

void error(String msg) {
    log.error msg
}