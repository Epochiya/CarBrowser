package com.abdullah.carbrowser

import android.app.Application
import com.abdullah.carbrowser.browser.Library
import com.abdullah.carbrowser.browser.Prefs
import com.abdullah.carbrowser.privacy.FilterLists

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Library.init(this)
        FilterLists.init(this)
    }
}
