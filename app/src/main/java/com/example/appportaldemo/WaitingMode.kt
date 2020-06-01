package com.example.appportaldemo

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import android.os.Handler
import java.io.File
import java.io.FileInputStream

enum class VideoFase {
    IDLE, // Não mostranda nenhum video
    WAITING_PEOPLE,
    WELCOME,
    TEMPERATURE_MEASURE,
    HELP,
    ALCOHOL,
    FEVER,
    ENTER,
    TEST
}

@SuppressLint("StaticFieldLeak")
object WaitingMode {
    var modoWaitingRunning=false

    var lastPlayedVideo: Int = -1

    lateinit private var videoView: VideoView
    lateinit private var imageView: Button
    lateinit private var btnVideo: Button
    private var myActivity: AppCompatActivity? = null

    lateinit var mediasList: ArrayList<Media>

    var runningFase : VideoFase = VideoFase.IDLE

    fun start(mainActivity: AppCompatActivity, view: VideoView, imagemTela: Button, btnInvisivel: Button) {
        myActivity = mainActivity
        videoView = view
        imageView = imagemTela
        btnVideo = btnInvisivel
    }

    fun enterWaitingMode(fase : VideoFase) {
        Timber.e("ZZ===============>>>>>>> VideoFase : ${fase} ")
        modoWaitingRunning = true
        runningFase = fase
        releasePlayer()
        initPlayer(fase)
    }

    fun leaveWaitingMode() {
        Timber.e("ZZ<<<<<<<< =============== VideoFase : ${runningFase} ")
        runningFase = VideoFase.IDLE
        releasePlayer()
        videoView.visibility = View.GONE
        imageView.visibility = View.GONE
        btnVideo.setVisibility(View.GONE)
        btnVideo.isEnabled = false
        modoWaitingRunning = false
    }

    fun erroFatal(str: String?) {
        myActivity?.runOnUiThread {
            (myActivity as MainActivity).erroFatal(str)
        }
    }


    private fun playNextVideo() {
        if (++lastPlayedVideo == mediasList.size ) {
            lastPlayedVideo = 0
        }
        prepareMediaEnvironment(mediasList[lastPlayedVideo])
    }


    private fun initPlayer(fase: VideoFase) {

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

        lastPlayedVideo = 0

        prepareMediaEnvironment(mediasList[lastPlayedVideo])
    }


    private fun releasePlayer() {
        if ( videoView.isPlaying ) {
            videoView.stopPlayback()
        }
        cancelRunFaseTimer()
        imageView.visibility = View.GONE
        videoView.visibility = View.GONE
        btnVideo.visibility = View.GONE
        btnVideo.isEnabled = false

    }

    private fun prepareMediaEnvironment(media : Media) {

        when (media.mediaType) {
            Media.IMAGE -> {
                Timber.i("ZZ----> SHOW IMAGE $lastPlayedVideo  Video: ${media.filename}")

                cancelRunFaseTimer()

                imageView.visibility = View.VISIBLE
                videoView.visibility = View.GONE

                myActivity?.runOnUiThread {
                    if (media.drawable != null ) {
                        imageView.background = media.drawable
                    } else if ( media.resourceId != 0 ) {
                        imageView.setBackgroundResource(media.resourceId)
                    } else {
                        erroFatal("Imprevisto 9.123")
                    }

                    imageView.setVisibility(View.VISIBLE)

                    // Se tempo > ZERO aceita mudar imagem com click
                    // Se ZERO : Nao sai nunca da imagem (só quem chamou WaitMode pode sair da tela)
                    // Se < ZERO muda em tempo pre definido
                    if ( media.tempoApresentacao > 0  ) {
                        btnVideo.isEnabled = true
                        btnVideo.setVisibility(View.VISIBLE)
                        btnVideo.setOnClickListener{
                            try {
                                playNextVideo()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        initRunFaseTimer(media.tempoApresentacao)
                    } else {
                        btnVideo.isEnabled = false
                        btnVideo.setVisibility(View.GONE)
                        initRunFaseTimer(media.tempoApresentacao * -1)
                    }
                }
            }
            Media.VIDEO -> {

                Timber.i("ZZ----> PLAYING VIDEO $lastPlayedVideo  Video: ${media.filename}")

                imageView.visibility = View.GONE
                videoView.setVisibility(View.VISIBLE)
                videoView.visibility = View.VISIBLE
                //        btnVideo.setVisibility(View.VISIBLE)

                if ( Config.path != null) {
                    videoView.setVideoPath(media.filename)
                } else {
                    videoView.setVideoURI(Uri.parse(media.filename))
                }

                videoView.setOnCompletionListener {
                    try {
                        playNextVideo()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                videoView.start()
            }
        }
    }


    var contaRunnable=0
    var runFaseHandler: Handler = Handler()
    var runFaseRunnable: Runnable = Runnable {
        Timber.i("==> runFaseRunnable contaRunnable = ${++contaRunnable}")
        playNextVideo()
    }

    private fun initRunFaseTimer(timeout:Int) {
        if ( timeout > 0L ) {
            Timber.i("Vai fazer postDelayed runFaseRunnable ${timeout.toLong()}")
            runFaseHandler.postDelayed(runFaseRunnable, timeout.toLong() )
        } else {
            cancelRunFaseTimer()
        }
    }

    private fun cancelRunFaseTimer() {
        try {
            runFaseHandler.removeCallbacks(runFaseRunnable)
        } catch (e: Exception) {}
    }



//    private fun initRunDemoTimer() {
//        cancelRunDemoRunnable()
//        cancelRunDemoTimeoutRunnable()
//
//        if ( modoWaitingRunning ) {
//            runDemoHandler.postDelayed(runDemoRunnable, 10 * 60 * 1000 )
//        }
//    }
//
//    fun onDemoFinishedEventReturn() {
//        initRunDemoTimer()
//    }
//
//    private fun cancelRunDemoRunnable() {
//        try {
//            runDemoHandler.removeCallbacks(runDemoRunnable)
//        } catch (e: Exception) {}
//    }
//
//    private fun cancelRunDemoTimeoutRunnable() {
//        try {
//            runDemoTimeoutHandler.removeCallbacks(runDemoTimeoutRunnable)
//        } catch (e: Exception) {}
//    }

    // fw_demo (on)  Se ( Estado != FSM_IDLE ) retorna busy
    // fw_demo (on)  Se ( Estado == FSM_IDLE ) retorna: OK, fsm_state = RUNNING_DEMO
    // fw_demo (off) Se ( Estado == RUNNING_DEMO ) || ((Estado == RUNNING_DEMO_WAIT_Y) || ((Estado == RUNNING_DEMO_WAIT_XZ) retorna: OK, fsm_state = START_HOMMING
    // Enquanto estiver movimentando vai voltar "RUNNING_DEMO_WAIT_XZ" depois RUNNING_DEMO_WAIT_Y depois START_HOMMING e finalmente ????

}
