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
//import static java.time.temporal.TemporalAdjusters.*;
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
}

Map mainPage() {
    dynamicPage(name: "mainPage", title: "Holiday Lighting", install: true, uninstall: true) {
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
            int index = 0;
            for(index = 0; settings["device${index}"] != null; index++) {
                // Show all previously selected devices
                input "device${index}", "capability.colorControl", title: "RGB light ${index}", multiple: false, submitOnChange: true
            }
            input "device${index}", "capability.colorControl", title: "RGB light ${index}", multiple: false, submitOnChange: true
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
        log.debug "Indices are ${state.holidayIndices}"
        def numHolidays = state.holidayIndices.size();
        if( numHolidays ) {
            for( int j = 0; j < numHolidays; j++) {
                int i = state.holidayIndices[j];
                def title = "${settings["holiday${i}Name"]}"

                section(hideable: true, hidden: true, title) {
                    /* TODO:  Show color list here */
                    href(
                        name: "editHoliday${i}",
                        page: "pageEditHoliday",
                        title: "Edit ${settings["holiday${i}Name"]}",
                        description: StringifyDate(i),
                        params: [holidayIndex: i]
                    )
                    input "deleteHoliday${i}", "button", title: "Delete", submitOnChange: true
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
                        app.updateSetting("holiday${i}Name", [type: "text", value: source.name])
                        app.updateSetting("holiday${i}Span", [type: "bool", value: source.startDate != null])
                        ["Start", "End"].each {
                            def key = it.toLowerCase() + "Date"
                            if( source[key] ) {
                                app.updateSetting("holiday${i}${it}Type", [type: "enum", value: source[key].type])
                                if( source[key].type != "special" ) {
                                    if( source[key].type == "ordinal" ) {
                                        app.updateSetting("holiday${i}${it}Ordinal", [type: "enum", value: source[key].ordinal])
                                        app.updateSetting("holiday${i}${it}Weekday", [type: "enum", value: source[key].weekday])
                                    }
                                    else {
                                        //Fixed
                                        app.updateSetting("holiday${i}${it}Day", [type: "number", value: source[key].day])
                                    }
                                    app.updateSetting("holiday${i}${it}Month", [type: "enum", value: Month.of(source[key].month)])
                                }
                                else {
                                    app.updateSetting("holiday${i}${it}Special", [type: "enum", value: source[key].special])
                                }
                                app.updateSetting("holiday${i}${it}Offset", [type: "number", value: source[key].offset])
                            }
                        }
                        def indices = state.holidayIndices;
                        indices.add(i);
                        state.holidayIndices = indices;
                        state.nextHolidayIndex += 1;
                        state.numHolidays = i;
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
    Integer index
    if( params.holidayIndex != null ) {
        index = params.holidayIndex
        state.editingHolidayIndex = index
    }
    else {
        index = state.editingHolidayIndex
        log.warn "Unexpected contents of params: ${params}"
    }

    if( index == state.nextHolidayIndex ) {
        state.nextHolidayIndex += 1;
        state.holidayIndices.add(index)
    }

    def formatter = DateTimeFormatter.ofPattern("MMMM");
    def monthOptions = Month.values().collectEntries { [it, LocalDate.now().with(it).format(formatter)] }
    formatter = DateTimeFormatter.ofPattern("EEEE");
    def dayOptions = DayOfWeek.values().collectEntries { [it, LocalDate.now().with(it).format(formatter)]}

    def i = index // Will use this indirection to sort later
    def name = settings["holiday${i}Name"] ?: "New Holiday"
    dynamicPage(name: "pageEditHoliday", title: "Edit ${name}") {
        section("Holiday definition") {
            input "holiday${i}Name", "text", title: "Name", required: true
            input "holiday${i}Span", "bool", defaultValue: false, title: "Date range? (If false, lights show the night before and night of the holiday.)", submitOnChange: true
        }

        def dates = ["End"]
        if( settings["holiday${i}Span"] ) {
            dates.push("Start")
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
                            options: ORDINALS, required: true
                        input "holiday${i}${date}Weekday", "enum", title: "Which day?",
                            options: dayOptions, width: 5, required: true
                        paragraph "\nof", width: 2
                    }
                    input "holiday${i}${date}Month", "enum", title: "Month", options: monthOptions,
                        submitOnChange: true, width: 5, required: true
                    if( settings["holiday${i}${date}Type"] == "fixed" && settings["holiday${i}${date}Month"] ) {
                        def numDays = Month.valueOf(unarray(settings["holiday${i}${date}Month"])).length(true)
                        input "holiday${i}${date}Day", "number", title: "Date", range:"1..${numDays}",
                            width: 5, required: true
                    }
                }
                else if (settings["holiday${i}${date}Type"] == "special" ) {
                    input "holiday${i}${date}Special", "enum", options: SPECIALS, required: true
                }
                input "holiday${i}${date}Offset", "number", title: "Offset (optional)", range:"-60..60"
            }
        }
    }
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
    settings.keySet().find{ it.startsWith("holiday${index}") }.each {
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
                if( ![null, "null", "0", 0].contains(offset) ) {
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
    app.updateSetting()
    state.nextHolidayIndex = state.nextHolidayIndex ?: 0
    state.holidayIndices = state.holidayIndices ?: []
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
    "Navy Blue": [hueColor: 61, saturation: 100],
    "Blue": [hueColor: 65, saturation: 100],
    "Green": [hueColor: 33, saturation: 100],
    "Turquoise": [hueColor: 47, saturation: 100],
    "Aqua": [hueColor: 50, saturation: 100],
    "Amber": [hueColor: 13, saturation: 100],
    "Yellow": [hueColor: 17, saturation: 100],
    "Safety Orange": [hueColor: 7, saturation: 100],
    "Orange": [hueColor: 10, saturation: 100],
    "Indigo": [hueColor: 73, saturation: 100],
    "Purple": [hueColor: 82, saturation: 100],
    "Pink": [hueColor: 90.78, saturation: 67.84],
    "Rasberry": [hueColor: 94, saturation: 100],
    "Red": [hueColor: 0, saturation: 100],
    "Brick Red": [hueColor: 4, saturation: 100]
];


// [name: "", startDate: {year -> }, colors: []]
// or [name: "", startDate: {year -> }, endDate: {year -> } colors: []]
private Map GetDefaultHolidays() {
    final List RedWhiteAndBlue = [COLORS["Red"], COLORS["White"], COLORS["Blue"]];
    return [
        "United States": [
            [name: "Presidents Day", endDate: [type: "ordinal", month: 2, weekday: DayOfWeek.MONDAY, ordinal: 3], colors: RedWhiteAndBlue],
            [name: "St. Patrick's Day", endDate: [type: "fixed", month: 3, day: 17], colors: [COLORS["Green"]]],
            [name: "Memorial Day", endDate: [type: "ordinal", month: 5, weekday: DayOfWeek.MONDAY, ordinal: -1], colors: RedWhiteAndBlue],
            [name: "Pride Month", startDate: [type: "fixed", month: 6, day: 1],
                endDate: [type: "fixed", month: 6, day: 30],
                colors: [COLORS["Red"], COLORS["Orange"], COLORS["Yellow"],
                        COLORS["Green"], COLORS["Indigo"], COLORS["Purple"]]
            ],
            [name: "Juneteenth", endDate: [type: "fixed", month: 6, day: 19], colors: RedWhiteAndBlue],
            [name: "Independence Day", endDate: [type: "fixed", month: 7, day: 4], colors: RedWhiteAndBlue],
            [name: "Labor Day", endDate: [type: "ordinal", month: 9, weekday: DayOfWeek.MONDAY, ordinal: 1], colors: RedWhiteAndBlue],
            [name: "Veterans Day", endDate: [type: "fixed", month: 9, day: 11], colors: RedWhiteAndBlue],
            [name: "Halloween", endDate: [type: "fixed", month: 10, day: 31],
                colors: [COLORS["Orange"], COLORS["Indigo"]]],
            [name: "Thanksgiving Day", endDate: [type: "ordinal", month: 11, weekday: DayOfWeek.THURSDAY, ordinal: 4],
                colors: [COLORS["Orange"], COLORS["White"]]],
            [name: "Christmas", startDate: [type: "ordinal", month: 11, weekday: DayOfWeek.THURSDAY, ordinal: 4, offset: 1],
                endDate: [type: "fixed", month: 12, day: 26],
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
            return new GregorianCalendar(Y, 4, 19);
        }
        // Another corner case,
        // when D is 28
        else if ((D == 28) && (E == 6)) {
            return new GregorianCalendar(Y, 4, 18);
        }
        else {

            // If days > 31, move to April
            // April = 4th Month
            if (days > 31) {
                return new GregorianCalendar(Y, 4, days-31);
            }
            // Otherwise, stay on March
            // March = 3rd Month
            else {
                return new GregorianCalendar(Y, 3, days);
            }
        }
}