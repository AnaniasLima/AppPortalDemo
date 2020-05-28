package com.example.appportaldemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Environment.getExternalStorageDirectory
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.os.EnvironmentCompat
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.InputStream


enum class ErrorType(val type: Int, val message: String) {
    INVALID_WAITING_MODE_VIDEOS( 1, "ON_WAITING_VIDEO: Sem videos definidos para modo waiting"),
    INVALID_TIME_TO_DEMO( 1, "DEMO_TIME: Tempo configurado menor que tempo minimo (120 segundos)"),
    RUN_DEMO_TIMEOUT( 2, "Sem Resposta da finalização da DEMO")
    ;
}


class MainActivity : AppCompatActivity() {
    var temperaturaMedida: Float = 0F

//    fun getURI(videoname:String): Uri {
//        if (URLUtil.isValidUrl(videoname)) {
//            //  an external URL
//            return Uri.parse(videoname)
//        } else { //  a raw resource
//            return Uri.parse("android.resource://" + packageName + "/raw/" + videoname);
//        }
//    }


    fun dealWithError(errorType: ErrorType) {

        ScreenLog.add(LogType.TO_HISTORY, "dealWithError errorType = ${errorType}")

        when (errorType) {
            ErrorType.INVALID_WAITING_MODE_VIDEOS -> erroFatal(errorType.message)
            ErrorType.INVALID_TIME_TO_DEMO -> erroFatal(errorType.message)
            ErrorType.RUN_DEMO_TIMEOUT -> erroFatal(errorType.message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1024x600 resolução

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        setContentView(R.layout.activity_main)

        val ins: InputStream = getResources().openRawResource(
            getResources().getIdentifier("config", "raw", getPackageName())
        )

//        Timber.e( "=========== getAbsolutePath = ${Environment.getExternalStorageDirectory().getAbsolutePath()}")

        // TODO: Ajustar para edir permissao para usuário ao invez de habilitar permissao na mão
        if (!Config.loadConfig(this, "config.json", ins)) {
            erroFatal(Config.msgErro)
        }

        ScreenLog.start(this, applicationContext, log_recycler_view, history_recycler_view)
        WaitingMode.start(this, video_view, btnInvisivel)
        ArduinoDevice.start(this, applicationContext)
        CleaningMachine.start(this, applicationContext)

        insertSpinnerGameMachine()
        xxx()
        setButtonListeners()
    }

    fun xxx() {


        ajustaSensores(false, false)
        ajustaBalancas(false)

        btn_door_in.setBackgroundResource(R.drawable.door_red)
        btn_door_out.setBackgroundResource(R.drawable.door_red)
        btn_cleaning_area.setBackgroundResource(R.drawable.cleaning_area_off)
        btn_alcohol_dispenser.setBackgroundResource(R.drawable.devices)
        btn_money.setBackgroundResource(R.drawable.dindin_futuro)

        temperatura_seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                temperaturaMedida = 35F + ((progress * 5F) / 100F)
                temperatura_valor.text = String.format("%.2f°", temperaturaMedida)
                if ( temperaturaMedida > 37F ) {
                    temperatura_valor.setTextColor( getColor(R.color.red))
                } else {
                    temperatura_valor.setTextColor( getColor(R.color.blue))
                }
                println("${progress.toString()} - ${String.format("%.2f", temperaturaMedida)}")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (  CleaningMachine.waitingThermometer ) {
                    CleaningMachine.onThermometerFinished(temperaturaMedida)
                }
            }
        })


