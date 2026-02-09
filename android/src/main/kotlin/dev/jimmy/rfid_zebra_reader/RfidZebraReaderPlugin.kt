package dev.jimmy.rfid_zebra_reader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

class RfidZebraReaderPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.RequestPermissionsResultListener {

    companion object {
        private const val CHANNEL_NAME = "rfid_zebra_reader"
        private const val EVENT_CHANNEL = "rfid_zebra_reader/events"
        private const val PERMISSION_REQUEST_CODE = 1001
        
        private val PERMISSIONS_ANDROID_12_BELOW = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        private val PERMISSIONS_ANDROID_12_AND_ABOVE = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private lateinit var context: Context
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    
    private var activity: Activity? = null
    private var rfidHandler: RFIDHandler? = null
    private var eventStreamHandler: EventStreamHandler? = null
    
    private var pendingInitResult: Result? = null

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext

        methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL_NAME)
        methodChannel.setMethodCallHandler(this)

        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, EVENT_CHANNEL)
        eventStreamHandler = EventStreamHandler()
        eventChannel.setStreamHandler(eventStreamHandler)

        try {
            rfidHandler = RFIDHandler(context, eventStreamHandler!!, eventStreamHandler!!)
        } catch (e: Exception) {
            log("Plugin", "Failed to create RFIDHandler: ${e.message}")
        }
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        try {
            when (call.method) {
                // Main initialization - handles permissions + SDK + connect
                "initialize" -> handleInitialize(result)
                
                // Status checks
                "getStatus" -> handleGetStatus(result)
                "isPermissionGranted" -> handleIsPermissionGranted(result)
                
                // Manual controls
                "disconnect" -> handleDisconnect(result)
                "startInventory" -> handleStartInventory(result)
                "stopInventory" -> handleStopInventory(result)
                "setAntennaPower" -> handleSetAntennaPower(call, result)
                "getAntennaPower" -> handleGetAntennaPower(result)
                
                // Utility
                "getPlatformVersion" -> {
                    result.success(mapOf("version" to "Android ${Build.VERSION.RELEASE}"))
                }

                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            log("Plugin", "Method call error: ${e.message}")
            result.error("METHOD_ERROR", "Error executing ${call.method}: ${e.message}", null)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INITIALIZE - Auto permissions + SDK + Connect
    // ═══════════════════════════════════════════════════════════════════

    private fun handleInitialize(result: Result) {
        log("Plugin", "Initialize called")
        
        // Check permissions first
        if (!checkPermissions()) {
            log("Plugin", "Permissions not granted, requesting...")
            
            if (activity == null) {
                result.success(mapOf(
                    "permissionsGranted" to false,
                    "sdkInitialized" to false,
                    "readerConnected" to false,
                    "readerName" to null,
                    "error" to "Activity not available for permission request"
                ))
                return
            }
            
            pendingInitResult = result
            requestPermissions()
            return
        }
        
        // Permissions granted, proceed with auto-init
        proceedWithAutoInit(result)
    }

    private fun proceedWithAutoInit(result: Result) {
        log("Plugin", "Proceeding with auto initialization")
        
        rfidHandler?.autoInitialize(object : RFIDHandler.ResultCallback {
            override fun onSuccess(data: Any?) {
                result.success(data)
            }
            override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                result.success(mapOf(
                    "permissionsGranted" to true,
                    "sdkInitialized" to (rfidHandler?.isSdkInitialized() ?: false),
                    "readerConnected" to false,
                    "readerName" to null,
                    "error" to errorMessage
                ))
            }
        })
    }

    // ═══════════════════════════════════════════════════════════════════
    // PERMISSION HANDLING
    // ═══════════════════════════════════════════════════════════════════

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PERMISSIONS_ANDROID_12_AND_ABOVE
        } else {
            PERMISSIONS_ANDROID_12_BELOW
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        activity?.let {
            val permissions = getRequiredPermissions()
            val deniedPermissions = permissions.filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
            }
            
            if (deniedPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    it,
                    deniedPermissions.toTypedArray(),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun handleIsPermissionGranted(result: Result) {
        val granted = checkPermissions()
        val permissions = getRequiredPermissions()
        val status = mutableMapOf<String, Boolean>()
        
        permissions.forEach { permission ->
            val name = permission.substringAfterLast(".")
            status[name] = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        result.success(mapOf(
            "granted" to granted,
            "permissions" to status,
            "androidVersion" to Build.VERSION.SDK_INT
        ))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode != PERMISSION_REQUEST_CODE) return false
        
        val allGranted = grantResults.isNotEmpty() && 
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        
        log("Plugin", "Permission result: allGranted=$allGranted")
        
        pendingInitResult?.let { result ->
            if (allGranted) {
                proceedWithAutoInit(result)
            } else {
                val deniedList = permissions.filterIndexed { index, _ ->
                    grantResults.getOrNull(index) != PackageManager.PERMISSION_GRANTED
                }.map { it.substringAfterLast(".") }
                
                result.success(mapOf(
                    "permissionsGranted" to false,
                    "sdkInitialized" to false,
                    "readerConnected" to false,
                    "readerName" to null,
                    "error" to "Permissions denied: ${deniedList.joinToString(", ")}"
                ))
            }
            pendingInitResult = null
        }
        
        return true
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATUS
    // ═══════════════════════════════════════════════════════════════════

    private fun handleGetStatus(result: Result) {
        val permissionsGranted = checkPermissions()
        
        rfidHandler?.getFullStatus(object : RFIDHandler.ResultCallback {
            override fun onSuccess(data: Any?) {
                val status = (data as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()
                status["permissionsGranted"] = permissionsGranted
                result.success(status)
            }
            override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                result.success(mapOf(
                    "permissionsGranted" to permissionsGranted,
                    "sdkInitialized" to false,
                    "readerConnected" to false,
                    "readerName" to null,
                    "error" to errorMessage
                ))
            }
        })
    }

    // ═══════════════════════════════════════════════════════════════════
    // MANUAL CONTROLS
    // ═══════════════════════════════════════════════════════════════════

    private fun handleDisconnect(result: Result) {
        rfidHandler?.disconnectReader(object : RFIDHandler.ResultCallback {
            override fun onSuccess(data: Any?) {
                result.success(data)
            }
            override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                result.error(errorCode, errorMessage, errorDetails)
            }
        })
    }

    private fun handleStartInventory(result: Result) {
        rfidHandler?.startInventory(object : RFIDHandler.ResultCallback {
            override fun onSuccess(data: Any?) {
                result.success(data)
            }
            override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                result.error(errorCode, errorMessage, errorDetails)
            }
        })
    }

    private fun handleStopInventory(result: Result) {
        rfidHandler?.stopInventory(object : RFIDHandler.ResultCallback {
            override fun onSuccess(data: Any?) {
                result.success(data)
            }
            override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                result.error(errorCode, errorMessage, errorDetails)
            }
        })
    }

    private fun handleSetAntennaPower(call: MethodCall, result: Result) {
        val powerLevel = call.argument<Int>("powerLevel")
        if (powerLevel == null) {
            result.error("INVALID_ARGUMENT", "powerLevel is required", null)
            return
        }
        
        rfidHandler?.setAntennaPower(powerLevel, object : RFIDHandler.ResultCallback {
            override fun onSuccess(data: Any?) {
                result.success(data)
            }
            override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                result.error(errorCode, errorMessage, errorDetails)
            }
        })
    }

    private fun handleGetAntennaPower(result: Result) {
        rfidHandler?.getAntennaPower(object : RFIDHandler.ResultCallback {
            override fun onSuccess(data: Any?) {
                result.success(data)
            }
            override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                result.error(errorCode, errorMessage, errorDetails)
            }
        })
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    private fun log(tag: String, message: String) {
        android.util.Log.d("RfidZebraPlugin", "[$tag] $message")
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTIVITY LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════
    
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        try {
            methodChannel.setMethodCallHandler(null)
            eventChannel.setStreamHandler(null)
            rfidHandler?.dispose()
            rfidHandler = null
            eventStreamHandler = null
        } catch (e: Exception) {
            log("Plugin", "Detach error: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT STREAM HANDLER
    // ═══════════════════════════════════════════════════════════════════

    class EventStreamHandler : EventChannel.StreamHandler {
        private var eventSink: EventChannel.EventSink? = null

        override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
            eventSink = events
        }

        override fun onCancel(arguments: Any?) {
            eventSink = null
        }

        fun sendEvent(event: Map<String, Any?>) {
            eventSink?.success(event)
        }

        fun sendError(errorCode: String, errorMessage: String, errorDetails: Any?) {
            eventSink?.error(errorCode, errorMessage, errorDetails)
        }
    }
}