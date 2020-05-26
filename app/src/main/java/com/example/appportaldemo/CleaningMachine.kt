package com.example.appportaldemo

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.util.*

enum class CleaningMachineState  {
    UNKNOW,
    RESTART,
    OUT_OF_SERVICE,
    WAITING_PERSON,
    WAITING_THERMOMETER,
    FEVER_PROCEDURE,
    ALCOHOL_PROCESS,
    WAITING_ENTER,
    CLEANING_PROCESS_1,
    CLEANING_PROCESS_2,
    CLEANING_PROCESS_3,
    FINAL_INSTRUCTIONS,
    WAITING_FINISH,

//    RUNNING_DEMO,
    PLAYING;
}

enum class InOut  {
    IN,
    OUT;
}


enum class CleaningMachineCommand  {
    STATUS_RQ,
    FW_PLAY,
    FW_DEMO;
}

enum class Sensor  {
    PRESENCA,
    ENTRADA,
    SAIDA;
}

@SuppressLint("StaticFieldLeak")
object CleaningMachine {
    private const val WAIT_WHEN_OFFLINE = 5000L
    private var DEFAULT_TIME_TO_QUESTION = 1000L
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

    var sensor1Status = 0
    var sensor2Status = 0
    var sensor3Status = 0

    var balanca1Status = 0
    var balanca2Status = 0
    var balanca3Status = 0

    fun pessoaEmSensor(sensor : Sensor) : Boolean {
        when(sensor) {
            Sensor.PRESENCA -> if ( sensor1Status < Config.sensor1DistanciaDetecta ) return true
            Sensor.ENTRADA ->  if ( sensor2Status < Config.sensor2DistanciaDetecta ) return true
            Sensor.SAIDA ->  if ( sensor2Status < Config.sensor2DistanciaDetecta ) return true
        }
        return false
    }

