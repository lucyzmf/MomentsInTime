package dev.lucy.momentsintime.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Helper class for managing USB serial port connections and sending trigger codes
 */
class SerialPortHelper(private val context: Context) {

    companion object {
        private const val TAG = "SerialPortHelper"
        private const val ACTION_USB_PERMISSION = "dev.lucy.momentsintime.USB_PERMISSION"
        private const val BAUD_RATE = 9600
        
        // Trigger codes for different event types
        object TriggerCode {
            const val EXPERIMENT_START = 1
            const val EXPERIMENT_END = 2
            const val BLOCK_START = 10
            const val BLOCK_END = 11
            const val TRIAL_START = 20
            const val TRIAL_END = 21
            const val VIDEO_START = 30
            const val VIDEO_END = 31
            const val FIXATION_START = 40
            const val FIXATION_END = 41
            const val RECORDING_START = 50
            const val RECORDING_END = 51
        }
    }
    
    // USB components
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbSerialPort: UsbSerialPort? = null
    
    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Permission request pending intent
    private val permissionIntent = PendingIntent.getBroadcast(
        context, 
        0, 
        Intent(ACTION_USB_PERMISSION),
        PendingIntent.FLAG_IMMUTABLE
    )
    
    // USB permission and detach receivers
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            Log.d(TAG, "USB permission granted for device: ${it.deviceName}")
                            connectToDevice(it)
                        }
                    } else {
                        Log.e(TAG, "USB permission denied")
                        _connectionState.value = ConnectionState.PERMISSION_DENIED
                    }
                }
            }
        }
    }
    
    private val usbDetachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                device?.let {
                    if (it == usbDevice) {
                        Log.d(TAG, "USB device detached: ${it.deviceName}")
                        disconnect()
                    }
                }
            }
        }
    }
    
    init {
        // Register receivers
        val permissionFilter = IntentFilter(ACTION_USB_PERMISSION)
        context.registerReceiver(usbPermissionReceiver, permissionFilter)
        
        val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        context.registerReceiver(usbDetachReceiver, detachFilter)
        
        // Initial device scan
        scanForDevices()
    }
    
    /**
     * Scan for available USB serial devices
     * @return List of available USB devices
     */
    fun scanForDevices(): List<UsbDevice> {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val devices = mutableListOf<UsbDevice>()
        
        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "No USB serial devices found")
            return devices
        }
        
        for (driver in availableDrivers) {
            val device = driver.device
            devices.add(device)
            Log.d(TAG, "Found USB device: ${device.deviceName}, " +
                    "Product ID: ${device.productId}, " +
                    "Vendor ID: ${device.vendorId}")
        }
        
        return devices
    }
    
    /**
     * Request permission for a USB device
     * @param device The USB device to request permission for
     */
    fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "Already have permission for device: ${device.deviceName}")
            connectToDevice(device)
        } else {
            Log.d(TAG, "Requesting permission for device: ${device.deviceName}")
            usbManager.requestPermission(device, permissionIntent)
            _connectionState.value = ConnectionState.PERMISSION_PENDING
        }
    }
    
    /**
     * Connect to the first available USB serial device
     * @return true if a device was found and connection attempt started
     */
    fun connectToFirstAvailable(): Boolean {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        
        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "No USB serial devices found")
            _connectionState.value = ConnectionState.NO_DEVICES
            return false
        }
        
        // Just use the first available driver
        val driver = availableDrivers[0]
        val device = driver.device
        
        requestPermission(device)
        return true
    }
    
    /**
     * Connect to a specific USB device
     * @param device The USB device to connect to
     */
    private fun connectToDevice(device: UsbDevice) {
        scope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                
                // Find the driver for the device
                val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                if (driver == null) {
                    Log.e(TAG, "No driver found for device: ${device.deviceName}")
                    _connectionState.value = ConnectionState.DRIVER_NOT_FOUND
                    return@launch
                }
                
                // Open a connection to the device
                val connection = usbManager.openDevice(device)
                if (connection == null) {
                    Log.e(TAG, "Could not open connection to device: ${device.deviceName}")
                    _connectionState.value = ConnectionState.CONNECTION_FAILED
                    return@launch
                }
                
                // Get the first port (most devices have just one)
                val port = driver.ports[0]
                
                try {
                    // Open and configure the port
                    port.open(connection)
                    port.setParameters(BAUD_RATE, UsbSerialPort.DATABITS_8, 
                                      UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    
                    // Store references
                    usbDevice = device
                    usbConnection = connection
                    usbSerialPort = port
                    
                    _connectionState.value = ConnectionState.CONNECTED
                    Log.d(TAG, "Successfully connected to device: ${device.deviceName}")
                    
                    // Send a test byte to verify connection
                    sendTriggerCode(0)
                    
                } catch (e: IOException) {
                    Log.e(TAG, "Error opening serial port: ${e.message}")
                    try {
                        port.close()
                    } catch (e2: IOException) {
                        // Ignore
                    }
                    _connectionState.value = ConnectionState.CONNECTION_FAILED
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to device: ${e.message}")
                _connectionState.value = ConnectionState.CONNECTION_FAILED
            }
        }
    }
    
    /**
     * Disconnect from the current USB device
     */
    fun disconnect() {
        scope.launch {
            try {
                usbSerialPort?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing serial port: ${e.message}")
            }
            
            usbSerialPort = null
            usbConnection = null
            usbDevice = null
            
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.d(TAG, "Disconnected from USB device")
        }
    }
    
    /**
     * Send a trigger code to the connected device
     * @param code The trigger code to send
     * @return true if the code was sent successfully
     */
    fun sendTriggerCode(code: Int): Boolean {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send trigger code: not connected (state: ${_connectionState.value})")
            return false
        }
        
        return try {
            val buffer = byteArrayOf(code.toByte())
            val port = usbSerialPort ?: return false
            
            scope.launch {
                try {
                    port.write(buffer, 1000)
                    Log.d(TAG, "Sent trigger code: $code")
                } catch (e: IOException) {
                    Log.e(TAG, "Error sending trigger code: ${e.message}")
                    _connectionState.value = ConnectionState.ERROR
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing trigger code: ${e.message}")
            false
        }
    }
    
    /**
     * Send a trigger code for an experiment event
     * @param eventType The event type from EventType enum
     * @return true if the code was sent successfully
     */
    fun sendEventTrigger(eventType: dev.lucy.momentsintime.logging.EventType): Boolean {
        val triggerCode = when (eventType) {
            dev.lucy.momentsintime.logging.EventType.EXPERIMENT_START -> TriggerCode.EXPERIMENT_START
            dev.lucy.momentsintime.logging.EventType.EXPERIMENT_END -> TriggerCode.EXPERIMENT_END
            dev.lucy.momentsintime.logging.EventType.BLOCK_START -> TriggerCode.BLOCK_START
            dev.lucy.momentsintime.logging.EventType.BLOCK_END -> TriggerCode.BLOCK_END
            dev.lucy.momentsintime.logging.EventType.TRIAL_START -> TriggerCode.TRIAL_START
            dev.lucy.momentsintime.logging.EventType.TRIAL_END -> TriggerCode.TRIAL_END
            dev.lucy.momentsintime.logging.EventType.VIDEO_START -> TriggerCode.VIDEO_START
            dev.lucy.momentsintime.logging.EventType.VIDEO_END -> TriggerCode.VIDEO_END
            dev.lucy.momentsintime.logging.EventType.FIXATION_START -> TriggerCode.FIXATION_START
            dev.lucy.momentsintime.logging.EventType.FIXATION_END -> TriggerCode.FIXATION_END
            dev.lucy.momentsintime.logging.EventType.RECORDING_START -> TriggerCode.RECORDING_START
            dev.lucy.momentsintime.logging.EventType.RECORDING_END -> TriggerCode.RECORDING_END
            else -> return false // No trigger for other event types
        }
        
        return sendTriggerCode(triggerCode)
    }
    
    /**
     * Clean up resources when no longer needed
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(usbPermissionReceiver)
            context.unregisterReceiver(usbDetachReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receivers: ${e.message}")
        }
        
        disconnect()
    }
    
    /**
     * Connection state enum
     */
    enum class ConnectionState {
        DISCONNECTED,
        NO_DEVICES,
        PERMISSION_PENDING,
        PERMISSION_DENIED,
        CONNECTING,
        CONNECTED,
        DRIVER_NOT_FOUND,
        CONNECTION_FAILED,
        ERROR
    }
}
