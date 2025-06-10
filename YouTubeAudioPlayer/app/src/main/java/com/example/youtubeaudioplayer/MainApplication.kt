package com.example.youtubeaudioplayer

import android.app.Application

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        YoutubeHelper.initialize(applicationContext)
    }
}
