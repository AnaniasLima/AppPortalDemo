package com.example.appportaldemo

import android.R.attr.button
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.util.*


enum class CleaningMachineState  {
    UNKNOW,
    RESTART,
//    OUT_OF_SERVICE,
    WAITING_PERSON,
    WAITING_THERMOMETER,
    CALL_ATENDENT,
    FEVER_PROCEDURE,
    ALCOHOL_PROCESS,
    WAITING_ENTER,
    CLEANING_PROCESS_1,
    CLEANING_PROCESS_2,
    CLEANING_PROCESS_3,
    WAITING_FINISH,
    GRANA_BOLSO,

//    RUNNING_DEMO,
    PLAYING;
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
    private const val WAIT_TIME_TO_RESPONSE = 300L
    private const val MAX_RUN_DEMO_TIMEOUT = 30000L

    private var mainActivity: AppCompatActivity? = null
    private var appContext: Context? = null

    private var cleaningMachineHandler = Handler()

    private var desiredState: CleaningMachineState = CleaningMachineState.RESTART
    private var receivedState: CleaningMachineState = CleaningMachineState.UNKNOW
    private var countCommandsToDesiredState = 0

    private var inBusyStateCounter = 0

    // TODO: Teoricamente nunca deve acontecer. Vamos criar uma forma de tratar caso fique > 0
    private var inErrorStateCounter = 0


    private var stateMachineRunning = false

    private var runningDemo = false

    var questionDelayList = ArrayList<String>()

    var sensor1Status = 255
    var sensor2Status = 255
    var sensor3Status = 255
    var sensor4Status = 255

    var balanca1Status = 0
    var balanca2Status = 0
    var balanca3Status = 0

    var waitingThermometer : Boolean = false
    var temperaturaMedida : Float = 0F
    var flagTimeout = false

    var valorFatura = 0

    var gerarTemperaturaFake=0
    var temperaturaFake = 0F

    var pessoaDentro = false


    var runFaseHandler: Handler = Handler()
    var runFaseRunnable: Runnable = Runnable {
        flagTimeout = true
        when (receivedState ) {
            CleaningMachineState.UNKNOW -> on_UNKNOW(InOut.TIMEOUT)
            CleaningMachineState.RESTART -> on_RESTART(InOut.TIMEOUT)
            CleaningMachineState.WAITING_PERSON -> on_WAITING_PEOPLE(InOut.TIMEOUT)
            CleaningMachineState.WAITING_THERMOMETER -> on_WAITING_THERMOMETER(InOut.TIMEOUT)
            CleaningMachineState.CALL_ATENDENT   -> on_CALL_ATENDENT(InOut.TIMEOUT)
            CleaningMachineState.ALCOHOL_PROCESS -> on_ALCOHOL_PROCESS(InOut.TIMEOUT)
            CleaningMachineState.FEVER_PROCEDURE -> on_FEVER_PROCEDURE(InOut.TIMEOUT)
            CleaningMachineState.WAITING_ENTER -> on_WAITING_ENTER(InOut.TIMEOUT)

            CleaningMachineState.CLEANING_PROCESS_1 -> on_CLEANING_PROCESS_1(InOut.TIMEOUT)
            CleaningMachineState.CLEANING_PROCESS_2 -> on_CLEANING_PROCESS_2(InOut.TIMEOUT)
            CleaningMachineState.CLEANING_PROCESS_3 -> on_CLEANING_PROCESS_3(InOut.TIMEOUT)
            CleaningMachineState.WAITING_FINISH     -> on_WAITING_FINISH(InOut.TIMEOUT)
            CleaningMachineState.GRANA_BOLSO        -> on_GRANA_BOLSO(InOut.TIMEOUT)

            CleaningMachineState.PLAYING     -> {}

        }
    }



    private fun initRunFaseTimer(timeout:Long) {
        if ( timeout > 0L ) {
            runFaseHandler.postDelayed(runFaseRunnable, timeout )
        } else {
            cancelRunFaseTimer()
        }
    }

    private fun cancelRunFaseTimer() {
        try {
            flagTimeout = false
            runFaseHandler.removeCallbacks(runFaseRunnable)
        } catch (e: Exception) {}
    }



