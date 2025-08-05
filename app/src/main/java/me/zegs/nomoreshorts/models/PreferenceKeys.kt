package me.zegs.nomoreshorts.models

object PreferenceKeys {
    const val APP_ENABLED = "app_enabled"
    const val BLOCK_FEED = "block_shorts_feed"
    const val BLOCKING_MODE = "blocking_mode"
    const val LIMIT_TYPE = "limit_type"
    const val SWIPE_LIMIT_COUNT = "swipe_limit_count"
    const val TIME_LIMIT_MINUTES = "time_limit_minutes"
    const val RESET_PERIOD_TYPE = "reset_period_type"
    const val RESET_PERIOD_MINUTES = "reset_period_minutes"
    const val SCHEDULE_ENABLED = "schedule_enabled"
    const val SCHEDULE_START_TIME = "schedule_start_time"
    const val SCHEDULE_END_TIME = "schedule_end_time"
    const val SCHEDULE_DAYS = "schedule_days"
    const val ALLOWLIST_ENABLED = "allowlist_enabled"
    const val ALLOWED_CHANNELS = "allowed_channels" // JSON array of channel names
}

enum class BlockingMode {
    ONLY_SWIPING,
    ALL_SHORTS
}

enum class LimitType {
    SWIPE_COUNT,
    TIME_LIMIT
}

enum class ResetPeriodType {
    AFTER_SESSION_END,
    PER_DAY
}
