package dev.jimmy.rfid_zebra_reader

import android.app.Activity
import android.content.Context
import androidx.annotation.NonNull
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
    }

    private lateinit var context: Context
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    
    private var activity: Activity? = null
    private var rfidHandler: RFIDHandler? = null
    private var eventStreamHandler: EventStreamHandler? = null
    private var permissionHelper: PermissionHelper? = null
    
    // Store pending result for permission requests
    private var pendingPermissionResult: Result? = null

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
            // Silent - handler creation failed
        }
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        try {
            when (call.method) {
                "initialize" -> {
                    // Auto-check and request permissions before initializing
                    autoHandlePermissions(result)
                }

                "getAllAvailableReaders" -> {
                    override fun onSuccess(data: Any?) {
                        result.success(data)
                    }
                    override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                        result.error(errorCode, errorMessage, errorDetails)
                    }
                }

                "getAllAvailableReaders" -> {
                    rfidHandler?.getAllAvailableReaders(object : RFIDHandler.ResultCallback {
                        override fun onSuccess(data: Any?) {
                            result.success(data)
                        }
                        override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                            result.error(errorCode, errorMessage, errorDetails)
                        }
                    })
                }

                "isReaderConnected" -> {
                    rfidHandler?.isReaderConnected(object : RFIDHandler.ResultCallback {
                        override fun onSuccess(data: Any?) {
                            result.success(data)
                        }
                        override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                            result.error(errorCode, errorMessage, errorDetails)
                        }
                    })
                }

                "connectReader" -> {
                    val readerName = call.argument<String>("readerName")
                    rfidHandler?.connectReader(readerName, object : RFIDHandler.ResultCallback {
                        override fun onSuccess(data: Any?) {
                            result.success(data)
                        }
                        override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                            result.error(errorCode, errorMessage, errorDetails)
                        }
                    })
                }

                "disconnectReader" -> {
                    rfidHandler?.disconnectReader(object : RFIDHandler.ResultCallback {
                        override fun onSuccess(data: Any?) {
                            result.success(data)
                        }
                        override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                            result.error(errorCode, errorMessage, errorDetails)
                        }
                    })
                }

                "startInventory" -> {
                    rfidHandler?.startInventory(object : RFIDHandler.ResultCallback {
                        override fun onSuccess(data: Any?) {
                            result.success(data)
                        }
                        override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                            result.error(errorCode, errorMessage, errorDetails)
                        }
                    })
                }

                "stopInventory" -> {
                    rfidHandler?.stopInventory(object : RFIDHandler.ResultCallback {
                        override fun onSuccess(data: Any?) {
                            result.success(data)
                        }
                        override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                            result.error(errorCode, errorMessage, errorDetails)
                        }
                    })
                }

                "setAntennaPower" -> {
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

                "getAntennaPower" -> {
                    rfidHandler?.getAntennaPower(object : RFIDHandler.ResultCallback {
                        override fun onSuccess(data: Any?) {
                            result.success(data)
                        }
                        override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                            result.error(errorCode, errorMessage, errorDetails)
                        }
                    })
                }

                "getPlatformVersion" -> {
                    val version = "Android ${android.os.Build.VERSION.RELEASE}"
                    result.success(mapOf("version" to version))
                }

                else -> {
                    result.notImplemented()
                }
            }
        } catch (e: Exception) {
            result.error("METHOD_CALL_ERROR", "Error executing ${call.method}: ${e.message}", null)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // AUTO PERMISSION HANDLER
    // ═══════════════════════════════════════════════════════════════════
    
    private fun autoHandlePermissions(result: Result) {
        try {
            val helper = permissionHelper
            
            if (helper == null || activity == null) {
                // No activity available, try to initialize anyway
                proceedWithInitialization(result)
                return
            }
            
            // Check if permissions are already granted
            if (helper.checkPermissions()) {
                proceedWithInitialization(result)
                return
            }
            
            // Store result for later
            pendingPermissionResult = result
            
            // Request permissions
            helper.requestPermissions(object : PermissionHelper.PermissionCallback {
                override fun onPermissionsGranted() {
                    // Permissions granted, proceed with initialization
                    proceedWithInitialization(pendingPermissionResult!!)
                    pendingPermissionResult = null
                }
                
                override fun onPermissionsDenied(deniedPermissions: List<String>) {
                    pendingPermissionResult?.error(
                        "PERMISSIONS_REQUIRED",
                        "RFID reader requires permissions to function",
                        mapOf(
                            "deniedPermissions" to deniedPermissions,
                            "message" to "Please grant Bluetooth and Location permissions in Settings"
                        )
                    )
                    pendingPermissionResult = null
                }
            })
        } catch (e: Exception) {
            result.error("PERMISSION_ERROR", "Failed to handle permissions: ${e.message}", null)
        }
    }
    
    private fun proceedWithInitialization(result: Result) {
        rfidHandler?.initialize(object : RFIDHandler.ResultCallback {
            override fun onSuccess(data: Any?) {
                result.success(data)
            }
            override fun onError(errorCode: String, errorMessage: String, errorDetails: Any?) {
                result.error(errorCode, errorMessage, errorDetails)
            }
        })
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // PERMISSION METHODS (kept for manual checking if needed)
    // ═══════════════════════════════════════════════════════════════════
    
    private fun checkPermissions(result: Result) {
        try {
            val helper = permissionHelper ?: run {
                result.error("NO_ACTIVITY", "Activity not available", null)
                return
            }
            
            val allGranted = helper.checkPermissions()
            result.success(allGranted)
        } catch (e: Exception) {
            result.error("CHECK_PERMISSIONS_ERROR", e.message, null)
        }
    }
    
    private fun requestPermissions(result: Result) {
        try {
            val helper = permissionHelper
            
            if (helper == null || activity == null) {
                result.error("NO_ACTIVITY", "Activity not available", null)
                return
            }
            
            // Store result for later
            pendingPermissionResult = result
            
            helper.requestPermissions(object : PermissionHelper.PermissionCallback {
                override fun onPermissionsGranted() {
                    pendingPermissionResult?.success(true)
                    pendingPermissionResult = null
                }
                
                override fun onPermissionsDenied(deniedPermissions: List<String>) {
                    pendingPermissionResult?.error(
                        "PERMISSIONS_DENIED",
                        "Some permissions were denied",
                        mapOf("deniedPermissions" to deniedPermissions)
                    )
                    pendingPermissionResult = null
                }
            })
        } catch (e: Exception) {
            result.error("REQUEST_PERMISSIONS_ERROR", e.message, null)
        }
    }
    
    private fun getPermissionStatus(result: Result) {
        try {
            val helper = permissionHelper ?: run {
                result.error("NO_ACTIVITY", "Activity not available", null)
                return
            }
            
            val status = helper.getPermissionStatus()
            result.success(status)
        } catch (e: Exception) {
            result.error("GET_STATUS_ERROR", e.message, null)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PERMISSION RESULT HANDLER
    // ═══════════════════════════════════════════════════════════════════
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        return permissionHelper?.handlePermissionResult(
            requestCode,
            permissions,
            grantResults
        ) ?: false
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTIVITY LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════
    
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        permissionHelper = PermissionHelper(context, activity)
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
        permissionHelper?.setActivity(null)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        permissionHelper?.setActivity(activity)
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
        permissionHelper?.setActivity(null)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        try {
            methodChannel.setMethodCallHandler(null)
            eventChannel.setStreamHandler(null)
            rfidHandler?.dispose()
            rfidHandler = null
            eventStreamHandler = null
            permissionHelper = null
        } catch (e: Exception) {
            // Silent
        }
    }

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