package com.shilapi.xcertplay

/** Dialog-only state. Merely opening or dismissing it never creates a preference. */
internal class NavigationChannelSelection(initial: Int?) {
    private val original = initial?.takeIf { it in 0..20 }
    var value: Int? = original
        private set
    val index: Int get() = value?.plus(1) ?: 0
    val changed: Boolean get() = value != original

    /** Index zero retains legacy routing and must never preview its private stream ID. */
    fun select(index: Int): Int? {
        require(index in 0..21)
        value = if (index == 0) null else index - 1
        return value
    }
}
