package com.example.appportaldemo

import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.Window
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.File


enum class ErrorType(val type: Int, val message: String) {
    INVALID_WAITING_MODE_VIDEOS( 1, "ON_WAITING_PEOPLE: Sem videos definidos para modo waiting"),
    INVALID_TIME_TO_DEMO( 1, "DEMO_TIME: Tempo configurado menor que tempo minimo (120 segundos)"),
    RUN_DEMO_TIMEOUT( 2, "Sem Resposta da finalização da DEMO")
    ;
}


class MainActivity : AppCompatActivity() {
    var temperaturaFake: Float = 0F
    var contaMagica = 0
    var jaViuUSB:Boolean=false

    var primeiraConexaoHandler: Handler = Handler()
    var primeiraConexaoRunnable: Runnable = Runnable {
        ArduinoDevice.connect()
    }

    private fun primeiraConexaoTimer() {
        var timeout = 5000L
        Timber.e("=======WWW ======= primeiraConexaoTimer jaViuUSB=${jaViuUSB} ====================")
        if ( jaViuUSB  ) {
            primeiraConexaoHandler.postDelayed(primeiraConexaoRunnable, timeout )
        } else {
            cancelPrimeiraConexaoTimer()
        }
    }

    private fun cancelPrimeiraConexaoTimer() {
        try {
            primeiraConexaoHandler.removeCallbacks(primeiraConexaoRunnable)
        } catch (e: Exception) {}
    }

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


//
//        fun createExternalStoragePrivateFile() {
//        // Create a path where we will place our private file on external
//        // storage.
//        val file = File(getExternalFilesDir(null), "alc_gel_on.png")
//
//            Timber.e( "=========== file = ${file}")
//
//            try {
//            // Very simple code to copy a picture from the application's
//            // resource into the external file.  Note that this code does
//            // no error checking, and assumes the picture is small (does not
//            // try to copy it in chunks).  Note that if external storage is
//            // not currently mounted this will silently fail.
//            val bbb = resources.openRawResource(R.drawable.alc_gel_on)
//            val os: OutputStream = FileOutputStream(file)
//            val data = ByteArray(bbb.available())
//            bbb.read(data)
//            os.write(data)
//            bbb.close()
//            os.close()
//        } catch (e: IOException) {
//            // Unable to create file, likely because external storage is
//            // not currently mounted.
//            Timber.e("ExternalStorage Error writing $file")
//        }
//    }


    fun validateConfigLocalization() {
        // Create a path where we will place our private file on external
        // storage.
        val path = getExternalFilesDir(null)
        val file = File(path, "config.json")

        if ( file.isFile  ) {
            Timber.e( "Achou arquivo ")
        } else {
            Timber.e( "Nao Achou arquivo ")
        }
        Timber.e( "=========== path=$path    file = ${file}")
    }


    override fun onCreate(savedInstanceState: Bundle?) {


        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)

        super.onCreate(savedInstanceState)

        // 1024x600 Resolução HOW 705G

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

//        validateConfigLocalization()

        Config.start(this, applicationContext)
        ScreenLog.start(this, applicationContext, log_recycler_view, history_recycler_view)
        ArduinoDevice.start(this, applicationContext)
        CleaningMachine.start(this, applicationContext)
        WaitingModeThread.initialSetting(this, waiting_mode_painel_video, waiting_mode_painel_imagem, btnInvisivel)

        xxx()
        setButtonListeners()

