package dev.jimmy.zebra_rfid_reader

import android.content.Context
import android.os.AsyncTask
import android.util.Log
import com.zebra.rfid.api3.*
import io.flutter.plugin.common.EventChannel
import java.util.ArrayList

class RFIDHandler(private val context: Context) : Readers.RFIDReaderEventHandler {

    companion object {
        private const val TAG = "RFIDHandler"
    }

    private var readers: Readers? = null
    private var availableRFIDReaderList: ArrayList<ReaderDevice?>? = null
    private var readerDevice: ReaderDevice? = null
    private var reader: RFIDReader? = null
    private var eventHandler: EventHandler? = null
    private var eventSink: EventChannel.EventSink? = null

    private val MAX_POWER = 270

    // Reader names - adjust based on your device
    private val readerNames = listOf(
        "RFD40+",
        "RFD4031",
        "RFD8500",
        "TC27" // TC27 with attached RFID
    )

    fun setEventSink(sink: EventChannel.EventSink?) {
        this.eventSink = sink
    }

    fun initialize() {
        Log.d(TAG, "Initializing RFID SDK")
        if (readers == null) {
            CreateInstanceTask().execute()
        }
    }

    val isConnected: Boolean
        get() = reader != null && reader!!.isConnected

    fun connectReader(callback: (String) -> Unit) {
        if (!isConnected) {
            ConnectionTask(callback).execute()
        } else {
            callback("Already connected")
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting reader")
        try {
            reader?.let {
                if (eventHandler != null) {
                    it.Events.removeEventsListener(eventHandler)
                }
                it.disconnect()
                sendEvent(mapOf("type" to "disconnected"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        }
    }

    fun dispose() {
        disconnect()
        try {
            readers?.Dispose()
            readers = null
            reader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing: ${e.message}")
        }
    }

    fun startInventory(callback: (String) -> Unit) {
        try {
            if (!isConnected) {
                callback("Reader not connected")
                return
            }
            reader?.Actions?.Inventory?.perform()
            callback("Inventory started")
        } catch (e: Exception) {
            callback("Error starting inventory: ${e.message}")
        }
    }

    fun stopInventory(callback: (String) -> Unit) {
        try {
            if (!isConnected) {
                callback("Reader not connected")
                return
            }
            reader?.Actions?.Inventory?.stop()
            callback("Inventory stopped")
        } catch (e: Exception) {
            callback("Error stopping inventory: ${e.message}")
        }
    }

    fun setAntennaPower(powerLevel: Int, callback: (String) -> Unit) {
        try {
            if (!isConnected) {
                callback("Reader not connected")
                return
            }

            val config = reader?.Config?.Antennas?.getAntennaRfConfig(1)
            config?.setTransmitPowerIndex(powerLevel)
            reader?.Config?.Antennas?.setAntennaRfConfig(1, config)

            callback("Power set to $powerLevel")
        } catch (e: Exception) {
            callback("Error setting power: ${e.message}")
        }
    }

    private fun configureReader() {
        Log.d(TAG, "Configuring reader")

        reader?.let { rfidReader ->
            try {
                // Set up event listeners
                if (eventHandler == null) {
                    eventHandler = EventHandler()
                }
                rfidReader.Events.addEventsListener(eventHandler)

                // Enable events
                rfidReader.Events.setHandheldEvent(true)
                rfidReader.Events.setTagReadEvent(true)
                rfidReader.Events.setAttachTagDataWithReadEvent(false)

                // Set trigger mode
                rfidReader.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, true)

                // Configure triggers
                val triggerInfo = TriggerInfo()
                triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE)
                triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE)

                rfidReader.Config.setStartTrigger(triggerInfo.StartTrigger)
                rfidReader.Config.setStopTrigger(triggerInfo.StopTrigger)

                // Set antenna power
                val powerLevels = rfidReader.ReaderCapabilities.getTransmitPowerLevelValues()
                val maxPower = powerLevels.size - 1

                val config = rfidReader.Config.Antennas.getAntennaRfConfig(1)
                config.setTransmitPowerIndex(maxPower)
                config.setrfModeTableIndex(0)
                config.setTari(0)
                rfidReader.Config.Antennas.setAntennaRfConfig(1, config)

                // Set singulation control
                val singulationControl = rfidReader.Config.Antennas.getSingulationControl(1)
                singulationControl.setSession(SESSION.SESSION_S0)
                singulationControl.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A)
                singulationControl.Action.setSLFlag(SL_FLAG.SL_ALL)
                rfidReader.Config.Antennas.setSingulationControl(1, singulationControl)

                Log.d(TAG, "Reader configured successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error configuring reader: ${e.message}")
            }
        }
    }

