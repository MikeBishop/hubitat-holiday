/*
    Single Active Switch
    Copyright 2022 Mike Bishop,  All Rights Reserved

*/

metadata {
    definition (name: "Single Active Switch", namespace: "evequefou", author: "Mike Bishop") {
        command "allOff"

    }
    preferences {
        input name: "debugSpew", type: "bool", title: "Enable debug logging", defaultValue: false
    }
}

def fetchChild(String id) {
    String thisId = device.id
    def cd = getChildDevice("${thisId}-${id}")
    if (!cd) {
        cd = addChildDevice("hubitat", "Generic Component Switch", "${thisId}-${id}", [name: "${device.displayName} Switch ${id}", isComponent: true])
        //set initial attribute values
        cd.parse([[name:"switch", value:"off", descriptionText:"set initial switch value"]])
    }
    return cd
}

def removeExcess(List<String> liveIds) {
    def thisId = device.id
    def children = getChildDevices()
    children.each { child ->
        def id = child.getDeviceNetworkId();
        def childId = id.minus("${thisId}-");
        if (!liveIds.contains(childId)) {
            log.debug "Removing child ${childId}"
            deleteChildDevice(childId);
        }
    }
}

//child device methods
void componentOn(cd){
    if (debugSpew) log.info "received on request from ${cd.displayName}"
    def targetId = cd.getDeviceNetworkId();
    getChildDevices().each { child ->
    {
        if( targetId == child.getDeviceNetworkId() ) {
            // Delay turning on the new switch by 1 second to allow the old
            // switch to turn off cleanly.
            runIn(1, "relayToChild", [data: [child: targetId, message: [[name:"switch", value: "on", descriptionText:"switch turned on"]]]])
        }
        else if ( child.currentValue("switch") == "on" ) {
            child.parse([[name:"switch", value: "off", descriptionText:"turned off when ${cd.getLabel() ?: cd.getName()} was turned on"]]);
        }
    }
}

void relayToChild(Map params) {
    def child = getChildDevice(params.child);
    def message = params.message;
    child.parse(message);
}

void componentOff(cd){
    if (logEnable) log.info "received off request from ${cd.displayName}"
    cd.parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])
}

