/*
    Holiday Lighting Manager
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field
import java.util.GregorianCalendar;
import java.time.DayOfWeek;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.time.LocalDate;
import static java.time.temporal.TemporalAdjusters.*;
import java.text.*;

definition (
    name: "Holiday Lighting", namespace: "evequefou", author: "Mike Bishop", description: "Themed light shows on RGB lights on holidays",
    importUrl: "TBD",
    category: "My Apps",
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
                ]

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
            )
            href(
                name: "holidaySelectionHref",
                page: "holidayDefinitions",
                title: "Holiday Displays",
            )

        }
    }
}

Map deviceSelection() {
    dynamicPage(name: "deviceSelection", title: "Devices to Use") {
        section("Devices for Holiday Display") {
            def key;
            def displayIndex = 0
            log.debug "Device indices are ${state.deviceIndices}"
            def deviceIndices = state.deviceIndices.clone();
            for( def index in deviceIndices ) {
                key = "device${index}";
                log.debug "settings[${key}] is ${settings[key]}";
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
    }
}

Map holidayDefinitions() {
    dynamicPage(name: "holidayDefinitions", title: "Configure Holiday Displays") {
        sortHolidays()
        if( !state.colorIndices ) {
            log.debug "Creating colorIndices"
            state.colorIndices = [:];
        }

        section("Devices and Times") {
            input "holidayStartTime", "enum", title: "Start holiday lights at...",
                width: 6, options: TIME_OPTIONS, submitOnChange: true
            input "holidayStopTime", "enum", title: "Stop holiday lights at...",
                width: 6, options: TIME_OPTIONS, submitOnChange: true
            if( startTime == CUSTOM || stopTime == CUSTOM ) {
                if( startTime == CUSTOM ) {
                    input "startTimeCustom", "time", title: "Specify start time:",
                    width: 6
                }
                else {
                    paragraph "", width: 6
                }
                if( stopTime == CUSTOM ) {
                    input "stopTimeCustom", "time", title: "Specify stop time:", width: 6
                }
                else {
                    paragraph "", width: 6
                }
            }
            input "switchesForHoliday", "capability.switch", multiple: true,
                title: "Other switches to turn on when holiday lights are active"
        }

        log.debug "Indices are ${state.holidayIndices.inspect()}"
        log.debug "Colors are ${state.colorIndices.inspect()}"
        state.holidayIndices?.each { int i ->
            def title = "${settings["holiday${i}Name"]}"
            section(hideable: true, hidden: true, title) {
                // List colors here!
                def colorDescription = "No colors yet. Add some!"
                def colorsForThisHoliday = state.colorIndices["${i}"];
                log.debug "Colors for ${i} are ${colorsForThisHoliday}"
                if( colorsForThisHoliday?.size() && colorsForThisHoliday.every {
                    settings["holiday${i}Color${it}"] != null
                } ) {
                    colorDescription = colorsForThisHoliday.collect {
                        log.debug "Color stored as ${settings["holiday${i}Color${it}"]}"
                        def colorMap;
                        try {
                            colorMap = evaluate(settings["holiday${i}Color${it}"].toString());
                        }
                        catch (Exception ex) {
                            log.debug "Rehydration failed with ${ex}"
                        }
                        log.debug "Color rehydrated as ${colorMap}"
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
                    width: 8
                )
                href(
                    name: "editHoliday${i}",
                    page: "pageEditHoliday",
                    title: "Edit ${settings["holiday${i}Name"]} schedule",
                    description: StringifyDate(i),
                    params: [holidayIndex: i],
                    width: 8
                )
                def delete = "<img src='${trashIcon}' width='30' style='float: left; width: 30px; padding: 3px 16px 0 0'>"
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
        log.debug "Finished page"
    }
}

Map pageImport() {
    dynamicPage(name: "pageImport", title: "Holiday Import progress") {
        importSelected.each {
            def alreadyImported = state.imported ?: [:];
            def list = it
            section("Importing ${list}...") {
                try {
                    def holidaysToImport = GetDefaultHolidays()[list];
                    log.debug "Plan to import ${holidaysToImport.size()} from ${holidaysToImport}"
                    holidaysToImport.each {
                        log.debug "Entering holiday parser..."
                        def i = state.nextHolidayIndex;
                        def source = it;
                        log.debug "Attempting ${source.name}"
                        def importSearch = alreadyImported.find{ it.value == source["id"] &&
                                state.holidayIndices.contains(Integer.parseInt(it.key)) }
                        if( importSearch ) {
                            log.debug "${source.name} already exists at index ${importSearch.key}"
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
                                            log.debug "Ordinal, ${source[key].ordinal} ${source[key].weekday.toString()} of ${source[key].month.toString()}"
                                            app.updateSetting("holiday${i}${it}Ordinal", source[key].ordinal.toString())
                                            app.updateSetting("holiday${i}${it}Weekday", source[key].weekday.toString())
                                        }
                                        else {
                                            //Fixed
                                            log.debug "Fixed, ${source[key].month.toString()} ${source[key].day}"
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
                    log.error "${ex} at ${ex.getStackTrace()}"
                    paragraph "Importing failed!"
                }
            }
            state.imported = alreadyImported;
        }
        app.clearSetting("importSelected")
    }
}

def pageEditHoliday(params) {
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
                log.debug "date is ${date}"
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
    }
}

def pageColorSelect(params) {
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


        log.debug "colorIndices is now ${state.colorIndices.inspect()}"
        def colorsForThisHoliday = state.colorIndices["${i}"];
        if( !colorsForThisHoliday ) {
            colorsForThisHoliday = [];
            state.colorIndices["${i}"] = colorsForThisHoliday;
        }
        log.debug "colorsForThisHoliday ${colorsForThisHoliday.inspect()}"

        colorsForThisHoliday.eachWithIndex { color, index ->

            def inputKey = "holiday${i}Color${color}"
            log.debug "Color ${index+1} is ${settings[inputKey]}"

            // For each existing color slot, display four things:
            section("Color ${index+1}") {

                // Map, picker, and presets displayed here
                drawPicker(inputKey, color);

                // And finally a delete button.
                def delete = "<img src='${trashIcon}' width='30' style='float: left; width: 30px; padding: 3px 16px 0 0'>"
                input "deleteHoliday${i}Color${color}", "button", title: "${delete} Delete", submitOnChange: true, width: 3
            }
        }

        section("") {
            input "addColorToHoliday${i}", "button", title: "Add Color", submitOnChange: true
        }
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
    dynamicPage(name: "illuminationConfig", title: "Illumination Configuration") {
        section("Switch Configuration") {
            input "illuminationSwitch", "capability.switch", title: "Switch to control/reflect illumination state"
            input "otherIlluminationSwitches", "capability.switch", title: "Other switches to turn on when triggered", multiple: true
        }
        section("Triggered Configuration") {
            input "illumStartTime", "enum", title: "Allow triggers from...",
                width: 6, options: TIME_OPTIONS, submitOnChange: true
            input "illumStopTime", "enum", title: "Allow triggers until...",
                width: 6, options: TIME_OPTIONS, submitOnChange: true
            if( startTime == CUSTOM || stopTime == CUSTOM ) {
                if( illumStartTime == CUSTOM ) {
                    input "illumStartTimeCustom", "time", title: "Specify start time:", width: 6
                }
                else {
                    paragraph "", width: 6
                }
                if( illumStopTime == CUSTOM ) {
                    input "illumStopTimeCustom", "time", title: "Specify stop time:", width: 6
                }
                else {
                    paragraph "", width: 6
                }
            }
            input "motionTriggers", "capability.motionSensor", title: "Motion sensors to trigger lights", multiple: true
            input "contactTriggers", "capability.contactSensor", title: "Contact sensors to trigger lights", multiple: true
            input "duration", "number", title: "How long to stay illuminated after motion stops / contact is closed?"
        }
        def devices = state.deviceIndices.collect{settings["device${it}"]};
        log.debug "${devices}";
        def areLightsCT = devices*.hasCapability("ColorTemperature");
        log.debug "${areLightsCT}";
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
    }
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
    // log.debug "Finding concrete ${dateType} date for ${i} (${name})"
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
            // log.debug "Fixed ${year}, ${month}, ${date}"
            result = LocalDate.of(year, Month.valueOf(month), date);
            break;
        case ORDINAL:
            def ordinal = settings["${key}Ordinal"]
            def weekday = settings["${key}Weekday"]
            // log.debug "Ordinal ${year}, ${month}, ${ordinal}, ${weekday}"
            result = LocalDate.of(year, Month.valueOf(month), 15).
                with(dayOfWeekInMonth(Integer.parseInt(ordinal), DayOfWeek.valueOf(weekday)));
            break;
        case SPECIAL:
            def special = settings["${key}Special"];
            switch(special) {
                case "easter":
                    result = easterForYear(year);
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
    log.debug "Sorting holidays...."
    def originalList = state.holidayIndices
    def sortedList = originalList.collect{
            [it, holidayDate(it, "Start", thisYear), holidayDate(it, "End", thisYear)]
        }.sort{ a,b ->
            a[2] <=> b[2] ?: a[1] <=> b[1]
        }.collect{it[0]};
    state.holidayIndices = sortedList;
    log.debug "${originalList} became ${sortedList}"
}

void appButtonHandler(btn) {
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
}

private AddColorToHoliday(int holidayIndex) {
    if( !state.nextColorIndices ) {
        state.nextColorIndices = [:];
    }

    def nextColor = state.nextColorIndices["${holidayIndex}"] ?: 0;
    def indicesForHoliday = state.colorIndices["${holidayIndex}"];

    log.debug "indicesForHoliday is ${indicesForHoliday.inspect()}"
    if( indicesForHoliday ) {
        indicesForHoliday.add(nextColor);
        state.colorIndices["${holidayIndex}"] = indicesForHoliday;
    }
    else {
        state.colorIndices["${holidayIndex}"] = [nextColor];
    }
    log.debug "colorIndices is now ${state.colorIndices.inspect()}"
    state.nextColorIndices["${holidayIndex}"] = nextColor + 1;
    return nextColor;
}

private DeleteHoliday(int index) {
    log.debug "Deleting ${index}";
    settings.keySet().findAll{ it.startsWith("holiday${index}") }.each {
        log.debug "Removing setting ${it}"
        app.removeSetting(it);
    }
    state.holidayIndices.removeElement(index);
    state.colorIndices.remove("${index}");
    state.imported.remove("${index}");
}

private DeleteColor(int holidayIndex, int colorIndex) {
    app.removeSetting("holiday${holidayIndex}Color${colorIndex}")
    state.colorIndices["${holidayIndex}"].removeElement(colorIndex);
}

private StringifyDate(int index) {
    def dates = ["End"];
    if( settings["holiday${index}Span"] ) {
        dates.add(0, "Start");
    }
    log.debug "Dates are ${dates}"
    dates.collect {
        try {
            def result = ""
            if( settings["holiday${index}${it}Type"] != SPECIAL ) {
                formatter = DateTimeFormatter.ofPattern("MMMM");
                log.debug "Value of settings[\"holiday${index}${it}Month\"]} is ${settings["holiday${index}${it}Month"]}"
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
                    log.debug "Ordinal is ${ordinal} -> ${ORDINALS[ordinal]}"
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
            log.debug ex
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
	initialize()
}

void installed() {
	initialize()
}

void initialize() {
    if( state.deviceIndices instanceof Boolean ) {
        state.deviceIndices = []
    }
    state.nextHolidayIndex = state.nextHolidayIndex ?: 0;
    state.holidayIndices = state.holidayIndices ?: [];
    state.nextDeviceIndex = state.nextDeviceIndex ?: 0;
    state.deviceIndices = state.deviceIndices ?: [];
    log.debug "Initialize.... ${state.nextHolidayIndex} and ${state.holidayIndices}"
}

void debug(String msg) {
    if( debugSpew ) {
        log.debug msg
    }
}

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
    "easter": "Easter"
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
                        COLORS["Green"], COLORS["Indigo"], COLORS["Purple"]]
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
            settings: [ type: RANDOM, colors: [COLORS["Red"], COLORS["Green"]]]]
    ];
    return MasterHolidayList[id];
}

private Map GetDefaultHolidays() {
    final Map indices = [
        "United States": [
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
        ],
        "Christian": [
            10,
        ],
        "Jewish": [

        ],
        "Satanic Temple": [

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
            log.debug "${num} becomes ${String.format("%02x", num)}"
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
// #endregion
