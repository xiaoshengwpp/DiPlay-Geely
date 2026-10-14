package com.shilapi.xcertplay

/** The screen before CarPlay video follows the selected DiPlay app appearance. */
internal data class WaitingScreenColors(val background: Int, val text: Int, val secondary: Int) {
    companion object {
        fun of(night: Boolean): WaitingScreenColors = DiPlayPalette.of(night).let {
            WaitingScreenColors(it.background, it.primaryText, it.secondaryText)
        }
    }
}
