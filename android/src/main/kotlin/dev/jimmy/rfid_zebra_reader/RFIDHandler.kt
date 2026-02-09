package dev.jimmy.rfid_zebra_reader

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.zebra.rfid.api3.*
import java.util.ArrayList

class RFIDHandler(
    private val context: Context,
    private val tagReadEventSink: RfidZebraReaderPlugin.EventStreamHandler,
    private val statusEventSink: RfidZebraReaderPlugin.EventStreamHandler
) : Readers.RFIDReaderEventHandler {

    private var readers: Readers? = null
    private var availableRFIDReaderList: ArrayList<ReaderDevice>? = null
    private var readerDevice: ReaderDevice? = null
    private var reader: RFIDReader? = null
    private var eventHandler: EventHandler? = null

    @Volatile
    private var isInitialized = false
    
    @Volatile
    private var isConnectedFlag = false
    
    @Volatile
    private var connectedReaderName: String? = null
    
    @Volatile
    private var isReconnecting = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var maxPower = 270
    
    // Reconnect settings
    private val reconnectDelayMs = 3000L
    private val maxReconnectAttempts = 5
    private var reconnectAttempts = 0

    interface ResultCallback {
        fun onSuccess(data: Any?)
        fun onError(errorCode: String, errorMessage: String, errorDetails: Any?)
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET CURRENT STATUS
    // ═══════════════════════════════════════════════════════════════════

    fun getStatus(): Map<String, Any?> {
        return mapOf(
            "sdkInitialized" to isInitialized,
            "readerConnected" to isConnectedFlag,
            "readerName" to connectedReaderName,
            "isReconnecting" to isReconnecting
        )
    }

    fun isSdkInitialized(): Boolean = isInitialized
    
    fun isReaderConnected(): Boolean = isConnectedFlag && reader?.isConnected == true

    // ═══════════════════════════════════════════════════════════════════
    // AUTO INITIALIZE - SDK + CONNECT
    // ═══════════════════════════════════════════════════════════════════

    fun autoInitialize(callback: ResultCallback) {
        log("AUTO_INIT", "Starting auto initialization...")
        sendStatusEvent("initializing", "Starting auto initialization...")

        Thread {
            try {
                // Step 1: Initialize SDK
                if (!isInitialized) {
                    log("AUTO_INIT", "Step 1: Initializing SDK...")
                    val sdkResult = initializeSdkSync()
                    
                    if (!sdkResult.success) {
                        log("AUTO_INIT", "SDK initialization failed: ${sdkResult.error}")
                        mainHandler.post {
                            callback.onSuccess(mapOf(
                                "permissionsGranted" to true,
                                "sdkInitialized" to false,
                                "readerConnected" to false,
                                "readerName" to null,
                                "error" to sdkResult.error
                            ))
                        }
                        return@Thread
                    }
                    log("AUTO_INIT", "SDK initialized successfully")
                }

                // Step 2: Connect to reader
                log("AUTO_INIT", "Step 2: Connecting to reader...")
                val connectResult = connectReaderSync(null)
                
                if (!connectResult.success) {
                    log("AUTO_INIT", "Reader connection failed: ${connectResult.error}")
                    mainHandler.post {
                        callback.onSuccess(mapOf(
                            "permissionsGranted" to true,
                            "sdkInitialized" to true,
                            "readerConnected" to false,
                            "readerName" to null,
                            "error" to connectResult.error
                        ))
                    }
                    return@Thread
                }

                log("AUTO_INIT", "Auto initialization completed successfully!")
                sendStatusEvent("ready", "Connected to ${connectedReaderName}", mapOf(
                    "readerName" to connectedReaderName
                ))

                mainHandler.post {
                    callback.onSuccess(mapOf(
                        "permissionsGranted" to true,
                        "sdkInitialized" to true,
                        "readerConnected" to true,
                        "readerName" to connectedReaderName,
                        "error" to null
                    ))
                }

            } catch (e: Exception) {
                log("AUTO_INIT", "Exception: ${e.message}")
                mainHandler.post {
                    callback.onSuccess(mapOf(
                        "permissionsGranted" to true,
                        "sdkInitialized" to isInitialized,
                        "readerConnected" to isConnectedFlag,
                        "readerName" to connectedReaderName,
                        "error" to "Exception: ${e.message}"
                    ))
                }
            }
        }.start()
    }

    // ═══════════════════════════════════════════════════════════════════
    // SYNCHRONOUS HELPERS FOR AUTO INIT
    // ═══════════════════════════════════════════════════════════════════

    private data class SyncResult(val success: Boolean, val error: String? = null)

    private fun initializeSdkSync(): SyncResult {
        try {
            if (isInitialized) {
                return SyncResult(true)
            }

            log("SDK_INIT", "Device: ${Build.MODEL} | Android: ${Build.VERSION.RELEASE}")

            val transports = listOf(
                ENUM_TRANSPORT.BLUETOOTH,
                ENUM_TRANSPORT.SERVICE_USB,
                ENUM_TRANSPORT.SERVICE_SERIAL
            )

            for (transport in transports) {
                try {
                    log("SDK_INIT", "Trying transport: $transport")

                    val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Android13ContextWrapper(context)
                    } else {
                        context
                    }

                    readers = Readers(safeContext, transport)

                    val list = readers?.GetAvailableRFIDReaderList()
                    val count = list?.size ?: 0
                    log("SDK_INIT", "Found $count reader(s) on $transport")

                    if (count > 0) {
                        availableRFIDReaderList = list
                        isInitialized = true
                        log("SDK_INIT", "SUCCESS on $transport!")
                        
                        list?.forEachIndexed { index, device ->
                            log("SDK_INIT", "Reader[$index]: ${device.getName()}")
                        }
                        
                        return SyncResult(true)
                    }
                } catch (e: InvalidUsageException) {
                    log("SDK_INIT", "InvalidUsageException on $transport: ${e.info}")
                } catch (e: Exception) {
                    log("SDK_INIT", "Exception on $transport: ${e.message}")
                }
            }

            return SyncResult(false, "No RFID readers found on any transport")

        } catch (e: Exception) {
            return SyncResult(false, "SDK initialization error: ${e.message}")
        }
    }

    private fun connectReaderSync(readerName: String?): SyncResult {
        try {
            // Check if already connected
            if (reader != null && reader!!.isConnected) {
                isConnectedFlag = true
                connectedReaderName = reader!!.hostName
                log("CONNECT", "Already connected to $connectedReaderName")
                return SyncResult(true)
            }

            // Refresh reader list
            refreshAvailableReaders()

            if (availableRFIDReaderList.isNullOrEmpty()) {
                log("CONNECT", "No readers in list")
                return SyncResult(false, "No readers available. Make sure Bluetooth is enabled and the reader is powered on.")
            }

            // Select reader
            if (readerName != null) {
                val foundReader = availableRFIDReaderList!!.find {
                    it.getName().contains(readerName, ignoreCase = true)
                }
                if (foundReader != null) {
                    readerDevice = foundReader
                    reader = readerDevice?.rfidReader
                    log("CONNECT", "Selected reader by name: ${foundReader.getName()}")
                } else {
                    return SyncResult(false, "Reader '$readerName' not found")
                }
            } else {
                readerDevice = availableRFIDReaderList!![0]
                reader = readerDevice?.rfidReader
                log("CONNECT", "Auto-selected reader: ${readerDevice?.getName()}")
            }

            // Connect with retry logic
            reader?.let { rfidReader ->
                if (!rfidReader.isConnected) {
                    val hostname = rfidReader.hostName ?: readerDevice?.getName() ?: "Unknown"
                    log("CONNECT", "Attempting connection to $hostname...")

                    // First attempt
                    try {
                        rfidReader.connect()
                        log("CONNECT", "connect() completed")
                    } catch (e: OperationFailureException) {
                        log("CONNECT", "First connect attempt failed: ${e.vendorMessage ?: e.message ?: "Unknown error"}")
                        
                        // Wait and retry
                        Thread.sleep(1000)
                        
                        try {
                            log("CONNECT", "Retrying connection...")
                            rfidReader.connect()
                            log("CONNECT", "Retry connect() completed")
                        } catch (e2: OperationFailureException) {
                            val errorMsg = e2.vendorMessage ?: e2.message ?: "Connection failed"
                            log("CONNECT", "Retry failed: $errorMsg")
                            return SyncResult(false, "Connection failed: $errorMsg. Try restarting the reader.")
                        }
                    }

                    // Small delay before configuration
                    Thread.sleep(500)

                    // Configure
                    try {
                        configureReader()
                    } catch (configError: Exception) {
                        log("CONNECT", "Configuration warning: ${configError.message}")
                        // Continue anyway, connection might still work
                    }

                    if (rfidReader.isConnected) {
                        isConnectedFlag = true
                        connectedReaderName = hostname
                        reconnectAttempts = 0
                        log("CONNECT", "Successfully connected to $hostname")
                        return SyncResult(true)
                    } else {
                        log("CONNECT", "isConnected returned false after connect()")
                        return SyncResult(false, "Connection verification failed. Reader may need restart.")
                    }
                } else {
                    isConnectedFlag = true
                    connectedReaderName = rfidReader.hostName
                    log("CONNECT", "Reader was already connected")
                    return SyncResult(true)
                }
            }

            return SyncResult(false, "Reader object is null")

        } catch (e: InvalidUsageException) {
            val errorMsg = e.info ?: e.message ?: "Invalid usage"
            log("CONNECT", "InvalidUsageException: $errorMsg")
            return SyncResult(false, "Invalid usage: $errorMsg")
        } catch (e: OperationFailureException) {
            val errorMsg = e.vendorMessage ?: e.message ?: "Operation failed"
            log("CONNECT", "OperationFailureException: $errorMsg")
            return SyncResult(false, "Operation failed: $errorMsg")
        } catch (e: Exception) {
            log("CONNECT", "Exception: ${e.javaClass.simpleName} - ${e.message}")
            return SyncResult(false, "${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // AUTO RECONNECT
    // ═══════════════════════════════════════════════════════════════════

    private fun startAutoReconnect() {
        if (isReconnecting) {
            log("RECONNECT", "Already reconnecting, skipping...")
            return
        }

        isReconnecting = true
        reconnectAttempts = 0
        
        log("RECONNECT", "Starting auto-reconnect...")
        sendStatusEvent("reconnecting", "Reader disconnected, attempting to reconnect...")
        
        attemptReconnect()
    }

    private fun attemptReconnect() {
        if (!isReconnecting) return
        
        reconnectAttempts++
        log("RECONNECT", "Attempt $reconnectAttempts of $maxReconnectAttempts")

        Thread {
            try {
                // Refresh reader list
                refreshAvailableReaders()

                if (availableRFIDReaderList.isNullOrEmpty()) {
                    log("RECONNECT", "No readers found")
                    scheduleNextReconnect()
                    return@Thread
                }

                // Try to reconnect
                readerDevice = availableRFIDReaderList!![0]
                reader = readerDevice?.rfidReader

                reader?.let { rfidReader ->
                    if (!rfidReader.isConnected) {
                        rfidReader.connect()
                        configureReader()

                        if (rfidReader.isConnected) {
                            isConnectedFlag = true
                            connectedReaderName = rfidReader.hostName
                            isReconnecting = false
                            reconnectAttempts = 0
                            
                            log("RECONNECT", "Reconnected successfully to ${connectedReaderName}")
                            sendStatusEvent("connected", "Reconnected to ${connectedReaderName}", mapOf(
                                "readerName" to connectedReaderName
                            ))
                            return@Thread
                        }
                    }
                }

                scheduleNextReconnect()

            } catch (e: Exception) {
                log("RECONNECT", "Reconnect attempt failed: ${e.message}")
                scheduleNextReconnect()
            }
        }.start()
    }

    private fun scheduleNextReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            log("RECONNECT", "Max reconnect attempts reached")
            isReconnecting = false
            sendStatusEvent("disconnected", "Failed to reconnect after $maxReconnectAttempts attempts", mapOf(
                "error" to "Max reconnect attempts reached"
            ))
            return
        }

        reconnectHandler.postDelayed({
            attemptReconnect()
        }, reconnectDelayMs)
    }

    private fun stopAutoReconnect() {
        isReconnecting = false
        reconnectHandler.removeCallbacksAndMessages(null)
    }

    // ═══════════════════════════════════════════════════════════════════
    // MANUAL DISCONNECT
    // ═══════════════════════════════════════════════════════════════════

    fun disconnectReader(callback: ResultCallback) {
        log("DISCONNECT", "Manual disconnect called")
        stopAutoReconnect()

        Thread {
            try {
                if (reader == null || !reader!!.isConnected) {
                    isConnectedFlag = false
                    connectedReaderName = null
                    mainHandler.post {
                        callback.onSuccess(mapOf(
                            "message" to "Already disconnected",
                            "status" to getStatus()
                        ))
                    }
                    return@Thread
                }

                eventHandler?.let { reader!!.Events.removeEventsListener(it) }
                reader!!.disconnect()

                isConnectedFlag = false
                connectedReaderName = null

                log("DISCONNECT", "Disconnected successfully")
                sendStatusEvent("disconnected", "Reader disconnected")

                mainHandler.post {
                    callback.onSuccess(mapOf(
                        "message" to "Disconnected successfully",
                        "status" to getStatus()
                    ))
                }

            } catch (e: Exception) {
                log("DISCONNECT", "Error: ${e.message}")
                mainHandler.post {
                    callback.onError("DISCONNECT_ERROR", "Disconnect failed: ${e.message}", null)
                }
            }
        }.start()
    }

    // ═══════════════════════════════════════════════════════════════════
    // START INVENTORY
    // ═══════════════════════════════════════════════════════════════════

    fun startInventory(callback: ResultCallback) {
        log("INVENTORY", "Start inventory called")

        Thread {
            try {
                if (reader == null || !reader!!.isConnected) {
                    mainHandler.post {
                        callback.onError("NOT_CONNECTED", "Reader not connected", getStatus())
                    }
                    return@Thread
                }

                try {
                    reader!!.Actions.Inventory.perform()
                    log("INVENTORY", "Inventory started")
                    sendStatusEvent("inventory_started", "Scanning for tags...")

                    mainHandler.post {
                        callback.onSuccess(mapOf(
                            "message" to "Inventory started",
                            "status" to getStatus()
                        ))
                    }
                } catch (e: OperationFailureException) {
                    // Retry once
                    log("INVENTORY", "First attempt failed, retrying...")
                    try {
                        reader!!.Actions.Inventory.stop()
                        Thread.sleep(300)
                        reader!!.Actions.Inventory.perform()
                        
                        log("INVENTORY", "Inventory started (retry)")
                        sendStatusEvent("inventory_started", "Scanning for tags...")

                        mainHandler.post {
                            callback.onSuccess(mapOf(
                                "message" to "Inventory started",
                                "status" to getStatus()
                            ))
                        }
                    } catch (retryE: Exception) {
                        mainHandler.post {
                            callback.onError("START_FAILED", "Failed to start: ${e.vendorMessage}", getStatus())
                        }
                    }
                }

            } catch (e: Exception) {
                log("INVENTORY", "Error: ${e.message}")
                mainHandler.post {
                    callback.onError("INVENTORY_ERROR", "Error: ${e.message}", getStatus())
                }
            }
        }.start()
    }

    // ═══════════════════════════════════════════════════════════════════
    // STOP INVENTORY
    // ═══════════════════════════════════════════════════════════════════

    fun stopInventory(callback: ResultCallback) {
        log("INVENTORY", "Stop inventory called")

        Thread {
            try {
                if (reader == null || !reader!!.isConnected) {
                    mainHandler.post {
                        callback.onError("NOT_CONNECTED", "Reader not connected", getStatus())
                    }
                    return@Thread
                }

                reader!!.Actions.Inventory.stop()
                log("INVENTORY", "Inventory stopped")
                sendStatusEvent("inventory_stopped", "Scanning stopped")

                mainHandler.post {
                    callback.onSuccess(mapOf(
                        "message" to "Inventory stopped",
                        "status" to getStatus()
                    ))
                }

            } catch (e: Exception) {
                log("INVENTORY", "Error: ${e.message}")
                mainHandler.post {
                    callback.onError("STOP_ERROR", "Error: ${e.message}", getStatus())
                }
            }
        }.start()
    }

    // ═══════════════════════════════════════════════════════════════════
    // SET ANTENNA POWER
    // ═══════════════════════════════════════════════════════════════════

    fun setAntennaPower(powerLevel: Int, callback: ResultCallback) {
        log("POWER", "Set antenna power to $powerLevel")

        Thread {
            try {
                if (reader == null || !reader!!.isConnected) {
                    mainHandler.post {
                        callback.onError("NOT_CONNECTED", "Reader not connected", getStatus())
                    }
                    return@Thread
                }

                if (powerLevel !in 0..maxPower) {
                    mainHandler.post {
                        callback.onError("INVALID_POWER", "Power must be 0-$maxPower", null)
                    }
                    return@Thread
                }

                val config = reader!!.Config.Antennas.getAntennaRfConfig(1)
                config.setTransmitPowerIndex(powerLevel)
                reader!!.Config.Antennas.setAntennaRfConfig(1, config)

                log("POWER", "Power set to $powerLevel")

                mainHandler.post {
                    callback.onSuccess(mapOf(
                        "message" to "Power set to $powerLevel",
                        "powerLevel" to powerLevel,
                        "maxPower" to maxPower
                    ))
                }

            } catch (e: Exception) {
                log("POWER", "Error: ${e.message}")
                mainHandler.post {
                    callback.onError("POWER_ERROR", "Error: ${e.message}", null)
                }
            }
        }.start()
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET ANTENNA POWER
    // ═══════════════════════════════════════════════════════════════════

    fun getAntennaPower(callback: ResultCallback) {
        log("POWER", "Get antenna power")

        Thread {
            try {
                if (reader == null || !reader!!.isConnected) {
                    mainHandler.post {
                        callback.onError("NOT_CONNECTED", "Reader not connected", getStatus())
                    }
                    return@Thread
                }

                val config = reader!!.Config.Antennas.getAntennaRfConfig(1)
                val currentPower = config.getTransmitPowerIndex()

                log("POWER", "Current: $currentPower, Max: $maxPower")

                mainHandler.post {
                    callback.onSuccess(mapOf(
                        "currentPower" to currentPower,
                        "maxPower" to maxPower
                    ))
                }

            } catch (e: Exception) {
                log("POWER", "Error: ${e.message}")
                mainHandler.post {
                    callback.onError("POWER_ERROR", "Error: ${e.message}", null)
                }
            }
        }.start()
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET FULL STATUS
    // ═══════════════════════════════════════════════════════════════════

    fun getFullStatus(callback: ResultCallback) {
        mainHandler.post {
            callback.onSuccess(mapOf(
                "sdkInitialized" to isInitialized,
                "readerConnected" to (isConnectedFlag && reader?.isConnected == true),
                "readerName" to connectedReaderName,
                "isReconnecting" to isReconnecting,
                "maxPower" to maxPower
            ))
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DISPOSE
    // ═══════════════════════════════════════════════════════════════════

    fun dispose() {
        log("DISPOSE", "Disposing RFIDHandler...")
        stopAutoReconnect()

        try {
            if (reader?.isConnected == true) {
                eventHandler?.let { reader!!.Events.removeEventsListener(it) }
                reader!!.disconnect()
            }

            readers?.Dispose()
            readers = null
            reader = null
            isInitialized = false
            isConnectedFlag = false
            connectedReaderName = null

            log("DISPOSE", "Disposed successfully")

        } catch (e: Exception) {
            log("DISPOSE", "Error: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIVATE HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    private fun log(tag: String, message: String) {
        android.util.Log.d("RFIDHandler", "[$tag] $message")
    }

    private fun configureReader() {
        log("CONFIG", "Configuring reader: ${reader?.hostName}")

        try {
            reader?.let { rfidReader ->
                // Setup event listener
                if (eventHandler == null) {
                    eventHandler = EventHandler()
                }
                
                try {
                    rfidReader.Events.addEventsListener(eventHandler)
                    log("CONFIG", "Event listener added")
                } catch (e: Exception) {
                    log("CONFIG", "Warning: Could not add event listener: ${e.message}")
                }

                // Configure events
                try {
                    rfidReader.Events.setHandheldEvent(true)
                    rfidReader.Events.setTagReadEvent(true)
                    rfidReader.Events.setAttachTagDataWithReadEvent(false)
                    rfidReader.Events.setReaderDisconnectEvent(true)
                    log("CONFIG", "Events configured")
                } catch (e: Exception) {
                    log("CONFIG", "Warning: Could not configure events: ${e.message}")
                }

                // Set trigger mode
                try {
                    rfidReader.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, true)
                    log("CONFIG", "Trigger mode set")
                } catch (e: Exception) {
                    log("CONFIG", "Warning: Could not set trigger mode: ${e.message}")
                }

                // Configure triggers
                try {
                    val triggerInfo = TriggerInfo()
                    triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE)
                    triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE)
                    rfidReader.Config.setStartTrigger(triggerInfo.StartTrigger)
                    rfidReader.Config.setStopTrigger(triggerInfo.StopTrigger)
                    log("CONFIG", "Triggers configured")
                } catch (e: Exception) {
                    log("CONFIG", "Warning: Could not configure triggers: ${e.message}")
                }

                // Get max power
                try {
                    val powerLevels = rfidReader.ReaderCapabilities.getTransmitPowerLevelValues()
                    maxPower = powerLevels.size - 1
                    log("CONFIG", "Max power: $maxPower")
                } catch (e: Exception) {
                    log("CONFIG", "Warning: Could not get power levels: ${e.message}")
                    maxPower = 270
                }

                // Configure antenna
                try {
                    val config = rfidReader.Config.Antennas.getAntennaRfConfig(1)
                    config.setTransmitPowerIndex(maxPower)
                    config.setrfModeTableIndex(0)
                    config.setTari(0)
                    rfidReader.Config.Antennas.setAntennaRfConfig(1, config)
                    log("CONFIG", "Antenna configured with power: $maxPower")
                } catch (e: Exception) {
                    log("CONFIG", "Warning: Could not configure antenna: ${e.message}")
                }

                // Configure singulation
                try {
                    val singulationControl = rfidReader.Config.Antennas.getSingulationControl(1)
                    singulationControl.setSession(SESSION.SESSION_S0)
                    singulationControl.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A)
                    singulationControl.Action.setSLFlag(SL_FLAG.SL_ALL)
                    rfidReader.Config.Antennas.setSingulationControl(1, singulationControl)
                    log("CONFIG", "Singulation configured")
                } catch (e: Exception) {
                    log("CONFIG", "Warning: Could not configure singulation: ${e.message}")
                }

                // Clear prefilters
                try {
                    rfidReader.Actions.PreFilters.deleteAll()
                    log("CONFIG", "Prefilters cleared")
                } catch (e: Exception) {
                    log("CONFIG", "Warning: Could not clear prefilters: ${e.message}")
                }

                log("CONFIG", "Reader configuration completed")
            }
        } catch (e: Exception) {
            log("CONFIG", "Configuration error: ${e.message}")
            // Don't throw - let connection continue even if config partially fails
        }
    }

    private fun refreshAvailableReaders() {
        try {
            readers?.let { rfidReaders ->
                Readers.attach(this)
                availableRFIDReaderList = rfidReaders.GetAvailableRFIDReaderList()
                log("REFRESH", "Found ${availableRFIDReaderList?.size ?: 0} reader(s)")
            }
        } catch (e: Exception) {
            log("REFRESH", "Error: ${e.message}")
        }
    }

    private fun sendTagReadEvent(tags: List<Map<String, Any>>) {
        mainHandler.post {
            try {
                tagReadEventSink.sendEvent(mapOf(
                    "type" to "tagRead",
                    "tags" to tags
                ))
            } catch (e: Exception) {
                log("EVENT", "Failed to send tag event: ${e.message}")
            }
        }
    }

    private fun sendStatusEvent(type: String, message: String, data: Map<String, Any?>? = null) {
        mainHandler.post {
            try {
                val event = mutableMapOf<String, Any?>(
                    "type" to type,
                    "message" to message
                )
                data?.let { event.putAll(it) }
                statusEventSink.sendEvent(event)
            } catch (e: Exception) {
                log("EVENT", "Failed to send status event: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // READER EVENT HANDLERS
    // ═══════════════════════════════════════════════════════════════════

    override fun RFIDReaderAppeared(readerDevice: ReaderDevice?) {
        val name = readerDevice?.getName() ?: "Unknown"
        log("READER_EVENT", "Reader appeared: $name")
        sendStatusEvent("readerAppeared", "Reader appeared: $name", mapOf("readerName" to name))
    }

    override fun RFIDReaderDisappeared(readerDevice: ReaderDevice?) {
        val name = readerDevice?.getName() ?: "Unknown"
        log("READER_EVENT", "Reader disappeared: $name")
        sendStatusEvent("readerDisappeared", "Reader disappeared: $name", mapOf("readerName" to name))
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT HANDLER CLASS
    // ═══════════════════════════════════════════════════════════════════

    inner class EventHandler : RfidEventsListener {

        override fun eventReadNotify(e: RfidReadEvents?) {
            try {
                val tags = reader?.Actions?.getReadTags(100)

                tags?.let {
                    if (it.isNotEmpty()) {
                        val tagList = mutableListOf<Map<String, Any>>()

                        for (tag in it) {
                            tagList.add(mapOf(
                                "tagId" to (tag.getTagID() ?: ""),
                                "rssi" to tag.getPeakRSSI(),
                                "antennaId" to tag.getAntennaID(),
                                "count" to tag.getTagSeenCount()
                            ))
                        }

                        log("TAG_READ", "Read ${it.size} tags")
                        sendTagReadEvent(tagList)
                    }
                }
            } catch (e: Exception) {
                log("TAG_READ", "Error: ${e.message}")
            }
        }

        override fun eventStatusNotify(statusEvent: RfidStatusEvents?) {
            try {
                statusEvent?.let { event ->
                    when (event.StatusEventData.getStatusEventType()) {
                        STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT -> {
                            val pressed = event.StatusEventData.HandheldTriggerEventData
                                .getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED

                            log("TRIGGER", if (pressed) "PRESSED" else "RELEASED")
                            sendStatusEvent(
                                "trigger",
                                if (pressed) "Trigger pressed" else "Trigger released",
                                mapOf("pressed" to pressed)
                            )
                        }
                        STATUS_EVENT_TYPE.DISCONNECTION_EVENT -> {
                            log("STATUS", "Unexpected disconnection detected")
                            isConnectedFlag = false
                            sendStatusEvent("disconnected", "Reader disconnected unexpectedly")
                            
                            // Start auto-reconnect
                            startAutoReconnect()
                        }
                        else -> {
                            log("STATUS", "Event: ${event.StatusEventData.getStatusEventType()}")
                        }
                    }
                }
            } catch (e: Exception) {
                log("STATUS_EVENT", "Error: ${e.message}")
            }
        }
    }
}

// Android 13+ Context Wrapper
class Android13ContextWrapper(base: Context) : ContextWrapper(base) {
    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            super.registerReceiver(receiver, filter)
        }
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler, Context.RECEIVER_EXPORTED)
        } else {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
        }
    }
}