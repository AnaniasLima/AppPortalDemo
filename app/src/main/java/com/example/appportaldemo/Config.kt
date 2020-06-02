package com.example.appportaldemo

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.*


enum class ConfigType(val type: Int, val token: String) {
    SERVER                 (0, "SERVER"),
    WAITING_PEOPLE         (1, "WAITING_PEOPLE"),
    WELCOME_VIDEO          (2, "WELCOME_VIDEO"),
    CALL_HELP              (3, "CALL_HELP"),
    ALCOHOL_INSTRUCTION    (4, "ALCOHOL_INSTRUCTION"),
    FEVER_INSTRUCTION      (5, "FEVER_INSTRUCTION"),
    ENTER_INSTRUCTION      (6, "ENTER_INSTRUCTION"),
    ON_DEMO_VIDEO          (7, "ON_DEMO_VIDEO"),

    SENSOR1_VALUE   (12, "SENSOR1_VALUE"),
    SENSOR2_VALUE   (13, "SENSOR2_VALUE"),
    SENSOR3_VALUE   (14, "SENSOR3_VALUE"),
    SENSOR4_VALUE   (15, "SENSOR4_VALUE"),
    TEMPERATURE_MEASURE   (16, "TEMPERATURE_MEASURE"),
    MEDIAS_TEST     (99, "MEDIAS_TEST");
}



object Config {

    var msgErro: String? = null
    var server: Server = Server("http://vm.sger.com.br/", 1234, "", "")
    var mainActivity: AppCompatActivity? = null
    var appContext: Context? = null
    var path : File? = null // If config file is loaded from other location it will be indicate the new location

    var waitingVideo = ArrayList<Media>()
    var welcomeVideo = ArrayList<Media>()
    var helpVideo = ArrayList<Media>()
    var alcoholVideo = ArrayList<Media>()
    var feverVideo = ArrayList<Media>()
    var enterVideo = ArrayList<Media>()
    var videosDemo = ArrayList<Media>()
    var mediasTempMeasure = ArrayList<Media>()

    var mediasTest = ArrayList<Media>()

    var sensor1DistanciaDetecta: Int = 50
        var sensor2DistanciaDetecta: Int = 50
        var sensor3DistanciaDetecta: Int = 50
        var sensor4DistanciaDetecta: Int = 50

    init {
        Timber.e("===== =====  ==== Init 2222")
        printConfig()
    }


    fun getResourceId(name: String, where : String): Int {
        var resourceId = appContext!!.resources!!.getIdentifier(name, where, appContext!!.getPackageName())
        return (resourceId)
    }

    fun start(activity: AppCompatActivity, context: Context) {
        mainActivity = activity
        appContext = context
        var configInputStream : InputStream

        path = context.getExternalFilesDir(null)
        val file = File(path, "config.json")

        if ( file.isFile  ) {
            configInputStream = FileInputStream(file)
        } else {
            path = null
            configInputStream = context.resources.openRawResource(R.raw.config)
            Timber.e( "Nao Achou arquivo ")
        }
        Timber.e( "=========== path=$path    file = ${file}")

        // TODO: Ajustar para pedir permissao para usuário ao invez de habilitar permissao na mão
        if ( ! loadConfig(configInputStream )  ) {
            mainActivity?.runOnUiThread {
                (mainActivity as MainActivity).erroFatal(Config.msgErro)
            }
        }
    }



    private fun loadConfig( inputStream: InputStream) : Boolean {
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
            msgErro = "Arquivo Config.json inválido"
            Timber.e("%s: %s", msgErro, e.message.toString())
            return false
        }

