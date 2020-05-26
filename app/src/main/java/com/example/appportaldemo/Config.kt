package com.example.appportaldemo

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.charset.Charset


enum class ConfigType(val type: Int, val token: String) {
    SERVER                 (0, "SERVER"),
    WAITING_VIDEO          (1, "WAITING_VIDEO"),
    WELCOME_VIDEO          (2, "WELCOME_VIDEO"),
    CALL_HELP              (3, "CALL_HELP"),
    ALCOHOL_INSTRUCTION    (4, "ALCOHOL_INSTRUCTION"),
    FEVER_INSTRUCTION      (5, "FEVER_INSTRUCTION"),
    ENTER_INSTRUCTION      (6, "ENTER_INSTRUCTION"),
    ON_DEMO_VIDEO          (7, "ON_DEMO_VIDEO"),

    SENSOR1_VALUE   (12, "SENSOR1_VALUE"),
    SENSOR2_VALUE   (13, "SENSOR2_VALUE"),
    SENSOR3_VALUE   (14, "SENSOR3_VALUE"),
    SENSOR4_VALUE   (15, "SENSOR4_VALUE");
}



object Config {

        var msgErro: String? = null
        var server: Server = Server("http://vm.sger.com.br/", 1234, "", "")


        var waitingVideo: ArrayList<Media> = arrayListOf(
            Media("V_Demo1.mp3", 99),
            Media("V_Demo2.mp3", 99)
        )

        var welcomeVideo: ArrayList<Media> = arrayListOf(
            Media("V_Demo1.mp3", 99),
            Media("V_Demo2.mp3", 99)
        )

        var helpVideo: ArrayList<Media> = arrayListOf(
            Media("V_Demo1.mp3", 99),
            Media("V_Demo2.mp3", 99)
        )

        var alcoholVideo: ArrayList<Media> = arrayListOf(
            Media("V_Demo1.mp3", 99),
            Media("V_Demo2.mp3", 99)
        )

        var feverVideo: ArrayList<Media> = arrayListOf(
            Media("V_Demo1.mp3", 99),
            Media("V_Demo2.mp3", 99)
        )

        var enterVideo: ArrayList<Media> = arrayListOf(
            Media("V_Demo1.mp3", 99),
            Media("V_Demo2.mp3", 99)
        )

        var videosDemo: ArrayList<Media> = arrayListOf(
            Media("V_Demo1.mp3", 99),
            Media("V_Demo2.mp3", 99)
        )

        var sensor1DistanciaDetecta: Int = 50
        var sensor2DistanciaDetecta: Int = 50
        var sensor3DistanciaDetecta: Int = 50
        var sensor4DistanciaDetecta: Int = 50

    init {
        Timber.e("===== =====  ==== Init 2222")
        printConfig()
    }


    fun loadConfig( context : Context, file: String, inputStream: InputStream) : Boolean {
        val jsonObject : JSONObject?
        var curItem: String

//        if ( ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) !=
//            PackageManager.PERMISSION_GRANTED ) {
//            msgErro = "\n   Sem permissao para ler arquivo   \n   " + file +  "   \n"
//            return false
//        }

        try {
            jsonObject = JSONObject(readJsonFileIns(inputStream))
        } catch (e: Exception) {
            msgErro = "Invalid file"
            Timber.e("%s: %s", msgErro, e.message.toString())
            return false
        }

        curItem = ""
        try {
            for (value in ConfigType.values()) {
                curItem = value.token
                when(value) {
                    ConfigType.SERVER                  -> server      = getServer(jsonObject.getJSONObject(value.token))
                    ConfigType.WAITING_VIDEO           -> waitingVideo     = getVideos(jsonObject.getJSONArray(value.token))
                    ConfigType.WELCOME_VIDEO           -> welcomeVideo     = getVideos(jsonObject.getJSONArray(value.token))
                    ConfigType.CALL_HELP               -> helpVideo        = getVideos(jsonObject.getJSONArray(value.token))
                    ConfigType.ALCOHOL_INSTRUCTION     -> alcoholVideo     = getVideos(jsonObject.getJSONArray(value.token))
                    ConfigType.FEVER_INSTRUCTION       -> feverVideo       = getVideos(jsonObject.getJSONArray(value.token))
                    ConfigType.ENTER_INSTRUCTION       -> enterVideo       = getVideos(jsonObject.getJSONArray(value.token))
                    ConfigType.ON_DEMO_VIDEO           -> videosDemo       = getVideos(jsonObject.getJSONArray(value.token))
                    ConfigType.SENSOR1_VALUE           -> sensor1DistanciaDetecta = jsonObject.getInt(value.token)
                    ConfigType.SENSOR2_VALUE           -> sensor2DistanciaDetecta = jsonObject.getInt(value.token)
                    ConfigType.SENSOR3_VALUE           -> sensor3DistanciaDetecta = jsonObject.getInt(value.token)
                    ConfigType.SENSOR4_VALUE           -> sensor4DistanciaDetecta = jsonObject.getInt(value.token)
                }
            }
        } catch (e: Exception) {
            msgErro = curItem
            Timber.e("Config item: %s: %s", msgErro, e.message.toString())
            return false
        }

        printConfig()
        return true
    }

