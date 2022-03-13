package com.example.vitaldatasender

import android.Manifest
import android.content.BroadcastReceiver
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import com.example.vitaldatasender.databinding.ActivityMainBinding
import com.google.gson.GsonBuilder
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.io.File
import java.io.FileWriter
import java.util.*


interface VitalSender{
    @Headers("Accept: application/json")
    @POST("data/upload")
    fun postFile(@Body body: MultipartBody): Call<String>
}

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    object Config {
        const val CONF_HTTP_HOSTNAME = "HTTP_HOSTNAME"
        const val CONF_HTTP_PORT = "HTTP_PORT"
        const val CONF_PERSON_ID = "PERSON_ID"

        const val CONF_HTTP_HOSTNAME_DEFAULT = "192.168.11.4"
        const val CONF_HTTP_PORT_DEFAULT = 12345

        const val CONF_BROADCAST_HEARTRATE_UPDATE = "vitalstore_forwearos.updateHeartRate"
        const val CONF_BROADCAST_STATUS = "vitalstore_forwearos.updateStatus"

    }

    private lateinit var textStatus: TextView
    private lateinit var mStatusUpdateReceiver: BroadcastReceiver
    private lateinit var textCurrentHr: TextView
    private lateinit var binding: ActivityMainBinding
    private lateinit var startButton: Button

    private lateinit var ambientController: AmbientModeSupport.AmbientController

    private lateinit var preferences: SharedPreferences

    private lateinit var mHeartRateUpdateReceiver: BroadcastReceiver


    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = MyAmbientCallback()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            101
        )

        ambientController = AmbientModeSupport.attach(this)
        initConfig()
        bindConfigToInputs()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BODY_SENSORS
            ) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS), 100)
        }

        startButton = findViewById(R.id.buttonStartStop)

        wireButton()


    }


    private fun wireButton() {

        startButton.setOnClickListener {
            mkDeviceID()

            val dir = File("/storage/self/primary/Android/data/com.example.vitalstore_forwearos/files")
            val files = dir.listFiles()
            files.forEach {
                sendVital(it)
                Log.d("file: ", it.name)
            }
        }
    }
    private fun sendVital(fileContent: File) {
        val httpUrl = "http://" +
                preferences.getString(
                    MainActivity.Config.CONF_HTTP_HOSTNAME,
                    MainActivity.Config.CONF_HTTP_HOSTNAME_DEFAULT
                ) +
                ":" + preferences.getInt(
            MainActivity.Config.CONF_HTTP_PORT,
            MainActivity.Config.CONF_HTTP_PORT_DEFAULT
        ).toString() + "/"

        val fileReqBody: RequestBody = RequestBody.create("audio/*".toMediaTypeOrNull(), fileContent)


        val boundary = UUID.randomUUID().toString()
        val body: MultipartBody = MultipartBody.Builder("---$boundary")
            .setType(MultipartBody.FORM)
            .addFormDataPart("watch_id", "jjjff")
            .addFormDataPart("file_content", fileContent.getName(), fileReqBody)
            .build()

//        val service: VitalSender =
//            ServiceGenerator.createService(VitalSender::class.java)
//
//        val file = File("/storage/self/primary/Android/data/com.example.vitalstore_forwearos/files")
//        val requestBody = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
        val gson = GsonBuilder()
            .serializeNulls()
            .create()
        val service = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create(gson))
            .baseUrl(httpUrl)
            .build()
            .create(VitalSender::class.java)

        val call: Call<String> = service.postFile(body)
        call.enqueue(object :Callback<String> {
            override fun onFailure(call: Call<String>, t: Throwable) {

            }

            override fun onResponse(call: Call<String>, response: Response<String>) {

            }
        })
        Log.d("jjjj","jlkjljlj")

    }

    private fun mkDeviceID() {
        val idFile = File("/storage/self/primary/Android/data/com.example.vitalstore_forwearos/files/identifier.txt")
        if(idFile.exists()){
            return
        }

        val id = UUID.randomUUID().toString()
        val filewriter = FileWriter(idFile, false)
        filewriter.write(id)
        filewriter.close()
    }



    private fun bindConfigToInputs() {
        val hostnameInput = findViewById<EditText>(R.id.editTextHostname)
        val portInput = findViewById<EditText>(R.id.editTextPort)

        hostnameInput.setText(
            preferences.getString(
                Config.CONF_HTTP_HOSTNAME,
                Config.CONF_HTTP_HOSTNAME_DEFAULT
            )
        )
        portInput.setText(
            preferences.getInt(Config.CONF_HTTP_PORT, Config.CONF_HTTP_PORT_DEFAULT).toString()
        )

        hostnameInput.doAfterTextChanged {
            with(preferences.edit()) {
                putString(Config.CONF_HTTP_HOSTNAME, it.toString())
                apply()
                Log.d("config", "Saved new Hostname: " + it.toString())
            }
        }

        portInput.doAfterTextChanged {
            with(preferences.edit()) {
                putInt(Config.CONF_HTTP_PORT, it.toString().toIntOrNull() ?: Config.CONF_HTTP_PORT_DEFAULT)
                apply()
                Log.d("config", "Saved new Port: " + it.toString())
            }
        }

    }

    private fun initConfig() {
        preferences = this.getSharedPreferences(packageName + "_preferences", MODE_PRIVATE)

        with(preferences.edit()) {
            if (!preferences.contains(Config.CONF_HTTP_HOSTNAME)) {
                putString(Config.CONF_HTTP_HOSTNAME, Config.CONF_HTTP_HOSTNAME_DEFAULT)
            }

            if (!preferences.contains(Config.CONF_HTTP_PORT)) {
                putInt(Config.CONF_HTTP_PORT, Config.CONF_HTTP_PORT_DEFAULT)
            }
            apply()
        }
    }


    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }



}

private class MyAmbientCallback : AmbientModeSupport.AmbientCallback() {

    override fun onEnterAmbient(ambientDetails: Bundle?) {
        // Handle entering ambient mode
    }

    override fun onExitAmbient() {
        // Handle exiting ambient mode
    }

    override fun onUpdateAmbient() {
        // Update the content
    }
}