    fun pessoaEmSensor(sensor : Sensor) : Boolean {
        when(sensor) {
            Sensor.PRESENCA -> if ( sensor1Status < Config.sensor1DistanciaDetecta ) return true
            Sensor.ENTRADA ->  if ( sensor2Status < Config.sensor2DistanciaDetecta ) return true
            Sensor.SAIDA ->    if ( sensor3Status < Config.sensor3DistanciaDetecta ) return true
            Sensor.ALCOHOL ->  if ( sensor4Status < Config.sensor4DistanciaDetecta ) {
                Timber.e("WWW 002 (pessoaEmSensor) CleaningMachine.sensor4Status esta = ${CleaningMachine.sensor4Status}")
                return true
            }
        }
        return false
    }

    fun start(activity: AppCompatActivity, context: Context) {
        mainActivity = activity
        appContext = context


//        (mainActivity as MainActivity).btn_decreto

        receivedState = CleaningMachineState.UNKNOW

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
            desiredState = CleaningMachineState.RESTART
            Timber.e("setei desiredState = ${desiredState} em startStateMachine")

            machineChecking(WAIT_TIME_TO_RESPONSE)

            mainActivity?.runOnUiThread {
                (mainActivity as MainActivity).log_recycler_view.visibility = View.INVISIBLE
            }

            WaitingMode.leaveWaitingMode()
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

//
//    fun isRunningDemo(): Boolean {
//        return (receivedState == CleaningMachineState.RUNNING_DEMO)
//    }
//
//    fun stopRunDemo() {
//        if (isRunningDemo()) {
//            ArduinoDevice.requestToSend(EventType.FW_DEMO, Event.OFF)
//        }
//    }


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
                Timber.i("agendando deviceChecking ${strHora} + ${delayToNext}ms")
            }

