package app.duet.music.ui

actual fun currentHour(): Int = java.time.LocalTime.now().hour

actual fun epochSeconds(): Long = System.currentTimeMillis() / 1000
