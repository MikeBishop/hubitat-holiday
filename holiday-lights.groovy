/*
    Holiday Lighting Manager
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field
import java.util.GregorianCalendar;
import java.time.*;
import java.time.format.DateTimeFormatter;
import static java.time.temporal.TemporalAdjusters.*;
import java.text.*;

definition (
    name: "Holiday Lighting", namespace: "evequefou", author: "Mike Bishop", description: "Themed light shows on RGB lights on holidays",
    importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-holiday/main/holiday-lights.groovy",
    category: "Lighting",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "deviceSelection")
    page(name: "holidayDefinitions")
    page(name: "pageEditHoliday")
    page(name: "pageImport")
    page(name: "pageColorSelect")
    page(name: "illuminationConfig")
}

Map mainPage() {
    debug("Rendering mainPage");
    dynamicPage(name: "mainPage", title: "Holiday Lighting", install: true, uninstall: true) {
        initialize();
        section("Options") {
            input "thisName", "text", title: "Name this instance", submitOnChange: true
            if(thisName) app.updateLabel("$thisName")

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

            def descr = "Choose which RGB/RGB bulbs to use"
            def deviceIndices = state.deviceIndices;
            if(deviceIndices.size()) {
                descr = "${deviceIndices.size()} devices selected"
            }
            href(
                name: "deviceSelectionHref",
                page: "deviceSelection",
                title: "RGB Device Selection",
                description: descr
            )
            href(
                name: "illuminationConfigHref",
                page: "illuminationConfig",
                title: "Non-Holiday Illumination",
                description: "When should lights be turned to white for normal illumination?"
            )
            href(
                name: "holidaySelectionHref",
                page: "holidayDefinitions",
                title: "Holiday Displays",
                description: "What colors on what special days?"
            )

            input "debugSpew", "bool", title: "Log debug messages?",
                submitOnChange: true, defaultValue: false;
        }
        debug("Finished with mainPage");
    }
}

Map deviceSelection() {
    debug("Rendering deviceSelection");
    dynamicPage(name: "deviceSelection", title: "Devices to Use") {
        section("Devices for Holiday Display") {
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
        debug("Finished with deviceSelection");
    }
}

Map holidayDefinitions() {
    debug("Rendering holidayDefinitions");
    dynamicPage(name: "holidayDefinitions", title: "Configure Holiday Displays") {
        sortHolidays()
        if( !state.colorIndices ) {
            debug("Creating colorIndices")
            state.colorIndices = [:];
        }

        section("Devices and Times") {
            selectStartStopTimes("holiday", "Display holiday lights");

            input "switchesForHoliday", "capability.switch", multiple: true,
                title: "Other switches to turn on when holiday lights are active"
        }

        debug("Colors are ${state.colorIndices.inspect()}")
        state.holidayIndices?.each { int i ->
            def title = "${settings["holiday${i}Name"]}"
            section(hideable: true, hidden: true, title) {
                // List colors here!
                def colorDescription = "No colors yet. Add some!"
                def colorsForThisHoliday = state.colorIndices["${i}"];
                if( colorsForThisHoliday?.size() && colorsForThisHoliday.every {
                    settings["holiday${i}Color${it}"] != null
                } ) {
                    colorDescription = colorsForThisHoliday.collect {
                        def colorMap;
                        try {
                            colorMap = evaluate(settings["holiday${i}Color${it}"].toString());
                        }
                        catch (Exception ex) {
                            warn("Rehydration failed with ${ex}")
                        }
                        def colorInRGB = HSVtoRGB(colorMap) ?: "#000000";
                        "<div style=\"background-color: ${colorInRGB}; padding: 10px; border: 1px solid black; display: inline-block\">&nbsp;</div>"
                    }.join()
                }

                href(
                    name: "selectColors${i}",
                    page: "pageColorSelect",
                    title: "Edit ${settings["holiday${i}Name"]} colors",
                    description: colorDescription,
                    params: [holidayIndex: i],
                )
                href(
                    name: "editHoliday${i}",
                    page: "pageEditHoliday",
                    title: "Edit ${settings["holiday${i}Name"]} schedule",
                    description: StringifyDate(i),
                    params: [holidayIndex: i],
                )
                input "testHoliday${i}", "button", title: "Test Holiday", submitOnChange: true, width: 4
                paragraph "", width: 1
                def delete = "<img src='${trashIcon}' width='30' style='float: left; width: 30px; margin: 8px 8px 0px -6px;'>"
                input "deleteHoliday${i}", "button", title: "${delete} Delete", submitOnChange: true, width: 4
            }
        }

        section("Add Holidays") {
            href(
                name: "editHoliday${state.nextHolidayIndex}",
                page: "pageEditHoliday",
                title: "Add new holiday...",
                params: [holidayIndex: state.nextHolidayIndex]
            )

            input "importSelected", "enum", title: "Import holiday lists",
                options: GetDefaultHolidays().keySet(), multiple: true,
                submitOnChange: true, width: 6
            if( importSelected ) {
                href(
                    name: "importHref",
                    page: "pageImport",
                    title: "Import selected lists...",
                    width: 6
                )
            }
        }
        debug("Finished with holidayDefinitions");
    }
}

Map pageImport() {
    debug("Rendering pageImport");
    dynamicPage(name: "pageImport", title: "Holiday Import progress") {
        def alreadyImported = state.imported ?: [:];
        importSelected.each {
            def list = it
            section("Importing ${list}...") {
                try {
                    def holidaysToImport = GetDefaultHolidays()[list];
                    debug("Plan to import ${holidaysToImport.size()} from ${holidaysToImport}")
                    holidaysToImport.each {
                        debug("Entering holiday parser...")
                        def i = state.nextHolidayIndex;
                        def source = it;
                        debug("Attempting ${source.name}");
                        def importSearch = alreadyImported.find{ it.value == source["id"] &&
                            state.holidayIndices.contains(Integer.parseInt("${it.key}")) };
                        if( importSearch ) {
                            debug("${source.name} already exists at index ${importSearch.key}")
                            paragraph "${source.name} already imported; skipping"
                        }
                        else {
                            app.updateSetting("holiday${i}Name", source.name)
                            app.updateSetting("holiday${i}Span", source.startDate != null)
                            ["Start", "End"].each {
                                def key = it.toLowerCase() + "Date"
                                if( source[key] ) {
                                    app.updateSetting("holiday${i}${it}Type", source[key].type)
                                    if( source[key].type != SPECIAL ) {
                                        if( source[key].type == ORDINAL ) {
                                            debug("Ordinal, ${source[key].ordinal} ${source[key].weekday.toString()} of ${source[key].month.toString()}")
                                            app.updateSetting("holiday${i}${it}Ordinal", source[key].ordinal.toString())
                                            app.updateSetting("holiday${i}${it}Weekday", source[key].weekday.toString())
                                        }
                                        else {
                                            //Fixed
                                            debug("Fixed, ${source[key].month.toString()} ${source[key].day}")
                                            app.updateSetting("holiday${i}${it}Day", source[key].day)
                                        }
                                        app.updateSetting("holiday${i}${it}Month", source[key].month.toString())
                                    }
                                    else {
                                        app.updateSetting("holiday${i}${it}Special", source[key].special)
                                    }
                                    app.updateSetting("holiday${i}${it}Offset", source[key].offset ?: 0)
                                }
                            }
                            def colorSettings = source["settings"];
                            app.updateSetting("holiday${i}Alignment",
                                colorSettings["type"] != STATIC || colorSettings["colors"]?.size() > 1);
                            app.updateSetting("holiday${i}Rotation", colorSettings["type"]);
                            colorSettings["colors"].each {
                                def idToImport = AddColorToHoliday(i);
                                app.updateSetting("holiday${i}Color${idToImport}", it.toString());
                            }
                            def indices = state.holidayIndices;
                            indices.add(i);
                            state.holidayIndices = indices;
                            state.nextHolidayIndex += 1;
                            alreadyImported[i] = source["id"];
                            paragraph "Imported ${source.name}"
                        }
                    }
                    paragraph "Finished importing ${list}!"
                }
                catch( Exception ex) {
                    error(ex);
                    paragraph "Importing failed!"
                }
            }
            state.imported = alreadyImported;
        }
        app.clearSetting("importSelected")
        debug("Finished with pageImport");
    }
}

def pageEditHoliday(params) {
    debug("Rendering pageEditHoliday");
    Integer i
    if( params.holidayIndex != null ) {
        i = params.holidayIndex
        state.editingHolidayIndex = i
    }
    else {
        i = state.editingHolidayIndex
        log.warn "Unexpected contents of params: ${params}"
    }

    if( i == state.nextHolidayIndex && holidayIsValid(i) ) {
        state.nextHolidayIndex += 1;
        state.holidayIndices.add(i);
        sortHolidays();
    }

    def formatter = DateTimeFormatter.ofPattern("MMMM");
    def monthOptions = Month.values().collectEntries { [it, LocalDate.now().with(it).format(formatter)] }
    formatter = DateTimeFormatter.ofPattern("EEEE");
    def dayOptions = DayOfWeek.values().collectEntries { [it, LocalDate.now().with(it).format(formatter)]}

    def name = settings["holiday${i}Name"] ?: "New Holiday"
    dynamicPage(name: "pageEditHoliday", title: "Edit ${name}") {
        section("Holiday definition") {
            input "holiday${i}Name", "text", title: "Name", required: true
            input "holiday${i}Span", "bool", defaultValue: false, title: "Date range? (If false, lights show the night before and night of the holiday.)", submitOnChange: true
        }

        def dates = ["End"]
        if( settings["holiday${i}Span"] ) {
            dates.add(0, "Start")
        }

        dates.each{
            def date = it
            section("${settings["holiday${i}Span"] ? it : "Select"} date") {
                debug("date is ${date}")
                input "holiday${i}${date}Type", "enum", title: "Type of Schedule", multiple: false, options: [
                    (FIXED): "Fixed date",
                    (ORDINAL): "Fixed weekday",
                    (SPECIAL): "Special days"
                ], submitOnChange: true, required: true
                if( settings["holiday${i}${date}Type"] in [ORDINAL, FIXED] ) {
                    if( settings["holiday${i}${date}Type"] == ORDINAL ) {
                        input "holiday${i}${date}Ordinal", "enum", title: "Which week?",
                            options: ORDINALS, required: true, submitOnChange: true
                        input "holiday${i}${date}Weekday", "enum", title: "Which day?",
                            options: dayOptions, width: 5, required: true, submitOnChange: true
                        paragraph "\nof", width: 2
                    }
                    input "holiday${i}${date}Month", "enum", title: "Month", options: monthOptions,
                        submitOnChange: true, width: 5, required: true
                    if( settings["holiday${i}${date}Type"] == FIXED && settings["holiday${i}${date}Month"] ) {
                        def numDays = Month.valueOf(unarray(settings["holiday${i}${date}Month"])).length(true)
                        input "holiday${i}${date}Day", "number", title: "Date", range:"1..${numDays}",
                            width: 5, required: true, submitOnChange: true
                    }
                }
                else if (settings["holiday${i}${date}Type"] == SPECIAL ) {
                    input "holiday${i}${date}Special", "enum", options: SPECIALS, required: true, submitOnChange: true
                }
                input "holiday${i}${date}Offset", "number", title: "Offset (optional)", range:"-60..60"
            }
        }
        debug("Finished with pageEditHoliday");
    }
}

def pageColorSelect(params) {
    debug("Rendering pageColorSelect");
    int i;
    if( params?.holidayIndex != null ) {
        i = params.holidayIndex
        state.editingHolidayIndex = i
    }
    else {
        i = state.editingHolidayIndex
        log.warn "Unexpected contents of params: ${params}"
    }

    def name = settings["holiday${i}Name"] ?: "New Holiday"
    dynamicPage(name: "pageColorSelect", title: "Colors for ${name}") {
        section("Display Options") {
            input "holiday${i}Alignment", "bool", title: "Different colors on different lights?",
                width: 5, submitOnChange: true, defaultValue: true
            input "holiday${i}Rotation", "enum", title: "How to rotate colors",
                width: 5, options: [
                    (RANDOM): "Random",
                    (STATIC):  "Static",
                    (SEQUENTIAL): "Sequential"
                ], submitOnChange: true
            if( !settings["holiday${i}Alignment"] && settings["holiday${i}Rotation"] == STATIC) {
                paragraph "Note: With this combination, only the first color will ever be used!"
            }
            paragraph PICKER_JS, width: 1
        }


        debug("colorIndices is now ${state.colorIndices.inspect()}")
        def colorsForThisHoliday = state.colorIndices["${i}"];
        if( !colorsForThisHoliday ) {
            colorsForThisHoliday = [];
            state.colorIndices["${i}"] = colorsForThisHoliday;
        }
        debug("colorsForThisHoliday ${colorsForThisHoliday.inspect()}")

        colorsForThisHoliday.eachWithIndex { color, index ->

            def inputKey = "holiday${i}Color${color}"
            debug("Color ${index+1} is ${settings[inputKey]}")

            // For each existing color slot, display four things:
            section("Color ${index+1}") {

                // Map, picker, and presets displayed here
                drawPicker(inputKey, color);

                // And finally a delete button.
                def delete = "<img src='${trashIcon}' width='30' style='float: left; width: 30px; margin: 5px 5px 0 -8px;'>"
                input "deleteHoliday${i}Color${color}", "button", title: "${delete} Delete", submitOnChange: true, width: 3
            }
        }

        section("") {
            input "testHoliday${i}", "button", title: "Test Holiday", submitOnChange: true
            input "addColorToHoliday${i}", "button", title: "Add Color", submitOnChange: true
        }
        debug("Finished with pageColorSelect");
    }
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
<select name="${presetKey}" onChange="
    debugger;
    let mapElement = document.getElementById('${inputId}');
    mapElement.value = this.value;
    syncColors('${pickerId}', '${inputId}');">
${colorOptions}
</select>
    """,width: 4
}

Map illuminationConfig() {
    debug("Rendering illuminationConfig");
    dynamicPage(name: "illuminationConfig", title: "Illumination Configuration") {
        section("Switch Configuration") {
            input "illuminationSwitch", "capability.switch", title: "Switch to control/reflect illumination state"
            input "otherIlluminationSwitches", "capability.switch", title: "Other switches to turn on when triggered", multiple: true
        }
        section("Triggered Configuration") {
            selectStartStopTimes("illumination", "Allow triggers");
            input "motionTriggers", "capability.motionSensor", title: "Motion sensors to trigger lights when active", multiple: true
            input "contactTriggers", "capability.contactSensor", title: "Contact sensors to trigger lights when open", multiple: true
            input "lockTriggers", "capability.lock", title: "Locks to trigger lights when unlocked", multiple: true
            input "duration", "number", title: "How many minutes to stay illuminated after sensor activity stops?"
        }
        def devices = state.deviceIndices.collect{settings["device${it}"]};
        debug("${devices}");
        def areLightsCT = devices*.hasCapability("ColorTemperature");
        debug("${areLightsCT}");
        if( areLightsCT.any{ a -> a }) {
            // Some lights support CT, so we can show the CT section.
            section("Config for CT lights") {
                input "colorTemperature", "number", title: "Color temperature", width: 6
                input "level", "number", title: "Brightness", width: 6, range: "0..100"
            }
        }
        if( areLightsCT.any{ a -> !a }) {
            // Some lights do not support CT, so we need to show a color picker.
            section("Config for non-CT lights") {
                drawPicker("illuminationColor");
                paragraph PICKER_JS, width:1;
            }
        }
        debug("Finished with illuminationConfig");
    }
}

private selectStartStopTimes(prefix, description) {
    input "${prefix}StartTime", "enum", title: "${description} from...",
        width: 6, options: TIME_OPTIONS, submitOnChange: true
    input "${prefix}StopTime", "enum", title: "${description} until...",
        width: 6, options: TIME_OPTIONS, submitOnChange: true
    ["Start", "Stop"].each {
        if( settings["${prefix}${it}Time"] == CUSTOM ) {
            input "${prefix}${it}TimeCustom", "time", title: "Specify ${it.toLowerCase()} time:", width: 6
        }
        else if (settings["${prefix}${it}Time"]) {
            input "${prefix}${it}TimeOffset", "number", title: "Start ${it.toLowerCase()} offset in minutes:", width: 6, range: "-240:240"
        }
    }
    input "${prefix}Modes", "enum", title: "${description} during the following modes, regardless of time...",
        width: 6, options: location.getModes()*.toString(), multiple: true
}

private holidayIsValid(int i) {
    return settings["holiday${i}Name"] && holidayDateIsValid("holiday${i}End") &&
        (!settings["holiday${i}Span"] || holidayDateIsValid("holiday${i}Start"))
}

private holidayDateIsValid(String key) {
    return settings["${key}Type"] in [ORDINAL, FIXED, SPECIAL] && (
            (settings["${key}Type"] == ORDINAL && settings["${key}Ordinal"] && settings["${key}Weekday"] ) ||
            (settings["${key}Type"] == FIXED && settings["${key}Day"]) ||
            (settings["${key}Type"] == SPECIAL && settings["${key}Special"] in SPECIALS.keySet())
        ) && (settings["${key}Type"] == SPECIAL || settings["${key}Month"] != null)
}

private holidayDate(int i, String dateType, int year) {
    def name = settings["holiday${i}Name"];
    // debug("Finding concrete ${dateType} date for ${i} (${name})")
    if( dateType == "Start" && !settings["holiday${i}Span"]) {
        // For non-Span holidays, the start date is the day before the end date
        return holidayDate(i, "End", year)?.minusDays(1);
    }

    def key = "holiday${i}${dateType}";
    def type = settings["${key}Type"];
    def month = settings["${key}Month"];
    Integer date = settings["${key}Day"];
    def result;
    switch(type) {
        case FIXED:
            // debug("Fixed ${year}, ${month}, ${date}")
            result = LocalDate.of(year, Month.valueOf(month), date);
            break;
        case ORDINAL:
            def ordinal = settings["${key}Ordinal"]
            def weekday = settings["${key}Weekday"]
            // debug("Ordinal ${year}, ${month}, ${ordinal}, ${weekday}")
            result = LocalDate.of(year, Month.valueOf(month), 15).
                with(dayOfWeekInMonth(Integer.parseInt(ordinal), DayOfWeek.valueOf(weekday)));
            break;
        case SPECIAL:
            def special = settings["${key}Special"];
            switch(special) {
                case "easter":
                    result = easterForYear(year);
                    break;
                case "passover":
                    result = passoverForYear(year);
                    break;
                case "roshHashanah":
                    result = roshHashanahForYear(year);
                    break;
                default:
                    log.warn "Unknown special ${special}"
            }
            break;
        default:
            log.warn "Invalid date format ${type} in holiday ${name}!"
            return null;
    }

    def offset = settings["${key}Offset"] ?: 0;

    return result.plusDays(offset);
}

private sortHolidays() {
    def thisYear = LocalDate.now().getYear()
    def originalList = state.holidayIndices
    debug("Sorting holidays: ${originalList.inspect()}....")
    def invalid = originalList.findAll{!holidayIsValid(it)};
    invalid.each{ log.warn "Invalid holiday ${it}"; DeleteHoliday(it); }
    def sortedList = originalList.minus(invalid).collect{
            [it, holidayDate(it, "Start", thisYear), holidayDate(it, "End", thisYear)]
        }.sort{ a,b ->
            a[2] <=> b[2] ?: a[1] <=> b[1]
        }.collect{it[0]};
    state.holidayIndices = sortedList;
    debug("List became ${sortedList.inspect()}")
}

void appButtonHandler(btn) {
    debug("Button ${btn} pressed");
    if( btn.startsWith("deleteHoliday") ) {
        // Extract index
        def parts = btn.minus("deleteHoliday").split("Color")
        def index = Integer.parseInt(parts[0]);
        if( parts.size() == 1 ) {
            DeleteHoliday(index);
        }
        else {
            DeleteColor(index, Integer.parseInt(parts[1]));
        }
    }
    else if (btn.startsWith("addColorToHoliday")) {
        def holidayIndex = Integer.parseInt(btn.minus("addColorToHoliday"));
        AddColorToHoliday(holidayIndex);
    }
    else if (btn.startsWith("testHoliday")) {
        def holidayIndex = Integer.parseInt(btn.minus("testHoliday"));
        testHoliday(holidayIndex);
    }
}

private AddColorToHoliday(int holidayIndex) {
    debug("Adding color to holiday ${holidayIndex}");
    if( !state.nextColorIndices ) {
        state.nextColorIndices = [:];
    }

    def nextColor = state.nextColorIndices["${holidayIndex}"] ?: 0;
    def indicesForHoliday = state.colorIndices["${holidayIndex}"];

    if( indicesForHoliday ) {
        indicesForHoliday.add(nextColor);
        state.colorIndices["${holidayIndex}"] = indicesForHoliday;
    }
    else {
        state.colorIndices["${holidayIndex}"] = [nextColor];
    }
    debug("colorIndices is now ${state.colorIndices.inspect()}")
    state.nextColorIndices["${holidayIndex}"] = nextColor + 1;
    return nextColor;
}

private DeleteHoliday(int index) {
    debug("Deleting holiday ${index}");
    state.holidayIndices.removeElement(index);
    settings.keySet().findAll{
        it.startsWith("holiday${index}") &&
        !(it.minus("holiday${index}")[0] as char).isDigit()
    }.each {
        debug("Removing setting ${it}")
        app.removeSetting(it);
    }
    state.colorIndices.remove("${index}");
    state.imported.remove("${index}");
}

private DeleteColor(int holidayIndex, int colorIndex) {
    debug("Deleting color ${colorIndex} from holiday ${holidayIndex}");
    state.colorIndices["${holidayIndex}"].removeElement(colorIndex);
    app.removeSetting("holiday${holidayIndex}Color${colorIndex}")
}

private StringifyDate(int index) {
    def dates = ["End"];
    if( !holidayIsValid(index) ) {
        return "Invalid date";
    }

    if( settings["holiday${index}Span"] ) {
        dates.add(0, "Start");
    }
    dates.collect {
        try {
            def result = ""
            if( settings["holiday${index}${it}Type"] != SPECIAL ) {
                formatter = DateTimeFormatter.ofPattern("MMMM");
                def monthEnum = unarray(settings["holiday${index}${it}Month"]);
                def month = Month.valueOf(monthEnum);
                def monthString = LocalDate.now().with(month).format(formatter);

                def offset = settings["holiday${index}${it}Offset"]
                if( offset && Integer.parseInt(offset.toString()) != 0 ) {
                    if( offset instanceof String ) {
                        offset = Integer.parseInt(offset);
                    }
                    def absOffset = Math.abs(offset)
                    result += "${absOffset} day${absOffset > 1 ? "s" : ""} ${offset > 0 ? "after" : "before"} "
                }

                if( settings["holiday${index}${it}Type"] == ORDINAL ) {
                    def ordinal = unarray(settings["holiday${index}${it}Ordinal"]);
                    result += "${ORDINALS[ordinal]} ";
                    def formatter = DateTimeFormatter.ofPattern("EEEE");
                    def dayEnum = unarray(settings["holiday${index}${it}Weekday"]);
                    def day = DayOfWeek.valueOf(dayEnum);
                    result += LocalDate.now().with(day).format(formatter) + " of "
                    result += monthString;
                    return result;
                }
                else {
                    //Fixed
                    return "${monthString} ${settings["holiday${index}${it}Day"]}"
                }
            }
            else {
                return SPECIALS[settings["holiday${index}${it}Special"]]
            }
        }
        catch(Exception ex) {
            error(ex);
            return "Invalid date"
        }
    }.join(" through ")
}

private unarray(thing) {
    if( thing instanceof ArrayList ) {
        return thing[0]
    }
    else {
        return thing
    }
}

void updated() {
	initialize();
    beginStateMachine();
}

void installed() {
	initialize();
}

void initialize() {
    if( state.deviceIndices instanceof Boolean ) {
        state.deviceIndices = []
    }
    state.nextHolidayIndex = state.nextHolidayIndex ?: 0;
    state.holidayIndices = state.holidayIndices ?: [];
    state.nextDeviceIndex = state.nextDeviceIndex ?: 0;
    state.deviceIndices = state.deviceIndices ?: [];
    debug("Initialize.... ${state.nextHolidayIndex.inspect()} and ${state.holidayIndices.inspect()}")
}

void debug(String msg) {
    if( debugSpew ) {
        log.debug(msg)
    }
}

void error(Exception ex) {
    error("${ex} at ${ex.getStackTrace()}");
}

void error(String msg) {
    log.error(msg);
}

// #region Event Handlers

void beginStateMachine() {
    debug("Begin state machine");
    unsubscribe();
    unschedule();
    state.test = false;
    state.currentHoliday = null;
    state.sequentialIndex = null;

    // Basic subscriptions -- subscribe to switch changes and schedule begin/end
    // of other periods.
    if( illuminationSwitch ) {
        subscribe(illuminationSwitch, "switch.on", "beginIlluminationPeriod");
    }
    if( duringIlluminationPeriod() ) {
        beginIlluminationPeriod();
    }

    // Create schedules for things that don't change
    startFixedSchedules();
    // Schedule the next instances for sunrise/sunset values
    scheduleSunriseAndSunset();

    // ...and listen to the event to schedule future iterations.
    subscribe(location, "sunriseTime", "scheduleSunriseAndSunset");
    subscribe(location, "sunsetTime", "scheduleSunriseAndSunset");

    // Listen for mode changes.
    if( illuminationModes || holidayModes ) {
        subscribe(location, "mode", "onModeChange");
    }

    // If illumination mode is active, should it be?
    if( state.illuminationMode ) {
        debug("Illumination mode is on when starting");
        checkIlluminationOff();
    }
    else {
        turnOffIllumination();
    }
    // If not, turn off the lights and schedule for next holiday.
}

private testHoliday(index) {
    state.currentHoliday = index;
    state.test = true;
    turnOffIllumination();
    beginHolidayPeriod();
    if( settings["holiday${currentHoliday}Display"] != STATIC ) {
        [15, 30, 45].each {
            runIn(it, "doLightUpdate");
        }
    }
    runIn(60, "beginStateMachine");
}

private onModeChange(evt) {
    debug("Mode changed to ${location.getMode().toString()}");
    if( duringIlluminationPeriod() ) {
        beginIlluminationPeriod();
    }
    else {
        // End Illumination will check for active holiday; no need to
        // handle that case explicitly.
        endIlluminationPeriod();
    }
}

private beginHolidayPeriod() {
    debug("Begin holiday period");
    state.currentHoliday = state.currentHoliday ?: getCurrentOrNextHoliday();
    def currentHoliday = state.currentHoliday;
    if( currentHoliday != null ) {
        def dates = getHolidayDates(currentHoliday);
        def startTime = LocalDateTime.of(dates[0], getLocalTime("holidayStart") ?: LocalTime.MIDNIGHT);
        def endTime = LocalDateTime.of(dates[1], getLocalTime("holidayStop") ?: LocalTime.MAX);
        def now = LocalDateTime.now();

        if( state.test || (now.isAfter(startTime) && now.isBefore(endTime)) ) {
            debug("Holiday is active");

            // We're going to start the display; unless it's static,
            // schedule the updates.
            def handlerName = "doLightUpdate";
            unschedule(handlerName);
            if( frequency && settings["holiday${currentHoliday}Display"] != STATIC && !state.test ) {
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
            switchesForHoliday*.on();
        }
    }
}

private doLightUpdate() {
    debug("Do light update");
    def currentHoliday = state.currentHoliday;
    if( currentHoliday != null ) {
        // Assemble the list of devices to use.
        def devices = state.deviceIndices.collect{ settings["device${it}"] };
        if( settings["holiday${currentHoliday}Alignment"] ) {
            // Multiple colors displayed simultaneously.
            devices = devices.collect{ [it] };
        }
        else {
            // Single color displayed at a time.
            devices = [devices];
        }

        // Assemble the list of colors to apply.
        def colors = getColorsForHoliday(currentHoliday, devices.size());

        // Apply the colors to the devices.
        debug("Applying colors ${colors.inspect()} to devices ${devices.inspect()}");
        if( devices && colors ) {
            [devices, colors].transpose().each {
                def device = it[0];
                def color = it[1];
                debug("Setting ${device} to ${color}");
                if( color ) {
                    device*.setColor(color);
                }
            }
        }
    }
}

private endHolidayPeriod() {
    debug("Not in holiday period");
    state.currentHoliday = null;
    unschedule("doLightUpdate");
    lightsOff();
}

private getColorsForHoliday(index, desiredLength) {
    def colors = state.colorIndices["${index}"].collect{
        try {
            def mapText = settings["holiday${index}Color${it}"];
            if( mapText ) {
                evaluate(mapText)
            }
            else {
                null
            }
        }
        catch(Exception ex) {
            error(ex);
            null
        }
    };
    debug("Colors for holiday ${index}: ${colors.inspect()}");
    colors = colors.findAll{it && it.containsKey("hue") && it.containsKey("saturation") && it.containsKey("level")};

    if( colors.size() <= 0 ) {
        error("No colors found for holiday ${index}");
        return null;
    }

    def mode = settings["holiday${index}Rotation"];
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

    debug("Colors for holiday ${index}: ${result.inspect()}");
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

private beginIlluminationPeriod(event = null) {
    debug("Begin illumination period" + (event ? " after ${event.device} sent ${event.value}" : ""));
    // Subscribe to the triggers
    subscribe(motionTriggers, "motion.active", "triggerIllumination");
    subscribe(contactTriggers, "contact.open", "triggerIllumination");
    subscribe(lockTriggers, "lock.unlocked", "triggerIllumination");
    if( illuminationSwitch?.currentValue("switch") == "on" ||
        anyIlluminationTriggers()
    ) {
        debug("Sensor trigger is active");
        triggerIllumination();
    }
}

private anyIlluminationTriggers() {
    def motion = motionTriggers.any { it.currentValue("motion") == "active" };
    def contact = contactTriggers.any { it.currentValue("contact") == "open" };
    def lock = lockTriggers.any { it.currentValue("lock").startsWith("unlocked") };

    debug "Motion: ${motion}, ${motionTriggers ? motionTriggers*.currentValue("motion") : "none"}";
    debug "Contact: ${contact}, ${contactTriggers ? contactTriggers*.currentValue("contact") : "none"}";
    debug "Lock: ${lock}, ${lockTriggers ? lockTriggers*.currentValue("lock") : "none"}";

    return motion || contact || lock;
}

private endIlluminationPeriod() {
    if( !duringIlluminationPeriod() ) {
        debug("End illumination period");
        turnOffIllumination();
    }
}

private triggerIllumination(event = null) {
    debug("Illumination triggered" + (event ? " after ${event.device} sent ${event.value}" : ""));
    state.illuminationMode = true;
    illuminationSwitch?.on();
    def devices = state.deviceIndices.collect{ settings["device${it}"] };
    def ctDevices = devices.findAll { it.hasCapability("ColorTemperature")};
    debug("CT-capable devices: ${ctDevices.inspect()}");
    def rgbOnlyDevices = devices.minus(ctDevices);
    debug("RGB devices: ${rgbOnlyDevices.inspect()}");

    ctDevices*.setColorTemperature(colorTemperature, level, null);
    if( rgbOnlyDevices) {
        try {
            def colorMap = evaluate(illuminationColor);
            rgbOnlyDevices*.setColor(colorMap);
        }
        catch(Exception ex) {
            error(ex);
        }
    }
    otherIlluminationSwitches*.on();

    subscribe(motionTriggers, "motion.inactive", "checkIlluminationOff");
    subscribe(contactTriggers, "contact.closed", "checkIlluminationOff");
    subscribe(lockTriggers, "lock.locked", "checkIlluminationOff");
    subscribe(illuminationSwitch, "switch.off", "turnOffIllumination");
    unschedule("turnOffIllumination");
    unschedule("doLightUpdate");
}

private checkIlluminationOff(event = null) {
    debug("Checking if illumination should be turned off" + (event ? " after ${event.device} sent ${event.value}" : ""));
    if( !anyIlluminationTriggers() ) {
            debug("No sensor activity detected, turning off illumination in ${duration} minutes");
            unsubscribe(motionTriggers, "motion.inactive");
            unsubscribe(contactTriggers, "contact.closed");
            unsubscribe(lockTriggers, "lock.locked");
            runIn(duration * 60, "turnOffIllumination");
    }
}

private turnOffIllumination(event = null) {
    debug("Turning off illumination" + (event ? " after ${event.device} sent ${event.value}" : ""));
    illuminationSwitch?.off();
    state.illuminationMode = false;
    unschedule("turnOffIllumination");
    if( !duringIlluminationPeriod() ) {
        unsubscribe(motionTriggers);
        unsubscribe(contactTriggers);
        unsubscribe(lockTriggers);
    }

    if( !duringHolidayPeriod() ) {
        // Lights Off
        endHolidayPeriod();
    }
    else {
        def currentOrNext = getCurrentOrNextHoliday();
        def holidayDates = currentOrNext != null ? getHolidayDates(currentOrNext) : [];
        if ( holidayDates && dateIsBetweenInclusive(LocalDate.now(), holidayDates[0], holidayDates[1]) )
        {
            // Lights On
            beginHolidayPeriod();
        }
        else {
            // No holiday to show; Lights Off
            lightsOff();
        }
    }
}

private dateIsBetweenInclusive(date, start, end) {
    return start && end && (date?.isEqual(start) || date?.isAfter(start)) &&
        (date?.isBefore(end) || date?.isEqual(end));
}

private getHolidayDates(index) {
    def today = LocalDate.now()
    def thisYear = today.getYear();
    def nextYear = thisYear + 1;

    def startDate = holidayDate(index, "Start", thisYear);
    def endDate = holidayDate(index, "End", thisYear);

    if( !startDate || !endDate ) {
        // If calculations failed, don't try to do any fixups.
        return [];
    }

    // If the end date is before the start date, it crosses the year boundary.
    if( endDate.isBefore(startDate) ) {
        endDate = holidayDate(index, "End", nextYear);
    }

    // If the end date has passed, we need to move to the next occurrence.
    if( endDate.isBefore(today) ) {
        startDate = holidayDate(index, "Start", startDate.getYear() + 1);
        endDate = holidayDate(index, "End", endDate.getYear() + 1);
    }

    // If the end time is before the start time, the display period crosses
    // into the following day.
    if( getLocalTime("holidayStop")?.isBefore(getLocalTime("holidayStart")) ) {
        endDate = endDate.plusDays(1);
    }
    debug("Holiday ${index} starts ${startDate} and ends ${endDate}");

    return [startDate, endDate];
}

private getCurrentOrNextHoliday() {
    def thisYear = LocalDate.now().getYear();
    def nextYear = thisYear + 1;
    def today = LocalDate.now();
    def futureHolidays = state.holidayIndices.collect{
        def dates = getHolidayDates(it);
        [it, dates[0], dates[1]]
    };
    debug("Future holidays: ${futureHolidays}");
    def currentHolidays = futureHolidays.findAll{
        def startDate = it[1];
        def endDate = it[2];
        dateIsBetweenInclusive(today, startDate, endDate);
    };
    debug("Current holidays: ${currentHolidays}");
    if( currentHolidays.size() > 1 ) {
        def result = currentHolidays.collect{
            [it[0], Duration.between(it[1].atStartOfDay(), it[2].atStartOfDay())]
        }.sort{ a,b -> a[1] <=> b[1] }.first();
        debug("Selected holiday: ${result}");
        return result[0];
    }
    else if ( currentHolidays.size() == 1 ) {
        debug("Selected holiday: ${currentHolidays[0]}");
        return currentHolidays.first()[0];
    }
    else if ( futureHolidays.size() ) {
        def result = futureHolidays.
            sort{ a,b -> a[1] <=> b[1] ?: a[2] <=> b[2] }.first();
        debug("Next holiday: ${result}");
        return result[0];
    }
    else {
        debug("No holidays");
        return null;
    }
}

private lightsOff() {
    debug("Turning off lights");
    def devices = state.deviceIndices.collect{ settings["device${it}"] };
    devices*.off();
    otherIlluminationSwitches*.off();
    switchesForHoliday*.off();
}

private scheduleSunriseAndSunset(event = null) {
    def sunrise = null;
    def sunset = null;
    if( event ) {
        debug("Event ${event.name} says ${event.value}");
    }
    if( event?.name == "sunriseTime" ) {
        sunrise = toDateTime(event.value);
    }
    else if( event?.name == "sunsetTime" ) {
        sunset = toDateTime(event.value);
    }
    else {
        // No event; do both.
        sunrise = toDateTime(
            getLocationEventsSince("sunriseTime", new Date() - 2, [max: 1])[0]?.value
        );
        sunset = toDateTime(
            getLocationEventsSince("sunsetTime", new Date() - 2, [max: 1])[0]?.value
        );
        debug("Got sunrise: ${sunrise} and sunset: ${sunset} from location events");
    }
    // Sunrise/sunset just changed, so schedule the upcoming events...
    PREFIX_AND_HANDLERS.each {
        def prefix = it[0];
        def handler = it[1];
        def targetTime = settings["${prefix}Time"];
        if ( (targetTime == SUNRISE  && sunrise != null) ||
             (targetTime == SUNSET && sunset != null) ) {
            def offset = settings["${prefix}TimeOffset"] ?: 0;

            def scheduleFor = targetTime == SUNRISE ? sunrise : sunset;

            // Apply offset
            scheduleFor = Date.from(scheduleFor.toInstant().plus(Duration.ofMinutes(offset ?: 0)));

            // Should no longer happen, but just in case...
            if( scheduleFor < new Date() ) {
                // Date in past; advance by a day
                scheduleFor = Date.from(scheduleFor.toInstant().plus(Duration.ofDays(1)));
            }

            debug("Scheduling ${prefix} for ${scheduleFor} (${targetTime} with ${offset} minutes offset)");
            runOnce(scheduleFor, handler);
        }
    }
}

@Field static final List PREFIX_AND_HANDLERS = [
    ["illuminationStart", "beginIlluminationPeriod"],
    ["illuminationStop", "endIlluminationPeriod"],
    ["holidayStart", "beginHolidayPeriod"],
    ["holidayStop", "endHolidayPeriod"]
];

private startFixedSchedules() {
    PREFIX_AND_HANDLERS.each {
        def prefix = it[0];
        def handler = it[1];
        if( settings["${prefix}Time"] == CUSTOM ) {
            def time = getAsTimeString(prefix);
            debug("Scheduling ${prefix} for ${time} (custom)");
            schedule(time, handler);
        }
    }
}

// #endregion

// #region Time Helper Functions

private getAsTimeString(prefix) {
    def legacyDate = timeToday(settings["${prefix}TimeCustom"]);

    if( legacyDate ) {
        return "0 " + new SimpleDateFormat("m H").format(legacyDate) +
            " * * ?";
    }
    return null;
}

private Boolean duringHolidayPeriod() {
    return duringPeriod("holiday");
}

private Boolean duringIlluminationPeriod() {
    return duringPeriod("illumination");
}

private Boolean duringPeriod(prefix) {
    def beginTime = getLocalTimeToday("${prefix}Start");
    def endTime = getLocalTimeToday("${prefix}Stop");
    def activeModes = settings["${prefix}Modes"];
    def reverseResults = false;

    if( activeModes?.contains(location.getMode().toString()) ) {
        return true;
    }

    if( !beginTime || !endTime ) {
        if( !activeModes ) {
            log.warn "No ${prefix} time set; ${beginTime} - ${endTime}";
        }
        return false;
    }

    if( endTime < beginTime ) {
        def swap = beginTime;
        beginTime = endTime;
        endTime = swap;
        reverseResults = true;
    }
    def now = LocalDateTime.now();
    def result = now.isAfter(beginTime) && now.isBefore(endTime);
    return reverseResults ? !result : result;
}

private LocalDateTime getLocalTimeToday(prefix) {
    def localTime = getLocalTime(prefix);
    if( localTime ) {
        return LocalDateTime.of(LocalDate.now(), localTime);
    }
    else {
        return null;
    }
}

private LocalDateTime getLocalTimeTomorrow(prefix) {
    def localTime = getLocalTime(prefix);
    if( localTime ) {
        return LocalDateTime.of(LocalDate.now().plusDays(1), localTime);
    }
    else {
        return null;
    }
}

private LocalDateTime getNextLocalTime(prefix) {
    def today = getLocalTimeToday(prefix);
    if( today && LocalDateTime.now().isAfter(today) ) {
        return getLocalTimeTomorrow(prefix);
    }
    else {
        return today;
    }
}

private LocalTime getLocalTime(prefix) {
    def offset = settings["${prefix}Offset"] ?: 0;
    def result;
    switch(settings["${prefix}Time"]) {
        case SUNRISE:
            result = location.sunrise;
            break;
        case SUNSET:
            result = location.sunset;
            break;
        case CUSTOM:
            result = timeToday(settings["${prefix}TimeCustom"]);
            break;
        default:
            return null;
    }
    if( result ) {
        return LocalTime.parse(new SimpleDateFormat("HH:mm:ss").format(result)).plusMinutes(offset);
    }
    else {
        return null;
    }
}

// #endregion

// #region Constants

// UI Elements
@Field static final String trashIcon = "https://raw.githubusercontent.com/MikeBishop/hubitat-holiday/main/images/trash40.png"
@Field static final Map ORDINALS = [
    "1": "First",
    "2": "Second",
    "3": "Third",
    "4": "Fourth",
    "5": "Fifth",
    "-1": "Last"
]

@Field static final Map TIME_OPTIONS = [
    sunrise: "Sunrise",
    sunset:  "Sunset",
    custom:  "A specific time..."
];

@Field static final Map SPECIALS = [
    "easter": "Easter",
    "passover": "Passover",
    "roshHashanah": "Rosh Hashanah",
    // Others to be added later
]
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

@Field static final String ORDINAL = "ordinal";
@Field static final String FIXED = "fixed";
@Field static final String SPECIAL = "special";

@Field static final String SUNRISE = "sunrise";
@Field static final String SUNSET = "sunset";
@Field static final String CUSTOM = "custom";

// Can't be constants because they reference other fields, but effectively constants.
private Map GetHolidayByID(int id) {
    final Map RedWhiteAndBlue = [type: RANDOM, colors:[COLORS["Red"], COLORS["White"], COLORS["Blue"]]];
    final List MasterHolidayList = [
        // 0
        [name: "Presidents Day", endDate: [type: ORDINAL, month: Month.FEBRUARY, weekday: DayOfWeek.MONDAY, ordinal: 3], settings: RedWhiteAndBlue],
        // 1
        [name: "St. Patrick's Day", endDate: [type: FIXED, month: Month.MARCH, day: 17], settings: [ type: STATIC, colors: [COLORS["Green"]]]],
        // 2
        [name: "Memorial Day", endDate: [type: ORDINAL, month: Month.MAY, weekday: DayOfWeek.MONDAY, ordinal: -1], settings: RedWhiteAndBlue],
        // 3
        [name: "Pride Month", startDate: [type: FIXED, month: Month.JUNE, day: 1],
            endDate: [type: FIXED, month: Month.JUNE, day: 30],
            settings: [ type: SEQUENTIAL,
                colors: [COLORS["Red"], COLORS["Orange"], COLORS["Yellow"],
                        COLORS["Green"], COLORS["Blue"], COLORS["Purple"]]
            ]
        ],
        // 4
        [name: "Juneteenth", endDate: [type: FIXED, month: Month.JUNE, day: 19], settings: RedWhiteAndBlue],
        // 5
        [name: "Independence Day", endDate: [type: FIXED, month: Month.JULY, day: 4], settings: RedWhiteAndBlue],
        // 6
        [name: "Labor Day", endDate: [type: ORDINAL, month: Month.SEPTEMBER, weekday: DayOfWeek.MONDAY, ordinal: 1], settings: RedWhiteAndBlue],
        // 7
        [name: "Veterans Day", endDate: [type: FIXED, month: Month.OCTOBER, day: 11], settings: RedWhiteAndBlue],
        // 8
        [name: "Halloween", endDate: [type: FIXED, month: Month.OCTOBER, day: 31], settings: [ type: STATIC,
            colors: [COLORS["Orange"], COLORS["Indigo"]]]],
        // 9
        [name: "Thanksgiving Day", endDate: [type: ORDINAL, month: Month.NOVEMBER, weekday: DayOfWeek.THURSDAY, ordinal: 4],
            settings: [ type: STATIC, colors: [COLORS["Orange"], COLORS["White"]]]],
        // 10
        [name: "Christmas", startDate: [type: ORDINAL, month: Month.NOVEMBER, weekday: DayOfWeek.THURSDAY, ordinal: 4, offset: 1],
            endDate: [type: FIXED, month: Month.DECEMBER, day: 26],
            settings: [ type: RANDOM, colors: [COLORS["Red"], COLORS["Green"]]]],
        // 11
        [name: "Valentine's Day", endDate: [type: FIXED, month: Month.FEBRUARY, day: 14],
            settings: [ type: RANDOM, colors: [COLORS["Red"], COLORS["Pink"], COLORS["Raspberry"]]]],
        // 12
        [name: "Easter", endDate: [type: SPECIAL, special: "easter"], settings: [ type: STATIC, colors: [COLORS["White"], COLORS["Amber"]]]],
        // 13
        [name: "Holy Week", startDate: [type: SPECIAL, special: "easter", offset: -7],
            endDate: [type: SPECIAL, special: "easter", offset: -1],
            settings: [ type: STATIC, colors: [COLORS["Purple"]]]],
        // 14
        [name: "Pentecost", endDate: [type: SPECIAL, special: "easter", offset: 50], settings: [ type: STATIC, colors: [COLORS["Red"]]]],
        // 15
        [name: "Epiphany", endDate: [type: FIXED, month: Month.JANUARY, day: 6], settings: [ type: STATIC, colors: [COLORS["Green"]]]],
        // 16
        [name: "Passover", startDate: [type: SPECIAL, special: "passover", offset: -1],
            endDate: [type: SPECIAL, special: "passover", offset: 7],
            settings: [ type: STATIC, colors: [COLORS["Blue"], COLORS["Purple"]]]],
        // 17
        [name: "Rosh Hashanah", endDate: [type: SPECIAL, special: "roshHashanah"],
            settings: [ type: STATIC, colors: [COLORS["Red"], COLORS["White"]]]],
    ];
    return MasterHolidayList[id];
}

private Map GetDefaultHolidays() {
    final Map indices = [
        "United States": [
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
        ],
        "Christian": [
            10, 12, 13, 14, 15
        ],
        "Jewish": [
            16, 17
        ]
    ];
    return indices.collectEntries{
        [it.key, it.value.collect{
            def holiday = GetHolidayByID(it);
            holiday["id"] = it;
            holiday
        }]
    }
}
// #endregion

// #region Utility Methods
private HSVtoRGB(Map hsv) {
    float r, g, b, i, f, p, q, t;
    float s = ( hsv?.saturation ?: 0 ) / 100;
    float v = ( hsv?.level ?: 0 ) / 100;
    float h = ( hsv?.hue ?: 0 ) / 100;
    i = Math.floor(h * 6);
    f = h * 6 - i;
    p = v * (1 - s);
    q = v * (1 - f * s);
    t = v * (1 - (1 - f) * s);
    switch (i % 6) {
        case 0: r = v; g = t; b = p; break;
        case 1: r = q; g = v; b = p; break;
        case 2: r = p; g = v; b = t; break;
        case 3: r = p; g = q; b = v; break;
        case 4: r = t; g = p; b = v; break;
        case 5: r = v; g = p; b = q; break;
    }
    return "#" +
        [ r, g, b ].collect{
            def num = (int) Math.round(it * 255);
            String.format("%02x", num)
        }.join();
}

private static easterForYear(int Y) {
        float A, B, C, P, Q, M, N, D, E;

        // All calculations done
        // on the basis of
        // Gauss Easter Algorithm
        //
        // Taken from https://www.geeksforgeeks.org/how-to-calculate-the-easter-date-for-a-given-year-using-gauss-algorithm/
        A = Y % 19;
        B = Y % 4;
        C = Y % 7;
        P = (float)Math.floor(Y / 100);
        Q = (float)Math.floor(
            (13 + 8 * P) / 25);
        M = (15 - Q + P - P / 4) % 30;
        N = (4 + P - P / 4) % 7;
        D = (19 * A + M) % 30;
        E = (2 * B + 4 * C + 6 * D + N) % 7;
        int days = (int)(22 + D + E);

        // A corner case,
        // when D is 29
        if ((D == 29) && (E == 6)) {
            return LocalDate.of(Y, 4, 19);
        }
        // Another corner case,
        // when D is 28
        else if ((D == 28) && (E == 6)) {
            return LocalDate.of(Y, 4, 18);
        }
        else {

            // If days > 31, move to April
            // April = 4th Month
            if (days > 31) {
                return LocalDate.of(Y, 4, days-31);
            }
            // Otherwise, stay on March
            // March = 3rd Month
            else {
                return LocalDate.of(Y, 3, days);
            }
        }
}

private static LocalDate passoverForYear(int Y) {
    // Taken from https://webspace.science.uu.nl/~gent0113/easter/easter_text2a.htm
    int MH, DH;
    float A,B,P,S,Q,R,C,DMH;

    A = (12*Y + 12) % 19;
    B = Y % 4;
    S = (5*(1979335 - 313*Y)+(765433 * A))/492480 + B/4;
    Q = (float) Math.floor(S);
    R = S - Q;
    C = (Q + 3*Y + 5*B + 1) % 7;

    float diff_jg = (float) Math.floor(Y/100) - (float) Math.floor(Y/400)-2;

    DMH = Q + diff_jg + 92;
    P = 0;
    if((C == 2) || (C == 4) || (C == 6)) P=1;           // because of Adu
    if((C == 1) && (A > 6) && (R > 1366/2160)) P=2;     // because of Gatarad
    if((C == 0) && (A > 11) && (R > 23268/25920)) P=1;  // because of Batu Thakpad

    DMH += P;
    MH = (int) Math.floor((DMH - 62)/30.6);
    DH = (int) Math.floor(DMH - 62 - 30.6*MH) + 1;
    MH += 2;

    return LocalDate.of(Y, MH, DH);
}

private static LocalDate roshHashanahForYear(int year) {
    // Taken from https://quasar.as.utexas.edu/BillInfo/ReligiousCalendars.html
    int Y = year - 1900;
    int G = year % 19 + 1;
    int S = (11*G - 6) % 30;

    double nPlusFrac = 6.057778996 + 1.554241797*((12*G) % 19) + 0.25*(Y%4) - 0.003177794*Y;

    int N = (int) Math.floor(nPlusFrac);
    double fraction = nPlusFrac - N;

    LocalDate tentative = LocalDate.of(year, Month.SEPTEMBER, N);

    switch(tentative.getDayOfWeek()) {
        case DayOfWeek.SUNDAY:
        case DayOfWeek.WEDNESDAY:
        case DayOfWeek.FRIDAY:
            return tentative.plusDays(1);
        case DayOfWeek.MONDAY:
            return tentative.plusDays((fraction > 0.898 && (12 * G) % 19 > 11) ? 1 : 0);
        case DayOfWeek.TUESDAY:
            return tentative.plusDays((fraction > 0.633 && (12 * G) % 19 > 6) ? 2 : 0);
        default:
            return tentative;
    }
}


// #endregion