    private fun getAvailableReaders() {
        Log.d(TAG, "Getting available readers")

        readers?.let { rfidReaders ->
            Readers.attach(this)

            try {
                availableRFIDReaderList = rfidReaders.GetAvailableRFIDReaderList()

                if (availableRFIDReaderList?.isNotEmpty() == true) {
                    Log.d(TAG, "Found ${availableRFIDReaderList?.size} reader(s)")

                    if (availableRFIDReaderList?.size == 1) {
                        readerDevice = availableRFIDReaderList?.get(0)
                        reader = readerDevice?.getRFIDReader()
                    } else {
                        // Search for known reader names
                        for (device in availableRFIDReaderList!!) {
                            val deviceName = device?.getName() ?: continue
                            Log.d(TAG, "Found device: $deviceName")

                            if (readerNames.any { deviceName.contains(it, ignoreCase = true) }) {
                                readerDevice = device
                                reader = device.getRFIDReader()
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting readers: ${e.message}")
            }
        }
    }

    private fun connectToReader(): String {
        reader?.let { rfidReader ->
            try {
                if (!rfidReader.isConnected) {
                    Log.d(TAG, "Connecting to ${rfidReader.getHostName()}")
                    rfidReader.connect()
                    configureReader()

                    if (rfidReader.isConnected) {
                        val message = "Connected to ${rfidReader.getHostName()}"
                        sendEvent(mapOf("type" to "connected", "reader" to rfidReader.getHostName()))
                        return message
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}")
                return "Connection failed: ${e.message}"
            }
        }
        return "No reader available"
    }

    private fun sendEvent(data: Map<String, Any>) {
        eventSink?.success(data)
    }

    // Reader event handlers
    override fun RFIDReaderAppeared(readerDevice: ReaderDevice?) {
        Log.d(TAG, "Reader appeared: ${readerDevice?.getName()}")
        sendEvent(mapOf("type" to "readerAppeared", "name" to (readerDevice?.getName() ?: "Unknown")))
    }

    override fun RFIDReaderDisappeared(readerDevice: ReaderDevice?) {
        Log.d(TAG, "Reader disappeared: ${readerDevice?.getName()}")
        sendEvent(mapOf("type" to "readerDisappeared", "name" to (readerDevice?.getName() ?: "Unknown")))

        if (readerDevice?.getName() == reader?.getHostName()) {
            disconnect()
        }
    }

    // Inner event handler class
    inner class EventHandler : RfidEventsListener {

        override fun eventReadNotify(e: RfidReadEvents?) {
            val tags = reader?.Actions?.getReadTags(100)

            tags?.let {
                val tagList = mutableListOf<Map<String, Any>>()

                for (tag in it) {
                    tagList.add(mapOf(
                        "tagId" to tag.getTagID(),
                        "rssi" to tag.getPeakRSSI(),
                        "antennaId" to tag.getAntennaID(),
                        "count" to tag.getTagSeenCount()
                    ))
                }

                sendEvent(mapOf(
                    "type" to "tagRead",
                    "tags" to tagList
                ))
            }
        }

        override fun eventStatusNotify(statusEvent: RfidStatusEvents?) {
            statusEvent?.let { event ->
                when (event.StatusEventData.getStatusEventType()) {
                    STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT -> {
                        val triggerPressed = event.StatusEventData.HandheldTriggerEventData
                            .getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED

                        sendEvent(mapOf(
                            "type" to "trigger",
                            "pressed" to triggerPressed
                        ))
                    }
                    STATUS_EVENT_TYPE.DISCONNECTION_EVENT -> {
                        disconnect()
                    }
                    else -> {
                        Log.d(TAG, "Status event: ${event.StatusEventData.getStatusEventType()}")
                    }
                }
            }
        }
    }

    // Async tasks
    private inner class CreateInstanceTask : AsyncTask<Void, Void, Boolean>() {
        private var error: String? = null

        override fun doInBackground(vararg params: Void?): Boolean {
            try {
                // Try different transports
                val transports = listOf(
                    ENUM_TRANSPORT.BLUETOOTH,
                    ENUM_TRANSPORT.SERVICE_USB,
                    ENUM_TRANSPORT.SERVICE_SERIAL
                )

                for (transport in transports) {
                    try {
                        readers = Readers(context, transport)
                        val list = readers?.GetAvailableRFIDReaderList()

                        if (list?.isNotEmpty() == true) {
                            availableRFIDReaderList = list
                            Log.d(TAG, "Found readers using $transport transport")
                            return true
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "No readers on $transport: ${e.message}")
                    }
                }

                error = "No readers found on any transport"
                return false

            } catch (e: Exception) {
                error = e.message
                return false
            }
        }

        override fun onPostExecute(result: Boolean) {
            if (result) {
                connectReader { }
            } else {
                Log.e(TAG, "Failed to initialize: $error")
                sendEvent(mapOf("type" to "error", "message" to (error ?: "Unknown error")))
            }
        }
    }

    private inner class ConnectionTask(
        private val callback: (String) -> Unit
    ) : AsyncTask<Void, Void, String>() {

        override fun doInBackground(vararg params: Void?): String {
            getAvailableReaders()
            return connectToReader()
        }

        override fun onPostExecute(result: String) {
            callback(result)
        }
    }
}
//package dev.jimmy.zebra_rfid_reader
//
//import android.os.AsyncTask
//import android.util.Log
//import android.widget.TextView
//import com.zebra.rfid.api3.*
//import com.zebra.scannercontrol.*
//import java.util.ArrayList
//
//internal class RFIDHandler : IDcsSdkApiDelegate, Readers.RFIDReaderEventHandler {
//    private val DEVICE_STD_MODE = 0
//    private val DEVICE_PREMIUM_PLUS_MODE = 1
//
//    //M781 - E28011112222333344445555 passowrd - 00000000
//    // E28011C1A5000062F792696D
//    var impinjTag: String? = null
//    var password: String? = null
//    var textView: TextView? = null
//
//    // In case of RFD8500 change reader name with intended device below from list of paired RFD8500
//    // If barcode scan is available in RFD8500, for barcode scanning change mode using mode button on RFD8500 device. By default it is set to RFID mode
//    var readerNamebt: String = "RFD40+_211545201D0011"
//    var readerName: String = "RFD4031-G10B700-US"
//    var RFD8500: String = "RFD8500161755230D5038"
//    private var readers: Readers? = null
//    private var availableRFIDReaderList: ArrayList<ReaderDevice?>? = null
//    private var readerDevice: ReaderDevice? = null
//    private var reader: RFIDReader? = null
//    private var eventHandler: com.zebra.rfid.demo.sdksample.RFIDHandler.EventHandler? = null
//    private var context: MainActivity? = null
//    private var sdkHandler: SDKHandler? = null
//    private var scannerList: ArrayList<DCSScannerInfo?>? = null
//    private var impinjExtensions: ImpinjExtensions? = null
//    private var scannerID = 0
//    private var MAX_POWER = 270
//
//
//    fun onCreate(activity: MainActivity) {
//        context = activity
//        textView = activity.textViewStatus
//        scannerList = ArrayList()
//        InitSDK()
//    }
//
//    @Override
//    fun dcssdkEventScannerAppeared(dcsScannerInfo: DCSScannerInfo?) {
//    }
//
//    @Override
//    fun dcssdkEventScannerDisappeared(i: Int) {
//    }
//
//    @Override
//    fun dcssdkEventCommunicationSessionEstablished(dcsScannerInfo: DCSScannerInfo?) {
//    }
//
//    @Override
//    fun dcssdkEventCommunicationSessionTerminated(i: Int) {
//    }
//
//    @Override
//    fun dcssdkEventBarcode(barcodeData: ByteArray?, barcodeType: Int, fromScannerID: Int) {
//        val s = String(barcodeData)
//        context.barcodeData(s)
//        Log.d(com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG, "barcaode =" + s)
//    }
//
//    @Override
//    fun dcssdkEventImage(bytes: ByteArray?, i: Int) {
//    }
//
//    @Override
//    fun dcssdkEventVideo(bytes: ByteArray?, i: Int) {
//    }
//
//    @Override
//    fun dcssdkEventBinaryData(bytes: ByteArray?, i: Int) {
//    }
//
//
//    // TEST BUTTON functionality
//    // following two tests are to try out different configurations features
//    @Override
//    fun dcssdkEventFirmwareUpdate(firmwareUpdateEvent: FirmwareUpdateEvent?) {
//    }
//
//    @Override
//    fun dcssdkEventAuxScannerAppeared(
//        dcsScannerInfo: DCSScannerInfo?,
//        dcsScannerInfo1: DCSScannerInfo?
//    ) {
//    }
//
//    val isConnected: Boolean
//        get() = reader != null && reader.isConnected()
//
//    fun Test1(): String? {
//        // check reader connection
//        if (!isReaderConnected()) return "Not connected"
//        // set antenna configurations - reducing power to 200
//        try {
//            var config: Antennas.AntennaRfConfig? = null
//            config = reader.Config.Antennas.getAntennaRfConfig(1)
//            config.setTransmitPowerIndex(100)
//            config.setrfModeTableIndex(0)
//            config.setTari(0)
//            reader.Config.Antennas.setAntennaRfConfig(1, config)
//        } catch (e: InvalidUsageException) {
//            e.printStackTrace()
//        } catch (e: OperationFailureException) {
//            e.printStackTrace()
//            return e.getResults().toString() + " " + e.getVendorMessage()
//        }
//        return "Antenna power Set to 220"
//    }
//
//    fun Test2(): String? {
//        // check reader connection
//        if (!isReaderConnected()) return "Not connected"
//        // Set the singulation control to S2 which will read each tag once only
//        try {
//            val s1_singulationControl: Antennas.SingulationControl =
//                reader.Config.Antennas.getSingulationControl(1)
//            s1_singulationControl.setSession(SESSION.SESSION_S2)
//            s1_singulationControl.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A)
//            s1_singulationControl.Action.setSLFlag(SL_FLAG.SL_ALL)
//            reader.Config.Antennas.setSingulationControl(1, s1_singulationControl)
//        } catch (e: InvalidUsageException) {
//            e.printStackTrace()
//        } catch (e: OperationFailureException) {
//            e.printStackTrace()
//            return e.getResults().toString() + " " + e.getVendorMessage()
//        }
//        return "Session set to S2"
//    }
//
//    //
//    //  Activity life cycle behavior
//    //
//    fun Defaults(): String? {
//        // check reader connection
//        if (!isReaderConnected()) return "Not connected"
//        try {
//            // Power to 270
//            var config: Antennas.AntennaRfConfig? = null
//            config = reader.Config.Antennas.getAntennaRfConfig(1)
//            config.setTransmitPowerIndex(MAX_POWER)
//            config.setrfModeTableIndex(0)
//            config.setTari(0)
//            reader.Config.Antennas.setAntennaRfConfig(1, config)
//            // singulation to S0
//            val s1_singulationControl: Antennas.SingulationControl =
//                reader.Config.Antennas.getSingulationControl(1)
//            s1_singulationControl.setSession(SESSION.SESSION_S0)
//            s1_singulationControl.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A)
//            s1_singulationControl.Action.setSLFlag(SL_FLAG.SL_ALL)
//            reader.Config.Antennas.setSingulationControl(1, s1_singulationControl)
//        } catch (e: InvalidUsageException) {
//            e.printStackTrace()
//        } catch (e: OperationFailureException) {
//            e.printStackTrace()
//            return e.getResults().toString() + " " + e.getVendorMessage()
//        }
//        return "Default settings applied"
//    }
//
//    private val isReaderConnected: Boolean
//        get() {
//            if (reader != null && reader.isConnected()) return true
//            else {
//                Log.d(
//                    com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                    "reader is not connected"
//                )
//                return false
//            }
//        }
//
//    fun onResume(): String {
//        return connect()
//    }
//
//    fun onPause() {
//        disconnect()
//    }
//
//    fun onDestroy() {
//        dispose()
//    }
//
//    //
//    // RFID SDK
//    //
//    private fun InitSDK() {
//        Log.d(com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG, "InitSDK")
//        if (readers == null) {
//            com.zebra.rfid.demo.sdksample.RFIDHandler.CreateInstanceTask().execute()
//        } else connectReader()
//    }
//
//    fun testFunction() {
//        //setPreFilters();
//        //testReadevent();
//        try {
//            Log.d(com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG, "Delete Prefilter...")
//            //singulationControl // mRfidReader.Config.Antennas.getSingulationControl(1);
//            reader.Actions.PreFilters.deleteAll()
//        } catch (e: InvalidUsageException) {
//            e.printStackTrace()
//        } catch (e: OperationFailureException) {
//            e.printStackTrace()
//        }
//    }
//
//    private fun testReadevent() {
//        val tagAccess: TagAccess = TagAccess()
//        val lockAccessParams: TagAccess.LockAccessParams = tagAccess.LockAccessParams()
//        lockAccessParams.setLockPrivilege(
//            LOCK_DATA_FIELD.LOCK_USER_MEMORY,
//            LOCK_PRIVILEGE.LOCK_PRIVILEGE_READ_WRITE
//        )
//        lockAccessParams.setAccessPassword(Long.decode("0X" + "12341234"))
//        try {
//            reader.Actions.TagAccess.lockEvent(lockAccessParams, null, null)
//        } catch (e: InvalidUsageException) {
//            throw RuntimeException(e)
//        } catch (e: OperationFailureException) {
//            throw RuntimeException(e)
//        }
//    }
//
//    fun setPreFilters() {
//        Log.d("setPrefilter", "setPrefilter...")
//        val preFilterArray: Array<PreFilters.PreFilter?> = arrayOfNulls<PreFilters.PreFilter>(8)
//
//        val filters: PreFilters = PreFilters()
//        val filter: PreFilters.PreFilter = filters.PreFilter()
//        filter.setAntennaID(1.toShort())
//        filter.setTagPattern("00000")
//        filter.setTagPatternBitCount(4)
//        filter.setBitOffset(32)
//        filter.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC)
//        filter.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE)
//        filter.StateAwareAction.setTarget(TARGET.TARGET_SL)
//        filter.StateAwareAction.setStateAwareAction(STATE_AWARE_ACTION.STATE_AWARE_ACTION_ASRT_SL)
//        filter.setTruncateAction(TRUNCATE_ACTION.TRUNCATE_ACTION_DO_NOT_TRUNCATE)
//        preFilterArray[0] = filter
//
//        val filters1: PreFilters = PreFilters()
//        val filter1: PreFilters.PreFilter = filters1.PreFilter()
//        filter1.setAntennaID(1.toShort())
//        filter1.setTagPattern("111111")
//        filter1.setTagPatternBitCount(4)
//        filter1.setBitOffset(32)
//        filter1.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC)
//        filter1.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE)
//        filter1.StateAwareAction.setTarget(TARGET.TARGET_SL)
//        filter1.StateAwareAction.setStateAwareAction(STATE_AWARE_ACTION.STATE_AWARE_ACTION_ASRT_SL)
//        filter1.setTruncateAction(TRUNCATE_ACTION.TRUNCATE_ACTION_DO_NOT_TRUNCATE)
//        preFilterArray[1] = filter1
//
//        val filters2: PreFilters = PreFilters()
//        val filter2: PreFilters.PreFilter = filters2.PreFilter()
//        filter2.setAntennaID(1.toShort())
//        filter2.setTagPattern("22222")
//        filter2.setTagPatternBitCount(4)
//        filter2.setBitOffset(32)
//        filter2.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC)
//        filter2.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE)
//        filter2.StateAwareAction.setTarget(TARGET.TARGET_SL)
//        filter2.StateAwareAction.setStateAwareAction(STATE_AWARE_ACTION.STATE_AWARE_ACTION_ASRT_SL)
//        filter2.setTruncateAction(TRUNCATE_ACTION.TRUNCATE_ACTION_DO_NOT_TRUNCATE)
//        preFilterArray[2] = filter2
//
//        val filters3: PreFilters = PreFilters()
//        val filter3: PreFilters.PreFilter = filters3.PreFilter()
//        filter3.setAntennaID(1.toShort())
//        filter3.setTagPattern("33333")
//        filter3.setTagPatternBitCount(4)
//        filter3.setBitOffset(32)
//        filter3.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC)
//        filter3.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE)
//        filter3.StateAwareAction.setTarget(TARGET.TARGET_SL)
//        filter3.StateAwareAction.setStateAwareAction(STATE_AWARE_ACTION.STATE_AWARE_ACTION_ASRT_SL)
//        filter3.setTruncateAction(TRUNCATE_ACTION.TRUNCATE_ACTION_DO_NOT_TRUNCATE)
//        preFilterArray[3] = filter3
//
//        val filters4: PreFilters = PreFilters()
//        val filter4: PreFilters.PreFilter = filters4.PreFilter()
//        filter4.setAntennaID(1.toShort())
//        filter4.setTagPattern("44444")
//        filter4.setTagPatternBitCount(4)
//        filter4.setBitOffset(32)
//        filter4.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC)
//        filter4.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE)
//        filter4.StateAwareAction.setTarget(TARGET.TARGET_SL)
//        filter4.StateAwareAction.setStateAwareAction(STATE_AWARE_ACTION.STATE_AWARE_ACTION_ASRT_SL)
//        filter4.setTruncateAction(TRUNCATE_ACTION.TRUNCATE_ACTION_DO_NOT_TRUNCATE)
//        preFilterArray[4] = filter4
//
//        val filters5: PreFilters = PreFilters()
//        val filter5: PreFilters.PreFilter = filters5.PreFilter()
//        filter5.setAntennaID(1.toShort())
//        filter5.setTagPattern("55555")
//        filter5.setTagPatternBitCount(4)
//        filter5.setBitOffset(32)
//        filter5.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC)
//        filter5.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE)
//        filter5.StateAwareAction.setTarget(TARGET.TARGET_SL)
//        filter5.StateAwareAction.setStateAwareAction(STATE_AWARE_ACTION.STATE_AWARE_ACTION_ASRT_SL)
//        filter5.setTruncateAction(TRUNCATE_ACTION.TRUNCATE_ACTION_DO_NOT_TRUNCATE)
//        preFilterArray[5] = filter5
//
//        val filters6: PreFilters = PreFilters()
//        val filter6: PreFilters.PreFilter = filters6.PreFilter()
//        filter6.setAntennaID(1.toShort())
//        filter6.setTagPattern("66666")
//        filter6.setTagPatternBitCount(4)
//        filter6.setBitOffset(32)
//        filter6.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC)
//        filter6.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE)
//        filter6.StateAwareAction.setTarget(TARGET.TARGET_SL)
//        filter6.StateAwareAction.setStateAwareAction(STATE_AWARE_ACTION.STATE_AWARE_ACTION_ASRT_SL)
//        filter6.setTruncateAction(TRUNCATE_ACTION.TRUNCATE_ACTION_DO_NOT_TRUNCATE)
//        preFilterArray[6] = filter6
//
//        val filters7: PreFilters = PreFilters()
//        val filter7: PreFilters.PreFilter = filters7.PreFilter()
//        filter7.setAntennaID(1.toShort())
//        filter7.setTagPattern("77777")
//        filter7.setTagPatternBitCount(4)
//        filter7.setBitOffset(32)
//        filter7.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC)
//        filter7.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE)
//        filter7.StateAwareAction.setTarget(TARGET.TARGET_SL)
//        filter7.StateAwareAction.setStateAwareAction(STATE_AWARE_ACTION.STATE_AWARE_ACTION_ASRT_SL)
//        filter7.setTruncateAction(TRUNCATE_ACTION.TRUNCATE_ACTION_DO_NOT_TRUNCATE)
//        preFilterArray[7] = filter7
//
//
//        // Add to preFilterArray as needed
//
//
//        // not to select tags that match the criteria
//        try {
////            Log.d("setSingulationControl", "SingulationControl...");
////
////            Antennas.SingulationControl singulationControl = new Antennas.SingulationControl();
////            //singulationControl // mRfidReader.Config.Antennas.getSingulationControl(1);
////
////            singulationControl.setSession(SESSION.SESSION_S2);
////            singulationControl.setTagPopulation((short) 32);
////            singulationControl.Action.setSLFlag(SL_FLAG.SL_FLAG_ASSERTED);
////            singulationControl.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_AB_FLIP);
////            // mRfidReader.Config.Antennas.setSingulationControl(1, singulationControl);
//            reader.Actions.PreFilters.deleteAll()
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "PreFilter Started adding..................................."
//            )
//            reader.Actions.PreFilters.add(preFilterArray, null)
//            Thread.sleep(500)
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "PreFilter set successfully"
//            )
//            var prefilterLength: Int = reader.Actions.PreFilters.length()
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "PreFilter length = " + prefilterLength
//            )
//            for (i in 0..<prefilterLength) {
//                val fil: PreFilters.PreFilter = reader.Actions.PreFilters.getPreFilter(i)
//                Log.d(
//                    com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                    "PreFilter: " + fil.getStringTagPattern()
//                )
//            }
//
//            val deletefil: PreFilters.PreFilter = reader.Actions.PreFilters.getPreFilter(5)
//            deletefil.setTagPattern("AAAAAAAAAAAAAAAA")
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "Deleting PreFilter: " + deletefil.getStringTagPattern()
//            )
//
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "PreFilter before delete all................ = "
//            )
//            reader.Actions.PreFilters.deleteAll()
//
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "PreFilter add single filter"
//            )
//            reader.Actions.PreFilters.add(deletefil)
//
//
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "PreFilter add 2nd single filter"
//            )
//            deletefil.setTagPattern("BBBBBBBBBBBBBBBBB")
//            val second: PreFilters.PreFilter = deletefil
//            reader.Actions.PreFilters.add(second)
//
//
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "PreFilter add 3nd single filter"
//            )
//            deletefil.setTagPattern("CCCCCCCCCCCCCCCC")
//            reader.Actions.PreFilters.add(deletefil)
//
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "PreFilter delete single 2nd filter"
//            )
//            reader.Actions.PreFilters.delete(second)
//
//
//            Thread.sleep(500)
//            prefilterLength = reader.Actions.PreFilters.length()
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "PreFilter after delete length = " + prefilterLength
//            )
//            for (i in 0..<prefilterLength) {
//                val fil: PreFilters.PreFilter = reader.Actions.PreFilters.getPreFilter(i)
//                Log.d(
//                    com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                    "PreFilter: " + fil.getStringTagPattern()
//                )
//            }
//
//            val filterss8: PreFilters = PreFilters()
//            val filter8: PreFilters.PreFilter = filterss8.PreFilter()
//            filter8.setAntennaID(1.toShort())
//            filter8.setTagPattern("88888")
//            filter8.setTagPatternBitCount(4)
//            filter8.setBitOffset(32)
//            filter8.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC)
//            filter8.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE)
//            filter8.StateAwareAction.setTarget(TARGET.TARGET_SL)
//            filter8.StateAwareAction.setStateAwareAction(STATE_AWARE_ACTION.STATE_AWARE_ACTION_ASRT_SL)
//            filter8.setTruncateAction(TRUNCATE_ACTION.TRUNCATE_ACTION_DO_NOT_TRUNCATE)
//            preFilterArray[5] = filter8
//
//            //reader.Actions.PreFilters.add(preFilterArray, null);
//            reader.Actions.PreFilters.add(preFilterArray, null)
//            Thread.sleep(500)
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "PreFilter 2nd set successfully"
//            )
//            prefilterLength = reader.Actions.PreFilters.length()
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "PreFilter length = " + prefilterLength
//            )
//            for (i in 0..<prefilterLength) {
//                val fil: PreFilters.PreFilter = reader.Actions.PreFilters.getPreFilter(i)
//                Log.d(
//                    com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                    "PreFilter: " + fil.getStringTagPattern()
//                )
//            }
//
//
//            //            reader.Config.setUniqueTagReport(true);
//        } catch (e: InvalidUsageException) {
//            e.printStackTrace()
//        } catch (e: OperationFailureException) {
//            e.printStackTrace()
//        } catch (e: InterruptedException) {
//            throw RuntimeException(e)
//        }
//    }
//
//    @kotlin.jvm.Synchronized
//    private fun connectReader() {
//        if (!isReaderConnected()) {
//            com.zebra.rfid.demo.sdksample.RFIDHandler.ConnectionTask().execute()
//        }
//    }
//
//    @kotlin.jvm.Synchronized
//    private fun GetAvailableReader() {
//        Log.d(com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG, "GetAvailableReader")
//        if (readers != null) {
//            Readers.attach(this)
//            try {
//                val availableReaders: ArrayList<ReaderDevice?>? =
//                    readers.GetAvailableRFIDReaderList()
//                if (availableReaders != null && !availableReaders.isEmpty()) {
//                    availableRFIDReaderList = availableReaders
//                    // if single reader is available then connect it
//                    Log.e(
//                        com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                        "Available readers to connect = " + availableRFIDReaderList.size()
//                    )
//                    if (availableRFIDReaderList.size() === 1) {
//                        readerDevice = availableRFIDReaderList.get(0)
//                        reader = readerDevice.getRFIDReader()
//                    } else {
//                        // search reader specified by name
//                        for (device in availableRFIDReaderList) {
//                            Log.d(
//                                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                                "device: " + device.getName()
//                            )
//                            if (device.getName().startsWith(readerName)) {
//                                readerDevice = device
//                                reader = readerDevice.getRFIDReader()
//                            }
//                        }
//                    }
//                    if (impinjExtensions == null) impinjExtensions = ImpinjExtensions(reader)
//                }
//            } catch (ie: InvalidUsageException) {
//                ie.printStackTrace()
//            }
//        }
//    }
//
//    // handler for receiving reader appearance events
//    @Override
//    fun RFIDReaderAppeared(readerDevice: ReaderDevice) {
//        Log.d(
//            com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//            "RFIDReaderAppeared " + readerDevice.getName()
//        )
//        context.sendToast("RFIDReaderAppeared")
//        connectReader()
//    }
//
//    @Override
//    fun RFIDReaderDisappeared(readerDevice: ReaderDevice) {
//        Log.d(
//            com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//            "RFIDReaderDisappeared " + readerDevice.getName()
//        )
//        context.sendToast("RFIDReaderDisappeared")
//        if (readerDevice.getName().equals(reader.getHostName())) disconnect()
//    }
//
//
//    @kotlin.jvm.Synchronized
//    private fun connect(): String {
//        if (reader != null) {
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "connect " + reader.getHostName()
//            )
//            try {
//                if (!reader.isConnected()) {
//                    // Establish connection to the RFID Reader
//                    reader.connect()
//                    ConfigureReader()
//
//                    //Call this function if the readerdevice supports scanner to setup scanner SDK
//                    //setupScannerSDK();
//                    if (reader.isConnected()) {
//                        return "Connected: " + reader.getHostName()
//                    }
//                }
//            } catch (e: InvalidUsageException) {
//                e.printStackTrace()
//            } catch (e: OperationFailureException) {
//                e.printStackTrace()
//                Log.d(
//                    com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                    "OperationFailureException " + e.getVendorMessage()
//                )
//                val des: String? = e.getResults().toString()
//                return "Connection failed" + e.getVendorMessage() + " " + des
//            }
//        }
//        return ""
//    }
//
//    private fun ConfigureReader() {
//        Log.d(
//            com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//            "ConfigureReader " + reader.getHostName()
//        )
//        IRFIDLogger.getLogger("SDKSAmpleApp").EnableDebugLogs(true)
//        if (reader.isConnected()) {
//            val triggerInfo: TriggerInfo = TriggerInfo()
//            triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE)
//            triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE)
//            try {
//                // receive events from reader
//                if (eventHandler == null) eventHandler =
//                    com.zebra.rfid.demo.sdksample.RFIDHandler.EventHandler()
//                reader.Events.addEventsListener(eventHandler)
//                // HH event
//                reader.Events.setHandheldEvent(true)
//                // tag event with tag data
//                reader.Events.setTagReadEvent(true)
//                reader.Events.setAttachTagDataWithReadEvent(false)
//                // set trigger mode as rfid so scanner beam will not come
//                reader.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, true)
//                // set start and stop triggers
//                reader.Config.setStartTrigger(triggerInfo.StartTrigger)
//                reader.Config.setStopTrigger(triggerInfo.StopTrigger)
//                // power levels are index based so maximum power supported get the last one
//                MAX_POWER = reader.ReaderCapabilities.getTransmitPowerLevelValues().length - 1
//                // set antenna configurations
//                val config: Antennas.AntennaRfConfig = reader.Config.Antennas.getAntennaRfConfig(1)
//                config.setTransmitPowerIndex(MAX_POWER)
//                config.setrfModeTableIndex(0)
//                config.setTari(0)
//                reader.Config.Antennas.setAntennaRfConfig(1, config)
//                // Set the singulation control
//                val s1_singulationControl: Antennas.SingulationControl =
//                    reader.Config.Antennas.getSingulationControl(1)
//                s1_singulationControl.setSession(SESSION.SESSION_S0)
//                s1_singulationControl.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A)
//                s1_singulationControl.Action.setSLFlag(SL_FLAG.SL_ALL)
//                reader.Config.Antennas.setSingulationControl(1, s1_singulationControl)
//                // delete any prefilters
//                //reader.Actions.PreFilters.deleteAll();
//                //
//                val prefilterNo: Int = reader.Actions.PreFilters.length()
//                Log.d(
//                    com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                    "Prefilters count: " + prefilterNo
//                )
//                reader.Actions.PreFilters.deleteAll()
//                //  setPreFilters();
//            } catch (e: InvalidUsageException) {
//                e.printStackTrace()
//            } catch (e: OperationFailureException) {
//                e.printStackTrace()
//            }
//        }
//    }
//
//
//    /*
//
//    void onDestroy() {
//        dispose();
//    }
//
//    String onResume() {
//        return connect();
//    }
//
//    void onPause() {
//        disconnect();
//    }
//*/
//    /*
//    private synchronized String connect() {
//        Log.d(TAG, "connect");
//        if (reader != null) {
//            try {
//                if (!reader.isConnected()) {
//                    // Establish connection to the RFID Reader
//                    reader.connect();
//                    ConfigureReader();
//
//                    setupScannerSDK();
//                    return "Connected";
//                }
//            } catch (InvalidUsageException e) {
//                //e.printStackTrace();
//            } catch (OperationFailureException e) {
//                //e.printStackTrace();
//                Log.d(TAG, "OperationFailureException " + e.getVendorMessage());
//                return "Connection failed" + e.getVendorMessage() + " " + e.getStatusDescription();
//            }
//        }
//        return "";
//    }
//
//    private void ConfigureReader() {
//        if (reader.isConnected()) {
//            TriggerInfo triggerInfo = new TriggerInfo();
//            triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE);
//            triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE);
//            try {
//                // receive events from reader
//                if (eventHandler == null)
//                    eventHandler = new EventHandler();
//                reader.Events.addEventsListener(eventHandler);
//                // HH event
//                reader.Events.setHandheldEvent(true);
//                // tag event with tag data
//                reader.Events.setTagReadEvent(true);
//                reader.Events.setAttachTagDataWithReadEvent(false);
//                reader.Events.setReaderDisconnectEvent(true);
//
//                reader.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, false);
//                // set start and stop triggers
//                reader.Config.setStartTrigger(triggerInfo.StartTrigger);
//                reader.Config.setStopTrigger(triggerInfo.StopTrigger);
//
//            } catch (InvalidUsageException | OperationFailureException e) {
//                //e.printStackTrace();
//            }
//        }
//    }
//*/
//    fun setupScannerSDK() {
//        if (sdkHandler == null) {
//            sdkHandler = SDKHandler(context)
//            //For cdc device
//            val result: DCSSDKDefs.DCSSDK_RESULT? =
//                sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_USB_CDC)
//
//            //For bluetooth device
//            val btResult: DCSSDKDefs.DCSSDK_RESULT? =
//                sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_LE)
//            val btNormalResult: DCSSDKDefs.DCSSDK_RESULT? =
//                sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_NORMAL)
//
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                btNormalResult.toString() + " results " + btResult
//            )
//            sdkHandler.dcssdkSetDelegate(this)
//
//            var notifications_mask = 0
//            // We would like to subscribe to all scanner available/not-available events
//            notifications_mask =
//                notifications_mask or (DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_APPEARANCE.value or DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_DISAPPEARANCE.value)
//
//            // We would like to subscribe to all scanner connection events
//            notifications_mask =
//                notifications_mask or (DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value or DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value or DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_ESTABLISHMENT.value or DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_TERMINATION.value)
//
//
//            // We would like to subscribe to all barcode events
//            // subscribe to events set in notification mask
//            sdkHandler.dcssdkSubsribeForEvents(notifications_mask)
//        }
//        if (sdkHandler != null) {
//            var availableScanners: ArrayList<DCSScannerInfo?>? = ArrayList()
//            availableScanners =
//                sdkHandler.dcssdkGetAvailableScannersList() as ArrayList<DCSScannerInfo?>?
//
//            scannerList.clear()
//            if (availableScanners != null) {
//                for (scanner in availableScanners) {
//                    scannerList.add(scanner)
//                }
//            } else Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "Available scanners null"
//            )
//        }
//        if (reader != null) {
//            for (device in scannerList) {
//                if (device.getScannerName().contains(reader.getHostName())) {
//                    try {
//                        sdkHandler.dcssdkEstablishCommunicationSession(device.getScannerID())
//                        scannerID = device.getScannerID()
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                    }
//                }
//            }
//        }
//    }
//
//    @kotlin.jvm.Synchronized
//    private fun disconnect() {
//        Log.d(com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG, "Disconnect")
//        try {
//            if (reader != null) {
//                if (eventHandler != null) reader.Events.removeEventsListener(eventHandler)
//                if (sdkHandler != null) {
//                    sdkHandler.dcssdkTerminateCommunicationSession(scannerID)
//                    scannerList = null
//                }
//                reader.disconnect()
//                context.sendToast("Disconnecting reader")
//                //reader = null;
//            }
//        } catch (e: InvalidUsageException) {
//            e.printStackTrace()
//        } catch (e: OperationFailureException) {
//            e.printStackTrace()
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//
//    @kotlin.jvm.Synchronized
//    private fun dispose() {
//        disconnect()
//        try {
//            if (reader != null) {
//                //Toast.makeText(getApplicationContext(), "Disconnecting reader", Toast.LENGTH_LONG).show();
//                reader = null
//                readers.Dispose()
//                readers = null
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//
//    @kotlin.jvm.Synchronized
//    fun performInventory() {
//        try {
//            reader.Actions.Inventory.perform()
//        } catch (e: InvalidUsageException) {
//            throw RuntimeException("InvalidUsageException:\n" + e)
//        } catch (e: OperationFailureException) {
//            val error = "Operation Failed: " + e.getVendorMessage() + "\nResults: " + e.getResults()
//            throw RuntimeException("OperationFailureException:\n" + error)
//        } catch (e: Exception) {
//            throw RuntimeException("Another Exception:\n" + e)
//        }
//    }
//
//    @kotlin.jvm.Synchronized
//    fun stopInventory() {
//        try {
//            reader.Actions.Inventory.stop()
//        } catch (e: InvalidUsageException) {
//            throw RuntimeException("InvalidUsageException:\n" + e)
//        } catch (e: OperationFailureException) {
//            val error = "Operation Failed: " + e.getVendorMessage() + "\nResults: " + e.getResults()
//            throw RuntimeException("OperationFailureException:\n" + error)
//        } catch (e: Exception) {
//            throw RuntimeException("Another Exception:\n" + e)
//        }
//    }
//
//    fun scanCode() {
//        val in_xml = "<inArgs><scannerID>" + scannerID + "</scannerID></inArgs>"
//        com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.cmdExecTask =
//            com.zebra.rfid.demo.sdksample.RFIDHandler.MyAsyncTask(
//                scannerID,
//                DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_DEVICE_PULL_TRIGGER,
//                null
//            )
//        com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.cmdExecTask.execute(in_xml)
//    }
//
//    fun executeCommand(
//        opCode: DCSSDKDefs.DCSSDK_COMMAND_OPCODE?,
//        inXML: String?,
//        outXML: StringBuilder?,
//        scannerID: Int
//    ): Boolean {
//        var outXML: StringBuilder? = outXML
//        if (sdkHandler != null) {
//            if (outXML == null) {
//                outXML = StringBuilder()
//            }
//            val result: DCSSDKDefs.DCSSDK_RESULT =
//                sdkHandler.dcssdkExecuteCommandOpCodeInXMLForScanner(
//                    opCode,
//                    inXML,
//                    outXML,
//                    scannerID
//                )
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "execute command returned " + result.toString()
//            )
//            if (result === DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS) return true
//            else if (result === DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_FAILURE) return false
//        }
//        return false
//    }
//
//    fun enableImpinjVisibility(): String? {
//        try {
//            impinjExtensions.enableTagVisibility(password, 1.toShort())
//            //reader.enableImpinjVisibility("","");
//        } catch (e: OperationFailureException) {
//            e.printStackTrace()
//        } catch (e: InvalidUsageException) {
//            e.printStackTrace()
//        }
//
//        return "Impinj Visibility Enabled"
//    }
//
//    fun disableImpinjVisibilty(): String? {
//        try {
//            impinjExtensions.disableTagVisibility(password, 1.toShort())
//            //reader.enableImpinjVisibility("","");
//        } catch (e: OperationFailureException) {
//            e.printStackTrace()
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "Impinj Disable Visibility exception " + e.getMessage()
//            )
//            return "Failed to disable Impinj Visibility: " + e.getMessage()
//        } catch (e: InvalidUsageException) {
//            e.printStackTrace()
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "Impinj Disable Visibility exception " + e.getMessage()
//            )
//            return "Failed to disable Impinj Visibility: " + e.getMessage()
//        } catch (e: IllegalStateException) {
//            e.printStackTrace()
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "Impinj Disable Visibility exception " + e.getMessage()
//            )
//            return "Failed to disable Impinj Visibility: " + e.getMessage()
//        }
//        return "Impinj Visibility disabled"
//    }
//
//    fun enableImpinjProtection(): String? {
//        try {
//            val tagData: TagData = TagData()
//            impinjExtensions.enableTagProtection(impinjTag, password, tagData)
//        } catch (e: OperationFailureException) {
//            e.printStackTrace()
//            return "Failed to enable Impinj Protection: " + e.getMessage()
//        } catch (e: InvalidUsageException) {
//            e.printStackTrace()
//            return "Failed to enable Impinj Protection: " + e.getMessage()
//        }
//        return "Impinj Protection Enabled"
//    }
//
//    fun disableImpinjProtection(): String? {
//        try {
//            val tagData: TagData = TagData()
//            impinjExtensions.disableTagProtection(impinjTag, password, tagData, 1.toShort())
//            // return " Impinj Protection Disabled";
//        } catch (e: OperationFailureException) {
//            e.printStackTrace()
//            return "Failed "
//        } catch (e: InvalidUsageException) {
//            e.printStackTrace()
//            return "Failed "
//        }
//
//        //String tagId = "1234ABCD00000000000025B1";
//        return "Impinj Protection Disabled"
//    }
//
//    fun tagFocus(tagFocus: Boolean): String? {
//        try {
//            impinjExtensions.setTagFocus(tagFocus, 1.toShort())
//            return "Success"
//        } catch (e: InvalidUsageException) {
//            e.printStackTrace()
//            return "fail"
//        } catch (e: OperationFailureException) {
//            e.printStackTrace()
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "Tag focus exception " + e.getMessage() + e.getVendorMessage()
//            )
//            return "Fail"
//        } catch (e: IllegalStateException) {
//            e.printStackTrace()
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "Tag focus exception " + e.getMessage()
//            )
//            return "Fail"
//        }
//    }
//
//    //    D4 - S2
//    //11010100
//    //
//    //    F5 - S3
//    //11110101
//    //
//    //        90 - S0
//    //10010000
//    //    B1  - S3
//    //10110001
//    fun tagQuiet(
//        tagMask: Array<ENUM_TAGQUIET_MASK?>?,
//        target: TARGET?,
//        stateAwareAction: STATE_AWARE_ACTION?
//    ): String? {
//        try {
//            impinjExtensions.setTagQuiet(tagMask, target, stateAwareAction, 1.toShort())
//        } catch (e: InvalidUsageException) {
//            e.printStackTrace()
//            return "fail"
//        } catch (e: OperationFailureException) {
//            e.printStackTrace()
//            return "Fail"
//        }
//
//        return "Tagquiet Set to TID mask "
//    }
//
//    fun singulation(session: SESSION, inventoryState: INVENTORY_STATE): String? {
//        try {
//            val s1_singulationControl: Antennas.SingulationControl =
//                reader.Config.Antennas.getSingulationControl(1)
//            s1_singulationControl.setSession(session)
//            s1_singulationControl.Action.setInventoryState(inventoryState)
//            s1_singulationControl.Action.setSLFlag(SL_FLAG.SL_ALL)
//            reader.Config.Antennas.setSingulationControl(1, s1_singulationControl)
//        } catch (e: InvalidUsageException) {
//            e.printStackTrace()
//        } catch (e: OperationFailureException) {
//            e.printStackTrace()
//            return e.getResults().toString() + " " + e.getVendorMessage()
//        }
//        return "Session set to " + session.toString() + " " + "Inventory State set to " + inventoryState.toString()
//    }
//
//    fun setPrefilter(
//        mb: MEMORY_BANK?,
//        stateAwareAction: STATE_AWARE_ACTION?,
//        target: TARGET?,
//        tagPattern: String?,
//        offset: Int,
//        length: Int
//    ): String? {
//        val preFilters: PreFilters = PreFilters()
//        val filter: PreFilters.PreFilter = preFilters.PreFilter()
//        filter.setAntennaID(1.toShort())
//        filter.setBitOffset(offset)
//        filter.setTagPatternBitCount(length)
//        filter.setTagPattern(tagPattern)
//        filter.setMemoryBank(mb)
//        filter.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE)
//        filter.StateAwareAction.setTarget(target)
//        filter.StateAwareAction.setStateAwareAction(stateAwareAction)
//
//        try {
//            reader.Actions.PreFilters.add(filter)
//        } catch (e: InvalidUsageException) {
//            throw RuntimeException(e)
//        } catch (e: OperationFailureException) {
//            throw RuntimeException(e)
//        }
//        return "Prefilter set true"
//    }
//
//
//    internal interface ResponseHandlerInterface {
//        fun handleTagdata(tagData: Array<TagData?>?)
//
//        fun handleTriggerPress(pressed: Boolean)
//
//        fun barcodeData(`val`: String?)
//
//        fun sendToast(`val`: String?) //void handleStatusEvents(Events.StatusEventData eventData);
//    }
//
//    // Enumerates SDK based on host device
//    private inner class CreateInstanceTask : AsyncTask<Void?, Void?, Void?>() {
//        private var invalidUsageException: InvalidUsageException? = null
//
//        @Override
//        protected fun doInBackground(vararg voids: Void?): Void? {
//            Log.d(com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG, "CreateInstanceTask")
//            try {
//                readers = Readers(context, ENUM_TRANSPORT.BLUETOOTH)
//                availableRFIDReaderList = readers.GetAvailableRFIDReaderList()
//                if (availableRFIDReaderList.isEmpty()) {
//                    Log.d(
//                        com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                        "Reader not available in SERVICE_USB Transport trying with BLUETOOTH transport"
//                    )
//                    readers.setTransport(ENUM_TRANSPORT.BLUETOOTH)
//                    availableRFIDReaderList = readers.GetAvailableRFIDReaderList()
//                }
//                if (availableRFIDReaderList.isEmpty()) {
//                    Log.d(
//                        com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                        "Reader not available in BLUETOOTH Transport trying with SERVICE_SERIAL transport"
//                    )
//                    readers.setTransport(ENUM_TRANSPORT.SERVICE_SERIAL)
//                    availableRFIDReaderList = readers.GetAvailableRFIDReaderList()
//                }
//                if (availableRFIDReaderList.isEmpty()) {
//                    Log.d(
//                        com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                        "Reader not available in SERVICE_SERIAL Transport trying with SERVICE_USB transport"
//                    )
//                    readers.setTransport(ENUM_TRANSPORT.SERVICE_USB)
//                    availableRFIDReaderList = readers.GetAvailableRFIDReaderList()
//                }
//            } catch (e: InvalidUsageException) {
//                invalidUsageException = e
//                e.printStackTrace()
//            }
//            return null
//        }
//
//        @Override
//        protected fun onPostExecute(aVoid: Void?) {
//            super.onPostExecute(aVoid)
//            if (invalidUsageException != null) {
//                context.sendToast("Failed to get Available Readers\n" + invalidUsageException.getInfo())
//                readers = null
//            } else if (availableRFIDReaderList.isEmpty()) {
//                context.sendToast("No Available Readers to proceed")
//                readers = null
//            } else {
//                connectReader()
//            }
//        }
//    }
//
//    private inner class ConnectionTask : AsyncTask<Void?, Void?, String?>() {
//        @Override
//        protected fun doInBackground(vararg voids: Void?): String {
//            Log.d(com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG, "ConnectionTask")
//            GetAvailableReader()
//            if (reader != null) return connect()
//            return "Failed to find or connect reader"
//        }
//
//        @Override
//        protected fun onPostExecute(result: String?) {
//            super.onPostExecute(result)
//            textView.setText(result)
//        }
//    }
//
//    private inner class MyAsyncTask(
//        scannerId: Int,
//        opcode: DCSSDKDefs.DCSSDK_COMMAND_OPCODE?,
//        outXML: StringBuilder?
//    ) : AsyncTask<String?, Integer?, Boolean?>() {
//        var scannerId: Int
//        var outXML: StringBuilder?
//        var opcode: DCSSDKDefs.DCSSDK_COMMAND_OPCODE?
//
//        /** private CustomProgressDialog progressDialog; */
//        init {
//            this.scannerId = scannerId
//            this.opcode = opcode
//            this.outXML = outXML
//        }
//
//        @Override
//        protected fun onPreExecute() {
//            super.onPreExecute()
//        }
//
//
//        @Override
//        protected fun doInBackground(vararg strings: String?): Boolean {
//            return executeCommand(opcode, strings[0], outXML, scannerId)
//        }
//
//        @Override
//        protected fun onPostExecute(b: Boolean?) {
//            super.onPostExecute(b)
//        }
//    }
//
//    // Read/Status Notify handler
//    // Implement the RfidEventsLister class to receive event notifications
//    inner class EventHandler : RfidEventsListener {
//        // Read Event Notification
//        fun eventReadNotify(e: RfidReadEvents?) {
//            val myTags: Array<TagData?>? = reader.Actions.getReadTags(100)
//            if (myTags != null) {
//                for (index in myTags.indices) {
//                    //  Log.d(TAG, "Tag ID " + myTags[index].getTagID());
//                    Log.d(
//                        com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                        "Tag ID" + myTags[index].getTagID() + "RSSI value " + myTags[index].getPeakRSSI()
//                    )
//                    Log.d(
//                        com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                        "RSSI value " + myTags[index].getPeakRSSI()
//                    )
//
//                    /* To get the RSSI value*/   //   Log.d(TAG, "RSSI value "+ myTags[index].getPeakRSSI());
//                }
//                com.zebra.rfid.demo.sdksample.RFIDHandler.AsyncDataUpdate().execute(myTags)
//            }
//        }
//
//        // Status Event Notification
//        fun eventStatusNotify(rfidStatusEvents: RfidStatusEvents) {
//            Log.d(
//                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                "Status Notification: " + rfidStatusEvents.StatusEventData.getStatusEventType()
//            )
//            if (rfidStatusEvents.StatusEventData.getStatusEventType() === STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
//                if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() === HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {
//                    object : AsyncTask<Void?, Void?, Void?>() {
//                        @Override
//                        protected fun doInBackground(vararg voids: Void?): Void? {
//                            Log.d(
//                                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                                "HANDHELD_TRIGGER_PRESSED"
//                            )
//                            context.handleTriggerPress(true)
//                            return null
//                        }
//                    }.execute()
//                }
//                if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() === HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
//                    object : AsyncTask<Void?, Void?, Void?>() {
//                        @Override
//                        protected fun doInBackground(vararg voids: Void?): Void? {
//                            context.handleTriggerPress(false)
//                            Log.d(
//                                com.zebra.rfid.demo.sdksample.RFIDHandler.Companion.TAG,
//                                "HANDHELD_TRIGGER_RELEASED"
//                            )
//                            return null
//                        }
//                    }.execute()
//                }
//            }
//            if (rfidStatusEvents.StatusEventData.getStatusEventType() === STATUS_EVENT_TYPE.DISCONNECTION_EVENT) {
//                object : AsyncTask<Void?, Void?, Void?>() {
//                    @Override
//                    protected fun doInBackground(vararg voids: Void?): Void? {
//                        disconnect()
//                        return null
//                    }
//                }.execute()
//            }
//        }
//    }
//
//    private inner class AsyncDataUpdate : AsyncTask<Array<TagData?>?, Void?, Void?>() {
//        @Override
//        protected fun doInBackground(vararg params: Array<TagData?>?): Void? {
//            context.handleTagdata(params[0])
//
//            return null
//        }
//    } //    public String removeTagQuiet() {
//    //        try {
//    //            impinjExtensions.removeTagQuiet();
//    //        } catch (InvalidUsageException e) {
//    //            e.printStackTrace();
//    //            return "fail";
//    //
//    //        } catch (OperationFailureException e) {
//    //            e.printStackTrace();
//    //            return "Fail";
//    //        }catch (IllegalStateException e) {
//    //            e.printStackTrace();
//    //            Log.d(TAG,"Tag quiet exception "+e.getMessage());
//    //            return "Fail";
//    //        }
//    //        return "Tagquiet removed";
//    //    }
//
//
//    companion object {
//        val TAG: String = "RFID_SAMPLE"
//        var cmdExecTask: com.zebra.rfid.demo.sdksample.RFIDHandler.MyAsyncTask? = null
//    }
//}



