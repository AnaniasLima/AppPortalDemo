package com.example.appportaldemo

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.provider.Settings.Global.getString
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.felhr.usbserial.UsbSerialDevice
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.util.*


enum class FunctionType {
    FX_RX,
    FX_TX
}


@SuppressLint("StaticFieldLeak")
object ArduinoDevice {

    const val ACTION_USB_PERMISSION = "permission"
    private const val USB_SERIAL_REQUEST_INTERVAL = 30000L
    private const val USB_SERIAL_TIME_TO_CONNECT_INTERVAL = 10000L

    var mainActivity: AppCompatActivity? = null
    var appContext: Context? = null
    var usbManager  : UsbManager? = null

    private var usbSerialRequestHandler = Handler()
    //    private var EVENT_LIST: MutableList<Event> = mutableListOf()
    private var connectThread: ConnectThread? = null
    private var rxLogLevel = 0
    private var txLogLevel = 0


    fun start(activity: AppCompatActivity, context: Context) {
        mainActivity = activity
        appContext = context

        // ArduinoDevice.usbManager = applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        usbSetFilters()
        usbSerialImediateChecking(200)
    }


    private fun mostraNaTela(str:String) {
        ScreenLog.add(LogType.TO_LOG, str)

//        (mainActivity as MainActivity).mostraNaTela(str)
    }

    private fun mostraEmHistory(str:String) {
        ScreenLog.add(LogType.TO_HISTORY, str)

//        (mainActivity as MainActivity).mostraEmHistory(str)
    }


    private var usbSerialRunnable = Runnable {
        if ( ! ConnectThread.isConnected ) {
            mostraNaTela("usbSerialRunnable NAO Conectado")

            mainActivity?.runOnUiThread {
                (mainActivity as MainActivity).btn_aguardando_conexao.visibility = View.VISIBLE
            }

            connect()
        }

        usbSerialContinueChecking()
    }


    fun usbSerialContinueChecking() {
        var delayToNext: Long = USB_SERIAL_REQUEST_INTERVAL


        if ( ! ConnectThread.isConnected ) {
            val c = Calendar.getInstance()
            val strHora =  String.format("%02d:%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))

            delayToNext = USB_SERIAL_TIME_TO_CONNECT_INTERVAL
            mostraNaTela("agendando proximo STATUS_REQUEST para:---" + strHora + " ${delayToNext}ms")
        }

        usbSerialRequestHandler.removeCallbacks(usbSerialRunnable)
        usbSerialRequestHandler.postDelayed(usbSerialRunnable, delayToNext)
    }

    fun usbSerialImediateChecking(delayToNext: Long) {
        val c = Calendar.getInstance()
        val strHora =  String.format("%02d:%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))

        mostraNaTela("agendando STATUS_REQUEST para:---${strHora} + ${delayToNext}ms")

        usbSerialRequestHandler.removeCallbacks(usbSerialRunnable)
        usbSerialRequestHandler.postDelayed(usbSerialRunnable, delayToNext)
    }

    var lastDif = 0

    fun onEventResponse(eventResponse: EventResponse) {

        val dif = eventResponse.packetNumber.toInt() - eventResponse.numPktResp.toInt()
        if ( dif != lastDif) {

            if ( eventResponse.numPktResp.toInt() == 1) {
                Timber.e("========= Arduino resetou ======")
                Event.pktNumber = 1
                mostraEmHistory("*** Arduino resetou")
            } else {
                Timber.e("========= Perdeu pacote ======")
                lastDif = dif
                mostraEmHistory("Perdeu ${lastDif} pacotes (${eventResponse.packetNumber})")
            }

        }

        when ( eventResponse.eventType ) {
            EventType.FW_EJECT -> {
                Timber.e("FW_EJECT =====> ${eventResponse.toString()}")
            }
            EventType.FW_CONFIG -> {
                Timber.e("FW_CONFIG =====> ${eventResponse.toString()}")
            }
            EventType.FW_DUMMY -> {
                Timber.e("FW_DUMMY =====> ${eventResponse.toString()}")
            }
            EventType.FW_DEMO,
            EventType.FW_RESTART,
            EventType.FW_STATUS_RQ -> {
                CleaningMachine.processReceivedResponse(eventResponse)
            }
            EventType.FW_LED -> {
//                Timber.e("FW_LED =====> ${eventResponse.toString()}")
            }
            EventType.FW_ALARM -> {
//                Timber.e("FW_ALARM =====> ${eventResponse.toString()}")
            }
            else -> {
                println("===> Falta tratar resposta para comando ${eventResponse.eventType} ")
            }
        }
    }

    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Timber.i("WWW------------------------- BroadcastReceiver")
            if ( intent != null && usbManager != null) {
                Timber.i("WWW------------------------- intent.action = " + intent.action.toString())
                mostraNaTela("------------------------- intent.action = " + intent.action.toString())
                when (intent.action!!) {
                    ACTION_USB_PERMISSION -> {

                        val granted: Boolean = intent.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                        Timber.i("WWW ACTION_USB_PERMISSION------------------------- Permmissao concedida = ${granted.toString()}")
                        mostraNaTela("WWW ACTION_USB_PERMISSION------------------------- Permmissao concedida = ${granted.toString()}")

                        val granted1: Boolean = intent.extras!!.getBoolean(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                        Timber.i("WWW ACTION_USB_PERMISSION------------------------- Permmissao concedida = ${granted1.toString()}")
                        mostraNaTela("ACTION_USB_PERMISSION------------------------- Permmissao concedida = ${granted1.toString()}")

                        usbSerialImediateChecking(200)


                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        (mainActivity as MainActivity).jaViuUSB = true
                        mostraNaTela("ACTION_USB_DEVICE_ATTACHED")
                        connect()
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        mostraNaTela("ACTION_USB_DEVICE_DETACHED")
                        disconnect()
                    }
                }
            }
        }
    }

    fun connect() {
        mostraNaTela("Verificando conexão...")

        if ( ConnectThread.isConnected ) {
            if ( connectThread == null) {
                throw IllegalStateException("Erro interno 001")
            }
            mostraNaTela("Já estava connectado.")
            return
        }

        if ( usbManager != null ) {
            if ( usbManager!!.deviceList.size > 0  ) {
                mostraNaTela("Tentando connect...")
                connectThread = ConnectThread(ConnectThread.CONNECT, usbManager!!, mainActivity!!, appContext!!)
                if (connectThread != null ) {
                    Timber.i("Startando thread para tratar da conexao")
                    connectThread!!.priority = Thread.MAX_PRIORITY

                    connectThread!!.start()

                    // Vamos mandar pacotes para "esquentar" a conexao
                    connectThread!!.discardCommunicationData(true)
                    ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
                    ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
                    ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
                    Thread.sleep(500)
                    connectThread!!.discardCommunicationData(false)
                    Thread.sleep(500)

                    ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)

                    if ( ! CleaningMachine.isStateMachineRunning() ) {
                        if ( CleaningMachine.startStateMachine() ) {
                            mainActivity?.runOnUiThread {
//                                WaitingMode.enterWaitingMode(VideoFase.WAITING_PEOPLE)
                                (mainActivity as MainActivity).btnStateMachine.text = "Stop\\nFSM"
                            }
                        }
                    }

                    mainActivity?.runOnUiThread {
                        (mainActivity as MainActivity).btn_aguardando_conexao.visibility = View.GONE
                    }


                } else {
                    Timber.e("Falha na criação da thread ")
                }
            }
        }
    }



