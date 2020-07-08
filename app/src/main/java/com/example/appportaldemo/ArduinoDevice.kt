package com.example.appportaldemo

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Handler
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber


enum class FunctionType {
    FX_RX,
    FX_TX
}


@SuppressLint("StaticFieldLeak")
object ArduinoDevice {

    private const val USB_SERIAL_REQUEST_INTERVAL = 30000L
    private const val USB_SERIAL_TIME_TO_CONNECT_INTERVAL = 10000L

    var mainActivity: AppCompatActivity? = null
    var appContext: Context? = null
    var usbManager  : UsbManager? = null

    private var usbSerialRequestHandler = Handler()
    private var connectThread: ConnectThread? = null
    private var rxLogLevel = 0
    private var txLogLevel = 0


    fun start(activity: AppCompatActivity, context: Context) {
        mainActivity = activity
        appContext = context
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        val filter = IntentFilter()

        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)

        context.registerReceiver(usbEventsReceiver, filter)

        usbSerialImediateChecking(200)
    }

    private val usbEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            mostraNaTela("usbEventsReceiver recebendo uma notificacao de: action=${action}")

            if ( UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                ArduinoDevice.connect()
            }

            if ( UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                ArduinoDevice.disconnect()
            }
        }
    }

    private fun mostraNaTela(str:String) {
        ScreenLog.add(LogType.TO_LOG, str)
    }

    private fun mostraEmHistory(str:String) {
        ScreenLog.add(LogType.TO_HISTORY, str)
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
            delayToNext = USB_SERIAL_TIME_TO_CONNECT_INTERVAL
        }

        usbSerialRequestHandler.removeCallbacks(usbSerialRunnable)
        usbSerialRequestHandler.postDelayed(usbSerialRunnable, delayToNext)
    }

    fun usbSerialImediateChecking(delayToNext: Long) {
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
            EventType.FW_DEMO,
            EventType.FW_RESTART,
            EventType.FW_STATUS_RQ -> {
                CleaningMachine.processReceivedResponse(eventResponse)
            }
            EventType.FW_EJECT,
            EventType.FW_CONFIG,
            EventType.FW_LED,
            EventType.FW_ALARM -> {
//                Timber.i("${eventResponse.eventType} =====> ${eventResponse.toString()}")
            }
            else -> {
                println("===> Falta tratar resposta para comando ${eventResponse.eventType} ")
            }
        }
    }


    fun connect() {

        if ( ConnectThread.isConnected ) {
            mostraNaTela("connect: Já esta conectado.")
            return
        }

        mostraNaTela("Verificando conexão...")

        if ( usbManager != null ) {
            if ( usbManager!!.deviceList.size > 0  ) {
                mostraNaTela("Criando ConnectThread...")
                connectThread = ConnectThread(ConnectThread.CONNECT, usbManager!!, mainActivity!!, appContext!!)
                if (connectThread != null ) {
                    Timber.i("ConnectThread criada com sucesso.")
                    connectThread!!.start()

                    Thread.sleep(3000) // para esperar a reconfiguração do arduino // TODO_ANANA

                    if ( ! CleaningMachine.isStateMachineRunning() ) {
                        if ( CleaningMachine.startStateMachine() ) {
                            mainActivity?.runOnUiThread {
//                                WaitingMode.enterWaitingMode(VideoFase.WAITING_PEOPLE)
                                (mainActivity as MainActivity).btnStateMachine.text = "Stop\nFSM"
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