        curItem = ""
        try {
            for (value in ConfigType.values()) {
                curItem = value.token
                when(value) {
                    ConfigType.SERVER                  -> server      = getServer(jsonObject.getJSONObject(value.token))
                    ConfigType.WAITING_PEOPLE           -> waitingVideo     = getVideos(jsonObject.getJSONArray(value.token))
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
                    ConfigType.TEMPERATURE_MEASURE     -> mediasTempMeasure     = getVideos(jsonObject.getJSONArray(value.token))
                    ConfigType.MEDIAS_TEST             -> mediasTest     = getVideos(jsonObject.getJSONArray(value.token))


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

                ConfigType.WAITING_PEOPLE     -> {
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


                ConfigType.TEMPERATURE_MEASURE     -> {
                    mediasTempMeasure.forEach {
                        Timber.i("%-20s Volume: %d File:[%s] Duração:[%d]", value.token, it.volume, it.filename, it.tempoApresentacao)
                    }
                }


                ConfigType.MEDIAS_TEST     -> {
                    mediasTest.forEach {
                        Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)
                    }
                }

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

    fun getIntDefault(obj:JSONObject, token:String, default:Int) : Int {
        try {
            return( obj.getInt(token) )
        } catch (e: Exception) {
            return(default)
        }
    }

    private fun getVideos(jsonArray: JSONArray): ArrayList<Media> {
        val medias = ArrayList<Media>()
        for ( x in 0 until jsonArray.length()) {
            var mediaName = jsonArray.getJSONObject(x).getString("filename")
            var volume = getIntDefault(jsonArray.getJSONObject(x), "volume", 99)
            var duracao = getIntDefault(jsonArray.getJSONObject(x), "duracao", 0)

            if ( path == null ) {
                medias.add( Media(mediaName, volume, duracao) )
            } else {

                val file = File(path, mediaName)

                if ( file.isFile  ) {
                    Timber.e( "Achou arquivo ")
                } else {
                    mainActivity?.runOnUiThread {
                        (mainActivity as MainActivity).erroFatal("Não localizou arquivo $mediaName")
                    }

                    Timber.e( "Nao Achou arquivo ")
                }

                medias.add( Media(path, mediaName, volume, duracao) )
            }
        }
        return medias
    }

}

data class Server (val host: String, val port: Int, val username: String, val password:String ) {

}

data class Media(var filename: String="") {

    companion  object {
        val VIDEO = 1
        val AUDIO = 2
        val IMAGE = 3
        val UNKNOW = 9
    }


    var volume: Int =0
    var tempoApresentacao: Int = 0
    var parent:File? = null
    var mediaType = VIDEO
    var resourceId : Int=0
    var drawable : Drawable? = null

    init {
        mediaType = fileType(filename)
        filename = getFileBasename()

        when(mediaType) {
            Media.IMAGE -> {
                if ( Config.path != null ) {
                    drawable = Drawable.createFromPath(Config.path.toString() + "/" + filename)
                } else {
                    resourceId = Config.getResourceId(filename, "drawable")
                }

                if ( (drawable == null) && (resourceId == 0) ) {
                    Config.mainActivity?.runOnUiThread {
                        (Config.mainActivity as MainActivity).erroFatal("não localizou Imagem: ${filename}")
                    }
                }

            }
            Media.VIDEO -> {
                if ( Config.path != null ) {
                    filename = Config.path.toString() + "/" + filename
                } else {
                    filename = "android.resource://" + BuildConfig.APPLICATION_ID + "/raw/" + filename
                }
            }

        }


    }

    private fun fileType(filename:String) : Int {
        var type = Media.UNKNOW
        val ind = filename.indexOfFirst { c -> (c == '.') }
        if ( ind > 0 ) {
            var fileExtension = filename.removeRange(0, ind+1)
            Timber.i(" ind: ${ind} ${fileExtension}")

            when (fileExtension) {
                "mp4" -> {
                    type = Media.VIDEO
                }
                "mp3" -> {
                    type = Media.AUDIO
                }
                "bmp",
                "png",
                "jpeg" -> {
                    type = Media.IMAGE
                }
                else -> {
                    type = Media.UNKNOW
                }
            }

            if (type == Media.IMAGE ) {
                Timber.i("Imagem")
            }
        }

        return(type)
    }

    constructor (file:String, volume:Int) : this (file) {
        this.volume = volume
        if ( this.volume < 0) {
            this.volume = 0
        }
        if (this. volume > 99) {
            this.volume = 99
        }

    }

    constructor (file:String, volume:Int, duracao:Int) : this (file, volume) {
        tempoApresentacao = duracao
    }

    constructor (path: File?, file:String, volume:Int, duracao:Int) : this (file, volume, duracao) {


        parent = path
    }


    fun getFileBasename ( ) : String {
        var file = filename
        if ( Config.path == null )  {
            val ind = filename.indexOfFirst { c -> (c == '.') }
            if ( ind > 0 ) {
                Timber.i(" ind: ${ind} ${filename.removeRange(ind, filename.length)}")
                file = filename.removeRange(ind, filename.length)
                Timber.i(" name: ${file}")
            }
        }
        return(file)
    }


}