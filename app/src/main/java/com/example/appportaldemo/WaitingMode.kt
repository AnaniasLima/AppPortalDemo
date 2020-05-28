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

enum class VideoFase {
    WAITING_PEOPLE,
    WELCOME,
    HELP,
    ALCOHOL,
    FEVER,
    ENTER
}

@SuppressLint("StaticFieldLeak")
object WaitingMode {
    var modoWaitingRunning=false

    var lastPlayedVideo: Int = -1

    lateinit private var videoView: VideoView
    lateinit private var btnVideo: Button
    private var myActivity: AppCompatActivity? = null

    lateinit var videosList: ArrayList<Media>

    fun start(mainActivity: AppCompatActivity, view: VideoView, btnInvisivel: Button) {
        myActivity = mainActivity
        videoView = view
        btnVideo = btnInvisivel
    }

    fun enterWaitingMode(fase : VideoFase) {
        modoWaitingRunning = true

        releasePlayer()
        videoView.visibility = View.VISIBLE
//        btnVideo.setVisibility(View.VISIBLE)

        initPlayer(fase)
    }

    fun leaveWaitingMode() {
        releasePlayer()

        videoView.visibility = View.GONE
        btnVideo.setVisibility(View.GONE)
        modoWaitingRunning = false
    }



    private fun playNextVideo() {
        if (++lastPlayedVideo == videosList.size ) {
            lastPlayedVideo = 0
        }
        Timber.i("PLAYING NEXT VIDEO $lastPlayedVideo  Video:${videosList[lastPlayedVideo]} Max:${videosList.size}")
        setVideoFilename(videosList[lastPlayedVideo].filename)
        videoView.start()
    }


    private fun initPlayer(fase: VideoFase) {
        videoView.setVisibility(View.VISIBLE)

        when (fase) {
            VideoFase.WAITING_PEOPLE -> videosList = Config.waitingVideo
            VideoFase.WELCOME -> videosList = Config.welcomeVideo
            VideoFase.HELP -> videosList = Config.helpVideo
            VideoFase.ALCOHOL -> videosList = Config.alcoholVideo
            VideoFase.FEVER -> videosList = Config.feverVideo
            VideoFase.ENTER -> videosList = Config.enterVideo
        }

        lastPlayedVideo = 0
        setVideoFilename(videosList[lastPlayedVideo].filename)
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
        (myActivity as MainActivity).btnInvisivel.visibility = View.GONE
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
