package com.example.appportaldemo

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.util.*
import javax.crypto.Mac


enum class MachineState  {
    IDLE,
    RESTART,
    UNKNOWN,
    WAITING_PEOPLE,
    WAITING_THERMOMETER,
    CALL_HELP,
    FEVER_PROCEDURE,
    ALCOHOL_PROCEDURE,
    WAITING_ENTER,
    CLEANING_PROCESS_1,
    CLEANING_PROCESS_2,
    CLEANING_PROCESS_3,
    WAITING_FINISH,
    GRANA_BOLSO;
}

enum class InOut  {
    IN,
    OUT,
    TIMEOUT;
}


enum class CleaningMachineCommand  {
    STATUS_RQ,
    FW_EJECT,
    FW_LED,
    FW_ALARM,
    FW_DEMO;
}

enum class Sensor  {
    PRESENCA,
    ENTRADA,
    SAIDA,
    ALCOHOL;
}

@SuppressLint("StaticFieldLeak")
object CleaningMachine {
    private const val WAIT_WHEN_OFFLINE = 5000L
    private var DEFAULT_TIME_TO_QUESTION = 250L
    private var SELECTED_TIME_TO_QUESTION = DEFAULT_TIME_TO_QUESTION
    private const val WAIT_TIME_TO_RESPONSE = 400L
    private const val MAX_RUN_DEMO_TIMEOUT = 30000L
    private const val FROM_OUT = true
    private const val FROM_EXT = false


    private var mainActivity: AppCompatActivity? = null
    private var appContext: Context? = null

    private var cleaningMachineHandler = Handler()

    private var desiredState: MachineState = MachineState.RESTART
    private var receivedState: MachineState = MachineState.UNKNOWN
    private var countCommandsToDesiredState = 0

    private var inBusyStateCounter = 0

    // TODO: Teoricamente nunca deve acontecer. Vamos criar uma forma de tratar caso fique > 0
    private var inErrorStateCounter = 0


    private var stateMachineRunning = false

    private var runningDemo = false

    var modoManutencaoHabilitado = false

    var questionDelayList = ArrayList<String>()

    var sensorAnalogico1 = 0F
    var sensorAnalogico2 = 0F

    var sensor1Status = 0
    var sensor2Status = 0
    var sensor3Status = 0
    var sensor4Status = 0

    var balanca1Status = 0
    var balanca2Status = 0
    var balanca3Status = 0


    var valorOpcional1 = 0
    var valorOpcional2 = 0
    var valorOpcional3 = 0
    var valorOpcional4 = 0


    var waitingThermometer : Boolean = false
    var temperaturaMedida : Float = 0F
    var flagTimeout = false

    var valorFatura = 0

    var pessoaDentro = false

    var runFaseHandler: Handler = Handler()
    var runFaseRunnable: Runnable = Runnable {
        flagTimeout = true
        when (receivedState ) {
            MachineState.IDLE    -> {}
            MachineState.UNKNOWN -> on_UNKNOW(InOut.TIMEOUT)
            MachineState.RESTART -> on_RESTART(InOut.TIMEOUT)
            MachineState.WAITING_PEOPLE -> on_WAITING_PEOPLE(InOut.TIMEOUT)
            MachineState.WAITING_THERMOMETER -> on_WAITING_THERMOMETER(InOut.TIMEOUT)
            MachineState.CALL_HELP   -> on_CALL_HELP(InOut.TIMEOUT)
            MachineState.ALCOHOL_PROCEDURE -> on_ALCOHOL_PROCEDURE(InOut.TIMEOUT)
            MachineState.FEVER_PROCEDURE -> on_FEVER_PROCEDURE(InOut.TIMEOUT)
            MachineState.WAITING_ENTER -> on_WAITING_ENTER(InOut.TIMEOUT)

            MachineState.CLEANING_PROCESS_1 -> on_CLEANING_PROCESS_1(InOut.TIMEOUT)
            MachineState.CLEANING_PROCESS_2 -> on_CLEANING_PROCESS_2(InOut.TIMEOUT)
            MachineState.CLEANING_PROCESS_3 -> on_CLEANING_PROCESS_3(InOut.TIMEOUT)
            MachineState.WAITING_FINISH     -> on_WAITING_FINISH(InOut.TIMEOUT)
            MachineState.GRANA_BOLSO        -> on_GRANA_BOLSO(InOut.TIMEOUT)
        }
    }



    fun initRunFaseTimer(timeout:Long) {
        if ( timeout > 0L ) {
            runFaseHandler.postDelayed(runFaseRunnable, timeout )
        } else {
            cancelRunFaseTimer()
        }
    }

    fun cancelRunFaseTimer() {
        try {
            flagTimeout = false
            runFaseHandler.removeCallbacks(runFaseRunnable)
        } catch (e: Exception) {
            Timber.i("Ops Exception in cancelRunFaseTimer")
        }
    }



