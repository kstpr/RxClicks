package com.example.rzr.rxclicks

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MotionEvent
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Timed
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit

enum class Intent {
    UP,
    TIMER_STARTED,
    TIMER_ENDED
}

enum class Event {
    UP,
    CLICK,
    LONG_CLICK,
    IGNORED
}

const val TIME_INTERVAL = 300L

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val intents = RxView.touches(btn)
                .toFlowable(BackpressureStrategy.BUFFER)
                .map { it.action }
                .filter { it == MotionEvent.ACTION_DOWN || it == MotionEvent.ACTION_UP }
                .switchMap {
                    if (it == MotionEvent.ACTION_UP) {
                        Flowable.just(Intent.UP)
                    } else {
                        Flowable.timer(TIME_INTERVAL, TimeUnit.MILLISECONDS)
                                .map { Intent.TIMER_ENDED }
                                .startWith(Intent.TIMER_STARTED)
                    }
                }
                .timestamp()
                .share()

        val terminalIntents = intents.filter { it.value() == Intent.UP || it.value() == Intent.TIMER_ENDED }
        val initialIntents = intents.filter { it.value() == Intent.TIMER_STARTED }

        Flowable.combineLatest(terminalIntents, initialIntents,
                BiFunction<Timed<Intent>, Timed<Intent>, Event> { terminal, initial ->
                    val terminalValue = terminal.value()
                    val terminalTime = terminal.time(TimeUnit.MILLISECONDS)
                    val initialTime = initial.time(TimeUnit.MILLISECONDS)

                    if (terminalValue == Intent.UP) {
                        val timeDelta = terminalTime - initialTime
                        when {
                            timeDelta < 0 -> Event.IGNORED
                            timeDelta < TIME_INTERVAL -> Event.CLICK
                            else -> Event.UP
                        }
                    } else {
                        Event.LONG_CLICK
                    }
                })
                .filter { it != Event.IGNORED }
                .subscribe { Log.i("EVENT", it.toString()) }
    }
}