    private fun printConfig() {
        for (value in ConfigType.values()) {
            when(value) {
                ConfigType.SERVER            -> Timber.i("%-20s = %s", value.token, server.toString())

                ConfigType.WAITING_VIDEO     -> {
                    waitingVideo.forEach {
                        Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)
                    }
                }

                ConfigType.WELCOME_VIDEO     -> {
                    welcomeVideo.forEach {
                        Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)
                    }
                }

                ConfigType.CALL_HELP     -> {
                    helpVideo.forEach {
                        Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)
                    }
                }

                ConfigType.ALCOHOL_INSTRUCTION     -> {
                    alcoholVideo.forEach {
                        Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)
                    }
                }

                ConfigType.FEVER_INSTRUCTION     -> {
                    feverVideo.forEach {
                        Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)
                    }
                }
                ConfigType.ENTER_INSTRUCTION     -> {
                    enterVideo.forEach {
                        Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)
                    }
                }

                ConfigType.ON_DEMO_VIDEO     -> {
                    videosDemo.forEach {
                        Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)
                    }
                }
                ConfigType.SENSOR1_VALUE          -> Timber.i("%-20s = %d", value.token, sensor1DistanciaDetecta)
                ConfigType.SENSOR2_VALUE          -> Timber.i("%-20s = %d", value.token, sensor2DistanciaDetecta)
                ConfigType.SENSOR3_VALUE          -> Timber.i("%-20s = %d", value.token, sensor3DistanciaDetecta)
                ConfigType.SENSOR4_VALUE          -> Timber.i("%-20s = %d", value.token, sensor4DistanciaDetecta)
            }
        }
    }

    fun readJsonFileIns(inputStream: InputStream): String {
        val outputStream = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        var len: Int
        try {
            while (inputStream.read(buf).also { len = it } != -1) {
                outputStream.write(buf, 0, len)
            }
            outputStream.close()
            inputStream.close()
        } catch (e: IOException) {
        }
        return outputStream.toString()
    }


    private fun getServer(jsonObject: JSONObject): Server {
        return Server(
            jsonObject.getString("host"),
            jsonObject.getInt("port"),
            jsonObject.getString("username"),
            jsonObject.getString("password")
        )
    }

    private fun getMedia(jsonObject: JSONObject): Media {
        return Media(
            jsonObject.getString("filename"),
            jsonObject.getInt("volume")
        )
    }

    private fun getVideos(jsonArray: JSONArray): ArrayList<Media> {
        val medias = ArrayList<Media>()
        for ( x in 0 until jsonArray.length()) {
            medias.add( Media(
                jsonArray.getJSONObject(x).getString("filename"),
                jsonArray.getJSONObject(x).getInt("volume")))
        }
        return medias
    }
}

data class Server (val host: String, val port: Int, val username: String, val password:String ) {

}

data class Media(var filename: String="") {

    var volume: Int =0

    constructor (file:String, volume:Int) : this (file) {
        this.volume = volume
        if ( this.volume < 0) {
            this.volume = 0
        }
        if (this. volume > 99) {
            this.volume = 99
        }
    }
}