    fun pessoaEmSensor(sensor : Sensor) : Boolean {
        when(sensor) {
            Sensor.PRESENCA -> if ( sensor1Status > 0 ) return true
            Sensor.ENTRADA ->  if ( sensor2Status > 0) return true
            Sensor.SAIDA ->    if ( sensor3Status > 0) return true
            Sensor.ALCOHOL ->  if ( sensor4Status > 0) {
                Timber.e("WWW 002 (pessoaEmSensor) CleaningMachine.sensor4Status esta = ${CleaningMachine.sensor4Status}")
                return true
            }
        }
        return false
    }

    lateinit var bma_mostra_temperatura : Button

    fun start(activity: AppCompatActivity, context: Context) {
        mainActivity = activity
        appContext = context

        bma_mostra_temperatura = (mainActivity as MainActivity).texto_30_superior


//        (mainActivity as MainActivity).btn_decreto

        receivedState = MachineState.UNKNOWN

        questionDelayList.add("Default ${DEFAULT_TIME_TO_QUESTION} ms")
        questionDelayList.add("Question 50 ms")
        questionDelayList.add("Question 100 ms")
        questionDelayList.add("Question 500 ms")
        questionDelayList.add("Question 1000 ms")
        questionDelayList.add("Question 5000 ms")
        questionDelayList.add("Question 10000 ms")
        questionDelayList.add("Question 60000 ms")

    }

    fun startStateMachine() : Boolean {
        if ( ConnectThread.isConnected ) {
            stateMachineRunning = true
            desiredState = MachineState.RESTART
            Timber.e("setei desiredState = ${desiredState} em startStateMachine")

            machineChecking(WAIT_TIME_TO_RESPONSE)

            mainActivity?.runOnUiThread {
                (mainActivity as MainActivity).log_recycler_view.visibility = View.GONE
            }

            WaitingModeThread.newLeaveWaitingMode()
            return true
        } else {
            return false
        }
    }


    fun isStateMachineRunning() : Boolean{
        return stateMachineRunning
    }

    fun stopStateMachine() {
        stateMachineRunning = false
    }


    fun machineChecking(delay: Long) {
        var dropLog = false

        if (stateMachineRunning) {
            var delayToNext = delay

            if (!ConnectThread.isConnected) {
                delayToNext = WAIT_WHEN_OFFLINE
            } else {
                if (delayToNext == 0L) {
                    delayToNext = SELECTED_TIME_TO_QUESTION
                    dropLog = true
                }
            }

            if (!dropLog) {
                val c = Calendar.getInstance()
                val strHora = String.format(
                    "%02d:%02d:%02d",
                    c.get(Calendar.HOUR_OF_DAY),
                    c.get(Calendar.MINUTE),
                    c.get(Calendar.SECOND)
                )
//                Timber.i("agendando deviceChecking ${strHora} + ${delayToNext}ms")
            }

            cleaningMachineHandler.removeCallbacks(machineCheckRunnable)
            cleaningMachineHandler.postDelayed(machineCheckRunnable, delayToNext)
        }
    }

    fun ejetaProduto1() {
        if (  balanca1Status > 0 ) {
            ScreenLog.add(LogType.TO_HISTORY, "Ejetou ALCOOL")
            ArduinoDevice.requestToSend(EventType.FW_EJECT, "1,1")
            valorFatura += 1
        } else {
            ScreenLog.add(LogType.TO_HISTORY, "SEM ALCOOL")
        }
    }

    fun ejetaProduto2() {
        if (  balanca2Status > 0 ) {
            ScreenLog.add(LogType.TO_HISTORY, "Ejetou produto 2")
            ArduinoDevice.requestToSend(EventType.FW_EJECT, "2,1")
            valorFatura += 2
        } else {
            ScreenLog.add(LogType.TO_HISTORY, "SEM produto 2")
        }
    }

    fun ejetaProduto3() {
        if (  balanca3Status > 0 ) {
            valorFatura += 4
            ScreenLog.add(LogType.TO_HISTORY, "Ejetou produto 3")
            ArduinoDevice.requestToSend(EventType.FW_EJECT, "3,1")
        } else {
            ScreenLog.add(LogType.TO_HISTORY, "SEM produto 3")
        }
    }

    fun alarmeFebre(tipo:Int) {
        if ( (Config.alarmeFebre > 0) &&  (Config.alarmeFebre <= 9) ) {
            when (tipo) {
                1 -> {
                    ScreenLog.add(LogType.TO_HISTORY, "Acionando Alarme Febre")
                    ArduinoDevice.requestToSend(EventType.FW_ALARM, "${Config.alarmeFebre},1")
                }
                0 -> {
                    ScreenLog.add(LogType.TO_HISTORY, "Acionando Alarme Febre")
                    ArduinoDevice.requestToSend(EventType.FW_ALARM, "${Config.alarmeFebre},0")
                }
            }
        }
    }

