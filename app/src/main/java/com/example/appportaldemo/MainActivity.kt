package com.example.appportaldemo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader


class MainActivity : AppCompatActivity() {
    var contaMagica = 0
    var chamadoPeloBoot = 0
    var tipoDeTablet = 0
    companion  object {
        const val TABLET_MULTILASER_M10A    = 0x02
        const val TABLET_DL_DL_3723         = 0x04
        const val DESENV_MACHINE            = 0x01
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        var intent = intent
        var chamadoPeloBoot = intent.getIntExtra("CHAMANDO_DO_BOOT", 0)

        super.onCreate(savedInstanceState)

        // 1024x600 Resolução HOW 705G

        setContentView(R.layout.activity_main)

        Timber.e("chamadoPeloBoot= ${chamadoPeloBoot}")

        tipoDeTablet = machineType()

        if ( (tipoDeTablet and DESENV_MACHINE) > 0 ) {
            painel_suporte.visibility = View.VISIBLE
        }

        Config.start(this, applicationContext)
        ScreenLog.start(this, applicationContext, log_recycler_view, history_recycler_view)
        ArduinoDevice.start(this, applicationContext)
        CleaningMachine.start(this, applicationContext)
        WaitingModeThread.initialSetting(this, waiting_mode_painel_video, waiting_mode_painel_imagem, btnInvisivel)

        // Para saber se o App está sendo chamado pelo processo de Boot e ainda não vai interagir com o usuário
        var tempoSegundosDesdeUltimoBoot = SystemClock.elapsedRealtime() / 1000
        Timber.e(String.format("tempoSegundosDesdeUltimoBoot = %02d:%02d:%02d", tempoSegundosDesdeUltimoBoot / 3600, (tempoSegundosDesdeUltimoBoot%3600)/60,tempoSegundosDesdeUltimoBoot%60))

        var tempoBootParaStartarAplicacao : Int = 0
        if ( (tipoDeTablet and TABLET_DL_DL_3723) > 0 ) {
            // O Tablet DL só chama a Aplicação uma unica vez, então já entra na aplicação direto
            tempoBootParaStartarAplicacao = 0
        } else if ( (tipoDeTablet and TABLET_MULTILASER_M10A) > 0 ) {
            // O Tablet MULTILASER chama a Aplicação inicialmente no modo seguro, mata a aplicação
            // e depois chama a aplicação novamente. Nunca menos do que 1 minuto e meio
            tempoBootParaStartarAplicacao = 60 + 30
        }

        if ( tempoSegundosDesdeUltimoBoot < tempoBootParaStartarAplicacao ) {
            btn_show_telaBoot.setBackgroundResource(R.drawable.jm_port_xx)
            btn_show_telaBoot.visibility = View.VISIBLE
            main_area.visibility = View.GONE
        } else {
            AjusteIniciais()
            setButtonListeners()
            WaitingModeThread.start()
        }
    }


    fun AjusteIniciais() {
        ajustaSensores()
        btn_led.setBackgroundResource(R.drawable.led_white)
        btn_cleaning_area.setBackgroundResource(R.drawable.cleaning_area_off)
        btn_alcohol_dispenser.setBackgroundResource(R.drawable.alc_gel_off)
        btn_money.setBackgroundResource(R.drawable.dindin_futuro)
    }


