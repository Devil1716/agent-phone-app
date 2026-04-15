package com.gemma.agentphone.accessibility

data class ScreenState(
    val visibleText: List<String>,
    val packageName: String,
    val activityName: String,
    val timestamp: Long
)

class ScreenStateReader(
    private val serviceProvider: () -> PhoneControlService? = { PhoneControlService.instance }
) {
    fun read(): ScreenState {
        val service = serviceProvider()
        return ScreenState(
            visibleText = service?.getVisibleText().orEmpty(),
            packageName = service?.currentPackageName().orEmpty(),
            activityName = service?.currentActivityName().orEmpty(),
            timestamp = System.currentTimeMillis()
        )
    }
}
