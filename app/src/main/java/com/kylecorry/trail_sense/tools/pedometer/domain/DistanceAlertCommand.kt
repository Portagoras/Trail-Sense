package com.kylecorry.trail_sense.tools.pedometer.domain

import com.kylecorry.trail_sense.settings.infrastructure.IPedometerPreferences
import com.kylecorry.trail_sense.shared.commands.Command
import com.kylecorry.trail_sense.tools.pedometer.infrastructure.IDistanceAlertSender
import com.kylecorry.trail_sense.tools.pedometer.infrastructure.IStepCounter

class DistanceAlertCommand(
    private val prefs: IPedometerPreferences,
    private val counter: IStepCounter,
    private val pedometerService: IPedometerService,
    private val alertSender: IDistanceAlertSender
) : Command {
    override fun execute() {
        val alertDistance = prefs.alertDistance ?: return
        val distance = pedometerService.getDistance(counter.steps, prefs.strideLength)
        if (distance.meters().distance >= alertDistance.meters().distance) {
            alertSender.send()
            prefs.alertDistance = null
        }
    }
}