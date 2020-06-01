package com.example.appportaldemo

import android.annotation.SuppressLint
import android.net.Uri
import android.widget.Button
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber
import android.os.Handler
import android.view.View

@SuppressLint("StaticFieldLeak")
object  WaitingModeThread : Thread() {

    lateinit var myActivity: AppCompatActivity
    lateinit var videoView: VideoView
    lateinit var imageView: Button
    lateinit var invisibleButton: Button

    fun initialSetting( activity: AppCompatActivity,
              video: VideoView,
              image: Button,
              invisible: Button) {

        myActivity = activity
        videoView = video
        imageView =  image
        invisibleButton = invisible
    }


    var finishThread = false
    val WAITTIME_WHEN_NOT_RUNNING : Long = 50L
    val WAITTIME_WHEN_RUNNING     : Long = 20L

    var mediasList: ArrayList<Media>? = null
    var runningFase : Int = 0
    var modoWaitingRunning=false

    var lastPlayedVideo: Int = -1
    var changeVideo = false

    var runFaseHandler: Handler = Handler()
    var runFaseRunnable: Runnable = Runnable {
        Timber.i("==> runFaseRunnable changeVideo=true")
        changeVideo = true
    }

    override fun run() {
        while ( ! finishThread ) {
            if ( ! modoWaitingRunning) {
                sleep(WAITTIME_WHEN_NOT_RUNNING)
                continue
            }

            sleep(WAITTIME_WHEN_RUNNING)

            if ( (mediasList != null)  && (mediasList!!.size>0)) {
                if (  changeVideo ) {
                    if (++lastPlayedVideo == mediasList!!.size ) {
                        lastPlayedVideo = 0
                    }
                    playMedia(mediasList!![lastPlayedVideo])
                }
            }

        }
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


    fun newEnterWaitingMode(fase: Int, medias: ArrayList<Media>) {

        if ( fase == 0) {
            newLeaveWaitingMode()
            return
        }

        modoWaitingRunning = true

        if ( runningFase != 0) {
            Timber.e("ZZ Já estava rodando runningFase= ${runningFase} ")
            cancelFaseTimer()
            stopPlayingVideo()
        }

        runningFase = fase
        mediasList = medias

        Timber.e("ZZ===============>>>>>>> enterWaitingMode : ${fase} ")

        lastPlayedVideo = 0
        changeVideo=false

        myActivity.runOnUiThread {
            videoView.setOnCompletionListener {  changeVideo = true  }
            invisibleButton.setOnClickListener{  changeVideo = true  }
        }

        playMedia(mediasList!![lastPlayedVideo])
    }

    fun newLeaveWaitingMode() {
        Timber.e("ZZ<<<<<<<< =============== VideoFase : ${runningFase} ")
        cancelFaseTimer()
        stopPlayingVideo()
        myActivity.runOnUiThread {
            videoView.visibility = View.GONE
            imageView.visibility = View.GONE
            invisibleButton.visibility = View.GONE
            invisibleButton.isEnabled = false
        }
        modoWaitingRunning = false
        runningFase = 0
    }

    private fun cancelFaseTimer() {
        try {
            runFaseHandler.removeCallbacks(runFaseRunnable)
        } catch (e: Exception) {}
    }


    private fun playMedia(media : Media) {
        cancelRunFaseTimer()
        changeVideo = false
        when (media.mediaType) {
            Media.IMAGE -> {
                Timber.i("ZZ----> SHOW IMAGE $lastPlayedVideo  Video: ${media.filename}")
                myActivity.runOnUiThread {

                    // hide video view
                    videoView.visibility = View.GONE
                    imageView.visibility = View.VISIBLE

                    if (media.drawable != null ) {
                        imageView.background = media.drawable
                    } else if ( media.resourceId != 0 ) {
                        imageView.setBackgroundResource(media.resourceId)
                    } else {
                        erroFatal("Imprevisto 9.123")
                    }

                    // Se tempo > ZERO aceita mudar imagem com click
                    // Se ZERO : Nao sai nunca da imagem (só quem chamou WaitMode pode sair da tela)
                    // Se < ZERO muda em tempo pre definido
                    if ( media.tempoApresentacao > 0  ) {
                        invisibleButton.isEnabled = true
                        invisibleButton.visibility = View.VISIBLE
                        initRunFaseTimer(media.tempoApresentacao)
                    } else {
                        invisibleButton.isEnabled = false
                        invisibleButton.visibility = View.GONE
                        initRunFaseTimer(media.tempoApresentacao * -1)
                    }
                }
            }

            Media.VIDEO -> {
                Timber.i("ZZ----> PLAYING VIDEO $lastPlayedVideo  Video: ${media.filename}")
                myActivity.runOnUiThread {
                    // hide Image view
                    imageView.visibility = View.GONE
                    videoView.visibility = View.VISIBLE

                    // Disable invisibleButton
                    invisibleButton.isEnabled = false
                    invisibleButton.visibility = View.GONE

                    if ( Config.path != null) {
                        videoView.setVideoPath(media.filename)
                    } else {
                        videoView.setVideoURI(Uri.parse(media.filename))
                    }

                    videoView.setZOrderOnTop(true)
//                    videoView.setOnPreparedListener {
//                        imageView.visibility = View.GONE
//                    }
                    videoView.start()
                }
            }
        }
    }

    fun stopPlayingVideo() {
        if ( videoView.isPlaying ) {
            myActivity.runOnUiThread {
                videoView.stopPlayback()
            }
        }
    }

    fun erroFatal(str: String?) {
        myActivity.runOnUiThread {
            (myActivity as MainActivity).erroFatal(str)
        }
    }

}