package com.dollarblock

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Ponto de entrada da aplicação. Habilita a injeção de dependências via Hilt
 * em todo o app DollarBlock.
 */
@HiltAndroidApp
class DollarBlockApp : Application()
