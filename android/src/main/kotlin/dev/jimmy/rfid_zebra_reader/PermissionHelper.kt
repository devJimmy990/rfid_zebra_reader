package dev.jimmy.rfid_zebra_reader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionHelper(
    private val context: Context,
    private var activity: Activity?
) {
    companion object {
        const val PERMISSION_REQUEST_CODE = 1001
        
        // Permissions required for different Android versions
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
    
    private var permissionCallback: PermissionCallback? = null
    
    interface PermissionCallback {
        fun onPermissionsGranted()
        fun onPermissionsDenied(deniedPermissions: List<String>)
    }
    
    /**
     * Set the current activity (needed for requesting permissions)
     */
    fun setActivity(activity: Activity?) {
        this.activity = activity
    }
    
    /**
     * Get required permissions based on Android version
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            PERMISSIONS_ANDROID_12_AND_ABOVE
        } else {
            // Android 11 and below
            PERMISSIONS_ANDROID_12_BELOW
        }
    }
    
    /**
     * Check if all required permissions are granted
     */
    fun checkPermissions(): Boolean {
        val requiredPermissions = getRequiredPermissions()
        
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == 
                PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Get detailed permission status
     */
    fun getPermissionStatus(): Map<String, Any> {
        val requiredPermissions = getRequiredPermissions()
        val status = mutableMapOf<String, Boolean>()
        
        requiredPermissions.forEach { permission ->
            val granted = ContextCompat.checkSelfPermission(context, permission) == 
                PackageManager.PERMISSION_GRANTED
            
            // Get user-friendly name
            val permissionName = when (permission) {
                Manifest.permission.BLUETOOTH -> "Bluetooth"
                Manifest.permission.BLUETOOTH_ADMIN -> "Bluetooth Admin"
                Manifest.permission.BLUETOOTH_SCAN -> "Bluetooth Scan"
                Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth Connect"
                Manifest.permission.ACCESS_FINE_LOCATION -> "Fine Location"
                Manifest.permission.ACCESS_COARSE_LOCATION -> "Coarse Location"
                else -> permission.substringAfterLast(".")
            }
            
            status[permissionName] = granted
        }
        
        val allGranted = status.values.all { it }
        
        return mapOf(
            "allGranted" to allGranted,
            "permissions" to status,
            "androidVersion" to Build.VERSION.SDK_INT,
            "missingPermissions" to status.filterValues { !it }.keys.toList()
        )
    }
    
    /**
     * Request permissions from user
     */
    fun requestPermissions(callback: PermissionCallback): Boolean {
        val currentActivity = activity
        
        if (currentActivity == null) {
            callback.onPermissionsDenied(listOf("Activity not available"))
            return false
        }
        
        // Check if already granted
        if (checkPermissions()) {
            callback.onPermissionsGranted()
            return true
        }
        
        // Store callback for result handling
        permissionCallback = callback
        
        val requiredPermissions = getRequiredPermissions()
        val deniedPermissions = mutableListOf<String>()
        
        // Check which permissions are denied
        requiredPermissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(context, permission) != 
                PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permission)
            }
        }
        
        if (deniedPermissions.isEmpty()) {
            callback.onPermissionsGranted()
            return true
        }
        
        // Request missing permissions
        ActivityCompat.requestPermissions(
            currentActivity,
            deniedPermissions.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
        
        return false
    }
    
    /**
     * Handle permission request result
     * This should be called from Activity's onRequestPermissionsResult
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode != PERMISSION_REQUEST_CODE) {
            return false
        }
        
        val callback = permissionCallback ?: return false
        
        // Check if all permissions were granted
        val allGranted = grantResults.isNotEmpty() && 
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        
        if (allGranted) {
            callback.onPermissionsGranted()
        } else {
            // Find which permissions were denied
            val deniedPermissions = mutableListOf<String>()
            permissions.forEachIndexed { index, permission ->
                if (grantResults.getOrNull(index) != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permission)
                }
            }
            callback.onPermissionsDenied(deniedPermissions)
        }
        
        permissionCallback = null
        return true
    }
    
    /**
     * Check if permission rationale should be shown
     */
    fun shouldShowRationale(permission: String): Boolean {
        val currentActivity = activity ?: return false
        return ActivityCompat.shouldShowRequestPermissionRationale(currentActivity, permission)
    }
    
    /**
     * Get permissions that need rationale
     */
    fun getPermissionsNeedingRationale(): List<String> {
        val currentActivity = activity ?: return emptyList()
        val requiredPermissions = getRequiredPermissions()
        
        return requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != 
                PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.shouldShowRequestPermissionRationale(currentActivity, permission)
        }
    }
    
    /**
     * Check if permissions are permanently denied
     */
    fun hasPermissionsPermanentlyDenied(): Boolean {
        val currentActivity = activity ?: return false
        val requiredPermissions = getRequiredPermissions()
        
        return requiredPermissions.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) != 
                PackageManager.PERMISSION_GRANTED &&
            !ActivityCompat.shouldShowRequestPermissionRationale(currentActivity, permission)
        }
    }
}