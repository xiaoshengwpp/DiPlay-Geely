package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.geely.lib.oneosapi.input.IInputListener
import com.geely.lib.oneosapi.input.IInputManager
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.network.GeelyCapability
import com.shilapi.xcertplay.orchestration.CarPlayController

/**
 * The upstream private Geely input protocol, gated by hardware/opt-in and live service capability.
 * A subscription belongs to one controller generation and one binding attempt. Stop invalidates it
 * synchronously; all Android/Binder work runs on the main handler, without holding the state lock.
 * No key is intercepted: Bluetooth AVRCP retains media ownership when that audio option is enabled.
 */
internal object GeelyWheelKeys {
    private const val TAG = "DiPlay-GeelyWheel"
    internal const val INPUT_PACKAGE = "com.geely.inputservice"
    internal const val INPUT_CLASS = "com.geely.inputservice.InputService"
    private const val INPUT_DESCRIPTOR = "com.geely.lib.oneosapi.input.IInputManager"
    internal const val RETRY_MILLIS = 3_000L
    private const val MAX_RETRIES = 3

    private val handler = Handler(Looper.getMainLooper())
    private val stateLock = Any()
    private var generation = 0L
    private var active: Session? = null

    private class Session(val context: Context, val controller: CarPlayController, val generation: Long) {
        var connection: InputConnection? = null
        var retry: Runnable? = null
        var retryCount = 0
    }

    /** Idempotent for the same controller; a replacement invalidates every old callback immediately. */
    fun start(context: Context, controller: CarPlayController) {
        if (!GeelyInputSettings.enabled(context)) {
            stop()
            return
        }
        val app = context.applicationContext
        synchronized(stateLock) {
            if (active?.controller === controller) return
            val previous = active
            previous?.retry?.let(handler::removeCallbacks)
            previous?.retry = null
            val next = Session(app, controller, ++generation)
            active = next
            handler.post {
                previous?.connection?.let(::release)
                bind(next)
            }
        }
    }

    /** Invalidates queued events/retries before scheduling unregister/unbind. */
    fun stop() {
        synchronized(stateLock) {
            generation += 1
            val previous = active
            active = null
            previous?.retry?.let(handler::removeCallbacks)
            previous?.retry = null
            handler.post { previous?.connection?.let(::release) }
        }
    }

    private fun current(session: Session): Boolean = synchronized(stateLock) {
        active === session && generation == session.generation
    }

    private fun current(connection: InputConnection): Boolean = synchronized(stateLock) {
        active === connection.session && generation == connection.session.generation &&
            connection.session.connection === connection
    }

    private fun bind(session: Session) {
        if (!current(session)) return
        val intent = Intent().setComponent(ComponentName(INPUT_PACKAGE, INPUT_CLASS))
        if (!serviceUsable(session.context, intent)) {
            Log.i(TAG, "Geely input service unavailable or inaccessible")
            return
        }
        val connection = InputConnection(session)
        synchronized(stateLock) {
            if (!current(session) || session.connection != null) return
            session.connection = connection
        }
        val result = runCatching {
            session.context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.onFailure { Log.w(TAG, "Geely input bind failed", it) }
        connection.bound = result.getOrDefault(false)
        if (!current(connection)) {
            release(connection)
        } else if (!connection.bound) {
            failed(connection, retry = result.exceptionOrNull() !is SecurityException)
        }
    }

    /** Resolve only this explicit component; do not launch arbitrary services with a matching action. */
    internal fun serviceUsable(context: Context, intent: Intent): Boolean = runCatching {
        val info = context.packageManager.resolveService(intent, 0)?.serviceInfo
        val permission = info?.permission
        GeelyCapability.inputServiceUsable(
            resolved = info != null && info.packageName == INPUT_PACKAGE && info.name == INPUT_CLASS,
            enabled = info?.enabled == true,
            applicationEnabled = info?.applicationInfo?.enabled == true,
            exported = info?.exported == true,
            permissionGranted = permission.isNullOrBlank() ||
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED,
        )
    }.getOrDefault(false)

    private class InputConnection(val session: Session) : ServiceConnection {
        // Lifecycle fields are main-handler owned; subscribed also has command-thread readers.
        var bound = false
        var manager: IInputManager? = null
        var listener: IInputListener? = null
        // Also checked by the command-executor ownership predicate after main-thread dispatch.
        @Volatile var subscribed = false
        var codes = intArrayOf()

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            handler.post { connected(this, service) }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            handler.post { failed(this, retry = true) }
        }
        override fun onBindingDied(name: ComponentName?) {
            handler.post { failed(this, retry = true) }
        }
        override fun onNullBinding(name: ComponentName?) {
            handler.post { failed(this, retry = false) }
        }
    }

