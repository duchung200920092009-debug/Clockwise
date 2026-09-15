package com.zenpulse.wear.presentation.navigation

/** Navigation routes. Kept as plain constants — the graph is small enough not to need more. */
object Destinations {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    /** Breathing takes the pattern id so a notification can open straight into the right one. */
    const val BREATHING = "breathing/{patternId}"
    const val ARG_PATTERN_ID = "patternId"

    fun breathing(patternId: String) = "breathing/$patternId"
}
