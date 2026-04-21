package com.example.otpforwarder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 100

    private lateinit var statusReceiveSms: TextView
    private lateinit var statusReadSms: TextView
    private lateinit var statusSendSms: TextView
    private lateinit var statusNotifications: TextView
    
    private lateinit var editForwardTo: EditText
    private lateinit var editFilterText: EditText
    private lateinit var editPrefixText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusReceiveSms = findViewById(R.id.statusReceiveSms)
        statusReadSms = findViewById(R.id.statusReadSms)
        statusSendSms = findViewById(R.id.statusSendSms)
        statusNotifications = findViewById(R.id.statusNotifications)
        
        editForwardTo = findViewById(R.id.editForwardTo)
        editFilterText = findViewById(R.id.editFilterText)
        editPrefixText = findViewById(R.id.editPrefixText)

        val sharedPrefs = getSharedPreferences("forwarder_prefs", Context.MODE_PRIVATE)
        editForwardTo.setText(sharedPrefs.getString("forward_to", ""))
        editFilterText.setText(sharedPrefs.getString("filter_text", ""))
        editPrefixText.setText(sharedPrefs.getString("prefix_text", "my lord here you go:"))

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val forwardTo = editForwardTo.text.toString()
            val filterText = editFilterText.text.toString()
            val prefixText = editPrefixText.text.toString()
            
            sharedPrefs.edit().apply {
                putString("forward_to", forwardTo)
                putString("filter_text", filterText)
                putString("prefix_text", prefixText)
                apply()
            }
            Toast.makeText(this, "Configuration Saved!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            updatePermissionStatus()
        }

        updatePermissionStatus()
        requestSmsPermissions()
    }

    private fun updatePermissionStatus() {
        statusReceiveSms.text = "RECEIVE_SMS: ${getPermissionStatus(Manifest.permission.RECEIVE_SMS)}"
        statusReadSms.text = "READ_SMS: ${getPermissionStatus(Manifest.permission.READ_SMS)}"
        statusSendSms.text = "SEND_SMS: ${getPermissionStatus(Manifest.permission.SEND_SMS)}"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            statusNotifications.text = "NOTIFICATIONS: ${getPermissionStatus(Manifest.permission.POST_NOTIFICATIONS)}"
        } else {
            statusNotifications.text = "NOTIFICATIONS: Not required (< API 33)"
        }
    }

    private fun getPermissionStatus(permission: String): String {
        return if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            "Granted ✅"
        } else {
            "Denied ❌"
        }
    }

    private fun requestSmsPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), SMS_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == SMS_PERMISSION_CODE) {
            updatePermissionStatus()
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "All Permissions Granted ✅", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some Permissions Denied ❌", Toast.LENGTH_LONG).show()
            }
        }
    }
}
