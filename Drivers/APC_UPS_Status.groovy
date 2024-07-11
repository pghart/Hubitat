/*
 *  Copyright 2024
 *
 *  Based on the original work done by 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  APC UPS Status Device for Hubitat and APCUPSD dockerized
 *
 */


metadata {
    definition (name: "APC UPS Status Device", namespace: "shrug", author: "Patrick Hartford") {
        capability "Refresh"
        capability "Battery"
        capability "ContactSensor"
        
        attribute "Status", "string"
        attribute "LoadPercent", "number"
//        attribute "bCharge", "number"
        attribute "TimeLeft", "string"
    }
    
    preferences {
        input name: "upsUrl", type: "text", title: "UPS Status URL", description: "Enter the URL for APSUPSD-CGI", required: true
        input name: "refreshInterval", type: "number", title: "Refresh Interval", description: "Enter refresh interval in minutes", defaultValue: 5, required: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    log.debug "Installed..."
    updated()
}

def updated() {
    log.debug "Updated..."
    unschedule()
    schedule("0 */${settings.refreshInterval} * ? * *", refresh)
}

def refresh() {
    logDebug "Refreshing UPS status..."
    fetchAndParseData()
}

def fetchAndParseData() {
    try {
        def params = [
            uri: settings.upsUrl,
            contentType: "text/html"
        ]
        
        httpGet(params) { response ->
            if (response.status == 200) {
                def result = response.data.text()
                parseResult(result)
            } else {
                log.error "HTTP request failed. Status code: ${response.status}"
            }
        }
    } catch (Exception e) {
        log.error "Error fetching data: ${e.message}"
    }
}

def parseResult(String result) {
    def fields = ["STATUS", "LOADPCT", "BCHARGE", "TIMELEFT"]
    def parsedData = [:]
    
    fields.each { field ->
        def pattern = /${field}\s*:\s*(\S+)/
        def matcher = result =~ pattern
        if (matcher.find()) {
            parsedData[field] = matcher.group(1)
        } else {
            log.warn "Field ${field} not found in the result"
        }
    }
    
    logDebug "Parsed data: ${parsedData}"
    updateDeviceAttributes(parsedData)
}

def updateDeviceAttributes(Map parsedData) {
    if (parsedData.STATUS) sendEvent(name: "Status", value: parsedData.STATUS)
    if (parsedData.LOADPCT) sendEvent(name: "LoadPercent", value: parsedData.LOADPCT as BigDecimal, unit: "%")
    if (parsedData.BCHARGE) {
    def bCharge = (parsedData.BCHARGE as BigDecimal).intValue()
    sendEvent(name: "battery", value: bCharge, unit: "%")
    logDebug "Updated Battery: ${bCharge}"
    }
    if (parsedData.TIMELEFT) sendEvent(name: "TimeLeft", value: parsedData.TIMELEFT)
}

def logDebug(msg) {
    if (logEnable) log.debug(msg)
}
