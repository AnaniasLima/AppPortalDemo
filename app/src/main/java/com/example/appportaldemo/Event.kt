package com.example.appportaldemo

import org.json.JSONObject
import java.util.*

data class Event(
    var eventType: EventType = EventType.FW_STATUS_RQ,
    var action: String = QUESTION,
    var timestamp: Long = Date().time) {    // TODO: Acho que podemos sumir com este campo

    companion object {
        val ON = "on"
        val OFF = "off"
        val QUESTION = "question"
        val RESET = "reset"
        var pktNumber: Int = 0
        val SIMULA5REAIS  = "simula5"
        val SIMULA10REAIS = "simula10"
        val SIMULA20REAIS = "simula20"
        val SIMULA50REAIS = "simula50"

        fun getCommandData(event: Event): String {
            val commandData = JSONObject()
            ++pktNumber

            commandData.put("cmd", event.eventType.command)

            if (event.eventType == EventType.FW_PINPAD) { // Mateus porque FW_PINPAD ?
                if (event.action == ON) {
                    commandData.put("state", 1)
                } else {
                    commandData.put("state", 0)
                }
            } else {
                commandData.put("action", event.action)
            }

            commandData.put("packetNumber", pktNumber)

            val c = Calendar.getInstance()
            val strHora =  String.format("%02d:%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))

            commandData.put("hour", strHora)

            return commandData.toString()
        }
    }
}

data class EventResponse(
    var cmd: String = "",
    var action: String = "",
    var value: Int = 0,
    var error_n: Int = 0,
    var s1: Int = 0,
    var s2: Int = 0,
    var s3: Int = 0,
    var s4: Int = 0,
    var b1: Int = 0,
    var b2: Int = 0,
    var b3: Int = 0,
    var ret: String = "",
    var status:String = "",
    var fsm_state: String = "",
    var success: String = "",
    var R: String = "",
    var G: String = "",
    var B: String = "",
    var tR: String = "",
    var tB: String = "",
    var tG: String = "",
    var packetNumber: String = "",
    var numPktResp: String = "",

    var cordinates: String = "",
    var eventType: EventType = EventType.FW_STATUS_RQ) {
    companion object {
        val OK = "ok"
        val ERROR = "error"
        val BUSY = "busy"
        var invalidJsonPacketsReceived = 0
    }
}

enum class EventType(val type: Int, val command: String) {
    FW_RESTART( 101, "fw_restart"),

    FW_STATUS_RQ(0, "fw_status_rq"),

    FW_PINPAD(1, "fw_pinpad"),
    FW_TABLET_RESET(2, "fw_tablet_reset"),
    FW_PLAY(3, "fw_play"),
    FW_DEMO(4, "fw_demo"),
    FW_BILL_ACCEPTOR(5, "fw_noteiro"),
    FW_LED(6, "fw_led"),
    FW_CALIBRATE(5, "fw_calibrate"),
    FW_SENSOR1( 10, "fw_sensor1"),
    FW_DUMMY(998, "fw_dummy"),

    FW_NACK(999, "fw_nack");

    companion object {
        fun getByCommand(command: String): EventType? {
            for (value in values()) {
                if (value.command == command) {
                    return value
                }
            }
            return null
        }
    }
}