    fun ligaIndicadorSaida(tipo:Int) {

        if ( Config.gerenciaEntradaESaida > 0) {
            when (tipo) {
                0 -> {
                    buttonAdjust((mainActivity as MainActivity).btn_led, true, background = R.drawable.led_white)
                    ScreenLog.add(LogType.TO_HISTORY, "Led saida Off")
                    ArduinoDevice.requestToSend(EventType.FW_LED, "1,7")
                }
                1 -> {
                    buttonAdjust((mainActivity as MainActivity).btn_led, true, background = R.drawable.led_red)
                    ScreenLog.add(LogType.TO_HISTORY, "Led saida VERMELHO")
                    ArduinoDevice.requestToSend(EventType.FW_LED, "1,1")
                }
                2 -> {
                    buttonAdjust((mainActivity as MainActivity).btn_led, true, background = R.drawable.led_green)
                    ArduinoDevice.requestToSend(EventType.FW_LED, "1,2")
                    ScreenLog.add(LogType.TO_HISTORY, "Led saida VERDE")
                }
            }
        }
    }




    private var demoTimeoutResetMachine = Runnable {
        erroFatal("Nao finalizou demo em ${MAX_RUN_DEMO_TIMEOUT}ms")
    }

    fun on_UNKNOW(flag : InOut) {
        Timber.i("on_UNKNOW ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> return
            InOut.OUT -> return
            InOut.TIMEOUT -> return
        }
    }

    fun on_RESTART(flag : InOut) {
        Timber.i("on_RESTART ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> return
            InOut.OUT -> return
            InOut.TIMEOUT -> return
        }
    }

    fun buttonAdjust(btn: Button, flag:Boolean, str:String?=null, background : Int=0) {
        mainActivity?.runOnUiThread {
            if ( str != null ) {
                btn.text = if ( flag ) str else ""
            }
            if ( background > 0 ) {
                btn.setBackgroundResource(background)
            }
            btn.visibility = if ( flag ) View.VISIBLE else View.GONE
            btn.isEnabled = flag
        }
    }

    fun getMedias(state: MachineState ) : ArrayList<Media>? {
        var mediasList: ArrayList<Media>? = null

        when (state) {
            MachineState.IDLE                   -> mediasList = Config.idleMedias
            MachineState.UNKNOWN                -> mediasList = Config.unknownMedias
            MachineState.RESTART                -> mediasList = Config.restartMedias
            MachineState.WAITING_PEOPLE         -> mediasList = Config.waitingPeopleMedias
            MachineState.WAITING_THERMOMETER    -> mediasList = Config.waitingThermometerMedias
            MachineState.CALL_HELP              -> mediasList = Config.helpMedias
            MachineState.FEVER_PROCEDURE        -> mediasList = Config.feverMedias
            MachineState.ALCOHOL_PROCEDURE      -> mediasList = Config.alcoholMedias
            MachineState.WAITING_ENTER          -> mediasList = Config.waitingEnterMedias
            MachineState.CLEANING_PROCESS_1     -> mediasList = Config.cleaningProcess1Medias
            MachineState.CLEANING_PROCESS_2     -> mediasList = Config.cleaningProcess2Medias
            MachineState.CLEANING_PROCESS_3     -> mediasList = Config.cleaningProcess3Medias
            MachineState.WAITING_FINISH         -> mediasList = Config.WaitFinishMedias
            MachineState.GRANA_BOLSO            -> mediasList = Config.granaNoBolsoMedias
        }
        return(mediasList)
    }

    fun aguardando(state: MachineState, indicadorFundo: Button? = null) {

        Timber.e("aguardando fase: ${state} ")

        if ( state == MachineState.IDLE) {
            WaitingModeThread.newLeaveWaitingMode()
//            buttonAdjust((mainActivity as MainActivity).btn_show_full_screen, true, background = R.drawable.full_screen_background )
        } else {
            var mediasList = getMedias(state)
            if ( mediasList != null) {
                buttonAdjust((mainActivity as MainActivity).btn_show_full_screen, false)
                WaitingModeThread.newEnterWaitingMode(state.ordinal, mediasList, indicadorFundo)
            }
        }
    }