    fun start(activity: AppCompatActivity, context: Context) {
        mainActivity = activity
        appContext = context

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

    fun setDelayForQuestion(token: String) {
        val indStart = token.indexOfFirst { it == ' ' }
        val str2 = token.substring(indStart + 1)
        val indEnd = str2.indexOfFirst { it == ' ' }
        val str3 = str2.substring(0, indEnd)

        try {
            val delay: Long = str3.toLong()
            SELECTED_TIME_TO_QUESTION = delay
            machineChecking(0L) // Para iniciar novo ciclo
        } catch (e: Exception) {
            SELECTED_TIME_TO_QUESTION = DEFAULT_TIME_TO_QUESTION
        }
    }


    fun startStateMachine() : Boolean {
        if ( ConnectThread.isConnected ) {
            stateMachineRunning = true
            desiredState = CleaningMachineState.RESTART
            Timber.e("setei desiredState = ${desiredState} em startStateMachine")

            machineChecking(WAIT_TIME_TO_RESPONSE)
            return true
        } else {
            return false
        }
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


    private var demoTimeoutResetMachine = Runnable {
        erroFatal("Nao finalizou demo em ${MAX_RUN_DEMO_TIMEOUT}ms")
    }

//    fun on_WAITING_PERSON(flag : InOut) {
//        if ( flag == InOut.IN ) {
//            mainActivity?.runOnUiThread {
//                WaitingMode.enterWaitingMode(1)
//            }
//        } else {
//            mainActivity?.runOnUiThread {
//                WaitingMode.leaveWaitingMode()
//            }
//        }
//
//    }


    fun on_WAITING_PERSON(flag : InOut) {

        if ( flag == InOut.IN ) {
            ArduinoDevice.requestToSend(EventType.FW_SENSOR1, Event.ON)

            mainActivity?.runOnUiThread {
//                (mainActivity as MainActivity).btn_modo_waiting_person.visibility = View.VISIBLE
//                (mainActivity as MainActivity).btn_modo_waiting_person.isEnabled = true
                WaitingMode.enterWaitingMode(1)
            }

//            mainActivity?.runOnUiThread {
//                WaitingMode.enterWaitingMode()
//                (mainActivity as MainActivity).btn_sensor1.setBackgroundResource(R.drawable.sensor_sem_gente)
//                (mainActivity as MainActivity).btn_sensor1.isEnabled = true
//            }

        } else {
            ArduinoDevice.requestToSend(EventType.FW_SENSOR1, Event.OFF)
            mainActivity?.runOnUiThread {

                WaitingMode.leaveWaitingMode()

                (mainActivity as MainActivity).btn_modo_waiting_person.visibility = View.INVISIBLE
                (mainActivity as MainActivity).btn_modo_waiting_person.isEnabled = false
            }
        }

    }

    fun on_WAITING_THERMOMETER(flag : InOut) {
        if ( flag == InOut.IN ) {
            mainActivity?.runOnUiThread {
                (mainActivity as MainActivity).temperatura_layout.visibility = View.VISIBLE
                (mainActivity as MainActivity).temperatura_seekBar.isEnabled = true

                (mainActivity as MainActivity).alcohol_dispenser.visibility = View.INVISIBLE

                WaitingMode.enterWaitingMode(2)
            }
        } else {
            mainActivity?.runOnUiThread {
                WaitingMode.leaveWaitingMode()

                (mainActivity as MainActivity).temperatura_layout.visibility = View.INVISIBLE
                (mainActivity as MainActivity).alcohol_dispenser.visibility = View.VISIBLE
            }
        }

    }


    fun changeCurrentState(newState : CleaningMachineState) {

        countCommandsToDesiredState = 0

        if ( receivedState != newState) {

            //---------------------------------------------------------
            // Trata saida do estado atual
            //---------------------------------------------------------
            when (receivedState ) {
                CleaningMachineState.UNKNOW -> {}
                CleaningMachineState.RESTART -> { }
                CleaningMachineState.WAITING_PERSON -> {
                    on_WAITING_PERSON(InOut.OUT)
                }
                CleaningMachineState.WAITING_THERMOMETER -> {
                    on_WAITING_THERMOMETER(InOut.OUT)
                }
            }

            receivedState = newState

            //---------------------------------------------------------
            // Trata ENTRADA do estado atual
            //---------------------------------------------------------
            when (receivedState ) {
                CleaningMachineState.RESTART -> {
                    ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
                }
                CleaningMachineState.WAITING_PERSON -> {
                    desiredState = receivedState
                    on_WAITING_PERSON(InOut.IN)
                }
                CleaningMachineState.WAITING_THERMOMETER -> {
                    desiredState = receivedState
                    on_WAITING_THERMOMETER(InOut.IN)
                }
            }
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

//                CleaningMachineState.RUNNING_DEMO -> {
//                    if (countCommandsToDesiredState++ < 2) {
//                        ArduinoDevice.requestToSend(EventType.FW_DEMO, Event.ON)
//                    } else {
//                        // Não conseguimos entrar em modo demo, vamos desistir
//                        countCommandsToDesiredState = 0
//                        desiredState = CleaningMachineState.WAITING_PERSON
//                        Timber.e("setei desiredState = ${desiredState} em CleaningMachineState.RUNNING_DEMO")
//                    }
//                }

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
                    if ( (sensor1Status != response.s1) || (sensor2Status != response.s2) || (sensor3Status != response.s3)) {
                        mainActivity?.runOnUiThread {
                            (mainActivity as MainActivity).ajustaSensores(false)
                        }
                    }

                    if ( (balanca1Status != response.b1) || (balanca2Status != response.b2) || (balanca3Status != response.b3)) {
                        mainActivity?.runOnUiThread {
                            (mainActivity as MainActivity).ajustaBalancas(false)
                        }
                    }

                    sensor1Status = response.s1
                    sensor2Status = response.s2
                    sensor3Status = response.s3

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

                    }

                }
            }


            else -> {
                ScreenLog.add(LogType.TO_HISTORY, "EventType invalido chegando em processReceivedResponse(CleaningMachine)")

            }

        }
    }

}

