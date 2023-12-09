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
        section() {
            paragraph "This app manages holiday lighting on RGB lights. " +
            "It will turn on the lights during the time selected for the holiday. " +
            "It can also turn on the lights at the time selected for " +
            "non-holiday illumination. These time periods can overlap."
        }
        section() {
            input "thisName", "text", title: "Name this instance", submitOnChange: true
            if(thisName) app.updateLabel("$thisName")

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
            deviceSelector();
        }
        section("Advanced") {
            input "suspendSwitch", "capability.switch",
                title: "Switch to pause all instructions to lights"
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

        section() {
            paragraph "During the time selected for holiday lights, the colors " +
            "selected for any current holiday will be shown. If multiple holidays " +
            "overlap, the shortest holiday will be shown."

            paragraph "Single-day holidays display the night before and night " +
            "of the selected date. Multi-day holdays start " +
            "and end as indicated."
        }

        section("Devices and Times") {
            selectStartStopTimes("holiday", "Display holiday lights");

            input "switchesForHoliday", "capability.switch", multiple: true,
                title: "Other switches to turn on when holiday lights are active"

            input "frequency", "enum", title: "Update Frequency",
                options: FREQ_OPTIONS, required: true
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
                            if( colorSettings["type"] == RANDOM ) {
                                app.updateSetting("holiday${i}" + STAGGER, true);
                            }
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
    if( params?.holidayIndex != null ) {
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
        section() {
            paragraph "Select the colors and display options for ${name}."

            def floatFreq = Double.parseDouble(frequency);
            def freqString = "${(int) floatFreq} minutes";
            if( floatFreq <= 1 ) {
                freqString = "${(int) (floatFreq * 60)} seconds";
            }
            paragraph "Static means that the colors will be applied to the lights once " +
            "and will not change.  Otherwise, a new set of colors will " +
            "be applied to the lights every ${freqString}. " +
            "Random shuffles the colors between lights each time, attempting to avoid repeats. " +
            "Sequential means that the colors will advance through the colors strictly in order."
        }
        section("Display Options") {
            displayOptions("holiday${i}");
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
            drawColorSection("holiday${i}", color, index);
        }

        section("") {
            input "testHoliday${i}", "button", title: "Test Holiday", submitOnChange: true
            input "addColorToHoliday${i}", "button", title: "Add Color", submitOnChange: true
        }
        debug("Finished with pageColorSelect");
    }
}

Map illuminationConfig() {
    debug("Rendering illuminationConfig");
    dynamicPage(name: "illuminationConfig", title: "Illumination Configuration") {
        section() {
            paragraph "During the times and modes set below, the lights will be on, " +
            "either all the time or only when sensors indicate activity. You " +
            "can choose how the lights should behave when there's activity and " +
            "when there's not."

            paragraph "If the selected time overlaps with holiday times, the " +
            "holiday settings will take precedence unless activity is detected."

            paragraph PICKER_JS, width:1;
        }
        section("Control Switch") {
            input "illuminationSwitch", "capability.switch",
                title: "Switch to control/reflect illumination state",
                submitOnChange: true
        }
        section("Illumination timing") {
            selectStartStopTimes("illumination", "Illumination");
        }

        def anySensors = motionTriggers || contactTriggers || lockTriggers;
        def anyTriggers = anySensors || illuminationSwitch;
        section("Activity Sensors") {
            input "motionTriggers", "capability.motionSensor",
                title: "Motion sensors to trigger lights when active",
                multiple: true, submitOnChange: true
            input "contactTriggers", "capability.contactSensor",
                title: "Contact sensors to trigger lights when open",
                multiple: true, submitOnChange: true
            input "lockTriggers", "capability.lock",
                title: "Locks to trigger lights when unlocked",
                multiple: true, submitOnChange: true

            if( anySensors ) {
                input "duration", "number", title: "How many minutes to stay illuminated after sensor activity stops?"
            }
        }
        def untriggeredMode = settings["untriggeredIlluminationMode"];
        if( anyTriggers ) {
            section("Lights When " + (anySensors ? "Activity Detected" : "Switched On")) {
                getIlluminationConfig("triggered", false);
                input "otherIlluminationSwitches", "capability.switch",
                    title: "Other switches to turn on", multiple: true
            }
        }
        else if( untriggeredMode == null || untriggeredMode == OFF ) {
            app.updateSetting("untriggeredIlluminationMode",
                state.deviceIndices.collect{settings["device${it}"]}*.
                    hasCapability("ColorTemperature").any{ a -> a } ?
                    CT : RGB);
        }

        section("Lights " + (anyTriggers ? "When Idle" : "During Illumination Period")) {
            getIlluminationConfig("untriggered", anyTriggers);
        }
    }
}

private void getIlluminationConfig(String prefix, Boolean allowOff) {
    def devices = state.deviceIndices.collect{settings["device${it}"]};
    def areLightsCT = devices*.hasCapability("ColorTemperature");
    def anyCT = areLightsCT.any{ a -> a };
    def anyNonCT = areLightsCT.any{ a -> !a };
    def options = [];
    def mode;
    if( allowOff ) {
        options.add(OFF);
    }
    if( anyCT ) {
        options.add(CT);
    }
    options.add(RGB);

    if( options.size() == 1 ) {
        mode = options[0];
        app.updateSetting("${prefix}IlluminationMode", options[0]);
    }
    else if (options.size() == 2 ) {
        def proxyKey = "${prefix}IlluminationModeProxy";
        mode = settings[proxyKey] ? options[1] : options[0];
        app.updateSetting("${prefix}IlluminationMode", mode);
        input proxyKey, "bool", defaultValue: false, submitOnChange: true,
            title: maybeBold(options[0], mode == options[0]) +
                " or " +
                maybeBold(options[1], mode == options[1])
    }
    else {
        mode = settings["${prefix}IlluminationMode"];
        input "${prefix}IlluminationMode", "enum", title: "Illumination Mode",
            options: options, submitOnChange: true, defaultValue: options[0],
            required: true
    }

    if( mode == CT ) {
        input "${prefix}ColorTemperature", "number", title: "Color temperature", width: 6, required: true, defaultValue: "2700"
        input "${prefix}Level", "number", title: "Brightness", width: 6, range: "1..100", required: true, defaultValue: "100"
        if( anyNonCT ) {
            paragraph "Note: Some lights do not support color temperature, so they will be turned off."
        }
    }
    else if( mode == RGB ) {
        drawPicker("${prefix}IlluminationColor");
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
            input "${prefix}${it}TimeOffset", "number", title: "${it} offset in minutes:", width: 6, range: "-240:240"
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
    [state.colorIndices, state.imported].each{
        it?.findAll{
            !state.holidayIndices.contains(Integer.parseInt(it.key))
        }.each{ k,v ->
            it.remove(k);
        }
    };
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
    state.appType = HOLIDAY;
    state.nextHolidayIndex = state.nextHolidayIndex ?: 0;
    state.holidayIndices = state.holidayIndices ?: [];
    state.nextDeviceIndex = state.nextDeviceIndex ?: 0;
    state.deviceIndices = state.deviceIndices ?: [];
    debug("Initialize.... ${state.nextHolidayIndex.inspect()} and ${state.holidayIndices.inspect()}")

    updateSettings();
}

void updateSettings() {
    ["colorTemperature", "level", "illuminationColor"].each {
        if( settings[it] ) {
            app.updateSetting("triggered" + it[0].toUpperCase() + it[1..-1], settings[it]);
            app.removeSetting(it);
        }
    }
}

// #region Event Handlers

void beginStateMachine() {
    debug("Begin state machine");
    unsubscribe();
    unschedule();
    state.test = false;
    state.currentHoliday = null;
    state.sequentialIndex = null;

    subscribe(suspendSwitch, "switch", "determineNextLightMode");

    // Basic subscriptions -- subscribe to switch changes and schedule begin/end
    // of other periods.
    if( illuminationSwitch?.currentValue("switch") == "off" ) {
        subscribe(illuminationSwitch, "switch.on", "beginIlluminationPeriod");
        state.lockIllumination = false;
    }
    else {
        subscribe(illuminationSwitch, "switch.off", "turnOffIllumination");
        // Leave lockIllumination however it already is.
    }

    if( duringIlluminationPeriod() ) {
        manageTriggerSubscriptions(true, false, "triggerIllumination");
        state.illuminationMode = anyIlluminationTriggers();
    }
    if( duringHolidayPeriod() ) {
        def possibleHoliday = getCurrentOrNextHoliday();
        state.currentHoliday = isDuringHoliday(possibleHoliday) ? possibleHoliday : null;
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

    // Figure out where we go from here.
    determineNextLightMode();
}

private testHoliday(index) {
    state.currentHoliday = index;
    state.test = true;
    turnOffIllumination();
    beginHolidayPeriod();
    if( settings["holiday${currentHoliday}Display"] != STATIC ) {
        [15, 30, 45].each {
            runIn(it, "conditionalLightUpdate", [overwrite: false]);
        }
    }
    runIn(60, "beginStateMachine");
}

private onModeChange(evt) {
    debug("Mode changed to ${location.getMode().toString()}");
    if( duringHolidayPeriod() ) {
        beginHolidayPeriod();
    }
    else {
        endHolidayPeriod();
    }

    if( duringIlluminationPeriod() ) {
        beginIlluminationPeriod();
    }
    else {
        endIlluminationPeriod();
    }
}

private beginHolidayPeriod() {
    debug("Begin holiday period");
    state.currentHoliday = state.currentHoliday ?: getCurrentOrNextHoliday();
    if( state.currentHoliday == null ) {
        debug("No holiday is active");
        // This will call determineNextLightMode() for us.
        endHolidayPeriod();
    }
    else {
        // This will start the lights if the state is set, provided no triggers
        // pre-empt them.
        determineNextLightMode();
    }
}

private conditionalLightUpdate() {
    def currentHoliday = state.currentHoliday;
    if( currentHoliday != null ) {
        debug("Do light update");
        doLightUpdate(
            state.deviceIndices.collect{ "device${it}" },
            state.colorIndices["${currentHoliday}"],
            "holiday${currentHoliday}"
        )
    }
}

private endHolidayPeriod() {
    debug("Not in holiday period");
    state.currentHoliday = null;
    state.lastColors = null;
    unscheduleLightUpdate();
    determineNextLightMode();
}

private unscheduleLightUpdate() {
    unschedule("conditionalLightUpdate");
    unschedule("runHandler");
    unschedule("setColor");
}

private beginIlluminationPeriod(event = null) {
    if( illuminationSwitch?.getDeviceNetworkId() != null && event?.device?.getDeviceNetworkId() == illuminationSwitch?.getDeviceNetworkId() ) {
        if( !state.illuminationMode ) {
            state.lockIllumination = true;
        }
        else {
            debug("Ignoring duplicate switch trigger");
            return;
        }
    }

    debug("Begin illumination period" + (event ? " after ${event.device} sent ${event.value}" : ""));
    // Subscribe to the triggers
    manageTriggerSubscriptions(true, false, "triggerIllumination");

    state.illuminationMode = anyIlluminationTriggers();
    determineNextLightMode();
}

private anyIlluminationTriggers() {
    if( state.lockIllumination ) {
        return true;
    }

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
    else {
        debug("Not ending illumination period; still active");
    }
}

private triggerIllumination(event = null) {
    debug("Illumination triggered" + (event ? " after ${event.device} sent ${event.value}" : ""));
    if( suspendSwitch?.currentValue("switch") == "on" ) {
        // Stop doing anything
        debug("Suspend switch active; ignoring triggers until it turns off.");
        return;
    }

    state.illuminationMode = true;

    // Turn on the illumination switch, but stop listening to on
    unsubscribe(illuminationSwitch, "switch.on");
    illuminationSwitch?.on();
    subscribe(illuminationSwitch, "switch.off", "turnOffIllumination");

    applyIlluminationSettings("triggered");
    otherIlluminationSwitches*.on();

    manageTriggerSubscriptions(false, true, "checkIlluminationOff");
    unschedule("turnOffIllumination");
    unscheduleLightUpdate();
}

private determineNextLightMode(event = null) {
    updateSettings();
    def isHoliday = state.currentHoliday != null && duringHolidayPeriod();
    def isIllumination = duringIlluminationPeriod();
    def isTriggered = state.illuminationMode;

    if( suspendSwitch?.currentValue("switch") == "on" ) {
        // Stop doing anything
        debug("Suspend switch active; doing nothing until it turns off.");
        unscheduleLightUpdate();
        return;
    }

    debug("Determine next light mode: holiday=${isHoliday}, illumination=${isIllumination}, triggered=${isTriggered}");
    if( isIllumination && isTriggered ) {
        triggerIllumination();
        checkIlluminationOff();
    }
    else
    {
        illuminationSwitch?.off();
        if ( isHoliday ) {
            if( state.test || isDuringHoliday(state.currentHoliday) ) {
                debug("Holiday is active");

                // We're going to start the display; unless it's static,
                // schedule the updates.
                def handlerName = "conditionalLightUpdate";
                scheduleHandler(handlerName, frequency,
                    settings["holiday${currentHoliday}Display"] != STATIC &&
                        !state.test
                );
                switchesForHoliday*.on();
            }
            else {
                debug("Holiday is not active");
                endHolidayPeriod();
            }
        }
        else if ( isIllumination ) {
            applyIlluminationSettings("untriggered");
        }
        else {
            lightsOff();
        }
    }
}

private applyIlluminationSettings(String prefix) {
    def mode = settings["${prefix}IlluminationMode"];
    def devices = state.deviceIndices.collect{ settings["device${it}"] };
    def ctDevices = devices.findAll { it.hasCapability("ColorTemperature")};
    debug("CT-capable devices: ${ctDevices.inspect()}");
    def rgbOnlyDevices = devices.minus(ctDevices);
    debug("RGB-only devices: ${rgbOnlyDevices.inspect()}");

    if( mode == null ) {
        if( prefix == "triggered" ) {
            mode = (
                    ( ctDevices.size() >= devices.size() / 2) &&
                    settings["triggeredColorTemperature"] != null) ?
                CT : RGB;
        }
        else {
            mode = OFF;
        }
    }
    debug("Illumination mode for ${prefix}: ${mode}");

    switch( mode ) {
        case CT:
            def colorTemperature = settings["${prefix}ColorTemperature"];
            def level = settings["${prefix}Level"];
            if( colorTemperature == null ) {
                warn("No color temperature set for ${prefix} illumination; defaulting to 2700");
                colorTemperature = 2700;
            }
            if( level == null ) {
                warn("No level set for illumination; defaulting to 100");
                level = 100;
            }
            debug("Setting color temperature to ${colorTemperature}K and level to ${level}%");
            ctDevices*.setColorTemperature(colorTemperature, level);
            rgbOnlyDevices*.off();
            break;
        case RGB:
            def colorMap = null;
            def illuminationColor = settings["${prefix}IlluminationColor"];
            if( illuminationColor != null ) {
                try {
                    colorMap = evaluate(illuminationColor);
                }
                catch(Exception ex) {
                    error(ex);
                }
            }

            if( colorMap == null ) {
                warn("No color set for illumination; defaulting to white");
                colorMap = COLORS["White"];
            }
            debug("Setting color to ${colorMap.inspect()}");
            devices*.setColor(colorMap);
            break;
        case OFF:
            devices*.off();
            break;
        default:
            error("Unknown illumination mode: ${mode}");
            break;
    }
}

private checkIlluminationOff(event = null) {
    debug("Checking if illumination is still triggered" + (event ? " after ${event.device} sent ${event.value}" : ""));
    if( !anyIlluminationTriggers() ) {
            debug("No sensor activity detected, switching to untriggered in ${duration} minutes");
            manageTriggerSubscriptions(false, true);
            runIn((duration ?: 0) * 60, "turnOffIllumination");
    }
}

private turnOffIllumination(event = null) {
    if( illuminationSwitch && event?.device?.getDeviceNetworkId() == illuminationSwitch.getDeviceNetworkId() ) {
        if( state.illuminationMode ) {
            state.lockIllumination = false;
        }
        else {
            debug("Ignoring duplicate switch trigger");
            return;
        }
    }
    debug("Illumination not triggered" + (event ? " after ${event.device} sent ${event.value}" : ""));
    state.illuminationMode = false;

    unschedule("turnOffIllumination");
    unsubscribe(illuminationSwitch, "switch.off");
    subscribe(illuminationSwitch, "switch.on", "beginIlluminationPeriod");

    manageTriggerSubscriptions(!duringIlluminationPeriod(), true);
    determineNextLightMode();
}

private dateIsBetweenInclusive(date, start, end) {
    return start && end && (date?.isEqual(start) || date?.isAfter(start)) &&
        (date?.isBefore(end) || date?.isEqual(end));
}

private getHolidayDates(index) {
    def today = LocalDate.now()
    def thisYear = today.getYear();
    def nextYear = thisYear + 1;
    def lastYear = thisYear - 1;

    def startDate = holidayDate(index, "Start", thisYear);
    def endDate = holidayDate(index, "End", thisYear);

    if( !startDate || !endDate ) {
        // If calculations failed, don't try to do any fixups.
        return [];
    }

    // If the end date is before the start date, it crosses the year boundary.
    if( endDate.isBefore(startDate) ) {
        // Either it started last year and ends this year...
        if( today.isBefore(endDate) ) {
            startDate = holidayDate(index, "Start", lastYear);
        }
        // ...or it starts this year and ends next year.
        else {
            endDate = holidayDate(index, "End", nextYear);
        }
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

private manageTriggerSubscriptions(active, inactive, handler = null) {
    def triggerTypes =
        [["motion", "active", "inactive"],
         ["contact", "open", "closed"],
         ["lock", "unlocked", "locked"]];

    triggerTypes.each{
        def type = it[0];
        def activeEvent = "${type}.${it[1]}";
        def inactiveEvent = "${type}.${it[2]}";
        if( handler ) {
            if( active ) this.subscribe(settings["${type}Triggers"], activeEvent, handler);
            if( inactive ) this.subscribe(settings["${type}Triggers"], inactiveEvent, handler);
        }
        else {
            if( active ) this.unsubscribe(settings["${type}Triggers"], activeEvent);
            if( inactive ) this.unsubscribe(settings["${type}Triggers"], inactiveEvent);
        }
    }
}

private scheduleSunriseAndSunset(event = null) {
    def sunrise = [];
    def sunset = [];
    def now = new Date();
    if( event ) {
        debug("Event ${event.name} says ${event.value}");
    }
    if( event?.name == "sunriseTime" ) {
        sunrise.add(toDateTime(event.value));
    }
    else if( event?.name == "sunsetTime" ) {
        sunset.add(toDateTime(event.value));
    }
    else {
        // No event; do everything.
        sunrise += getLocationEventsSince("sunriseTime", now - 2, [max: 2]).
            collect { toDateTime(it?.value) };

        sunset += getLocationEventsSince("sunsetTime", now - 2, [max: 2]).
            collect { toDateTime(it?.value) };

        debug("Got sunrise: ${sunrise} and sunset: ${sunset} from location events");
    }
    // Sunrise/sunset just changed, so schedule the upcoming events...
    PREFIX_AND_HANDLERS.each {
        def prefix = it[0];
        def handler = it[1];
        def targetTime = settings["${prefix}Time"];
        if ( [SUNRISE, SUNSET].contains(targetTime) ) {
            def offset = settings["${prefix}TimeOffset"] ?: 0;
            def times = targetTime == SUNRISE ? sunrise : sunset;

            times.each {
                // Apply offset
                def scheduleFor = Date.from(it.toInstant().plus(Duration.ofMinutes(offset)));

                if( scheduleFor.after(now) ) {
                    debug("Scheduling ${prefix} for ${scheduleFor} (${targetTime} with ${offset} minutes offset)");
                    runOnce(scheduleFor, handler, [overwrite: false]);
                }
            }
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
            debug("Scheduling ${handler} for ${time} (custom)");
            schedule(time, handler);
        }
    }
}

// #endregion

// #region Time Helper Functions

private isDuringHoliday(currentHoliday) {
    def dates = getHolidayDates(currentHoliday);
    def startTime = LocalDateTime.of(dates[0], getLocalTime("holidayStart") ?: LocalTime.MIDNIGHT);
    def endTime = LocalDateTime.of(dates[1], getLocalTime("holidayStop") ?: LocalTime.MAX);
    def now = LocalDateTime.now();

    return now.isAfter(startTime) && now.isBefore(endTime);
}

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
    return duringPeriod("illumination") || state.lockIllumination;
}

private Boolean duringPeriod(prefix) {
    def beginTime = getLocalTimeToday("${prefix}Start");
    def endTime = getLocalTimeToday("${prefix}Stop");
    def activeModes = settings["${prefix}Modes"];

    if( activeModes?.contains(location.getMode().toString()) ) {
        return true;
    }

    if( !beginTime || !endTime ) {
        if( !activeModes ) {
            debug("No ${prefix} time set; ${beginTime} - ${endTime}");
        }
        return false;
    }

    def reverseResults = endTime < beginTime;
    if( reverseResults ) (beginTime, endTime) = [endTime, beginTime];

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

private LocalDateTime getNextLocalTime(prefix) {
    def today = getLocalTimeToday(prefix);
    if( today && LocalDateTime.now().isAfter(today) ) {
        return today.plusDays(1);
    }
    else {
        return today;
    }
}

private LocalTime getLocalTime(prefix) {
    def offset = settings["${prefix}TimeOffset"] ?: 0;
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
@Field static final String ORDINAL = "ordinal";
@Field static final String FIXED = "fixed";
@Field static final String SPECIAL = "special";

@Field static final String SUNRISE = "sunrise";
@Field static final String SUNSET = "sunset";
@Field static final String CUSTOM = "custom";

@Field static final String OFF = "Off";
@Field static final String CT = "Color Temperature";
@Field static final String RGB = "RGB Color";

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

#include evequefou.color-tools