    fun on_WAITING_PEOPLE(flag : InOut) {
        Timber.i("ZZ on_WAITING_PEOPLE ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                pessoaDentro = false

                initRunFaseTimer(0L)
                ArduinoDevice.requestToSend(EventType.FW_SENSOR1, Event.ON)

                Timber.i("aguardando(VideoFase.WAITING_PEOPLE) em 111 (on_WAITING_PEOPLE)")
                aguardando(MachineState.WAITING_PEOPLE)
            }

            InOut.OUT,
            InOut.TIMEOUT -> {
                aguardando(MachineState.IDLE)
                if (flag == InOut.OUT) cancelRunFaseTimer()
                ArduinoDevice.requestToSend(EventType.FW_SENSOR1, Event.OFF)
                buttonAdjust((mainActivity as MainActivity).btn_show_full_screen, false)
                Timber.i("aguardando(VideoFase.IDLE) em 222 - on_WAITING_PEOPLE ${flag}")
                ligaIndicadorSaida(0)
            }
        }
    }

    fun on_WAITING_THERMOMETER(flag : InOut) {
        Timber.i("ZZ on_WAITING_THERMOMETER ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                initRunFaseTimer(3500L )
                temperaturaMedida = 0F
                waitingThermometer = true
                aguardando(MachineState.WAITING_THERMOMETER)
            }

            InOut.OUT ,
            InOut.TIMEOUT -> {
                aguardando(MachineState.IDLE)
                Timber.i("vai chamar aguardando(VideoFase.IDLE) em 333 on_WAITING_THERMOMETER ${flag}")

                waitingThermometer = false

                if ( flag == InOut.OUT) {
                    cancelRunFaseTimer()
                } else {
                    temperaturaMedida = sensorAnalogico1

                    if ( temperaturaMedida < 37.3F) {
                        changeCurrentState(MachineState.ALCOHOL_PROCEDURE, FROM_OUT)
                    } else {
                        changeCurrentState(MachineState.FEVER_PROCEDURE, FROM_OUT)
                    }
                }
            }
        }
    }

    fun on_ALCOHOL_PROCEDURE(flag : InOut) {
        var timeout = 30000
        Timber.i("ZZ on_ALCOHOL_PROCEDURE ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                initRunFaseTimer(timeout.toLong() )
//                buttonAdjust((mainActivity as MainActivity).btn_alcohol_dispenser, true, background = R.drawable.alc_gel_on)
                buttonAdjust(bma_mostra_temperatura, true, str=String.format("%.2f°", temperaturaMedida))
                aguardando(MachineState.ALCOHOL_PROCEDURE, bma_mostra_temperatura)
            }

            InOut.OUT,
            InOut.TIMEOUT-> {
                aguardando(MachineState.IDLE)
                buttonAdjust((mainActivity as MainActivity).btn_show_full_screen, true)
//                buttonAdjust((mainActivity as MainActivity).btn_alcohol_dispenser, true, background = R.drawable.alc_gel_off)

                if (flag ==  InOut.TIMEOUT) {
                    changeCurrentState(MachineState.CALL_HELP, FROM_OUT)
                }
            }
        }
    }

    fun on_FEVER_PROCEDURE(flag : InOut) {
        Timber.i("ZZ on_FEVER_PROCEDURE ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                initRunFaseTimer(5000L )
                ligaIndicadorSaida(1)

                buttonAdjust(bma_mostra_temperatura, true, str=String.format("%.2f°", temperaturaMedida))
                aguardando(MachineState.FEVER_PROCEDURE, bma_mostra_temperatura)
                alarmeFebre(1)
            }

            InOut.OUT,
            InOut.TIMEOUT-> {
                aguardando(MachineState.IDLE)
                alarmeFebre(0)
                ligaIndicadorSaida(0)

                buttonAdjust((mainActivity as MainActivity).btn_show_full_screen, true)

                if (flag ==  InOut.TIMEOUT) {
                    changeCurrentState(MachineState.WAITING_PEOPLE, FROM_OUT)
                }

            }
        }
    }

    fun on_WAITING_ENTER(flag : InOut) {
        Timber.e("ZZ on_WAITING_ENTER ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                valorFatura =0
                pessoaDentro = false
                ejetaProduto1()

                if ( Config.gerenciaEntradaESaida == 1) {
                    ligaIndicadorSaida(2)
                }
                desiredState = receivedState
                initRunFaseTimer(10000L )
                aguardando(MachineState.WAITING_ENTER)
            }

            InOut.OUT -> {
                if ( Config.gerenciaEntradaESaida == 1) {
                    ligaIndicadorSaida(0)
                }
                aguardando(MachineState.IDLE)
                cancelRunFaseTimer()
            }

            InOut.TIMEOUT -> {
                if ( Config.gerenciaEntradaESaida == 1) {
                    ligaIndicadorSaida(0)
                }
                aguardando(MachineState.IDLE)
                changeCurrentState(MachineState.WAITING_PEOPLE, FROM_OUT)
            }
        }
    }

    fun on_CLEANING_PROCESS_1(flag : InOut) {
        Timber.e("ZZ on_CLEANING_PROCESS_1 ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                ligaIndicadorSaida(1)
                ejetaProduto2()
                initRunFaseTimer(2000L )
                aguardando(MachineState.CLEANING_PROCESS_1)
            }

            InOut.OUT -> {
                cancelRunFaseTimer()
                aguardando(MachineState.IDLE)
            }

            InOut.TIMEOUT -> {
                aguardando(MachineState.IDLE)
                var nextState = nextCleaningProcess()
                changeCurrentState(nextState, FROM_OUT)
            }
        }
    }

    fun nextCleaningProcess() : MachineState {

        var nextState = receivedState

        if  ( receivedState == MachineState.ALCOHOL_PROCEDURE ) {
            if ( pessoaDentro ) {
                nextState =  MachineState.CLEANING_PROCESS_1
            } else {
                nextState = MachineState.WAITING_ENTER
            }
        } else if ( receivedState == MachineState.WAITING_ENTER ) {
            nextState =  MachineState.CLEANING_PROCESS_1
        } else if ( receivedState == MachineState.CLEANING_PROCESS_1 ) {
            nextState =  MachineState.CLEANING_PROCESS_2
        } else if ( receivedState == MachineState.CLEANING_PROCESS_2 ) {
            nextState =  MachineState.CLEANING_PROCESS_3
        } else if ( receivedState == MachineState.CLEANING_PROCESS_3 ) {
            nextState =  MachineState.WAITING_FINISH
        }

        if ( nextState == MachineState.WAITING_ENTER ) {
            if ((Config.capacidadeReservatorio1 > 0) || (Config.capacidadeReservatorio2 > 0) || (Config.capacidadeReservatorio3 > 0) ) {
                return (MachineState.WAITING_ENTER)
            }
            nextState =  MachineState.CLEANING_PROCESS_1
        }

        if ( nextState ==  MachineState.CLEANING_PROCESS_1 )  {
            if ( Config.capacidadeReservatorio1 > 0) return(MachineState.CLEANING_PROCESS_1)
            nextState =  MachineState.CLEANING_PROCESS_2
        }

        if ( nextState ==  MachineState.CLEANING_PROCESS_2 )  {
            if ( Config.capacidadeReservatorio2 > 0) return(MachineState.CLEANING_PROCESS_2)
            nextState =  MachineState.CLEANING_PROCESS_3
        }

        if ( nextState ==  MachineState.CLEANING_PROCESS_3 )  {
            if ( Config.capacidadeReservatorio3 > 0) return(MachineState.CLEANING_PROCESS_3)
            nextState =  MachineState.WAITING_FINISH
        }


        if ( nextState ==  MachineState.WAITING_FINISH )  {
            if ( pessoaDentro ) {
                return(MachineState.WAITING_FINISH)
            }
        }


        return(MachineState.GRANA_BOLSO)
    }

    fun on_CLEANING_PROCESS_2(flag : InOut) {
        Timber.e("ZZ on_CLEANING_PROCESS_2 ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                ejetaProduto3()
                initRunFaseTimer(2000L )
                aguardando(MachineState.CLEANING_PROCESS_2)
            }

            InOut.OUT -> {
                cancelRunFaseTimer()
                aguardando(MachineState.IDLE)
            }

            InOut.TIMEOUT -> {
                aguardando(MachineState.IDLE)
                var nextState = nextCleaningProcess()
                changeCurrentState(nextState, FROM_OUT)
            }
        }
    }

    fun on_CLEANING_PROCESS_3(flag : InOut) {
        Timber.e("ZZ on_CLEANING_PROCESS_3 ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                initRunFaseTimer(2000L )
                aguardando(MachineState.CLEANING_PROCESS_3)
            }

            InOut.OUT -> {
                cancelRunFaseTimer()
                aguardando(MachineState.IDLE)
            }

            InOut.TIMEOUT -> {
                aguardando(MachineState.IDLE)
                changeCurrentState(MachineState.WAITING_FINISH, FROM_OUT)
            }
        }
    }

    fun on_WAITING_FINISH(flag : InOut) {
        Timber.e("ZZ on_WAITING_FINISH ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                ligaIndicadorSaida(2)
                initRunFaseTimer(10000L )
                aguardando(MachineState.WAITING_FINISH)
                buttonAdjust((mainActivity as MainActivity).btn_money, true)
            }

            InOut.OUT -> {
                cancelRunFaseTimer()
                ligaIndicadorSaida(0)
                aguardando(MachineState.IDLE)
                buttonAdjust((mainActivity as MainActivity).btn_money, false)
            }

            InOut.TIMEOUT -> {
                ligaIndicadorSaida(0)
                aguardando(MachineState.IDLE)

                buttonAdjust((mainActivity as MainActivity).btn_money, false)

                changeCurrentState(MachineState.GRANA_BOLSO, FROM_OUT)
            }
        }
    }


    fun on_GRANA_BOLSO(flag : InOut) {
        Timber.e("ZZ on_GRANA_BOLSO ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                initRunFaseTimer(2000L )
                aguardando(MachineState.GRANA_BOLSO)
                buttonAdjust((mainActivity as MainActivity).btn_money, true, background = R.drawable.dindin)
            }

            InOut.OUT -> {
                cancelRunFaseTimer()
                buttonAdjust((mainActivity as MainActivity).btn_money, true, background = R.drawable.dindin_futuro)
                mainActivity?.runOnUiThread {
                    ScreenLog.add(LogType.TO_HISTORY, "Faturou ${valorFatura}")
                }

            }

            InOut.TIMEOUT -> {
                buttonAdjust((mainActivity as MainActivity).btn_money, true, background = R.drawable.dindin_futuro)
                mainActivity?.runOnUiThread {
                    ScreenLog.add(LogType.TO_HISTORY, "Faturou ${valorFatura}")
                }
                changeCurrentState(MachineState.WAITING_PEOPLE, FROM_OUT)
            }
        }
    }


    fun on_CALL_HELP(flag : InOut) {
        Timber.i("ZZ on_CALL_ATENDENT ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                waitingThermometer = true
                aguardando(MachineState.CALL_HELP)
            }

            InOut.OUT,
            InOut.TIMEOUT -> {
                if (flag == InOut.OUT) cancelRunFaseTimer()
                Timber.i("vai chamar aguardando(VideoFase.IDLE) em 444 on_CALL_HELP ${flag}")
                aguardando(MachineState.IDLE)

            }
        }
    }


    fun onThermometerFinished(temperatura: Float) {

        if ( waitingThermometer && (temperaturaMedida == -1F)) {
            temperaturaMedida = temperatura
            if ( temperatura <= 37F) {
                changeCurrentState(MachineState.ALCOHOL_PROCEDURE, FROM_EXT)
            } else {
                changeCurrentState(MachineState.FEVER_PROCEDURE, FROM_EXT)
            }

        }
    }

    fun changeCurrentState(newState : MachineState, bypassOutProcedure : Boolean = false) {

        countCommandsToDesiredState = 0

        if ( receivedState == newState) {
            return
        }


        buttonAdjust(bma_mostra_temperatura, false)


//        mainActivity?.runOnUiThread {
//            (mainActivity as MainActivity).texto_30_superior.visibility = View.GONE
//            (mainActivity as MainActivity).texto_30_superior.isEnabled = false
//            (mainActivity as MainActivity).texto_30_superior.text="????"
//        }
//

        //---------------------------------------------------------
        // Trata saida do estado atual
        //---------------------------------------------------------
        if (! bypassOutProcedure) {
            when (receivedState ) {
                MachineState.IDLE                   -> {}
                MachineState.UNKNOWN                -> {}
                MachineState.RESTART                -> {}
                MachineState.WAITING_PEOPLE         -> { on_WAITING_PEOPLE(InOut.OUT) }
                MachineState.WAITING_THERMOMETER    -> { on_WAITING_THERMOMETER(InOut.OUT) }
                MachineState.WAITING_ENTER          -> { on_WAITING_ENTER(InOut.OUT) }
                MachineState.CALL_HELP              -> { on_CALL_HELP(InOut.OUT) }
                MachineState.ALCOHOL_PROCEDURE      -> { on_ALCOHOL_PROCEDURE(InOut.OUT) }
                MachineState.FEVER_PROCEDURE        -> { on_FEVER_PROCEDURE(InOut.OUT) }
                MachineState.CLEANING_PROCESS_1     -> { on_CLEANING_PROCESS_1(InOut.OUT) }
                MachineState.CLEANING_PROCESS_2     -> { on_CLEANING_PROCESS_2(InOut.OUT) }
                MachineState.CLEANING_PROCESS_3     -> { on_CLEANING_PROCESS_3(InOut.OUT) }
                MachineState.WAITING_FINISH         -> { on_WAITING_FINISH(InOut.OUT) }
                MachineState.GRANA_BOLSO            -> { on_GRANA_BOLSO(InOut.OUT) }
            }
        }

        receivedState = newState

        //---------------------------------------------------------
        // Trata ENTRADA do estado atual
        //---------------------------------------------------------
        when (receivedState ) {
            MachineState.IDLE                   -> { } // TODO:
            MachineState.UNKNOWN                -> { } // TODO:
            MachineState.RESTART                -> { ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION) }
            MachineState.WAITING_PEOPLE         -> { on_WAITING_PEOPLE(InOut.IN) }
            MachineState.WAITING_THERMOMETER    -> { on_WAITING_THERMOMETER(InOut.IN) }
            MachineState.WAITING_ENTER          -> { on_WAITING_ENTER(InOut.IN) }
            MachineState.CALL_HELP              -> { on_CALL_HELP(InOut.IN) }
            MachineState.ALCOHOL_PROCEDURE      -> { on_ALCOHOL_PROCEDURE(InOut.IN) }
            MachineState.FEVER_PROCEDURE        -> { on_FEVER_PROCEDURE(InOut.IN) }
            MachineState.CLEANING_PROCESS_1     -> { on_CLEANING_PROCESS_1(InOut.IN) }
            MachineState.CLEANING_PROCESS_2     -> { on_CLEANING_PROCESS_2(InOut.IN) }
            MachineState.CLEANING_PROCESS_3     -> { on_CLEANING_PROCESS_3(InOut.IN) }
            MachineState.WAITING_FINISH         -> { on_WAITING_FINISH(InOut.IN) }
            MachineState.GRANA_BOLSO            -> { on_GRANA_BOLSO(InOut.IN) }
        }

    }

    private var machineCheckRunnable = Runnable {

        if ( ! modoManutencaoHabilitado ) {
            machineChecking(0) // A principio agenda nova execução
            if (receivedState == desiredState) {
                ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
            } else {
                Timber.i("desiredState = ${desiredState} em 1111 (${MachineState.RESTART}")
                when (desiredState) {
                    MachineState.RESTART -> {
                        if ( (countCommandsToDesiredState++ % 10) == 0) {
                            ArduinoDevice.requestToSend(EventType.FW_RESTART, Event.RESET)
                            ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
                        }
                    }
                    MachineState.WAITING_PEOPLE,
                    MachineState.WAITING_THERMOMETER,
                    MachineState.CALL_HELP,
                    MachineState.FEVER_PROCEDURE,
                    MachineState.ALCOHOL_PROCEDURE,
                    MachineState.WAITING_ENTER,
                    MachineState.GRANA_BOLSO -> {
                        ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
                    }
                    else -> {
                        println("ATENÇÃO: CCC Situação nao deveria ocorrer. Preciso reavaliara") // TODO: Verificar se vai ocorrer
                    }
                }
            }

        } else {
            machineChecking(0) // A principio agenda nova execução
            ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
        }
    }

    fun erroFatal(str: String?) {
        mainActivity?.runOnUiThread {
            (mainActivity as MainActivity).erroFatal(str)
        }
    }

    fun processReceivedResponse(response: EventResponse) {

        var nextState = receivedState


        if ( modoManutencaoHabilitado ) {
            if  (response.eventType == EventType.FW_STATUS_RQ) {
                sensorAnalogico1 = response.f1 / 10F
                sensorAnalogico2 = response.f2 / 10F

                sensor1Status = response.s1
                sensor2Status = response.s2
                sensor3Status = response.s3
                sensor4Status = response.s4

                balanca1Status = 1
                balanca2Status = 1
                balanca3Status = 1
//                balanca1Status = response.b1
//                balanca2Status = response.b2
//                balanca3Status = response.b3

                valorOpcional1 = response.o1
                valorOpcional2 = response.o2
                valorOpcional3 = response.o3
                valorOpcional4 = response.o4

                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).mostraSensores()
                }
            }
            return
        }



        when (response.eventType) {

            EventType.FW_CONFIG -> {
                // Não faz nada
            }

            EventType.FW_RESTART -> {
                changeCurrentState(MachineState.RESTART, FROM_EXT)
                ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
            }

            EventType.FW_STATUS_RQ -> {
                if (response.ret == EventResponse.BUSY) {
                    // Só responderá BUSY quando estiver em processo de RESTART
                    if ( response.error_n != 0 ) {
                        ScreenLog.add(LogType.TO_HISTORY, "FW_RESTART ERRO")
                        erroFatal("Equipamento com problema FW_RESTART = ERRO")
                    }
                } else {

                    if ( modoManutencaoHabilitado ) {
                        sensorAnalogico1 = response.f1 / 10F
                        sensorAnalogico2 = response.f2 / 10F

                        sensor1Status = response.s1
                        sensor2Status = response.s2
                        sensor3Status = response.s3
                        sensor4Status = response.s4

                        balanca1Status = 1
                        balanca2Status = 1
                        balanca3Status =1
//                        balanca1Status = response.b1
//                        balanca2Status = response.b2
//                        balanca3Status = response.b3

                        valorOpcional1 = response.o1
                        valorOpcional2 = response.o2
                        valorOpcional3 = response.o3
                        valorOpcional4 = response.o4

                        mainActivity?.runOnUiThread {
                            (mainActivity as MainActivity).mostraSensores()
                        }
                        return
                    }

                    mainActivity?.runOnUiThread {
                        (mainActivity as MainActivity).ajustaSensores()
                    }


                    sensorAnalogico1 = response.f1 / 10F
                    sensorAnalogico2 = response.f2 / 10F

                    sensor1Status = response.s1
                    sensor2Status = response.s2
                    sensor3Status = response.s3
                    sensor4Status = response.s4

                    balanca1Status = 1
                    balanca2Status = 1
                    balanca3Status = 1
//                    balanca1Status = response.b1
//                    balanca2Status = response.b2
//                    balanca3Status = response.b3

                    valorOpcional1 = response.o1
                    valorOpcional2 = response.o2
                    valorOpcional3 = response.o3
                    valorOpcional4 = response.o4


                    when (receivedState) {

                        MachineState.IDLE-> {
                            Timber.e("Não deveria chegar em processReceivedResponse com MachineState.IDLE")
                        }

                        MachineState.UNKNOWN-> {
                            desiredState = MachineState.RESTART
                            Timber.e("setei desiredState = ${desiredState} em CleaningMachineState.UNKNOW")
                        }

                        MachineState.RESTART-> {
                            // Situações de erro e busy são são tratadas em EventResponse.BUSY
                            changeCurrentState(MachineState.WAITING_PEOPLE, FROM_EXT)
                        }

                        MachineState.WAITING_PEOPLE -> {
                            if ( pessoaEmSensor(Sensor.PRESENCA) ) {
                                changeCurrentState(MachineState.WAITING_THERMOMETER, FROM_EXT)
                            }
                        }

                        MachineState.WAITING_THERMOMETER -> {
                            if ( ! pessoaEmSensor(Sensor.PRESENCA) ) {
                                changeCurrentState(MachineState.WAITING_PEOPLE, FROM_EXT)
                            }
                        }

                        MachineState.FEVER_PROCEDURE -> {
                            // só sai por timeout
//                            if ( ! pessoaEmSensor(Sensor.PRESENCA) ) {
//                                changeCurrentState(CleaningMachineState.WAITING_PEOPLE, FROM_EXT)
//                            }
                        }

                        MachineState.CALL_HELP -> {
                            if ( ! pessoaEmSensor(Sensor.PRESENCA) ) {
                                changeCurrentState(MachineState.WAITING_PEOPLE, FROM_EXT)
                            }
                        }

                        MachineState.ALCOHOL_PROCEDURE -> {
                            if ( pessoaEmSensor(Sensor.ALCOHOL) ) {
                                Timber.e("WWW 005 testou e disse que tem mão no Alcool vai para WAITING_ENTER")
                                if ( Config.gerenciaEntradaESaida == 0) {
                                    changeCurrentState(MachineState.GRANA_BOLSO, FROM_EXT)
                                } else {
                                    nextState = nextCleaningProcess()
                                    changeCurrentState(nextState, FROM_EXT)
                                }

                            } else {
                                if ( pessoaEmSensor(Sensor.ENTRADA) ) {
                                    if ( Config.gerenciaEntradaESaida == 0) {
                                        changeCurrentState(MachineState.GRANA_BOLSO, FROM_EXT)
                                    } else {
                                        pessoaDentro = true
                                        nextState = nextCleaningProcess()
                                        changeCurrentState(nextState, FROM_EXT)
                                    }
                                }
                            }
                        }

                        MachineState.WAITING_ENTER -> {
                            if ( pessoaEmSensor(Sensor.ENTRADA) ) {
                                if ( Config.gerenciaEntradaESaida <= 1) {
                                    changeCurrentState(MachineState.GRANA_BOLSO, FROM_EXT)
                                } else {
                                    nextState = nextCleaningProcess()
                                    changeCurrentState(nextState, FROM_EXT)
                                    pessoaDentro = true
                                }
                            }
                        }


                        MachineState.CLEANING_PROCESS_1 -> {
                            if (  pessoaEmSensor(Sensor.SAIDA)  ) {
                                pessoaDentro = false
                            }

                            if ( ! pessoaDentro ) {
                                nextState = nextCleaningProcess()
                                changeCurrentState(nextState, FROM_EXT)
                            }
                        }

                        MachineState.CLEANING_PROCESS_2 -> {
                            if (  pessoaEmSensor(Sensor.SAIDA)  ) {
                                pessoaDentro = false
                            }
                            if ( ! pessoaDentro ) {
                                nextState = nextCleaningProcess()
                                changeCurrentState(nextState, FROM_EXT)
                            }
                        }

                        MachineState.CLEANING_PROCESS_3 -> {
                            if (  pessoaEmSensor(Sensor.SAIDA)  ) {
                                pessoaDentro = false
                            }
                            if ( ! pessoaDentro ) {
                                nextState = nextCleaningProcess()
                                changeCurrentState(nextState, FROM_EXT)
                            }
                        }

                        MachineState.WAITING_FINISH -> {
                            if (  pessoaEmSensor(Sensor.SAIDA)  ) {
                                pessoaDentro = false
                            }
                            if (  ! pessoaDentro ) {
                                changeCurrentState(MachineState.GRANA_BOLSO, FROM_EXT)
                            }
                        }

                        MachineState.GRANA_BOLSO -> {
                           changeCurrentState(MachineState.WAITING_PEOPLE, FROM_EXT)
                        }

                    }

                }
            }


            else -> {
                ScreenLog.add(LogType.TO_HISTORY, "EventType invalido chegando em processReceivedResponse(CleaningMachine)")

            }

        }
    }

}

