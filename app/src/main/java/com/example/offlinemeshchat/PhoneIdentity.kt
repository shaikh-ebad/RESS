package com.example.offlinemeshchat

import android.content.Context

object PhoneIdentity {

    private const val PREFS_NAME = "offline_mesh_identity"
    private const val PHONE_ID_KEY = "phone_id"

    fun getPhoneId(context: Context): String {

        val preferences = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        var phoneId = preferences.getString(PHONE_ID_KEY, null)

        if (phoneId == null) {

            phoneId = generatePhoneId()

            preferences.edit()
                .putString(PHONE_ID_KEY, phoneId)
                .apply()
        }

        return phoneId
    }

    private fun generatePhoneId(): String {

        val number = (100..999).random()

        return "PHONE_$number"
    }
}