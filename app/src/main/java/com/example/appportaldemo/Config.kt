package com.example.appportaldemo

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.*


enum class ConfigType(val type: Int, val token: String) {
    SERVER                      (0, "SERVER"),
    GERENCIA_ENTRADA_E_SAIDA    (1, "GERENCIA_ENTRADA_E_SAIDA"),

    RESERVATORIO_GEL      (5, "RESERVATORIO_GEL"),
    RESERVATORIO_1        (6, "RESERVATORIO_1"),
    RESERVATORIO_2        (7, "RESERVATORIO_2"),
    RESERVATORIO_3        (8, "RESERVATORIO_3"),

    SENSOR1_VALUE         ( 9, "SENSOR1_VALUE"),
    SENSOR2_VALUE         (10, "SENSOR2_VALUE"),
    SENSOR3_VALUE         (11, "SENSOR3_VALUE"),
    SENSOR4_VALUE         (12, "SENSOR4_VALUE"),

    TEMPO_BOMBA_1         (13, "TEMPO_BOMBA_1"),
    TEMPO_BOMBA_2         (14, "TEMPO_BOMBA_2"),
    TEMPO_BOMBA_3         (15, "TEMPO_BOMBA_3"),
    TEMPO_BOMBA_4         (16, "TEMPO_BOMBA_4"),

    ALARME_FEBRE          (17, "ALARME_FEBRE"),

    IDLE                 (20, "IDLE"),
    UNKNOWN              (21, "UNKNOWN"),
    RESTART              (22, "RESTART"),
    WAITING_PEOPLE       (23, "WAITING_PEOPLE"),
    WAITING_THERMOMETER  (24, "WAITING_THERMOMETER"),
    CALL_HELP            (25, "CALL_HELP"),
    FEVER_PROCEDURE      (26, "FEVER_PROCEDURE"),
    ALCOHOL_PROCEDURE    (27, "ALCOHOL_PROCEDURE"),
    WAITING_ENTER        (28, "WAITING_ENTER"),
    CLEANING_PROCESS_1   (29, "CLEANING_PROCESS_1"),
    CLEANING_PROCESS_2   (30, "CLEANING_PROCESS_2"),
    CLEANING_PROCESS_3   (31, "CLEANING_PROCESS_3"),
    WAITING_FINISH       (32, "WAITING_FINISH"),
    GRANA_BOLSO          (33, "GRANA_BOLSO"),

    MEDIAS_TEST          (40, "MEDIAS_TEST");
}



object Config {

    var msgErro: String? = null
    var server: Server = Server("http://vm.sger.com.br/", 1234, "", "")
    var mainActivity: AppCompatActivity? = null
    var appContext: Context? = null
    var path : File? = null // If config file is loaded from other location it will be indicate the new location

    var idleMedias               = ArrayList<Media>()
    var restartMedias            = ArrayList<Media>()
    var unknownMedias            = ArrayList<Media>()
    var waitingPeopleMedias      = ArrayList<Media>()
    var waitingThermometerMedias = ArrayList<Media>()
    var helpMedias               = ArrayList<Media>()
    var feverMedias              = ArrayList<Media>()
    var alcoholMedias            = ArrayList<Media>()
    var waitingEnterMedias       = ArrayList<Media>()
    var cleaningProcess1Medias   = ArrayList<Media>()
    var cleaningProcess2Medias   = ArrayList<Media>()
    var cleaningProcess3Medias   = ArrayList<Media>()
    var WaitFinishMedias         = ArrayList<Media>()
    var granaNoBolsoMedias       = ArrayList<Media>()
    var testMedias               = ArrayList<Media>()

    var gerenciaEntradaESaida : Int = 0
    var capacidadeReservatorioGel: Int = 0
    var capacidadeReservatorio1: Int = 0
    var capacidadeReservatorio2: Int = 0
    var capacidadeReservatorio3: Int = 0