        WaitingModeThread.start()
    }

    fun xxx() {


        ajustaSensores(false, false)

        btn_led.setBackgroundResource(R.drawable.led_white)
        btn_cleaning_area.setBackgroundResource(R.drawable.cleaning_area_off)
        btn_alcohol_dispenser.setBackgroundResource(R.drawable.alc_gel_off)
        btn_money.setBackgroundResource(R.drawable.dindin_futuro)

        temperatura_seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                temperaturaFake = 35F + ((progress * 5F) / 100F)
                temperatura_valor.text = String.format("%.2f°", temperaturaFake)
                if ( temperaturaFake > 37F ) {
                    temperatura_valor.setTextColor( getColor(R.color.red))
                } else {
                    temperatura_valor.setTextColor( getColor(R.color.blue))
                }
                println("${progress.toString()} - ${String.format("%.2f", temperaturaFake)}")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (  CleaningMachine.waitingThermometer ) {
                    CleaningMachine.onThermometerFinished(temperaturaFake)
                }
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


    fun ajustaSensores(sendToArduino:Boolean, simulaSensor4:Boolean) {

        // Todos os sensores vao desativar o sensor4
        var s4 = CleaningMachine.sensor4Status



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

        if ( CleaningMachine.sensor4Status > 0 ) {
            btn_alcohol_dispenser.setBackgroundResource(R.drawable.alc_gel_on)
        } else {
            btn_alcohol_dispenser.setBackgroundResource(R.drawable.alc_gel_off)
        }



        // So manda se for alterado pela interface
        if ( sendToArduino) {
            if ( simulaSensor4 ) {
//                s4 = 1
            }
            ArduinoDevice.requestToSend(EventType.FW_DUMMY, String.format("S,%d,%d,%d,%d",
                CleaningMachine.sensor1Status, CleaningMachine.sensor2Status, CleaningMachine.sensor3Status, s4))
            ArduinoDevice.requestToSend(EventType.FW_STATUS_RQ, Event.QUESTION)
        }
    }


    fun mostraSensores( ) {

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

        if ( CleaningMachine.sensor4Status > 0 ) {
            btn_alcohol_dispenser.setBackgroundResource(R.drawable.alc_gel_on)
        } else {
            btn_alcohol_dispenser.setBackgroundResource(R.drawable.alc_gel_off)
        }

        painelSuporte.text = "Temperatura: " + CleaningMachine.sensorAnalogico1.toString() + "\n\n"

    }



    fun ajustaBalancas() {
//        // TODO:xx
//        alcohol_seekBar.setProgress(CleaningMachine.balanca1Status)
//        desinfectante_seekBar.setProgress(CleaningMachine.balanca2Status)
//        desinfectante_pe_seekBar.setProgress(CleaningMachine.balanca3Status)
    }


    var mPlayer : MediaPlayer? = null

    fun setButtonListeners() {


        btnCantinhoSuperiorDireito.setOnClickListener{

//            if ( mPlayer != null) {
//                if (mPlayer!!.isPlaying) {
//                    mPlayer!!.stop()
//                }
//                mPlayer!!.release()
//                mPlayer = null
//            }
//
//            mPlayer = MediaPlayer.create(this, R.raw.audio3);
//            var volume = 0.1F * contaMagica
//
//            if ( mPlayer != null) {
//                Timber.e("----- volume=${volume}")
//                mPlayer!!.setVolume(volume, volume)
//                mPlayer!!.start()
//            }
//

            if ( applicationContext.checkSelfPermission(android.Manifest.permission.CHANGE_CONFIGURATION ) !=
                    PackageManager.PERMISSION_GRANTED) {
                Timber.e("----- NOK")
            } else {
                Timber.e("----- OK")

            }


            contaMagica++
            Timber.e("contaMagica=${contaMagica}")

//            if ( contaMagica == 1) {
//                WaitingModeThread.newEnterWaitingMode(5, Config.testMedias)
//            } else {
//                WaitingModeThread.newLeaveWaitingMode()
//            }



            if ( contaMagica > 3 ) {
                painel_inferior.visibility=View.VISIBLE
            }
            if ( contaMagica > 10 ) {
                painel_suporte.visibility=View.VISIBLE
            }
        }

        btnHidePainel.setOnClickListener{
            contaMagica = 0
            painel_inferior.visibility=View.GONE
            painel_suporte.visibility=View.GONE
        }



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

        btnSimulaRequest.setOnClickListener {
            ArduinoDevice.requestToSend(EventType.FW_LED, "9,0") // FW_LED
        }

        btn_modoSuporte.setOnClickListener {

            if ( CleaningMachine.modoManutencaoHabilitado ) {
                btn_modoSuporte.text = "Suporte\nON"
                CleaningMachine.modoManutencaoHabilitado = false

                painelSuporte.visibility = View.GONE
                waiting_mode_painel_imagem.visibility = View.VISIBLE
                waiting_mode_painel_video.visibility = View.VISIBLE

            } else {
                btn_modoSuporte.text = "Suporte\nOFF"
                CleaningMachine.modoManutencaoHabilitado = true

                painelSuporte.visibility = View.VISIBLE
                waiting_mode_painel_imagem.visibility = View.GONE
                waiting_mode_painel_video.visibility = View.GONE

            }
        }


        // --------------------------------------------------
        // Rodape -----------------------------------
        // --------------------------------------------------

// this goes wherever you setup your button listener:

        // this goes wherever you setup your button listener:
        btn_sensor1.setOnTouchListener(OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                CleaningMachine.sensor1Status = 1
                ajustaSensores(true, false)
                Timber.i("mudou btn_sensor1 para LIGADO")
            } else if (event.action == MotionEvent.ACTION_UP) {
                CleaningMachine.sensor1Status = 0
                ajustaSensores(true, false)
                Timber.i("mudou btn_sensor1 para DESLIGADO")
            }
            true
        })

        btn_sensor2.setOnTouchListener(OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                CleaningMachine.sensor2Status = 1
                ajustaSensores(true, false)
                Timber.i("mudou btn_sensor2 para LIGADO")
            } else if (event.action == MotionEvent.ACTION_UP) {
                CleaningMachine.sensor2Status = 0
                ajustaSensores(true, false)
                Timber.i("mudou btn_sensor2 para DESLIGADO")
            }
            true
        })

        btn_sensor3.setOnTouchListener(OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                CleaningMachine.sensor3Status = 1
                ajustaSensores(true, false)
                Timber.i("mudou btn_sensor3 para LIGADO")
            } else if (event.action == MotionEvent.ACTION_UP) {
                CleaningMachine.sensor3Status = 0
                ajustaSensores(true, false)
                Timber.i("mudou btn_sensor3 para DESLIGADO")
            }
            true
        })

