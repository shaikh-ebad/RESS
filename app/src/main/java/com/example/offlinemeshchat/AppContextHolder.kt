package com.example.offlinemeshchat

import android.content.Context

object AppContextHolder {

    lateinit var context: Context
        private set

    fun initialize(context: Context) {

        this.context =
            context.applicationContext
    }
}