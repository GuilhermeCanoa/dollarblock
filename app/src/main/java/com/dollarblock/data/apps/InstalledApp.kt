package com.dollarblock.data.apps

import androidx.compose.ui.graphics.ImageBitmap

/** Um app lançável instalado no dispositivo, para seleção no DollarBlock. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)