//        btn_sensor1.setOnClickListener  {
//            Timber.i("btn_sensor1 ${CleaningMachine.sensor1Status}")
//            if ( CleaningMachine.sensor1Status > 0 ) {
//                CleaningMachine.sensor1Status = 0
//            } else {
//                CleaningMachine.sensor1Status = 1
//                CleaningMachine.sensor2Status = 0
//                CleaningMachine.sensor3Status = 0
//            }
//            ajustaSensores(true, false)
//        }
//
//        btn_sensor2.setOnClickListener  {
//            Timber.i("btn_sensor2 ${CleaningMachine.sensor2Status}")
//            if ( CleaningMachine.sensor2Status > 0 ) {
//                CleaningMachine.sensor2Status = 0
//            } else {
//                CleaningMachine.sensor1Status = 0
//                CleaningMachine.sensor2Status = 1
//                CleaningMachine.sensor3Status = 1
//            }
//            ajustaSensores(true, false)
//        }
//
//        btn_sensor3.setOnClickListener  {
//            Timber.i("btn_sensor3 ${CleaningMachine.sensor3Status}")
//            if ( CleaningMachine.sensor3Status > 0 ) {
//                CleaningMachine.sensor3Status = 0
//            } else {
//                CleaningMachine.sensor1Status = 0
//                CleaningMachine.sensor2Status = 0
//                CleaningMachine.sensor3Status = 1
//            }
//            ajustaSensores(true, false)
//        }


        btn_money.setOnClickListener  {
            Timber.i("btn_money ${CleaningMachine.sensor2Status}")
            if ( (CleaningMachine.sensor2Status > 0 )  ||  (CleaningMachine.sensor3Status > 0) ) {
                CleaningMachine.sensor1Status = 0
                CleaningMachine.sensor2Status = 0
                CleaningMachine.sensor3Status = 0
            }
            ajustaSensores(true, false)
        }


        btn_alcohol_dispenser.setOnClickListener  {
            Timber.i("btn_alcohol_dispenser ")
            ajustaSensores(true, true)
        }

    }

}
