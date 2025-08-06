package me.zegs.nomoreshorts.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import me.zegs.nomoreshorts.R
import me.zegs.nomoreshorts.settings.SettingsManager
import me.zegs.nomoreshorts.utils.ValidationUtils

class ChannelManagementActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var editTextChannel: EditText
    private lateinit var buttonAdd: Button
    private lateinit var textInputLayout: TextInputLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_management)

        settingsManager = SettingsManager(this)

        setupViews()
        setupRecyclerView()
        setupAddButton()
        setupInputValidation()

        supportActionBar?.title = getString(R.string.channel_management_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recyclerViewChannels)
        editTextChannel = findViewById(R.id.editTextChannel)
        buttonAdd = findViewById(R.id.buttonAddChannel)
        textInputLayout = findViewById(R.id.textInputLayoutChannel)

        // Initially disable the add button until valid input
        buttonAdd.isEnabled = false
    }

    private fun setupRecyclerView() {
        try {
            channelAdapter = ChannelAdapter(
                channels = settingsManager.allowedChannels.toMutableList(),
                onDeleteChannel = { channel ->
                    removeChannelWithConfirmation(channel)
                }
            )

            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = channelAdapter
        } catch (e: Exception) {
            showError("Failed to load channels: ${e.message}")
        }
    }

    private fun setupAddButton() {
        buttonAdd.text = getString(R.string.add_channel)
        buttonAdd.setOnClickListener {
            addChannel()
        }
    }

    private fun setupInputValidation() {
        editTextChannel.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                validateChannelNameInput(s?.toString() ?: "")
            }
        })
    }

    private fun validateChannelNameInput(channelName: String): Boolean {
        val sanitizedInput = ValidationUtils.sanitizeInput(channelName)
        val result = ValidationUtils.validateChannelName(sanitizedInput, settingsManager.allowedChannels)

        // Clear previous errors
        textInputLayout.error = null
        textInputLayout.helperText = null

        when {
            result.isValid -> {
                textInputLayout.error = null
                if (result.warningMessage.isNotEmpty()) {
                    textInputLayout.helperText = "⚠ ${result.warningMessage}"
                } else {
                    textInputLayout.helperText = "Enter YouTube channel handle"
                }
                buttonAdd.isEnabled = true
                return true
            }

            sanitizedInput.isEmpty() -> {
                buttonAdd.isEnabled = false
                textInputLayout.helperText = "Enter YouTube channel handle"
                return false
            }

            else -> {
                textInputLayout.error = result.errorMessage
                buttonAdd.isEnabled = false
                return false
            }
        }
    }

    private fun addChannel() {

        val channelName = ValidationUtils.sanitizeInput(editTextChannel.text.toString())
        val result = ValidationUtils.validateChannelName(channelName, settingsManager.allowedChannels)
        if (!result.isValid) {
            showError(result.errorMessage)
            return
        }

        buttonAdd.isEnabled = false

        try {
            val currentChannels = settingsManager.allowedChannels.toMutableList()
            currentChannels.add(channelName)
            settingsManager.allowedChannels = currentChannels

            channelAdapter.addChannel(channelName)
            editTextChannel.text.clear()

            var message = "Channel '$channelName' added successfully"
            if (result.warningMessage.isNotEmpty()) {
                message += "\n${result.warningMessage}"
            }
            showSuccess(message)

        } catch (e: Exception) {
            showError("Failed to add channel: ${e.message}")
        }
    }

    private fun removeChannelWithConfirmation(channel: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Remove Channel")
            .setMessage("Are you sure you want to remove '$channel' from your allowed channels list?")
            .setPositiveButton("Remove") { _, _ ->
                removeChannel(channel)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeChannel(channel: String) {
        try {
            val currentChannels = settingsManager.allowedChannels.toMutableList()
            if (currentChannels.remove(channel)) {
                settingsManager.allowedChannels = currentChannels
                channelAdapter.removeChannel(channel)
                showSuccess("Channel '$channel' removed successfully")
            } else {
                showError("Channel not found in list")
            }

            // Re-validate current input in case removing a channel makes it valid again
            validateChannelNameInput(editTextChannel.text.toString())

        } catch (e: Exception) {
            showError("Failed to remove channel: ${e.message}")
        }
    }

    private fun showError(message: String) {
        try {
            val rootView = findViewById<View>(android.R.id.content)
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(getColor(android.R.color.holo_red_light))
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showSuccess(message: String) {
        try {
            val rootView = findViewById<View>(android.R.id.content)
            Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getColor(android.R.color.holo_green_light))
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
