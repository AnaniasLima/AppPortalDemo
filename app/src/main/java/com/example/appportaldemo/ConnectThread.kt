package com.example.appportaldemo

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.IOException
import java.util.HashMap
import android.os.Process

class ConnectThread(val operation:Int, val usbManager : UsbManager, val mainActivity: AppCompatActivity, val myContext: Context) : Thread(),
    UsbSerialInterface.UsbReadCallback {
    private var EVENT_LIST: MutableList<Event> = mutableListOf()
    private var finishThread: Boolean = true
    private var usbSerialDevice: UsbSerialDevice? = null
    var receivedBytes = ByteArray(512)
    var pktInd:Int=0
    var pendingResponse = 0
    var forcaDelay = 0

    companion object {
        var CONNECT = 1
        var DISCONNECT = 0
        val DROP_SAME_COMMAND_TIME_INTERVAL : Long = 100L
        val WAIT_INTER_PACKETS : Long = 30L
        val WAITTIME : Long = 50L
        var isConnected: Boolean  = false
    }

    fun init() {
        Timber.i("Criando Thread ConnectThread")
    }

    private fun mostraNaTela(str:String) {
        ScreenLog.add(LogType.TO_LOG, str)
    }

    private fun mostraEmHistory(str:String) {
        ScreenLog.add(LogType.TO_HISTORY, str)
    }

    /**
     * Create an Event and add in the List of Events to be sent by serial port
     * @return true if the Event was created and able to be sent
     */
    fun requestToSend(eventType: EventType, action: String) : Boolean {
        if ( isConnected && (!finishThread )) {
            val event = Event(eventType = eventType, action = action)
            EVENT_LIST.add(event)
            return true
        }
        return false
    }

    // onde chegam as respostas do Arduino
    override fun onReceivedData(pkt: ByteArray) {
        val tam:Int = pkt.size
        var ch:Byte

        if ( tam == 0) {
            return
        }

        for ( i in 0 until tam) {
            ch  =   pkt[i]
            if ( ch.toInt() == 0 ) break
            if ( ch.toChar() == '{') {
                if ( pktInd > 0 ) {
                    Timber.d("Vai desprezar: ${String(receivedBytes, 0, pktInd)}")
                }
                pktInd = 0
            }
            if ( ch.toInt() in 32..126 ) {
                if (pktInd < (receivedBytes.size - 1)) {
                    receivedBytes[pktInd++] = ch
                    receivedBytes[pktInd] = 0
                    if (ch.toChar() == '}') {
                        if ( receivedBytes[1].toChar() == '@' ) {
                            Timber.e("ARDUINO ==> ${String(receivedBytes, 0, pktInd)}")
                        } else {
                            onCommandReceived(String(receivedBytes, 0, pktInd))
                        }
                        pktInd = 0
                    }
                } else {
                    // ignora tudo
                    pktInd = 0
                }
            }
        }
    }


    fun onCommandReceived(commandReceived: String) {
        pendingResponse = 0
        if ( ArduinoDevice.getLogLevel(FunctionType.FX_RX)   ) {
            ScreenLog.add(LogType.TO_LOG, "RX: ${commandReceived}")
        } else {
            // Só vamos gerar log quando painel de suporte estiver habilitado
            if ( (Config.mainActivity!!).painel_suporte.visibility == View.VISIBLE) {
                Timber.d("RX: ${commandReceived}")
            }
        }

        try {
            val eventResponse = Gson().fromJson(commandReceived, EventResponse::class.java)
            EventType.getByCommand(eventResponse.cmd)?.let {
                eventResponse.eventType = it
                if ( eventResponse.eventType == EventType.FW_NACK ) {
                    Timber.e("===== FW_NACK =====: ${commandReceived}")
                } else {
                    try {
                        ArduinoDevice.onEventResponse(eventResponse)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Timber.e("===== ERRO AO AVALIAR PACOTE =====: ${eventResponse}")
                        mostraEmHistory("ERRO AO AVALIAR PACOTE")
                        return
                    }
                }
            }
        } catch (e: Exception) {
            EventResponse.invalidJsonPacketsReceived++
            Timber.e("===== JSON INVALIDO (%d) =====: ${commandReceived}", EventResponse.invalidJsonPacketsReceived)
            mostraEmHistory("Recebido JSON INVALIDO")
            return
        }
    }



    /**
     * set the thread to finish
     */
    fun finish() {
        finishThread = true
    }

    override fun run() {

        Process.setThreadPriority(10);


        if ( operation ==  DISCONNECT) {
            disconnectInBackground()
        } else {
            if ( connectInBackground() ) {
                finishThread = false
                pendingResponse = 0

                forcaDelay = 3

                ArduinoDevice.requestToSend(EventType.FW_CONFIG, String.format("S,%03d,%03d,%03d,%03d",
                    Config.sensor1DistanciaDetecta,
                    Config.sensor2DistanciaDetecta,
                    Config.sensor3DistanciaDetecta,
                    Config.sensor4DistanciaDetecta))


                ArduinoDevice.requestToSend(EventType.FW_CONFIG, String.format("B,%04d,%04d,%04d,%04d",
                    Config.tempoBomba1,
                    Config.tempoBomba2,
                    Config.tempoBomba3,
                    Config.tempoBomba4))

                ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)

                while ( ! finishThread ) {
                    if ( EVENT_LIST.isEmpty() ) {
                        sleep(WAITTIME)
                    }  else {
                        send(EVENT_LIST[0])
                        EVENT_LIST.removeAt(0)
                        if ( ! EVENT_LIST.isEmpty()) {
                            sleep(WAIT_INTER_PACKETS)
                        }

                        // Eventualmente acontece de mandar pacotes e não receber resposta (não sei por que, ,as acontece)
                        // sendo assim, se mandarmos mais de 2 pacotes e não recebermos resposta, vamos desconectar
                        // e esperar a reconexão automatica
                        if ( pendingResponse++ > 10 ) {
                            // Não estamos recebendo resposta
                            // Vamos desconectar
                            break
                        }

                    }
                }
                disconnectInBackground()
            }
        }

        isConnected = false
    }

    private var lastEventType: EventType = EventType.FW_NACK // We never send this command
    private var lastEventAction: String = ""
    private var lastEventTimestamp: Long = 0L

    private fun send( curEvent: Event) {

//        Timber.i("Send ${curEvent.eventType} action:${curEvent.action}: listSize=${EVENT_LIST.size}")

        try {
            if ( (curEvent.eventType == lastEventType) && (curEvent.action == lastEventAction) ) {
                if ( (curEvent.timestamp - lastEventTimestamp) < DROP_SAME_COMMAND_TIME_INTERVAL )  {
                    Timber.e("@@@ DROP_SAME_COMMAND_TIME_INTERVAL eventType=${curEvent.eventType.toString()} action=${curEvent.action} timestamp=${curEvent.timestamp}")
                    return
                }
            }

            lastEventType = curEvent.eventType
            lastEventAction = curEvent.action
            lastEventTimestamp = curEvent.timestamp

            val pktStr: String = Event.getCommandData(curEvent)

            val startBytes =  byteArrayOf( 2, 2, 2) // STX
            val endBytes =  byteArrayOf( 3, 3, 3) // ETX

            // Pacotes não chegavam no Arduino direito
            if ( forcaDelay > 0) {
                Timber.e("Aguardado delay do pacote ${forcaDelay}")
                forcaDelay--
                sleep(200) // ANANA
            }


            usbSerialDevice?.write(startBytes)
            usbSerialDevice?.write(pktStr.toByteArray())
            usbSerialDevice?.write(endBytes)

            if ( ArduinoDevice.getLogLevel(FunctionType.FX_TX)  ) {
                ScreenLog.add(LogType.TO_LOG, "TX: ${pktStr}")
            } else {
                // Só vamos logar quando painel de suoporte estiver habilitado
                if ( (Config.mainActivity!!).painel_suporte.visibility == View.VISIBLE) {
                    Timber.d("TX: $pktStr")
                }
            }
            // sleep(50)
        } catch (e: Exception) {
            Timber.d("Exception in send: ${e.message} ")
        }
    }


    private fun connectInBackground() : Boolean {

//            var serverAddr = InetAddress.getByName("ananiaslima.brazilsouth.cloudapp.azure.com")
//            var yyy: DataOutputStream
//
//            println( "hostname = $serverAddr.hostName")
//            println( "address = ${serverAddr.address}")
//            println( "hostAddress = ${serverAddr.hostAddress}")
//
//            val connection: Socket = Socket(serverAddr, 3000)
//
//            try  {
//                yyy = DataOutputStream(connection.getOutputStream())
//                yyy.writeUTF("abc\r\n")
//                yyy.writeUTF("def\r\n")
//            } catch (e: IOException) {
//                e.printStackTrace()
//            }
//
//            println( "isConnected = ${connection.isConnected}")
//            sleep(1000)

        isConnected = usbSerialDevice?.isOpen ?: false
        if ( isConnected ) {
            return true
        }

        try {
            val m_device    : UsbDevice? = selectDevice()

            if ( m_device != null ) {
                val m_connection: UsbDeviceConnection? = usbManager.openDevice(m_device)
                ScreenLog.add(LogType.TO_LOG,"vendorId = " + m_device.vendorId.toString())
                ScreenLog.add(LogType.TO_LOG,"productId = " + m_device.productId.toString())

                if (m_connection != null) {
                    Timber.i("Creating usbSerialDevice")
                    usbSerialDevice = UsbSerialDevice.createUsbSerialDevice(m_device, m_connection)
                    if ( usbSerialDevice != null ) {
                        Timber.i("Opening usbSerialDevice")
                        if ( usbSerialDevice!!.open()) {
                            usbSerialDevice!!.setBaudRate(115200)
                            usbSerialDevice!!.read( this )
                        }
                    } else {
                        ScreenLog.add(LogType.TO_LOG,"can´t create usbSerialDevice. createUsbSerialDevice(m_device, m_connection) Failure.")
                        Timber.e("can´t create usbSerialDevice. createUsbSerialDevice(m_device, m_connection) Failure.")
                    }
                } else {
                    ScreenLog.add(LogType.TO_LOG,"can´t create m_connection. openDevice(m_device) Failure.")
                    Timber.e("can´t create m_connection. openDevice(m_device) Failure.")
                }
            }
        } catch ( e: IOException) {
            usbSerialDevice = null
        }

        isConnected = usbSerialDevice?.isOpen ?: false

        if ( isConnected ) {
            ScreenLog.add(LogType.TO_LOG,"CONECTADO COM SUCESSO")
//            ArduinoDevice.usbSerialImediateChecking(300)
        }

        return isConnected
    }


    private fun disconnectInBackground() {
        isConnected = false
        if ( usbSerialDevice != null) {
            Timber.i("-------- disconnectInBackground Inicio")
            if ( usbSerialDevice!!.isOpen )  {
                usbSerialDevice!!.close()
            }
            usbSerialDevice = null
            Timber.i("-------- disconnectInBackground Fim")
            ArduinoDevice.usbSerialImediateChecking(100)
        }
    }

