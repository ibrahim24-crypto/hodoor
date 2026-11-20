package com.himo.hodoor

import android.content.Context
import com.himo.hodoor.data.repository.DataRepository
import com.himo.hodoor.ui.settings.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val repository = DataRepository(context.applicationContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            repository.ensureDefaultCauses(LocaleHelper.wrap(context.applicationContext))
        }
    }
}
