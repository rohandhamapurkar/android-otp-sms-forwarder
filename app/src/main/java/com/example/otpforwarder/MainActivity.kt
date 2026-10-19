package com.example.otpforwarder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 100

    private lateinit var statusReceiveSms: TextView
    private lateinit var statusReadSms: TextView
    private lateinit var statusSendSms: TextView
    private lateinit var statusNotifications: TextView
    
    private lateinit var switchMaster: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var recyclerConfigs: RecyclerView
    private lateinit var fabAddConfig: FloatingActionButton
    
    private val viewModel: ConfigViewModel by viewModels()
    private lateinit var adapter: ConfigAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusReceiveSms = findViewById(R.id.statusReceiveSms)
        statusReadSms = findViewById(R.id.statusReadSms)
        statusSendSms = findViewById(R.id.statusSendSms)
        statusNotifications = findViewById(R.id.statusNotifications)
        
        switchMaster = findViewById(R.id.switchMaster)
        recyclerConfigs = findViewById(R.id.recyclerConfigs)
        fabAddConfig = findViewById(R.id.fabAddConfig)

        val sharedPrefs = getSharedPreferences("forwarder_prefs", Context.MODE_PRIVATE)
        switchMaster.isChecked = sharedPrefs.getBoolean("master_enabled", true)
        
        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("master_enabled", isChecked).apply()
        }

        adapter = ConfigAdapter(
            onEdit = { config -> showAddEditDialog(config) },
            onDelete = { config -> showDeleteConfirmation(config) },
            onToggle = { config, isEnabled -> 
                viewModel.update(config.copy(isEnabled = isEnabled))
            }
        )
        
        recyclerConfigs.layoutManager = LinearLayoutManager(this)
        recyclerConfigs.adapter = adapter
        
        viewModel.allConfigs.observe(this) { configs ->
            adapter.submitList(configs)
        }

        fabAddConfig.setOnClickListener {
            showAddEditDialog(null)
        }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            updatePermissionStatus()
        }

        updatePermissionStatus()
        requestSmsPermissions()
    }

    private fun showAddEditDialog(config: ForwardConfig?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_config, null)
        val editForwardTo = dialogView.findViewById<EditText>(R.id.editForwardTo)
        val editPrefix = dialogView.findViewById<EditText>(R.id.editPrefix)
        val editFilterKeyword = dialogView.findViewById<EditText>(R.id.editFilterKeyword)
        val btnAddKeyword = dialogView.findViewById<Button>(R.id.btnAddKeyword)
        val chipGroupKeywords = dialogView.findViewById<ChipGroup>(R.id.chipGroupKeywords)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val layoutForwardTo = dialogView.findViewById<TextInputLayout>(R.id.layoutForwardTo)
        val layoutPrefix = dialogView.findViewById<TextInputLayout>(R.id.layoutPrefix)
        val textKeywordError = dialogView.findViewById<TextView>(R.id.textKeywordError)

        val hintColor = MaterialColors.getColor(
            textKeywordError, com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        val errorColor = MaterialColors.getColor(
            textKeywordError, com.google.android.material.R.attr.colorError
        )
        textKeywordError.setTextColor(hintColor)

        val keywords = mutableListOf<String>()

        fun refreshKeywordHint(showAsError: Boolean = false) {
            textKeywordError.text = if (keywords.isEmpty()) {
                "Required. Add at least one keyword"
            } else {
                "${keywords.size} keyword(s) added"
            }
            textKeywordError.setTextColor(
                if (showAsError && keywords.isEmpty()) errorColor else hintColor
            )
        }

        if (config != null) {
            dialogTitle.text = "Edit Configuration"
            editForwardTo.setText(config.forwardTo)
            editPrefix.setText(config.messagePrefix)
            keywords.addAll(config.filterKeywords)
            // Iterate a copy: the chip close listener mutates `keywords`.
            keywords.toList().forEach {
                addKeywordChip(it, chipGroupKeywords, keywords) { refreshKeywordHint() }
            }
        }
        refreshKeywordHint()

        // Clear a field's error as soon as the user starts correcting it.
        editForwardTo.doOnTextChanged { _, _, _, _ -> layoutForwardTo.error = null }
        editPrefix.doOnTextChanged { _, _, _, _ -> layoutPrefix.error = null }

        btnAddKeyword.setOnClickListener {
            val text = editFilterKeyword.text.toString().trim()
            when {
                text.isEmpty() ->
                    Toast.makeText(this, "Enter a keyword first", Toast.LENGTH_SHORT).show()

                keywords.any { it.equals(text, ignoreCase = true) } ->
                    Toast.makeText(this, "\"$text\" is already added", Toast.LENGTH_SHORT).show()

                else -> {
                    keywords.add(text)
                    addKeywordChip(text, chipGroupKeywords, keywords) { refreshKeywordHint() }
                    editFilterKeyword.text.clear()
                    refreshKeywordHint()
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        // Wiring the click listener after show() keeps the dialog open when validation fails,
        // instead of dismissing and discarding everything the user typed.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val forwardTo = editForwardTo.text.toString().trim()
                val prefix = editPrefix.text.toString().trim()

                val result = ConfigValidator.validate(forwardTo, prefix, keywords)
                layoutForwardTo.error = result.forwardToError
                layoutPrefix.error = result.prefixError
                refreshKeywordHint(showAsError = true)

                if (!result.isValid) return@setOnClickListener

                val newConfig = if (config == null) {
                    ForwardConfig(forwardTo = forwardTo, filterKeywords = keywords.toList(), messagePrefix = prefix)
                } else {
                    config.copy(forwardTo = forwardTo, filterKeywords = keywords.toList(), messagePrefix = prefix)
                }

                if (config == null) viewModel.insert(newConfig) else viewModel.update(newConfig)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun addKeywordChip(
        keyword: String,
        chipGroup: ChipGroup,
        keywords: MutableList<String>,
        onChanged: () -> Unit
    ) {
        val chip = Chip(this)
        chip.text = keyword
        chip.isCloseIconVisible = true
        chip.setOnCloseIconClickListener {
            chipGroup.removeView(chip)
            keywords.remove(keyword)
            onChanged()
        }
        chipGroup.addView(chip)
    }

    private fun showDeleteConfirmation(config: ForwardConfig) {
        AlertDialog.Builder(this)
            .setTitle("Delete Configuration")
            .setMessage("Are you sure you want to delete this forwarding rule?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.delete(config)
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
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