    fun ajustaSensores() { // TODO_ANANA

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


    fun mostraSensores( ) { // TODO_ANANA igual ao de cima

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


    fun setButtonListeners() {

        btnCantinhoSuperiorDireito.setOnClickListener{
            contaMagica++
            Timber.i("contaMagica=${contaMagica}")
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






    private fun getScreenResolution(context: Context): String? {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        display.getMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        return "{$width,$height}"
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }


    private fun hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }


    enum class ErrorType(val type: Int, val message: String) {
        INVALID_WAITING_MODE_VIDEOS( 1, "ON_WAITING_PEOPLE: Sem videos definidos para modo waiting"),
        INVALID_TIME_TO_DEMO( 1, "DEMO_TIME: Tempo configurado menor que tempo minimo (120 segundos)"),
        RUN_DEMO_TIMEOUT( 2, "Sem Resposta da finalização da DEMO")
        ;
    }

    fun dealWithError(errorType: ErrorType) {
        ScreenLog.add(LogType.TO_HISTORY, "dealWithError errorType = ${errorType}")
        when (errorType) {
            ErrorType.INVALID_WAITING_MODE_VIDEOS -> erroFatal(errorType.message)
            ErrorType.INVALID_TIME_TO_DEMO -> erroFatal(errorType.message)
            ErrorType.RUN_DEMO_TIMEOUT -> erroFatal(errorType.message)
        }
    }

    fun erroFatal(str: String?) {
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


    fun printDeviceInformation()  {
        Timber.i("ScreenResolution : " + getScreenResolution(applicationContext));  // 800,1232
        Timber.i("ID                     : " + Build.ID);
        Timber.i("DISPLAY                : " + Build.DISPLAY);
        Timber.i("PRODUCT                : " + Build.PRODUCT);
        Timber.i("DEVICE                 : " + Build.DEVICE);
        Timber.i("BOARD                  : " + Build.BOARD);
        Timber.i("CPU_ABI                : " + Build.CPU_ABI);
        Timber.i("CPU_ABI2               : " + Build.CPU_ABI2);
        Timber.i("MANUFACTURER           : " + Build.MANUFACTURER);
        Timber.i("BRAND                  : " + Build.BRAND);
        Timber.i("MODEL                  : " + Build.MODEL);
        Timber.i("BOOTLOADER             : " + Build.BOOTLOADER);
        Timber.i("RADIO                  : " + Build.RADIO);
        Timber.i("HARDWARE               : " + Build.HARDWARE);
        Timber.i("SERIAL                 : " + Build.SERIAL);
        Timber.i("INCREMENTAL            : " + Build.VERSION.INCREMENTAL);
        Timber.i("RELEASE                : " + Build.VERSION.RELEASE);
        Timber.i("BASE_OS                : " + Build.VERSION.BASE_OS);
        Timber.i("SECURITY_PATCH         : " + Build.VERSION.SECURITY_PATCH);
        Timber.i("SDK                    : " + Build.VERSION.SDK);
        Timber.i("CODENAME               : " + Build.VERSION.CODENAME);
        Timber.i("TYPE                   : " + Build.TYPE);
        Timber.i("TAGS                   : " + Build.TAGS);
        Timber.i("FINGERPRINT            : " + Build.FINGERPRINT);
        Timber.i("PARTITION_NAME_SYSTEM  : " + Build.Partition.PARTITION_NAME_SYSTEM);
        Timber.i("USER                   : " + Build.USER);
        Timber.i("HOST                   : " + Build.HOST);
        Timber.i("readKernelVersion      : " + readKernelVersion());
    }

    fun readKernelVersion(): String? {
        return try {
            val p = Runtime.getRuntime().exec("uname -a")
            var `is`: InputStream? = null
            `is` = if (p.waitFor() == 0) {
                p.inputStream
            } else {
                p.errorStream
            }
            val br = BufferedReader(InputStreamReader(`is`), 1024)
            val line: String = br.readLine()
            br.close()
            line
        } catch (ex: Exception) {
            "ERROR: " + ex.message
        }
    }

//    ScreenResolution : {600,960}
//    ID                     : NHG47K
//    DISPLAY                : NHG47K release-keys
//    PRODUCT                : Tablet_DL_3723
//    DEVICE                 : Tablet_DL_3723
//    BOARD                  : rk30sdk
//    CPU_ABI                : armeabi-v7a
//    CPU_ABI2               : armeabi
//    MANUFACTURER           : Sungworld
//    BRAND                  : DL
//    MODEL                  : Tablet_DL_3723
//    BOOTLOADER             : unknown
//    RADIO                  : unknown
//    HARDWARE               : rk30board
//    SERIAL                 : DL201700225582
//    INCREMENTAL            : user.wb.20170731.152222
//    RELEASE                : 7.1.2
//    BASE_OS                :
//    SECURITY_PATCH         : 2017-06-01
//    SDK                    : 25
//    CODENAME               : REL
//    TYPE                   : user
//    TAGS                   : release-keys
//    FINGERPRINT            : DL/Tablet_DL_3723/Tablet_DL_3723:7.1.2/NHG47K/user.wb.20170731.152222:user/release-keys
//    PARTITION_NAME_SYSTEM  : system
//    USER                   : wb
//    HOST                   : wb
//    readKernelVersion      : Linux localhost 3.10.104 #15 SMP PREEMPT Sat Jul 22 11:34:31 CST 2017 armv7l
//

//    ScreenResolution : {800,1232}
//    ID                     : NRD90M
//    DISPLAY                : ML-SO13-M10A_A7.V10_20180203
//    PRODUCT                : M10A
//    DEVICE                 : M10A
//    BOARD                  : M10A
//    CPU_ABI                : armeabi-v7a
//    CPU_ABI2               : armeabi
//    MANUFACTURER           : Multilaser
//    BRAND                  : Multilaser
//    MODEL                  : M10A
//    BOOTLOADER             : unknown
//    RADIO                  : unknown
//    HARDWARE               : mt6580
//    SERIAL                 : 170901193001003
//    INCREMENTAL            : 1517645122
//    RELEASE                : 7.0
//    BASE_OS                :
//    SECURITY_PATCH         : 2017-08-05
//    SDK                    : 24
//    CODENAME               : REL
//    TYPE                   : user
//    TAGS                   : release-keys
//    FINGERPRINT            : Multilaser/M10A/M10A:7.0/NRD90M/1498532819:user/release-keys
//    PARTITION_NAME_SYSTEM  : system
//    USER                   : maxw
//    HOST                   : mid-svr7
//    readKernelVersion      : Linux localhost 3.18.35 #2 SMP PREEMPT Sat Feb 3 16:41:26 CST 2018 armv7l


//    ID                     : NRD90M
//    DISPLAY                : HOW_20180718
//    PRODUCT                : 705-G
//    DEVICE                 : 705-G
//    BOARD                  : 705-G
//    CPU_ABI                : armeabi-v7a
//    CPU_ABI2               : armeabi
//    MANUFACTURER           : HOW
//    BRAND                  : HOW
//    MODEL                  : 705-G
//    BOOTLOADER             : unknown
//    RADIO                  : unknown
//    HARDWARE               : sp7731c_1h10
//    SERIAL                 : A705B81110074724
//    INCREMENTAL            : eng.lxj.20180718.145603
//    RELEASE                : 7.0
//    BASE_OS                :
//    SECURITY_PATCH         : 2017-09-05
//    SDK                    : 24
//    CODENAME               : REL
//    TYPE                   : user
//    TAGS                   : release-keys
//    FINGERPRINT            : HOW/705-G/705-G:7.0/NRD90M/lxj10301715:user/release-keys
//    PARTITION_NAME_SYSTEM  : system
//    USER                   : lxj
//    HOST                   : ubuntuR730
//    readKernelVersion      : Linux localhost 3.10.65 #1 SMP PREEMPT Wed Jul 18 14:54:13 CST 2018 armv7l





    fun machineType() : Int {
        var ret=0
        // Vamos Verificar se estamos numa maquina de desenvolvimento

        printDeviceInformation()

        if (  Build.MODEL.contains("M10A") ) {
           ret =  TABLET_MULTILASER_M10A
            if (  Build.SERIAL.contains("170901193001003") ) {
                ret = ret or DESENV_MACHINE
            }

        } else if (  Build.MODEL.contains("DL_3723") ) {
            ret =  TABLET_DL_DL_3723

            if (  Build.SERIAL.contains("DL201700225582") ) {
                ret = ret or DESENV_MACHINE
            }
        }

        return ret
    }


}
