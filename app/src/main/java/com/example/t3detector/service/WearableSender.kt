package com.example.t3detector.service

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WearableSender(context: Context) {

    // These clients handle the Bluetooth/Wifi connection to the watch
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    companion object {
        // This path MUST match exactly what is in the WatchListenerService
        const val ALARM_PATH = "/trigger_t3_alarm"
    }

    suspend fun sendAlarmToWatch() {
        withContext(Dispatchers.IO) {
            try {
                // 1. Find all connected devices (nodes)
                val nodes = Tasks.await(nodeClient.connectedNodes)

                // 2. Send the "Trigger" message to each connected watch
                nodes.forEach { node ->
                    val sendMessageTask = messageClient.sendMessage(
                        node.id,
                        ALARM_PATH,
                        ByteArray(0) // Empty payload, the message itself is the trigger
                    )

                    Tasks.await(sendMessageTask)
                    Log.d("WearableSender", "Sent alarm to watch: ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e("WearableSender", "Failed to send alarm to watch", e)
            }
        }
    }
}