    var sensor1DistanciaDetecta: Int = 0
    var sensor2DistanciaDetecta: Int = 0
    var sensor3DistanciaDetecta: Int = 0
    var sensor4DistanciaDetecta: Int = 0

    var tempoBomba1 = 0
    var tempoBomba2 = 0
    var tempoBomba3 = 0
    var tempoBomba4 = 0

    var alarmeFebre : Int = 0

    init {
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
//        Timber.i( "=========== path=$path")

        val file = File(path, "config.json")

        if ( file.isFile  ) {
            configInputStream = FileInputStream(file)
        } else {
            path = null
            configInputStream = context.resources.openRawResource(R.raw.config)
            Timber.e( "Nao Achou arquivo. Vamos usar R.raw.config")
        }
        Timber.i( "=========== path=$path    file = ${file}")

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
                    ConfigType.SERVER                  -> server                     = getServer(jsonObject.getJSONObject(value.token))

                    ConfigType.GERENCIA_ENTRADA_E_SAIDA-> gerenciaEntradaESaida       = jsonObject.getInt(value.token)

                    ConfigType.RESERVATORIO_GEL         -> capacidadeReservatorioGel  = jsonObject.getInt(value.token)
                    ConfigType.RESERVATORIO_1           -> capacidadeReservatorio1    = jsonObject.getInt(value.token)
                    ConfigType.RESERVATORIO_2           -> capacidadeReservatorio2    = jsonObject.getInt(value.token)
                    ConfigType.RESERVATORIO_3           -> capacidadeReservatorio3    = jsonObject.getInt(value.token)

                    ConfigType.SENSOR1_VALUE           -> sensor1DistanciaDetecta = jsonObject.getInt(value.token)
                    ConfigType.SENSOR2_VALUE           -> sensor2DistanciaDetecta = jsonObject.getInt(value.token)
                    ConfigType.SENSOR3_VALUE           -> sensor3DistanciaDetecta = jsonObject.getInt(value.token)
                    ConfigType.SENSOR4_VALUE           -> sensor4DistanciaDetecta = jsonObject.getInt(value.token)

                    ConfigType.TEMPO_BOMBA_1           -> tempoBomba1 = jsonObject.getInt(value.token)
                    ConfigType.TEMPO_BOMBA_2           -> tempoBomba2 = jsonObject.getInt(value.token)
                    ConfigType.TEMPO_BOMBA_3           -> tempoBomba3 = jsonObject.getInt(value.token)
                    ConfigType.TEMPO_BOMBA_4           -> tempoBomba4 = jsonObject.getInt(value.token)

                    ConfigType.ALARME_FEBRE             -> alarmeFebre                = jsonObject.getInt(value.token)

                    ConfigType.IDLE                    -> idleMedias                 = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.UNKNOWN                 -> unknownMedias              = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.RESTART                 -> restartMedias              = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.WAITING_PEOPLE          -> waitingPeopleMedias        = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.WAITING_THERMOMETER     -> waitingThermometerMedias   = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.CALL_HELP               -> helpMedias                 = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.FEVER_PROCEDURE         -> feverMedias                = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.ALCOHOL_PROCEDURE       -> alcoholMedias              = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.WAITING_ENTER           -> waitingEnterMedias         = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.CLEANING_PROCESS_1      -> cleaningProcess1Medias     = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.CLEANING_PROCESS_2      -> cleaningProcess2Medias     = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.CLEANING_PROCESS_3      -> cleaningProcess3Medias     = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.WAITING_FINISH          -> WaitFinishMedias           = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.GRANA_BOLSO             -> granaNoBolsoMedias         = getMedias(jsonObject.getJSONArray(value.token))
                    ConfigType.MEDIAS_TEST             -> testMedias                 = getMedias(jsonObject.getJSONArray(value.token))
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
                ConfigType.SERVER                  -> Timber.i("%-20s = %s", value.token, server.toString())
                ConfigType.GERENCIA_ENTRADA_E_SAIDA-> Timber.i("%-20s = %d", value.token, gerenciaEntradaESaida)

                ConfigType.RESERVATORIO_GEL        -> Timber.i("%-20s = %d", value.token, capacidadeReservatorioGel)
                ConfigType.RESERVATORIO_1          -> Timber.i("%-20s = %d", value.token, capacidadeReservatorio1)
                ConfigType.RESERVATORIO_2          -> Timber.i("%-20s = %d", value.token, capacidadeReservatorio2)
                ConfigType.RESERVATORIO_3          -> Timber.i("%-20s = %d", value.token, capacidadeReservatorio3)

                ConfigType.SENSOR1_VALUE          -> Timber.i("%-20s = %d", value.token, sensor1DistanciaDetecta)
                ConfigType.SENSOR2_VALUE          -> Timber.i("%-20s = %d", value.token, sensor2DistanciaDetecta)
                ConfigType.SENSOR3_VALUE          -> Timber.i("%-20s = %d", value.token, sensor3DistanciaDetecta)
                ConfigType.SENSOR4_VALUE          -> Timber.i("%-20s = %d", value.token, sensor4DistanciaDetecta)

                ConfigType.TEMPO_BOMBA_1          -> Timber.i("%-20s = %d", value.token, tempoBomba1)
                ConfigType.TEMPO_BOMBA_2          -> Timber.i("%-20s = %d", value.token, tempoBomba2)
                ConfigType.TEMPO_BOMBA_3          -> Timber.i("%-20s = %d", value.token, tempoBomba3)
                ConfigType.TEMPO_BOMBA_4          -> Timber.i("%-20s = %d", value.token, tempoBomba4)

                ConfigType.ALARME_FEBRE            -> Timber.i("%-20s = %s", value.token, alarmeFebre)

                ConfigType.IDLE                -> { idleMedias               .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.UNKNOWN             -> { unknownMedias            .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.RESTART             -> { restartMedias            .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.WAITING_PEOPLE      -> { waitingPeopleMedias      .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.WAITING_THERMOMETER -> { waitingThermometerMedias .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.CALL_HELP           -> { helpMedias               .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.FEVER_PROCEDURE     -> { feverMedias              .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.ALCOHOL_PROCEDURE   -> { alcoholMedias            .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.WAITING_ENTER       -> { waitingEnterMedias       .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.CLEANING_PROCESS_1  -> { cleaningProcess1Medias   .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.CLEANING_PROCESS_2  -> { cleaningProcess2Medias   .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.CLEANING_PROCESS_3  -> { cleaningProcess3Medias   .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.WAITING_FINISH      -> { WaitFinishMedias         .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.GRANA_BOLSO         -> { granaNoBolsoMedias       .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
                ConfigType.MEDIAS_TEST         -> { testMedias               .forEach { Timber.i("%-20s Volume: %d File:[%s]", value.token, it.volume, it.filename)} }
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

    private fun getMedias(jsonArray: JSONArray): ArrayList<Media> {
        val medias = ArrayList<Media>()
        for ( x in 0 until jsonArray.length()) {
            var mediaName = jsonArray.getJSONObject(x).getString("filename")
            var volume = getIntDefault(jsonArray.getJSONObject(x), "volume", 99)
            var duracao = getIntDefault(jsonArray.getJSONObject(x), "duracao", 0)

            if ( path == null ) {
                medias.add( Media(mediaName, volume, duracao) )
            } else {

                val file = File(path, mediaName)

                if ( ! file.isFile  ) {
                    mainActivity?.runOnUiThread {
                        (mainActivity as MainActivity).erroFatal("Não localizou arquivo $mediaName")
                    }
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

            Media.AUDIO -> {
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
            when (fileExtension) {
                "mp4" -> {
                    type = Media.VIDEO
                }
                "amr",
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
                file = filename.removeRange(ind, filename.length)
            }
        }
        return(file)
    }
}