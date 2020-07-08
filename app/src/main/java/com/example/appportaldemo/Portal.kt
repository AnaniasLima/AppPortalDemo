package com.example.appportaldemo

import android.app.Application
import android.util.Log
import timber.log.Timber

class Portal : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            println("ANANA Timber.plant ")
            Timber.plant(Timber.DebugTree())
        }

        Timber.e("ANANA em onCreate de Application") // e só  para aparecer no logcat do boot
    }
}