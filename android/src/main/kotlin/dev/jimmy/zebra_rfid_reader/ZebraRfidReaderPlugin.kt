package dev.jimmy.zebra_rfid_reader

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.EventChannel
import androidx.annotation.NonNull
import android.content.Context

class ZebraRfidReaderPlugin : FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {

    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private lateinit var rfidHandler: RFIDHandler
    private lateinit var context: Context

    companion object {
        private const val TAG = "ZebraRfidReaderPlugin"
        private const val METHOD_CHANNEL_NAME = "zebra_rfid_reader"
        private const val EVENT_CHANNEL_NAME = "zebra_rfid_reader/events"
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext

        // Setup method channel
        methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, METHOD_CHANNEL_NAME)
        methodChannel.setMethodCallHandler(this)

        // Setup event channel
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, EVENT_CHANNEL_NAME)
        eventChannel.setStreamHandler(this)

        // Initialize RFID handler
        rfidHandler = RFIDHandler(context)
        rfidHandler.initialize()
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "checkReaderConnection" -> {
                val isConnected = rfidHandler.isConnected
                result.success(if (isConnected) "Connected" else "Not connected")
            }

            "connect" -> {
                rfidHandler.connectReader { message ->
                    result.success(message)
                }
            }

            "disconnect" -> {
                rfidHandler.disconnect()
                result.success("Disconnected")
            }

            "isConnected" -> {
                result.success(rfidHandler.isConnected)
            }

            "startInventory" -> {
                rfidHandler.startInventory { message ->
                    result.success(message)
                }
            }

            "stopInventory" -> {
                rfidHandler.stopInventory { message ->
                    result.success(message)
                }
            }

            "setAntennaPower" -> {
                val powerLevel = call.argument<Int>("powerLevel")
                if (powerLevel != null) {
                    rfidHandler.setAntennaPower(powerLevel) { message ->
                        result.success(message)
                    }
                } else {
                    result.error("INVALID_ARGUMENT", "Power level is required", null)
                }
            }

            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }

            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        rfidHandler.dispose()
    }

    // EventChannel.StreamHandler implementation
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        rfidHandler.setEventSink(events)
    }

    override fun onCancel(arguments: Any?) {
        rfidHandler.setEventSink(null)
    }
}

//package dev.jimmy.zebra_rfid_reader
//
//import io.flutter.embedding.engine.plugins.FlutterPlugin
//import io.flutter.plugin.common.MethodCall
//import io.flutter.plugin.common.MethodChannel
//import io.flutter.plugin.common.MethodChannel.MethodCallHandler
//import io.flutter.plugin.common.MethodChannel.Result
//import androidx.annotation.NonNull
//
//class ZebraRfidReaderPlugin : FlutterPlugin, MethodCallHandler {
//     private lateinit var channel: MethodChannel
//
//    private lateinit var rfidManager: RFIDHandler
//
//    companion object {
//        private const val TAG = "ZebraRfidReader"
//        private const val CHANNEL_NAME = "zebra_rfid_reader"
//    }
//
//    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
//        channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL_NAME)
//        channel.setMethodCallHandler(this)
//
//        rfidHandler = RFIDHandler()
//        rfidHandler.onCreate(this)
//    }
//
//
//    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
//        when (call.method) {
//            "checkReaderConnection" -> {
//               result.success("method - checkReaderConnection")
//            }
//
//            "startScanning" -> {
//                result.success("method - startScanning")
//            }
//
//            "stopScanning" -> {
//                result.success("method - stopScanning")
//            }
//
//            "disconnect" -> {
//                result.success("method - disconnect")
//            }
//
//            "isConnected" -> {
//                result.success("method - isConnected")
//            }
//
////            "startInventory" -> {
////                rfidManager?.startInventory(result)
////            }
////
////            "stopInventory" -> {
////                rfidManager?.stopInventory(result)
////            }
////
////            "writeTag" -> {
////                val tagId = call.argument<String>("tagId")
////                val data = call.argument<String>("data")
////                val memoryBank = call.argument<String>("memoryBank") ?: "EPC"
////                val offset = call.argument<Int>("offset") ?: 0
////
////                if (tagId != null && data != null) {
////                    rfidManager?.writeTag(tagId, data, memoryBank, offset, result)
////                } else {
////                    result.error("INVALID_ARGUMENT", "TagId and data are required", null)
////                }
////            }
////
////            "startLocationing" -> {
////                val tagId = call.argument<String>("tagId")
////                if (tagId != null) {
////                    rfidManager?.startLocationing(tagId, result)
////                } else {
////                    result.error("INVALID_ARGUMENT", "TagId is required", null)
////                }
////            }
////
////            "stopLocationing" -> {
////                rfidManager?.stopLocationing(result)
////            }
////
////            "configureReader" -> {
////                val config = call.arguments as? Map<String, Any>
////                if (config != null) {
////                    rfidManager?.configureReader(config, result)
////                } else {
////                    result.error("INVALID_ARGUMENT", "Configuration is required", null)
////                }
////            }
////
////            "getReaderConfig" -> {
////                rfidManager?.getReaderConfig(result)
////            }
////
////            "setAntennaPower" -> {
////                val powerLevel = call.argument<Int>("powerLevel")
////                if (powerLevel != null) {
////                    rfidManager?.setAntennaPower(powerLevel, result)
////                } else {
////                    result.error("INVALID_ARGUMENT", "Power level is required", null)
////                }
////            }
////
////            "setBeeper" -> {
////                val enabled = call.argument<Boolean>("enabled") ?: false
////                rfidManager?.setBeeper(enabled, result)
////            }
////
////            "getBatteryLevel" -> {
////                rfidManager?.getBatteryLevel(result)
////            }
////
////            "dispose" -> {
////                rfidManager?.dispose(result)
////            }
////
////            else -> {
////                result.notImplemented()
////            }
//        }
//    }
//
//    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
//        channel.setMethodCallHandler(null)
//        rfidManager = null
//    }
//
//}
