package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.hud.CarPlayCallState
import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/** Read-only phone call state for input safety. Never starts a vehicle output or call-control backend. */
object CarPlayCallGuard {
    private var owner: Any? = null
    private var session: Any? = null
    private val calls = CarPlayCallState()

    @Synchronized fun start(owner: Any, session: Any) {
        if (this.owner === owner && this.session === session) return
        calls.clear()
        this.owner = owner
        this.session = session
    }

    @Synchronized fun accept(owner: Any, session: Any, frame: Iap2Frame) {
        if (this.owner === owner && this.session === session) calls.accept(frame)
    }

    @Synchronized fun end(owner: Any, session: Any? = null) {
        if (this.owner !== owner || (session != null && this.session !== session)) return
        calls.clear()
        this.owner = null
        this.session = null
    }

    @Synchronized fun inCall(): Boolean = calls.current() != null
}
