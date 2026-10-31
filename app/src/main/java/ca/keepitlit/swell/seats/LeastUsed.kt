package ca.keepitlit.swell.seats

/**
 * How much one installed app has been used, read from the book.
 *
 * An app can own several seats (a package with two launcher activities), so a [Use] sums every key
 * of one package: uninstalling removes them all at once.
 */
class Use(
    /** Launches from Swell, all time. */
    val opens: Int,
    /** The ranking score: recent launches count most. */
    val ema: Double,
    /** Days since the last launch; null when never opened or opened before the day was recorded. */
    val daysSince: Int?,
    /** Days the book has watched this app. */
    val watched: Int,
)

fun SeatBook.use(keys: Collection<String>): Use {
    var opens = 0
    var ema = 0.0
    var last = -1
    var watched = 0
    for (k in keys) {
        val a = app(k) ?: continue
        opens += a.opens
        ema += a.ema
        last = maxOf(last, a.lastDay)
        watched = maxOf(watched, a.tenure)
    }
    return Use(opens, ema, if (opens > 0 && last >= 0) (day - last).coerceAtLeast(0) else null, watched)
}

/**
 * Least used first, by the same score home ranks on, read from the bottom: never opened before
 * opened, then the lowest score. Among apps never opened, the one watched longest comes first: a
 * month unopened says more than a day.
 */
val LEAST_USED_FIRST: Comparator<Use> = compareBy<Use>({ it.opens > 0 }, { it.ema }, { -it.watched })
