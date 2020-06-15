package com.example.appportaldemo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.View
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
    var chamadoPeloBoot = 0
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


    fun validateConfigLocalization() {
        // Create a path where we will place our private file on external
        // storage.
        val path = getExternalFilesDir(null)
        Timber.e( "=========== path=$path")
        val file = File(path, "config.json")

        if ( file.isFile  ) {
            Timber.e( "Achou arquivo ")
        } else {
            Timber.e( "Nao Achou arquivo ")
        }
        Timber.e( "=========== path=$path    file = ${file}")
    }


    override fun onCreate(savedInstanceState: Bundle?) {

        var intent = intent

        var chamadoPeloBoot = intent.getIntExtra("CHAMANDO_DO_BOOT", 0)

//        requestedOrientation
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

//        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)

        super.onCreate(savedInstanceState)

        // 1024x600 Resolução HOW 705G

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        setContentView(R.layout.activity_main)


        var tempoSegundosDesdeUltimoBoot = SystemClock.elapsedRealtime() / 1000
        Timber.i(String.format("tempoSegundosDesdeUltimoBoot = %02d:%02d:%02d", tempoSegundosDesdeUltimoBoot / 3600, (tempoSegundosDesdeUltimoBoot%3600)/60,tempoSegundosDesdeUltimoBoot%60))
        Timber.i("chamadoPeloBoot = ${chamadoPeloBoot}")


//        validateConfigLocalization()

        Config.start(this, applicationContext)
        ScreenLog.start(this, applicationContext, log_recycler_view, history_recycler_view)
        ArduinoDevice.start(this, applicationContext)
        CleaningMachine.start(this, applicationContext)
        WaitingModeThread.initialSetting(this, waiting_mode_painel_video, waiting_mode_painel_imagem, btnInvisivel)

        if ( tempoSegundosDesdeUltimoBoot < (60 + 20) ) {
            btn_show_telaBoot.setBackgroundResource(R.drawable.jm_port)
            btn_show_telaBoot.visibility = View.VISIBLE
            main_area.visibility = View.GONE
        } else {
            xxx()
            setButtonListeners()
            WaitingModeThread.start()
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ScreenLog.add(LogType.TO_LOG, "WWW Entrei na funcao onNewIntent action=${intent.action}")

        if (intent.action != null && intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            ScreenLog.add(LogType.TO_LOG, "WWW ACTION_USB_DEVICE_ATTACHED")
            jaViuUSB = true
            ArduinoDevice.connect()
        }

        if (intent.action != null && intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
            println("WWW ACTION_USB_DEVICE_DETACHED")
            ScreenLog.add(LogType.TO_LOG, "WWW ACTION_USB_DEVICE_DETACHED")
            ArduinoDevice.disconnect()
        }
    }


    fun xxx() {
        ajustaSensores()

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


    fun ajustaSensores() {

        btn_sensor1.setBackgroundResource(R.drawable.sensor_sem_gente)
        btn_sensor2.setBackgroundResource(R.drawable.sensor_sem_gente)
        btn_sensor3.setBackgroundResource(R.drawable.sensor_sem_gente)
        btn_alcohol_dispenser.setBackgroundResource(R.drawable.alc_gel_off)

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

        painelSuporte.text = "Temperatura: " + CleaningMachine.sensorAnalogico1.toString() + "\n" +
                "M1: " + CleaningMachine.valorOpcional1.toString() + "\n" +
                "M2: " + CleaningMachine.valorOpcional2.toString() + "\n" +
                "L1: " + CleaningMachine.valorOpcional3.toString() + "\n" +
                "L2: " + CleaningMachine.valorOpcional4.toString()

    }



    var mPlayer : MediaPlayer? = null

    fun setButtonListeners() {

        btnCantinhoSuperiorDireito.setOnClickListener{
            contaMagica++
            Timber.e("contaMagica=${contaMagica}")
            if ( contaMagica > 3 ) {
                painel_inferior.visibility=View.VISIBLE
            }
            if ( contaMagica > 8 ) {
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

        btnOpenApp.setOnClickListener {
            openApp()
        }

        btn_modoSuporte.setOnClickListener {

            if ( CleaningMachine.modoManutencaoHabilitado ) {
                btn_modoSuporte.text = "Suporte\nON"
                CleaningMachine.initRunFaseTimer(100)
                CleaningMachine.modoManutencaoHabilitado = false

                painelSuporte.visibility = View.GONE
                waiting_mode_painel_imagem.visibility = View.VISIBLE
                waiting_mode_painel_video.visibility = View.VISIBLE

            } else {
                btn_modoSuporte.text = "Suporte\nOFF"
                CleaningMachine.modoManutencaoHabilitado = true
                CleaningMachine.cancelRunFaseTimer()

                painelSuporte.visibility = View.VISIBLE
                waiting_mode_painel_imagem.visibility = View.GONE
                waiting_mode_painel_video.visibility = View.GONE

            }
        }
    }

    fun openApp() {
        var manager : PackageManager = applicationContext.getPackageManager()
        try {
            var i =  manager.getLaunchIntentForPackage("com.simplemobiletools.filemanager.pro.debug");
            if (i == null) {
                return
            }
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            applicationContext.startActivity(i);
            return
        } catch (e: ActivityNotFoundException) {
            return
        }
    }



}
