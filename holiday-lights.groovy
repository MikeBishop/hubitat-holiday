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
// Not yet allowed: import static java.time.temporal.TemporalAdjusters.*;
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
}

Map mainPage() {
    dynamicPage(name: "mainPage", title: "Holiday Lighting", install: true, uninstall: true) {
        initialize();
        section("Options") {
            input "thisName", "text", title: "Name this instance", submitOnChange: true
            if(thisName) app.updateLabel("$thisName")
            def descr = "Choose which devices to automate"
            if(settings["device0"]) {
                descr = "";
                for(int index = 0; settings["device${index}"]; index++) {
                    def device = settings["device${index}"]
                    if( index > 0 ) {
                        descr += ", "
                    }
                    descr += device.getLabel() ?: device.getName()
                }
                descr += " selected"
            }
            href(
                name: "deviceSelectionHref",
                page: "deviceSelection",
                title: "Device Selection",
                description: descr
            )
            href(
                name: "holidaySelectionHref",
                page: "holidayDefinitions",
                title: "Holiday Scheduling",
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
        section("Triggers for Illumination") {
            paragraph "These devices will cause the bulbs to switch to white light temporarily, regardless of holiday settings"
            input "motionTriggers", "capability.motionSensor", title: "Motion sensors", multiple: true
            input "contactTriggers", "capability.contactSensor", title: "Contact sensors", multiple: true
            input "switchTriggers", "capability.switch", title: "Switches", multiple: true
            input "duration", "int", title: "How long to stay illuminated?"
        }
    }
}

Map holidayDefinitions() {
    dynamicPage(name: "holidayDefinitions", title: "Select Holidays to Illuminate") {
        sortHolidays()
        log.debug "Indices are ${state.holidayIndices}"
        def colorNames = COLORS.collect{ it.key };
        def numHolidays = state.holidayIndices.size();
        if( numHolidays ) {
            for( int j = 0; j < numHolidays; j++) {
                int i = state.holidayIndices[j];
                def title = "${settings["holiday${i}Name"]}"

                section(hideable: true, hidden: true, title) {
                    // List colors here!
                    href(
                        name: "selectColors${i}",
                        page: "pageColorSelect",
                        title: "Edit ${settings["holiday${i}Name"]} colors",
                        description: StringifyDate(i),
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
        }
        else {
            section("No Holidays configured") {
                paragraph "You can import holidays or manually add them."
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
            def list = it
            section("Importing ${list}...") {
                try {
                    // Add holidays to list; TODO: only if not already present
                    def holidaysToImport = GetDefaultHolidays()[list];
                    log.debug "Plan to import ${holidaysToImport.size()} from ${holidaysToImport}"
                    holidaysToImport.each {
                        log.debug "Entering holiday parser..."
                        def i = state.nextHolidayIndex;
                        def source = it;
                        log.debug "Attempting ${source.name}"
                        app.updateSetting("holiday${i}Name", source.name)
                        app.updateSetting("holiday${i}Span", source.startDate != null)
                        ["Start", "End"].each {
                            def key = it.toLowerCase() + "Date"
                            if( source[key] ) {
                                app.updateSetting("holiday${i}${it}Type", source[key].type)
                                if( source[key].type != "special" ) {
                                    if( source[key].type == "ordinal" ) {
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
                        def indices = state.holidayIndices;
                        indices.add(i);
                        state.holidayIndices = indices;
                        state.nextHolidayIndex += 1;
                        paragraph "Imported ${source.name}"
                    }
                    paragraph "Finished importing ${list}!"
                }
                catch( Exception ex) {
                    log.debug ex
                    paragraph "Importing failed!"
                }
            }
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
                    "fixed": "Fixed date",
                    "ordinal": "Fixed weekday",
                    "special": "Special days"
                ], submitOnChange: true, required: true
                if( settings["holiday${i}${date}Type"] in ["ordinal", "fixed"] ) {
                    if( settings["holiday${i}${date}Type"] == "ordinal" ) {
                        input "holiday${i}${date}Ordinal", "enum", title: "Which week?",
                            options: ORDINALS, required: true, submitOnChange: true
                        input "holiday${i}${date}Weekday", "enum", title: "Which day?",
                            options: dayOptions, width: 5, required: true, submitOnChange: true
                        paragraph "\nof", width: 2
                    }
                    input "holiday${i}${date}Month", "enum", title: "Month", options: monthOptions,
                        submitOnChange: true, width: 5, required: true
                    if( settings["holiday${i}${date}Type"] == "fixed" && settings["holiday${i}${date}Month"] ) {
                        def numDays = Month.valueOf(unarray(settings["holiday${i}${date}Month"])).length(true)
                        input "holiday${i}${date}Day", "number", title: "Date", range:"1..${numDays}",
                            width: 5, required: true, submitOnChange: true
                    }
                }
                else if (settings["holiday${i}${date}Type"] == "special" ) {
                    input "holiday${i}${date}Special", "enum", options: SPECIALS, required: true, submitOnChange: true
                }
                input "holiday${i}${date}Offset", "number", title: "Offset (optional)", range:"-60..60"
            }
        }
    }
}

def pageColorSelect(params) {
    Integer i
    if( params.holidayIndex != null ) {
        i = params.holidayIndex
        state.editingHolidayIndex = i
    }
    else {
        i = state.editingHolidayIndex
        log.warn "Unexpected contents of params: ${params}"
    }

    if( !state.colorIndices?.containsKey(i) ) {
        if( !state.colorIndices ) {
            state.colorIndices = [i: []];
        }
        else {
            state.colorIndices[i] = [];
        }
    }
    if( !state.nextColorIndices ) {
        state.nextColorIndices = [(i): 0];
    }

    def colorsForThisHoliday = state.colorIndices[i] ?: [];
    def nextColor = state.nextColorIndices[i] ?: 0;
    if( settings["holiday${i}color${nextColor}"] != null ) {
        // User made a selection on the next input
        colorsForThisHoliday.add(nextColor);
        state.colorIndices[i] = colorsForThisHoliday;
        nextColor += 1;
        state.nextColorIndices[i] = nextColor;
    }

    def displayIndex = 0;
    log.debug "colorsForThisHoliday ${colorsForThisHoliday}"
    for(int c = 0; c < colorsForThisHoliday.size(); c++) {
        def d = colorsForThisHoliday[c];
        if( settings["holiday${i}color${d}"] == null ) {
            // User unselected this color
            state.colorIndices[i].removeElement(d);
            app.removeSetting("holiday${i}color${d}");
        }
        else {
            displayIndex += 1;
            // For each existing color slot, display a selector
            def custom = settings["holiday${i}color${d}"] == "Custom"
            input "holiday${i}color${d}", "enum", title: "Color ${displayIndex}",
                multiple: false, options: colorNames, submitOnChange: true,
                required: true, width: custom ? 4 : 8
            if( custom ) {
                input "holiday${i}color${d}.custom", "COLOR_MAP", submitOnChange: true,
                title: "Custom Color ${displayIndex}", required: true, width: 4
            }
            else {
                app.removeSetting("holiday${i}color${d}.custom");
            }
        }
    }

    log.debug "Rendering next color selector"
    // Now supply the input for adding a new one
    displayIndex += 1;
    input "holiday${i}color${nextColor}", "enum", title: "Color ${displayIndex}",
        multiple: false, options: colorNames, submitOnChange: true, required: false
    // Handling "Custom" isn't required, since the page will refresh
    // and render it above.

}

private holidayIsValid(int i) {
    return settings["holiday${i}Name"] && holidayDateIsValid("holiday${i}End") &&
        (!settings["holiday${i}Span"] || holidayDateIsValid("holiday${i}Start"))
}

private holidayDateIsValid(String key) {
    return settings["${key}Type"] in ["ordinal", "fixed", "special"] && (
            (settings["${key}Type"] == "ordinal" && settings["${key}Ordinal"] && settings["${key}Weekday"] ) ||
            (settings["${key}Type"] == "fixed" && settings["${key}Day"]) ||
            (settings["${key}Type"] == "special" && settings["${key}Special"] in SPECIALS.keySet())
        ) && (settings["${key}Type"] == "special" || settings["${key}Month"] != null)
}

private holidayDate(int i, String dateType, int year) {
    def name = settings["holiday${i}Name}"];
    log.debug "Finding concrete ${dateType} date for ${name}"
    if( dateType == "Start" && !settings["holiday${i}Span"]) {
        // For non-Span holidays, the start date is the day before the end date
        return holidayDate(i, "End", year)?.minusDays(1);
    }

    def key = "holiday${i}${dateType}";
    def type = settings["${key}Type"];
    def month = settings["${key}Month"];
    def date = settings["${key}Date"];
    def result;
    switch(type) {
        case "fixed":
            log.debug "Fixed ${year}, ${month}, ${date}"
            result = LocalDate.of(year, Month.valueOf(month), date);
            break;
        case "ordinal":
            def ordinal = settings["${key}Ordinal"]
            def weekday = settings["${key}Weekday"]
            log.debug "Ordinal ${year}, ${month}, ${ordinal}, ${weekday}"
            result = LocalDate.of(year, Month.valueOf(month), 15).
                with(dayOfWeekInMonth(Integer.parseInt(ordinal), DayOfWeek.valueOf(weekday)));
            break;
        case "special":
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
    // For now, this relies on a prohibited function. Don't try to execute until allowed.
    return;

    def thisYear = LocalDate.now().getYear()
    log.debug "Sorting holidays...."
    def originalList = state.holidayIndices
    def sortedList = originalList.collect{
        [it, holidayDate(it, "Start", thisYear), holidayDate(it, "End", thisYear)]
        }.sort{ a,b ->
            def endResult = a[2] <=> b[2];
            if(endResult == 0) {
                return a[1] <=> b[1];
            }
            else {
                return endResult;
            }
        }.collect{it[0]}
    log.debug "${originalList} became ${sortedList}"
}

void appButtonHandler(btn) {
    if( btn.startsWith("deleteHoliday") ) {
        // Extract index
        def index = Integer.parseInt(btn.minus("deleteHoliday"));
        DeleteHoliday(index);
    }
}

private DeleteHoliday(int index) {
    log.debug "Deleting ${index}";
    settings.keySet().findAll{ it.startsWith("holiday${index}") }.each {
        log.debug "Removing setting ${it}"
        app.removeSetting(it);
    }
    state.holidayIndices.removeElement(index);
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
            if( settings["holiday${index}${it}Type"] != "special" ) {
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

                if( settings["holiday${index}${it}Type"] == "ordinal" ) {
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

void handler(evt) {
}

void debug(String msg) {
    if( debugSpew ) {
        log.debug msg
    }
}

@Field static final Map ORDINALS = [
    "1": "First",
    "2": "Second",
    "3": "Third",
    "4": "Fourth",
    "5": "Fifth",
    "-1": "Last"
]

@Field static final Map SPECIALS = [
    "easter": "Easter"
    // Others to be added later
]

@Field static final Map COLORS = [
    "White": [hueColor: 0, saturation: 0],
    "Daylight": [hueColor: 53, saturation: 91],
    "Soft White": [hueColor: 23, saturation: 56],
    "Warm White": [hueColor: 20, saturation: 80],
    "Pink": [hueColor: 90.78, saturation: 67.84],
    "Raspberry": [hueColor: 94, saturation: 100],
    "Red": [hueColor: 0, saturation: 100],
    "Brick Red": [hueColor: 4, saturation: 100],
    "Safety Orange": [hueColor: 7, saturation: 100],
    "Orange": [hueColor: 10, saturation: 100],
    "Amber": [hueColor: 13, saturation: 100],
    "Yellow": [hueColor: 17, saturation: 100],
    "Green": [hueColor: 33, saturation: 100],
    "Turquoise": [hueColor: 47, saturation: 100],
    "Aqua": [hueColor: 50, saturation: 100],
    "Navy Blue": [hueColor: 61, saturation: 100],
    "Blue": [hueColor: 65, saturation: 100],
    "Indigo": [hueColor: 73, saturation: 100],
    "Purple": [hueColor: 82, saturation: 100],
    "Custom": null
];


// [name: "", startDate: {year -> }, colors: []]
// or [name: "", startDate: {year -> }, endDate: {year -> } colors: []]
private Map GetDefaultHolidays() {
    final List RedWhiteAndBlue = [COLORS["Red"], COLORS["White"], COLORS["Blue"]];
    return [
        "United States": [
            [name: "Presidents Day", endDate: [type: "ordinal", month: Month.FEBRUARY, weekday: DayOfWeek.MONDAY, ordinal: 3], colors: RedWhiteAndBlue],
            [name: "St. Patrick's Day", endDate: [type: "fixed", month: Month.MARCH, day: 17], colors: [COLORS["Green"]]],
            [name: "Memorial Day", endDate: [type: "ordinal", month: Month.MAY, weekday: DayOfWeek.MONDAY, ordinal: -1], colors: RedWhiteAndBlue],
            [name: "Pride Month", startDate: [type: "fixed", month: Month.JUNE, day: 1],
                endDate: [type: "fixed", month: Month.JUNE, day: 30],
                colors: [COLORS["Red"], COLORS["Orange"], COLORS["Yellow"],
                        COLORS["Green"], COLORS["Indigo"], COLORS["Purple"]]
            ],
            [name: "Juneteenth", endDate: [type: "fixed", month: Month.JUNE, day: 19], colors: RedWhiteAndBlue],
            [name: "Independence Day", endDate: [type: "fixed", month: Month.JULY, day: 4], colors: RedWhiteAndBlue],
            [name: "Labor Day", endDate: [type: "ordinal", month: Month.SEPTEMBER, weekday: DayOfWeek.MONDAY, ordinal: 1], colors: RedWhiteAndBlue],
            [name: "Veterans Day", endDate: [type: "fixed", month: Month.OCTOBER, day: 11], colors: RedWhiteAndBlue],
            [name: "Halloween", endDate: [type: "fixed", month: Month.OCTOBER, day: 31],
                colors: [COLORS["Orange"], COLORS["Indigo"]]],
            [name: "Thanksgiving Day", endDate: [type: "ordinal", month: Month.NOVEMBER, weekday: DayOfWeek.THURSDAY, ordinal: 4],
                colors: [COLORS["Orange"], COLORS["White"]]],
            [name: "Christmas", startDate: [type: "ordinal", month: Month.NOVEMBER, weekday: DayOfWeek.THURSDAY, ordinal: 4, offset: 1],
                endDate: [type: "fixed", month: Month.DECEMBER, day: 26],
                colors: [COLORS["Red"], COLORS["Green"]]]
        ],
        "Christian": [

        ],
        "Jewish": [

        ],
        "Satanic Temple": [

        ]
    ]
}

// private static weekdayOfMonth(int Y, int M, DayOfWeek day, int number = 1) {
//     def firstOfMonth = new GregorianCalendar(Y, M, 1);
//     def firstDayInstance = firstOfMonth.with(TemporalAdjusters.nextOrSame(day));
//     return firstDayInstance.copyWith(date: firstDayInstance.date + (number - 1) * 7)
// }

// private static lastWeekdayOfMonth(int Y, int M, DayOfWeek day) {
//     def firstOfMonth = new GregorianCalendar(Y, M, 1);
//     def lastOfMonth = firstOfMonth.with(TemporalAdjusters.lastDayOfMonth());
//     return lastOfMonth.with(TemporalAdjusters.previousOrSame(day));
// }

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

@Field static final String trashIcon = "https://raw.githubusercontent.com/MikeBishop/hubitat-holiday/main/images/trash40.png"