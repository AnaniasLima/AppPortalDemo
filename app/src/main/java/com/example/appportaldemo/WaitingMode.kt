package com.example.appportaldemo

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

@SuppressLint("StaticFieldLeak")
object WaitingMode {
    var modoWaitingRunning=false

    var lastPlayedVideo: Int = -1

    lateinit private var videoView: VideoView
    lateinit private var btnVideo: Button
    private var myActivity: AppCompatActivity? = null

    var runDemoTimeoutHandler: Handler = Handler()
    var runDemoTimeoutRunnable: Runnable = Runnable {
        (myActivity as MainActivity).dealWithError(ErrorType.RUN_DEMO_TIMEOUT)
    }

    var runDemoHandler: Handler = Handler()
    var runDemoRunnable: Runnable = Runnable {
        ScreenLog.add(LogType.TO_HISTORY, "chamando startRunDemo()")
        var timeout = CleaningMachine.startRunDemo()
        runDemoTimeoutHandler.postDelayed(runDemoTimeoutRunnable, timeout)
    }


    fun start(mainActivity: AppCompatActivity, view: VideoView, btnInvisivel: Button) {
        myActivity = mainActivity
        videoView = view
        btnVideo = btnInvisivel
    }

    fun enterWaitingMode() {
        if (Config.videosDemo.isEmpty() ) {
            (myActivity as MainActivity).dealWithError(ErrorType.INVALID_WAITING_MODE_VIDEOS)
            return
        }

        // Tempo Minimo aceitável (em segundos) para executar uma demo
        // TODO: ajustar depois de testar para 120
        if ( Config.demoTime < 30 ) {
            (myActivity as MainActivity).dealWithError(ErrorType.INVALID_WAITING_MODE_VIDEOS)
            return
        }

        modoWaitingRunning = true

        releasePlayer()
        videoView.visibility = View.VISIBLE
        btnVideo.setVisibility(View.VISIBLE)

        initPlayer()
        initRunDemoTimer()

    }

    fun leaveWaitingMode() {
        releasePlayer()

        cancelRunDemoTimeoutRunnable()
        cancelRunDemoRunnable()

        videoView.visibility = View.GONE
        btnVideo.setVisibility(View.INVISIBLE)
        modoWaitingRunning = false
    }



    private fun playNextVideo() {
        if (++lastPlayedVideo == Config.videosDemo.size ) {
            lastPlayedVideo = 0
        }
        Timber.i("WWW PLAYING NEXT VIDEO $lastPlayedVideo  Video:${Config.videosDemo[lastPlayedVideo]} Max:${Config.videosDemo.size}")
        setVideoFilename(Config.videosDemo[lastPlayedVideo].filename)
        videoView.start()
    }


    private fun initPlayer() {
        videoView.setVisibility(View.VISIBLE)
        lastPlayedVideo = 0
        setVideoFilename(Config.videosDemo[lastPlayedVideo].filename)
        videoView.setOnCompletionListener {
            try {
                playNextVideo()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        videoView.start()
    }

    private fun releasePlayer() {
        if ( videoView.isPlaying ) {
            videoView.stopPlayback()
        }
        videoView.visibility = View.GONE
        (myActivity as MainActivity).btnInvisivel.visibility = View.INVISIBLE
    }

    private fun setVideoFilename(filename:String) {
        if ( filename.contains('/')) {
            videoView.setVideoPath(filename)
        } else {
            var file = filename
            val ind = filename.indexOfFirst { c -> (c == '.') }
            if ( ind > 0 ) {
                Timber.i(" ind: ${ind} ${filename.removeRange(ind, filename.length)}")
                file = filename.removeRange(ind, filename.length)
                Timber.i(" name: ${file}")
            }
            videoView.setVideoURI(Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/raw/" + file))
        }
    }


    private fun initRunDemoTimer() {
        cancelRunDemoRunnable()
        cancelRunDemoTimeoutRunnable()

        if ( modoWaitingRunning ) {
            runDemoHandler.postDelayed(runDemoRunnable,Config.demoTime * 1000L )
        }
    }


    fun onDemoFinishedEventReturn() {
        initRunDemoTimer()
    }

    private fun cancelRunDemoRunnable() {
        try {
            runDemoHandler.removeCallbacks(runDemoRunnable)
        } catch (e: Exception) {}
    }

    private fun cancelRunDemoTimeoutRunnable() {
        try {
            runDemoTimeoutHandler.removeCallbacks(runDemoTimeoutRunnable)
        } catch (e: Exception) {}
    }

    // fw_demo (on)  Se ( Estado != FSM_IDLE ) retorna busy
    // fw_demo (on)  Se ( Estado == FSM_IDLE ) retorna: OK, fsm_state = RUNNING_DEMO
    // fw_demo (off) Se ( Estado == RUNNING_DEMO ) || ((Estado == RUNNING_DEMO_WAIT_Y) || ((Estado == RUNNING_DEMO_WAIT_XZ) retorna: OK, fsm_state = START_HOMMING
    // Enquanto estiver movimentando vai voltar "RUNNING_DEMO_WAIT_XZ" depois RUNNING_DEMO_WAIT_Y depois START_HOMMING e finalmente ????

}