//    private fun selectDevice(vendorRequired:Int) : UsbDevice? {
//        var selectedDevice: UsbDevice? = null
//        val deviceList : HashMap<String, UsbDevice>? = usbManager.deviceList
//        if ( !deviceList?.isEmpty()!!) {
//            var device: UsbDevice?
//            println("Device list size: ${deviceList.size}")
//            deviceList.forEach { entry ->
//
//                device = entry.value
//                val deviceVendorId: Int = device!!.vendorId
//                ScreenLog.add(LogType.TO_LOG,"Device localizado. Vendor:" + deviceVendorId.toString() + "  productId: " + device!!.productId + "  Name: " + device!!.productName)
//                Timber.i("Device Vendor.Id: %d",  deviceVendorId)
//                if ( (vendorRequired == 0) || (deviceVendorId == vendorRequired) ) {
//
//                    if ( ! usbManager.hasPermission(device)) {
//                        ScreenLog.add(LogType.TO_LOG,"=============== Device Localizado NAO tem permissao")
//                        val intent: PendingIntent = PendingIntent.getBroadcast(myContext, 0, Intent(ArduinoDevice.ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT)
//                        usbManager.requestPermission(device, intent)
//                    } else {
//                        ScreenLog.add(LogType.TO_LOG,"=============== Device Localizado TEM permissao")
//                        ScreenLog.add(LogType.TO_LOG,"Device Selecionado")
//                        Timber.i("Device Selected")
//                        selectedDevice = device
//                        return selectedDevice
//                    }
//                }
//            }
//        } else {
//            ScreenLog.add(LogType.TO_LOG,"No serial device connected")
//            Timber.i("No serial device connected")
//        }
//        return selectedDevice
//    }


    private fun selectDevice() : UsbDevice? {
        val deviceList = usbManager.deviceList
        val deviceIterator = deviceList.values.iterator()
        var usbCommDevice: UsbDevice? = null

        var device: UsbDevice?
        while (deviceIterator.hasNext() && (usbCommDevice == null) ) {
            device = deviceIterator.next()
            val count = device!!.interfaceCount
            for (i in 0 until count) {
                val intf = device.getInterface(i)
                mostraNaTela("ZZZ intf.interfaceClass=${intf.interfaceClass}")
                if ( intf.interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_COMM  ) {
                    mostraNaTela("ZZZ vai executar usbManager.requestPermission(device, permissionIntent)")
//                    usbManager.requestPermission(device, permissionIntent)
                    usbCommDevice = device
                    break
                }
            }
        }

        return usbCommDevice
    }

}