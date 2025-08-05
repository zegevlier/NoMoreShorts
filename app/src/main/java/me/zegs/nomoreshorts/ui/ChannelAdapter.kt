package me.zegs.nomoreshorts.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.zegs.nomoreshorts.R

class ChannelAdapter(
    private val channels: MutableList<String>,
    private val onDeleteChannel: (String) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewChannelName: TextView = itemView.findViewById(R.id.textViewChannelName)
        val buttonDelete: ImageButton = itemView.findViewById(R.id.buttonDeleteChannel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]
        holder.textViewChannelName.text = channel
        holder.buttonDelete.setOnClickListener {
            onDeleteChannel(channel)
        }
    }

    override fun getItemCount(): Int = channels.size

    fun addChannel(channel: String) {
        channels.add(channel)
        notifyItemInserted(channels.size - 1)
    }

    fun removeChannel(channel: String) {
        val position = channels.indexOf(channel)
        if (position != -1) {
            channels.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}
