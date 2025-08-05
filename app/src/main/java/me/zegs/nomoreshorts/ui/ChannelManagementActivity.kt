package me.zegs.nomoreshorts.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.zegs.nomoreshorts.R
import me.zegs.nomoreshorts.settings.SettingsManager

class ChannelManagementActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var editTextChannel: EditText
    private lateinit var buttonAdd: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_management)

        settingsManager = SettingsManager(this)

        setupViews()
        setupRecyclerView()
        setupAddButton()

        supportActionBar?.title = getString(R.string.channel_management_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recyclerViewChannels)
        editTextChannel = findViewById(R.id.editTextChannel)
        buttonAdd = findViewById(R.id.buttonAddChannel)

        editTextChannel.hint = getString(R.string.channel_name_hint)
    }

    private fun setupRecyclerView() {
        channelAdapter = ChannelAdapter(
            channels = settingsManager.allowedChannels.toMutableList(),
            onDeleteChannel = { channel ->
                removeChannel(channel)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = channelAdapter
    }

    private fun setupAddButton() {
        buttonAdd.text = getString(R.string.add_channel)
        buttonAdd.setOnClickListener {
            addChannel()
        }
    }

    private fun addChannel() {
        val channelName = editTextChannel.text.toString().trim()

        if (channelName.isEmpty()) {
            Toast.makeText(this, getString(R.string.channel_name_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val currentChannels = settingsManager.allowedChannels.toMutableList()

        if (currentChannels.contains(channelName)) {
            Toast.makeText(this, "Channel already in allowlist", Toast.LENGTH_SHORT).show()
            return
        }

        currentChannels.add(channelName)
        settingsManager.allowedChannels = currentChannels

        channelAdapter.addChannel(channelName)
        editTextChannel.text.clear()

        Toast.makeText(this, "Channel added to allowlist", Toast.LENGTH_SHORT).show()
    }

    private fun removeChannel(channel: String) {
        val currentChannels = settingsManager.allowedChannels.toMutableList()
        currentChannels.remove(channel)
        settingsManager.allowedChannels = currentChannels

        channelAdapter.removeChannel(channel)

        Toast.makeText(this, "Channel removed from allowlist", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