    private fun connected(connection: InputConnection, service: IBinder?) {
        if (!current(connection)) {
            release(connection)
            return
        }
        if (connection.manager != null) return // Duplicate callback must not register twice.
        val compatible = runCatching {
            service != null && service.isBinderAlive && service.interfaceDescriptor == INPUT_DESCRIPTOR
        }.getOrDefault(false)
        if (!compatible) {
            Log.w(TAG, "Geely input Binder interface does not match; leaving vendor input untouched")
            failed(connection, retry = false)
            return
        }
        val input = IInputManager.Stub.asInterface(service)
        val listener = object : IInputListener.Stub() {
            override fun onKeyCodeEvent(keyCode: Int, event: Int, softKeyFunction: Int) = Unit
            override fun onShortClick(keyCode: Int, softKeyFunction: Int) {
                queueDispatch(connection, keyCode, longPress = false)
            }
            override fun onHoldingPressStarted(keyCode: Int, softKeyFunction: Int) = Unit
            override fun onHoldingPressStopped(keyCode: Int, softKeyFunction: Int) = Unit
            override fun onLongPressTriggered(keyCode: Int, softKeyFunction: Int) {
                queueDispatch(connection, keyCode, longPress = true)
            }
            override fun onDoubleClick(keyCode: Int, softKeyFunction: Int) = Unit
        }
        connection.manager = input
        connection.listener = listener
        val codes = GeelyWheelKeyCodes.registeredCodes(
            AirPlayPersistence.loadCarBluetoothAudio(connection.session.context),
        )
        connection.codes = codes.copyOf()
        val registered = runCatching {
            input.registerListener(listener, connection.session.context.packageName, codes)
        }.onFailure { Log.w(TAG, "Geely input registration failed", it) }
        if (registered.isFailure) {
            failed(connection, retry = registered.exceptionOrNull() !is SecurityException)
        } else if (!current(connection)) {
            release(connection)
        } else {
            connection.subscribed = true
            synchronized(stateLock) { connection.session.retryCount = 0 }
            Log.i(TAG, "Geely input registered codes=${codes.joinToString()}")
        }
    }

    private fun failed(connection: InputConnection, retry: Boolean) {
        val wasCurrent = current(connection)
        release(connection)
        if (wasCurrent && retry) scheduleRetry(connection.session)
    }

    private fun release(connection: InputConnection) {
        synchronized(stateLock) {
            if (connection.session.connection === connection) connection.session.connection = null
        }
        connection.subscribed = false
        val listener = connection.listener
        val manager = connection.manager
        connection.listener = null
        connection.manager = null
        if (listener != null && manager != null) runCatching {
            manager.unregisterListener(listener, connection.session.context.packageName)
        }.onFailure { Log.w(TAG, "Geely input unregister failed", it) }
        if (connection.bound) {
            connection.bound = false
            runCatching { connection.session.context.unbindService(connection) }
                .onFailure { Log.w(TAG, "Geely input unbind failed", it) }
        }
    }

    private fun scheduleRetry(session: Session) {
        synchronized(stateLock) {
            if (!current(session) || session.retry != null) return
            // Failed capabilities are not polled forever. Transient failures get 3 s / 6 s / 12 s;
            // a successful registration restores the budget for a later service restart.
            if (session.retryCount >= MAX_RETRIES) {
                Log.w(TAG, "Geely input retry budget exhausted until the next CarPlay connection")
                return
            }
            val delay = RETRY_MILLIS * (1L shl session.retryCount++)
            val retry = Runnable {
                synchronized(stateLock) { session.retry = null }
                if (current(session)) bind(session)
            }
            session.retry = retry
            handler.postDelayed(retry, delay)
        }
    }

    /** Capture phone ownership on the Binder callback thread, before waiting for the main handler. */
    private fun queueDispatch(connection: InputConnection, keyCode: Int, longPress: Boolean) {
        if (!current(connection)) return
        val token = connection.session.controller.activeAirPlaySessionToken() ?: return
        handler.post { dispatch(connection, keyCode, longPress, token) }
    }

    private fun dispatch(connection: InputConnection, keyCode: Int, longPress: Boolean, token: Any) {
        if (!current(connection) || !connection.subscribed || keyCode !in connection.codes) return
        val stillCurrent = {
            current(connection) && connection.subscribed &&
                connection.session.controller.activeAirPlaySessionToken() === token
        }
        if (!stillCurrent()) return
        if (longPress) {
            if (keyCode == GeelyWheelKeyCodes.KEY_VOICE_ASSIST) {
                CarPlayMediaKeys.onExternalSiriKey(connection.session.controller, stillCurrent)
            }
            return
        }
        // Recheck the saved choice as well as the registration list: enabling AVRCP while connected
        // must not let a previously registered media code double-fire.
        if (AirPlayPersistence.loadCarBluetoothAudio(connection.session.context)) return
        val index = when (keyCode) {
            GeelyWheelKeyCodes.KEY_PLAY_PAUSE -> CarPlayMediaButton.PLAY_PAUSE
            GeelyWheelKeyCodes.KEY_NEXT -> CarPlayMediaButton.NEXT
            GeelyWheelKeyCodes.KEY_PREVIOUS -> CarPlayMediaButton.PREVIOUS
            else -> return
        }
        CarPlayMediaKeys.onExternalMediaKey(index, connection.session.controller, stillCurrent)
    }
}
