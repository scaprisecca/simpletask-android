package nl.mpcjanssen.simpletask.notifications

enum class PinnedTaskDeliveryState {
    SCHEDULED,
    POSTED;

    companion object {
        fun fromPersisted(value: String?): PinnedTaskDeliveryState {
            return values().firstOrNull { it.name == value } ?: POSTED
        }
    }
}

fun PinnedTaskRecord.deliveryState(): PinnedTaskDeliveryState {
    return PinnedTaskDeliveryState.fromPersisted(deliveryState)
}

fun PinnedTaskRecord.isScheduledDelivery(): Boolean = deliveryState() == PinnedTaskDeliveryState.SCHEDULED

fun PinnedTaskRecord.isPostedDelivery(): Boolean = deliveryState() == PinnedTaskDeliveryState.POSTED

fun PinnedTaskRecord.isScheduledTrigger(): Boolean {
    return triggerMode == PinnedTaskRecord.TRIGGER_MODE_SCHEDULED && triggerAtMillis != null
}

fun PinnedTaskRecord.isScheduledForFuture(nowMillis: Long = System.currentTimeMillis()): Boolean {
    val scheduledTime = triggerAtMillis ?: return false
    return isScheduledDelivery() && isScheduledTrigger() && scheduledTime > nowMillis
}

fun PinnedTaskRecord.shouldPostNow(nowMillis: Long = System.currentTimeMillis()): Boolean {
    return !isScheduledForFuture(nowMillis)
}

fun PinnedTaskRecord.asImmediatePostedRecord(lastKnownText: String = taskText): PinnedTaskRecord {
    return copy(
        taskText = lastKnownText,
        lastKnownText = lastKnownText,
        triggerAtMillis = null,
        triggerMode = PinnedTaskRecord.TRIGGER_MODE_IMMEDIATE,
        deliveryState = PinnedTaskDeliveryState.POSTED.name
    )
}

fun PinnedTaskRecord.asScheduledRecord(triggerAtMillis: Long, lastKnownText: String = taskText): PinnedTaskRecord {
    return copy(
        taskText = lastKnownText,
        lastKnownText = lastKnownText,
        triggerAtMillis = triggerAtMillis,
        triggerMode = PinnedTaskRecord.TRIGGER_MODE_SCHEDULED,
        deliveryState = PinnedTaskDeliveryState.SCHEDULED.name
    )
}

fun PinnedTaskRecord.asPostedRecord(lastKnownText: String = taskText): PinnedTaskRecord {
    return copy(
        taskText = lastKnownText,
        lastKnownText = lastKnownText,
        deliveryState = PinnedTaskDeliveryState.POSTED.name
    )
}