            cleaningMachineHandler.removeCallbacks(machineCheckRunnable)
            cleaningMachineHandler.postDelayed(machineCheckRunnable, delayToNext)
        }
    }


    fun machineDemoTimeout(start: Boolean) {

        if ( start ) {
            val c = Calendar.getInstance()
            val strHora = String.format(
                "%02d:%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE),
                c.get(Calendar.SECOND)
            )
            Timber.i("agendando demoTimeout: ${strHora} + ${MAX_RUN_DEMO_TIMEOUT}ms")

            cleaningMachineHandler.removeCallbacks(demoTimeoutResetMachine)
            cleaningMachineHandler.postDelayed(demoTimeoutResetMachine, MAX_RUN_DEMO_TIMEOUT)
        } else {
            Timber.i("Removendo demoTimeout")
            cleaningMachineHandler.removeCallbacks(demoTimeoutResetMachine)
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
            ArduinoDevice.requestToSend(EventType.FW_EJECT, "2,1")
        } else {
            ScreenLog.add(LogType.TO_HISTORY, "SEM produto 3")
        }
    }

    fun ligaIndicadorSaida(tipo:Int) {
        when (tipo) {
            0 -> {
                ScreenLog.add(LogType.TO_HISTORY, "Led saida Off")
                ArduinoDevice.requestToSend(EventType.FW_LED, "1,7")
            }
            1 -> {
                ScreenLog.add(LogType.TO_HISTORY, "Led saida VERMELHO")
                ArduinoDevice.requestToSend(EventType.FW_LED, "1,1")
            }
            2 -> {
                ArduinoDevice.requestToSend(EventType.FW_LED, "1,2")
                ScreenLog.add(LogType.TO_HISTORY, "Led saida VERDE")
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
            btn.visibility = if ( flag ) View.VISIBLE else View.INVISIBLE
            btn.isEnabled = flag
        }
    }

    fun aguardando(fase: VideoFase) {
        if ( fase == VideoFase.IDLE) {
            WaitingModeThread.newLeaveWaitingMode()
        } else {
            var mediasList: ArrayList<Media>? = null
            when (fase) {
                VideoFase.IDLE -> return
                VideoFase.WAITING_PEOPLE -> mediasList = Config.waitingVideo
                VideoFase.WELCOME -> mediasList = Config.welcomeVideo
                VideoFase.TEMPERATURE_MEASURE -> mediasList = Config.mediasTempMeasure
                VideoFase.HELP -> mediasList = Config.helpVideo
                VideoFase.ALCOHOL -> mediasList = Config.alcoholVideo
                VideoFase.FEVER -> mediasList = Config.feverVideo
                VideoFase.ENTER -> mediasList = Config.enterVideo
                VideoFase.TEST  -> mediasList = Config.mediasTest
            }
            WaitingModeThread.newEnterWaitingMode(fase.ordinal, Config.mediasTest)
        }


//        mainActivity?.runOnUiThread {
//            if ( fase == VideoFase.IDLE) {
//                WaitingMode.leaveWaitingMode()
//                buttonAdjust((mainActivity as MainActivity).btn_show_full_screen, true, background = R.drawable.full_screen_background )
//            } else {
//                WaitingMode.enterWaitingMode(fase)
//                buttonAdjust((mainActivity as MainActivity).btn_show_full_screen, false)
//            }
//        }
    }

    fun on_WAITING_PEOPLE(flag : InOut) {
        Timber.i("ZZ on_WAITING_PEOPLE ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                gerarTemperaturaFake=0
                temperaturaFake = 0F
                desiredState = receivedState
                initRunFaseTimer(0L)
                ArduinoDevice.requestToSend(EventType.FW_SENSOR1, Event.ON)
//                buttonAdjust((mainActivity as MainActivity).btn_show_full_screen, true, background = R.drawable.bem_vindo)
                aguardando(VideoFase.WAITING_PEOPLE)
            }

            InOut.OUT,
            InOut.TIMEOUT -> {
                if (flag == InOut.OUT) cancelRunFaseTimer()
                ArduinoDevice.requestToSend(EventType.FW_SENSOR1, Event.OFF)
                buttonAdjust((mainActivity as MainActivity).btn_show_full_screen, false)
                aguardando(VideoFase.IDLE)
                ligaIndicadorSaida(0)
            }
        }
    }

    fun on_CALL_ATENDENT(flag : InOut) {
        Timber.i("ZZ on_CALL_ATENDENT ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                waitingThermometer = true
                aguardando(VideoFase.HELP)
            }

            InOut.OUT,
            InOut.TIMEOUT -> {
                if (flag == InOut.OUT) cancelRunFaseTimer()
                aguardando(VideoFase.IDLE)
            }
        }
    }

    fun on_WAITING_THERMOMETER(flag : InOut) {
        Timber.i("ZZ on_WAITING_THERMOMETER ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                initRunFaseTimer(3000L )
                waitingThermometer = true

                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).temperatura_seekBar.isEnabled = true
                }

                aguardando(VideoFase.TEMPERATURE_MEASURE)
            }

            InOut.OUT ,
            InOut.TIMEOUT -> {

                if (flag == InOut.OUT) cancelRunFaseTimer()

                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).temperatura_seekBar.isEnabled = false
                }

                if ( flag == InOut.OUT) {
                    cancelRunFaseTimer()
                } else {
                    val random = Random()
                    temperaturaFake= ((random.nextInt(15)) + 360) / 10F

                    if ( temperaturaFake < 37.3F) {
                        changeCurrentState(CleaningMachineState.ALCOHOL_PROCESS)
                    } else {
                        changeCurrentState(CleaningMachineState.FEVER_PROCEDURE)
                    }
                }

                aguardando(VideoFase.IDLE)
            }
        }
    }

    fun on_ALCOHOL_PROCESS(flag : InOut) {
        Timber.i("ZZ on_ALCOHOL_PROCESS ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {

                if ( temperaturaFake > 0 ) {
                    temperaturaMedida = temperaturaFake
                }
                desiredState = receivedState
                initRunFaseTimer(30000L )

                // Prepara botão de fundo com a temperatura medida
                buttonAdjust((mainActivity as MainActivity).btn_show_full_screen, false)
                buttonAdjust((mainActivity as MainActivity).btn_sem_febre, true, str=String.format("%.2f°", temperaturaMedida))
                buttonAdjust((mainActivity as MainActivity).btn_alcohol_dispenser, true, background = R.drawable.alc_gel_on)
//                WaitingMode.enterWaitingMode(VideoFase.ALCOHOL)
            }

            InOut.OUT,
            InOut.TIMEOUT-> {
                waitingThermometer = false

                buttonAdjust((mainActivity as MainActivity).btn_show_full_screen, true)
                buttonAdjust((mainActivity as MainActivity).btn_sem_febre, false)
                buttonAdjust((mainActivity as MainActivity).btn_alcohol_dispenser, true, background = R.drawable.alc_gel_off)

                if (flag ==  InOut.TIMEOUT) {
                    changeCurrentState(CleaningMachineState.CALL_ATENDENT)
                }
            }
        }
    }

    fun on_FEVER_PROCEDURE(flag : InOut) {
        Timber.i("ZZ on_FEVER_PROCEDURE ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {

                if ( temperaturaFake > 0 ) {
                    temperaturaMedida =  temperaturaFake
                }
                desiredState = receivedState
                initRunFaseTimer(5000L )

                // Oculta botao btn_show_full_screen
                buttonAdjust((mainActivity as MainActivity).btn_show_full_screen, false)

                var texto = "De acordo com decreto N° 40.778/2020 " +
                        "Não é permitida a entrada de pessoas com mais de 37,3°C " +
                        "em estabelecimentos comerciais."

                // Prepara botão de fundo com a temperatura medida
                buttonAdjust((mainActivity as MainActivity).btn_door_in, true, background = R.drawable.door_red)
                buttonAdjust((mainActivity as MainActivity).btn_fever_show_value, true, str = String.format("%.2f°", temperaturaMedida))
                buttonAdjust((mainActivity as MainActivity).btn_fever_show_message, true, str = texto)
            }

            InOut.OUT -> {
                waitingThermometer = false
                buttonAdjust((mainActivity as MainActivity).btn_fever_show_value, false)
                buttonAdjust((mainActivity as MainActivity).btn_fever_show_message, false)
            }

            InOut.TIMEOUT -> {
                waitingThermometer = false
                buttonAdjust((mainActivity as MainActivity).btn_fever_show_value, false)
                buttonAdjust((mainActivity as MainActivity).btn_fever_show_message, false)
                changeCurrentState(CleaningMachineState.WAITING_PERSON)
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

                desiredState = receivedState
                initRunFaseTimer(20000L )
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.text = "Entrada Liberada\n\n\nPasse\npelo\nPortal"
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.VISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_door_in.setBackgroundResource(R.drawable.door_green)
                }
            }

            InOut.OUT -> {
                cancelRunFaseTimer()
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.INVISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_door_in.setBackgroundResource(R.drawable.door_red)
                }
            }

            InOut.TIMEOUT -> {
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.INVISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_door_in.setBackgroundResource(R.drawable.door_red)
                }
                changeCurrentState(CleaningMachineState.WAITING_PERSON)
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
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.text = "Aguarde!\nFase 1"
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.VISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_cleaning_area.setBackgroundResource(R.drawable.cleaning_area_on)
                }
            }

            InOut.OUT -> {
                cancelRunFaseTimer()
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.INVISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_cleaning_area.setBackgroundResource(R.drawable.cleaning_area_off)
                }
            }

            InOut.TIMEOUT -> {
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.INVISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_cleaning_area.setBackgroundResource(R.drawable.cleaning_area_off)
                }
                changeCurrentState(CleaningMachineState.CLEANING_PROCESS_2)
            }
        }
    }

    fun on_CLEANING_PROCESS_2(flag : InOut) {
        Timber.e("ZZ on_CLEANING_PROCESS_2 ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                ejetaProduto3()
                initRunFaseTimer(2000L )
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.text = "Aguarde!\nFase 2"
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.VISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_cleaning_area.setBackgroundResource(R.drawable.cleaning_area_on_plus)
                }
            }

            InOut.OUT -> {
                cancelRunFaseTimer()
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.INVISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_cleaning_area.setBackgroundResource(R.drawable.cleaning_area_off)
                }
            }

            InOut.TIMEOUT -> {
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.INVISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_cleaning_area.setBackgroundResource(R.drawable.cleaning_area_off)
                }
                changeCurrentState(CleaningMachineState.CLEANING_PROCESS_3)
            }
        }
    }

    fun on_CLEANING_PROCESS_3(flag : InOut) {
        Timber.e("ZZ on_CLEANING_PROCESS_3 ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                initRunFaseTimer(2000L )
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.text = "Aguarde..."
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.VISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_cleaning_area.setBackgroundResource(R.drawable.cleaning_area_off)
                }
            }

            InOut.OUT -> {

                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.INVISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                }
                cancelRunFaseTimer()
            }

            InOut.TIMEOUT -> {
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.INVISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                }
                changeCurrentState(CleaningMachineState.WAITING_FINISH)
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
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.text = "Aguardando\nsaida"
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.VISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_door_out.setBackgroundResource(R.drawable.door_green)
                    (mainActivity as MainActivity).btn_money.isEnabled = true
                }
            }

            InOut.OUT -> {
                cancelRunFaseTimer()
                ligaIndicadorSaida(0)

                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.INVISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_door_out.setBackgroundResource(R.drawable.door_red)
                    (mainActivity as MainActivity).btn_money.isEnabled = false
                }
