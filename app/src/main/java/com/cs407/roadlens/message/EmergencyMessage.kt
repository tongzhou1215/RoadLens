package com.cs407.roadlens.message

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast

object EmergencyMessage {

    fun sendSms(context: Context, phone: String, message: String) {
        try {
            val sms = SmsManager.getDefault()
            sms.sendTextMessage(phone, null, message, null, null)

            Toast.makeText(context, "Emergency message sent!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MESSAGE_FAIL", e.message.toString());
            Toast.makeText(context, "Failed to send SMS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}