        alcohol_seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                alcohol_valor.text = String.format("%d%%", progress.toInt())
                CleaningMachine.balanca1Status = progress.toInt()
                if ( progress.toInt() < 25 ) {
                    alcohol_valor.setTextColor( getColor(R.color.red))
                } else {
                    alcohol_valor.setTextColor( getColor(R.color.blue))
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                ajustaBalancas(true)
            }
        })


        desinfectante_seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                desinfectante_valor.text = String.format("%d%%", progress.toInt())
                CleaningMachine.balanca2Status = progress.toInt()
                if ( progress.toInt() < 25 ) {
                    desinfectante_valor.setTextColor( getColor(R.color.red))
                } else {
                    desinfectante_valor.setTextColor( getColor(R.color.blue))
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                ajustaBalancas(true)
            }
        })

        desinfectante_pe_seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                desinfectante_pe_valor.text = String.format("%d%%", progress.toInt())
                CleaningMachine.balanca3Status = progress.toInt()
                if ( progress.toInt() < 25 ) {
                    desinfectante_pe_valor.setTextColor( getColor(R.color.red))
                } else {
                    desinfectante_pe_valor.setTextColor( getColor(R.color.blue))
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                ajustaBalancas(true)
            }
        })

    }

    fun erroFatal(str: String?)
    {
        if ( str != null && str.isNotEmpty() ) {
            buttonErro.setVisibility(View.VISIBLE)
            buttonErro.isClickable=true
            buttonErro.setText(str)
            buttonErro.setOnClickListener {
                Timber.e(str)
                finish()
                System.exit(0)
            }
        }
    }

    fun insertSpinnerGameMachine() {
        spinnerMachine.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1 , CleaningMachine.questionDelayList)
        CleaningMachine.setDelayForQuestion(spinnerMachine.selectedItem.toString())
        spinnerMachine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                Timber.i("Nada foi selecionado")
            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                CleaningMachine.setDelayForQuestion(parent!!.getItemAtPosition(pos).toString())
            }
        }
    }


    fun ajustaSensores(sendToArduino:Boolean, simulaSensor4:Boolean) {

        // Todos os sensores vao desativar o sensor4
        var s4 = Config.sensor4DistanciaDetecta + 10

        btn_sensor1.setBackgroundResource(R.drawable.sensor_sem_gente)
        btn_sensor2.setBackgroundResource(R.drawable.sensor_sem_gente)
        btn_sensor3.setBackgroundResource(R.drawable.sensor_sem_gente)

        if ( CleaningMachine.pessoaEmSensor(Sensor.PRESENCA) ) {
            btn_sensor1.setBackgroundResource(R.drawable.sensor_com_gente)
        }
        if ( CleaningMachine.pessoaEmSensor(Sensor.ENTRADA) ) {
            btn_sensor2.setBackgroundResource(R.drawable.sensor_com_gente)
        }
        if ( CleaningMachine.pessoaEmSensor(Sensor.SAIDA) ) {
            btn_sensor3.setBackgroundResource(R.drawable.sensor_com_gente)
        }

        // So manda se for alterado pela interface
        if ( sendToArduino) {
            if ( simulaSensor4 ) {
                s4 = Config.sensor4DistanciaDetecta - 10
            }
            ArduinoDevice.requestToSend(EventType.FW_DUMMY, String.format("S,%03d,%03d,%03d,%03d",
                CleaningMachine.sensor1Status, CleaningMachine.sensor2Status, CleaningMachine.sensor3Status, s4))
            ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
        }
    }


    fun ajustaBalancas(sendToArduino:Boolean) {
        // TODO:xx
        alcohol_seekBar.setProgress(CleaningMachine.balanca1Status)
        desinfectante_seekBar.setProgress(CleaningMachine.balanca2Status)
        desinfectante_pe_seekBar.setProgress(CleaningMachine.balanca3Status)
        if ( sendToArduino) {
            ArduinoDevice.requestToSend(EventType.FW_DUMMY, String.format("B,%03d,%03d,%03d", CleaningMachine.balanca1Status, CleaningMachine.balanca2Status, CleaningMachine.balanca3Status))
            ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
        }
    }



    fun setButtonListeners() {

        // --------------------------------------------------
        // Primeira Linha -----------------------------------
        // --------------------------------------------------
        btnLogTag.setOnClickListener{
            ScreenLog.tag(LogType.TO_LOG)
        }

        btnLogClear.setOnClickListener{
            ScreenLog.clear(LogType.TO_LOG)
            ScreenLog.clear(LogType.TO_HISTORY)
        }


        btnStateMachine.setOnClickListener{
            if ( CleaningMachine.isStateMachineRunning() ) {
                btnStateMachine.text = getString(R.string.startStateMachine)
                CleaningMachine.stopStateMachine()
            } else {
                if ( CleaningMachine.startStateMachine() ) {
                    btnStateMachine.text = getString(R.string.stopStateMachine)
                } else {
                    Toast.makeText(this, "Offline", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnStatusRequest.setOnClickListener {
            ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
        }


        // --------------------------------------------------
        // Rodape -----------------------------------
        // --------------------------------------------------


        btn_sensor1.setOnClickListener  {
            Timber.i("btn_sensor1 ${CleaningMachine.sensor1Status}")
            if ( CleaningMachine.sensor1Status < Config.sensor1DistanciaDetecta ) {
                CleaningMachine.sensor1Status = Config.sensor1DistanciaDetecta + 10
            } else {
                CleaningMachine.sensor1Status = Config.sensor1DistanciaDetecta - 10
                CleaningMachine.sensor2Status = Config.sensor2DistanciaDetecta + 10
                CleaningMachine.sensor3Status = Config.sensor3DistanciaDetecta + 10
            }
            ajustaSensores(true, false)
        }

        btn_sensor2.setOnClickListener  {
            Timber.i("btn_sensor2 ${CleaningMachine.sensor2Status}")
            if ( CleaningMachine.sensor2Status < Config.sensor2DistanciaDetecta ) {
                CleaningMachine.sensor2Status = Config.sensor2DistanciaDetecta + 10
            } else {
                CleaningMachine.sensor1Status = Config.sensor1DistanciaDetecta + 10
                CleaningMachine.sensor2Status = Config.sensor2DistanciaDetecta - 10
                CleaningMachine.sensor3Status = Config.sensor3DistanciaDetecta + 10
            }
            ajustaSensores(true, false)
        }

        btn_sensor3.setOnClickListener  {
            Timber.i("btn_sensor3 ${CleaningMachine.sensor3Status}")
            if ( CleaningMachine.sensor3Status < Config.sensor3DistanciaDetecta ) {
                CleaningMachine.sensor3Status = Config.sensor3DistanciaDetecta + 10
            } else {
                CleaningMachine.sensor1Status = Config.sensor1DistanciaDetecta + 10
                CleaningMachine.sensor2Status = Config.sensor2DistanciaDetecta + 10
                CleaningMachine.sensor3Status = Config.sensor3DistanciaDetecta - 10
            }
            ajustaSensores(true, false)
        }


        btn_money.setOnClickListener  {
            Timber.i("btn_money ${CleaningMachine.sensor2Status}")
            if ( (CleaningMachine.sensor2Status < Config.sensor2DistanciaDetecta) ||  (CleaningMachine.sensor3Status < Config.sensor3DistanciaDetecta) ) {
                CleaningMachine.sensor1Status = Config.sensor1DistanciaDetecta + 10
                CleaningMachine.sensor2Status = Config.sensor2DistanciaDetecta + 10
                CleaningMachine.sensor3Status = Config.sensor3DistanciaDetecta + 10
            }
            ajustaSensores(true, false)
        }


        btn_alcohol_dispenser.setOnClickListener  {
            Timber.i("btn_alcohol_dispenser ")
            ajustaSensores(true, true)
        }

        btnInvisivel.setOnClickListener  {

            if  (log_recycler_view.visibility == View.VISIBLE) {
                log_recycler_view.setVisibility(View.INVISIBLE)
                log_recycler_view.setVisibility(View.GONE)
            } else {
                log_recycler_view.setVisibility(View.VISIBLE)
            }

//            WaitingMode.leaveWaitingMode()
//            log_recycler_view.setVisibility(View.VISIBLE)
        }
    }

}
