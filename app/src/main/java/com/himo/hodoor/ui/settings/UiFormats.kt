package com.himo.hodoor.ui.settings

import java.text.SimpleDateFormat
import java.util.Locale

/** dd/MM/yyyy with Latin digits, regardless of the app's display locale. */
fun civilDateFormat(): SimpleDateFormat =
    SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { isLenient = false }