//                changeCurrentState(CleaningMachineState.GRANA_BOLSO)
            }

            InOut.TIMEOUT -> {
                ligaIndicadorSaida(0)
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.INVISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_door_out.setBackgroundResource(R.drawable.door_red)
                    (mainActivity as MainActivity).btn_money.isEnabled = false
                }
                changeCurrentState(CleaningMachineState.GRANA_BOLSO)
            }
        }
    }


    fun on_GRANA_BOLSO(flag : InOut) {
        Timber.e("ZZ on_GRANA_BOLSO ${flag} desiredState=$desiredState receivedState=$receivedState")
        when(flag) {
            InOut.IN -> {
                desiredState = receivedState
                initRunFaseTimer(3000L )
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.text = "Obrigado!"
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.VISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_money.setBackgroundResource(R.drawable.dindin)
                }
            }

            InOut.OUT -> {
                cancelRunFaseTimer()
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.INVISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_money.setBackgroundResource(R.drawable.dindin_futuro)
                    ScreenLog.add(LogType.TO_HISTORY, "Faturou ${valorFatura}")
                }

            }

            InOut.TIMEOUT -> {
                mainActivity?.runOnUiThread {
                    (mainActivity as MainActivity).btn_mensagem_tela.visibility = View.INVISIBLE
                    (mainActivity as MainActivity).btn_mensagem_tela.isEnabled = false
                    (mainActivity as MainActivity).btn_money.setBackgroundResource(R.drawable.dindin_futuro)
                    ScreenLog.add(LogType.TO_HISTORY, "Faturou ${valorFatura}")
                }
                changeCurrentState(CleaningMachineState.WAITING_PERSON)
            }
        }
    }


    fun onThermometerFinished(temperatura: Float) {
        temperaturaMedida = temperatura

        if ( temperatura <= 37F) {
            changeCurrentState(CleaningMachineState.ALCOHOL_PROCESS)
        } else {
            changeCurrentState(CleaningMachineState.FEVER_PROCEDURE)
        }
    }

    fun changeCurrentState(newState : CleaningMachineState) {

        countCommandsToDesiredState = 0

        if ( receivedState == newState) {
            return
        }


        //---------------------------------------------------------
        // Trata saida do estado atual
        //---------------------------------------------------------
        when (receivedState ) {
            CleaningMachineState.UNKNOW                 -> {}
            CleaningMachineState.RESTART                -> { }
            CleaningMachineState.WAITING_PERSON         -> { on_WAITING_PEOPLE(InOut.OUT) }
            CleaningMachineState.WAITING_THERMOMETER    -> { on_WAITING_THERMOMETER(InOut.OUT) }
            CleaningMachineState.WAITING_ENTER          -> { on_WAITING_ENTER(InOut.OUT) }
            CleaningMachineState.CALL_ATENDENT          -> { on_CALL_ATENDENT(InOut.OUT) }
            CleaningMachineState.ALCOHOL_PROCESS        -> { on_ALCOHOL_PROCESS(InOut.OUT) }
            CleaningMachineState.FEVER_PROCEDURE        -> { on_FEVER_PROCEDURE(InOut.OUT) }
            CleaningMachineState.CLEANING_PROCESS_1     -> { on_CLEANING_PROCESS_1(InOut.OUT) }
            CleaningMachineState.CLEANING_PROCESS_2     -> { on_CLEANING_PROCESS_2(InOut.OUT) }
            CleaningMachineState.CLEANING_PROCESS_3     -> { on_CLEANING_PROCESS_3(InOut.OUT) }
            CleaningMachineState.WAITING_FINISH         -> { on_WAITING_FINISH(InOut.OUT) }
            CleaningMachineState.GRANA_BOLSO            -> { on_GRANA_BOLSO(InOut.OUT) }
            CleaningMachineState.PLAYING                -> { } // TODO:
        }

        receivedState = newState

        //---------------------------------------------------------
        // Trata ENTRADA do estado atual
        //---------------------------------------------------------
        when (receivedState ) {
            CleaningMachineState.UNKNOW                 -> { } // TODO:
            CleaningMachineState.RESTART                -> { ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION) }
            CleaningMachineState.WAITING_PERSON         -> { on_WAITING_PEOPLE(InOut.IN) }
            CleaningMachineState.WAITING_THERMOMETER    -> { on_WAITING_THERMOMETER(InOut.IN) }
            CleaningMachineState.WAITING_ENTER          -> { on_WAITING_ENTER(InOut.IN) }
            CleaningMachineState.CALL_ATENDENT          -> { on_CALL_ATENDENT(InOut.IN) }
            CleaningMachineState.ALCOHOL_PROCESS        -> { on_ALCOHOL_PROCESS(InOut.IN) }
            CleaningMachineState.FEVER_PROCEDURE        -> { on_FEVER_PROCEDURE(InOut.IN) }
            CleaningMachineState.CLEANING_PROCESS_1     -> { on_CLEANING_PROCESS_1(InOut.IN) }
            CleaningMachineState.CLEANING_PROCESS_2     -> { on_CLEANING_PROCESS_2(InOut.IN) }
            CleaningMachineState.CLEANING_PROCESS_3     -> { on_CLEANING_PROCESS_3(InOut.IN) }
            CleaningMachineState.WAITING_FINISH         -> { on_WAITING_FINISH(InOut.IN) }
            CleaningMachineState.GRANA_BOLSO            -> { on_GRANA_BOLSO(InOut.IN) }
            CleaningMachineState.PLAYING                -> { } // TODO:
        }

    }

    private var machineCheckRunnable = Runnable {

        machineChecking(0) // A principio agenda nova execução

        if (receivedState == desiredState) {
            ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
        } else {
            Timber.i("desiredState = ${desiredState} em 1111 (${CleaningMachineState.RESTART}")
            when (desiredState) {
                CleaningMachineState.RESTART -> {
                    if (countCommandsToDesiredState++ == 0) {
                        ArduinoDevice.requestToSend(EventType.FW_RESTART, Event.RESET)
                    }
                }
                CleaningMachineState.WAITING_PERSON,
                CleaningMachineState.WAITING_THERMOMETER,
                CleaningMachineState.CALL_ATENDENT,
                CleaningMachineState.FEVER_PROCEDURE,
                CleaningMachineState.ALCOHOL_PROCESS,
                CleaningMachineState.WAITING_ENTER,
                CleaningMachineState.GRANA_BOLSO -> {
                    ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
                }
                else -> {
                    println("ATENÇÃO: CCC Situação nao deveria ocorrer. Preciso reavaliara") // TODO: Verificar se vai ocorrer
                }
            }
        }
    }

    fun erroFatal(str: String?) {
        mainActivity?.runOnUiThread {
            (mainActivity as MainActivity).erroFatal(str)
        }
    }

    fun processReceivedResponse(response: EventResponse) {

        when (response.eventType) {

            EventType.FW_DUMMY -> {
                // Não faz nada
            }

            EventType.FW_RESTART -> {
                changeCurrentState(CleaningMachineState.RESTART)
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
                    if ( (sensor1Status != response.s1) || (sensor2Status != response.s2) || (sensor3Status != response.s3) || (sensor4Status != response.s4)) {
                        mainActivity?.runOnUiThread {
                            (mainActivity as MainActivity).ajustaSensores(false, false)
                        }
                    }

                    if ( (balanca1Status != response.b1) || (balanca2Status != response.b2) || (balanca3Status != response.b3)) {
                        mainActivity?.runOnUiThread {
                            (mainActivity as MainActivity).ajustaBalancas()
                        }
                    }

                    if ( sensor4Status != response.s4) {
                        Timber.e("WWW 004 processReceivedResponse vai fazer CleaningMachine.sensor4Status = ${response.s4}")
                    }

                    if ( response.s1 < Config.sensor1DistanciaDetecta ) {
                        // gerar temperatura fake
                        gerarTemperaturaFake=1
                    }
                    sensor1Status = response.s1
                    sensor2Status = response.s2
                    sensor3Status = response.s3
                    sensor4Status = response.s4


                    balanca1Status = response.b1
                    balanca2Status = response.b2
                    balanca3Status = response.b3

                    Timber.e("estado de receivedState = ${receivedState} em processReceivedResponse")

                    when (receivedState) {

                        CleaningMachineState.UNKNOW-> {
                            desiredState = CleaningMachineState.RESTART
                            Timber.e("setei desiredState = ${desiredState} em CleaningMachineState.UNKNOW")
                        }

                        CleaningMachineState.RESTART-> {
                            // Situações de erro e busy são são tratadas em EventResponse.BUSY
                            changeCurrentState(CleaningMachineState.WAITING_PERSON)
                        }

                        CleaningMachineState.WAITING_PERSON -> {
                            if ( pessoaEmSensor(Sensor.PRESENCA) ) {
                                changeCurrentState(CleaningMachineState.WAITING_THERMOMETER)
                            }
                        }

                        CleaningMachineState.WAITING_THERMOMETER -> {
                            if ( ! pessoaEmSensor(Sensor.PRESENCA) ) {
                                changeCurrentState(CleaningMachineState.WAITING_PERSON)
                            }
                        }

                        CleaningMachineState.FEVER_PROCEDURE -> {
                            // só sai por timeout
//                            if ( ! pessoaEmSensor(Sensor.PRESENCA) ) {
//                                changeCurrentState(CleaningMachineState.WAITING_PERSON)
//                            }
                        }

                        CleaningMachineState.CALL_ATENDENT -> {
                            if ( ! pessoaEmSensor(Sensor.PRESENCA) ) {
                                changeCurrentState(CleaningMachineState.WAITING_PERSON)
                            }
                        }

                        CleaningMachineState.ALCOHOL_PROCESS -> {
                            if ( pessoaEmSensor(Sensor.ALCOHOL) ) {
                                Timber.e("WWW 005 testou e disse que tem mão no Alcool vai para WAITING_ENTER")
                                changeCurrentState(CleaningMachineState.WAITING_ENTER)
                            }
                        }

                        CleaningMachineState.WAITING_ENTER -> {
                            if ( pessoaEmSensor(Sensor.ENTRADA) ) {
                                changeCurrentState(CleaningMachineState.CLEANING_PROCESS_1)
                                pessoaDentro = true
                            }
                        }


                        CleaningMachineState.CLEANING_PROCESS_1 -> {
                            if (  pessoaEmSensor(Sensor.SAIDA)  ) {
                                pessoaDentro = false
                            }

                            if ( ! pessoaDentro ) {
                                changeCurrentState(CleaningMachineState.WAITING_FINISH)
                            }
                        }

                        CleaningMachineState.CLEANING_PROCESS_2 -> {
                            if (  pessoaEmSensor(Sensor.SAIDA)  ) {
                                pessoaDentro = false
                            }
                            if ( ! pessoaDentro ) {
                                changeCurrentState(CleaningMachineState.WAITING_FINISH)
                            }
                        }

                        CleaningMachineState.CLEANING_PROCESS_3 -> {
                            if (  pessoaEmSensor(Sensor.SAIDA)  ) {
                                pessoaDentro = false
                            }
                            if ( ! pessoaDentro ) {
                                changeCurrentState(CleaningMachineState.WAITING_FINISH)
                            }
                        }

                        CleaningMachineState.WAITING_FINISH -> {

                            if (  pessoaEmSensor(Sensor.SAIDA)  ) {
                                pessoaDentro = false
                            }

                            if (  ! pessoaDentro ) {
                                changeCurrentState(CleaningMachineState.GRANA_BOLSO)
                            }
                        }

                        CleaningMachineState.GRANA_BOLSO -> {
                           changeCurrentState(CleaningMachineState.WAITING_PERSON)
                        }

                        CleaningMachineState.PLAYING -> {
                            // TODO:
                            changeCurrentState(CleaningMachineState.WAITING_PERSON)
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