    fun disconnect() {
        mostraNaTela("Vai verificar usbSerialDevice em disconnect...")
        if ( connectThread != null ) {
            Timber.i("connectThread not null em disconnect vamos chamar finish")
            connectThread!!.finish()
            Timber.i("fazendo connectThread = NULL")
            connectThread = null
        } else {
            Timber.i("Disparando thread para desconectar")
            ConnectThread(ConnectThread.DISCONNECT, usbManager!!, mainActivity!!, appContext!!).start()
        }
    }


    fun requestToSend(eventType: EventType, action: String) : Boolean {
        var ret = false

        if ( ConnectThread.isConnected ) {
            try {
                when(eventType) {
                    EventType.FW_RESTART -> {
                        ret = connectThread!!.requestToSend(eventType, action=action)
                    }
                    EventType.FW_STATUS_RQ -> {
                        ret = connectThread!!.requestToSend(eventType, action=action)
                    }
                    EventType.FW_BILL_ACCEPTOR -> {
                        ret = connectThread!!.requestToSend(eventType, action=action)
                    }
                    EventType.FW_DEMO -> {
                        ret = connectThread!!.requestToSend(eventType, action=action)
                    }
                    EventType.FW_LED -> {
                        ret = connectThread!!.requestToSend(eventType, action=action)
                    }
                    EventType.FW_ALARM -> {
                        ret = connectThread!!.requestToSend(eventType, action=action)
                    }
                    EventType.FW_DUMMY -> {
                        ret = connectThread!!.requestToSend(eventType, action=action)
                    }
                    EventType.FW_CONFIG -> {
                        ret = connectThread!!.requestToSend(eventType, action=action)
                    }
                    EventType.FW_EJECT -> {
                        ret = connectThread!!.requestToSend(eventType, action=action)
                    }
                    else -> {
                        // do nothing
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return ret
    }

    fun usbSetFilters() {
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        appContext!!.registerReceiver(broadcastReceiver, filter)
    }

    private var rxLogEnabled : Boolean = false
    private var txLogEnabled : Boolean = false

    fun logRX(enabled:Boolean) {
        rxLogEnabled = enabled
    }
    fun logTX(enabled:Boolean) {
        txLogEnabled = enabled
    }

    fun getLogLevel(function : FunctionType) : Boolean {
        when ( function) {
            FunctionType.FX_RX -> {
                return  ( rxLogEnabled )
            }
            FunctionType.FX_TX -> {
                return  ( txLogEnabled )
            }
        }
    }


}