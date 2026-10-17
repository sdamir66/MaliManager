package com.sdamir66.dadban.data

import com.sdamir66.dadban.util.Jalali
import java.util.Locale

object ProfitEngine {
    suspend fun recalculateAll(db: AppDb) {
        db.tx().deleteAllAuto()
        for (account in db.accounts().allNow()) {
            val periods = db.profitPeriod().byAccountNow(account.id)
            if (periods.isEmpty()) continue
            recalculateForAccount(db, account.id, periods)
        }
    }

    suspend fun recalculateForAccount(db: AppDb, accountId: Long, periods: List<ProfitPeriod>) {
        if (periods.isEmpty()) return

        val base = db.tx().byAccountNow(accountId)
            .filter { !it.isAutoProfit }
            .sortedBy { it.dateMillis }
            .toMutableList()

        val today = Jalali.nowJalali()
        val todayMillis = System.currentTimeMillis()

        db.tx().deleteAutoByPrefix(accountId.toString())

        val sortedByStart = periods.sortedWith(
            compareBy({ it.startYear }, { it.startMonth }, { it.startDay })
        )
        val firstPeriod = sortedByStart.first()
        val overallStartMillis = toMillis(
            firstPeriod.startYear, firstPeriod.startMonth, firstPeriod.startDay
        )

        val out = mutableListOf<Transaction>()

        var cy = firstPeriod.startYear
        var cm = firstPeriod.startMonth

        while (true) {
            if (cy > today[0] || (cy == today[0] && cm > today[1])) break

            val daysInMonth = Jalali.daysInMonth(cy, cm)

            val activePeriod = periods.find { period ->
                val payoutDayForPeriod = if (period.payoutDay <= 0) daysInMonth
                                         else period.payoutDay.coerceIn(1, daysInMonth)
                val payoutMillis = toMillis(cy, cm, payoutDayForPeriod)

                val prevY: Int
                val prevM: Int
                if (cm == 1) { prevY = cy - 1; prevM = 12 } else { prevY = cy; prevM = cm - 1 }
                val prevMonthDays = Jalali.daysInMonth(prevY, prevM)
                val startDayInPrevMonth = payoutDayForPeriod.coerceAtMost(prevMonthDays)
                val calcStartMillis = toMillis(prevY, prevM, startDayInPrevMonth)
                val calcEndMillis = payoutMillis - 86400000L

                val pStart = toMillis(period.startYear, period.startMonth, period.startDay)
                val pEnd = if (period.endYear != null && period.endMonth != null && period.endDay != null) {
                    toMillis(period.endYear, period.endMonth, period.endDay)
                } else {
                    Long.MAX_VALUE
                }

                calcStartMillis <= pEnd && pStart <= calcEndMillis
            }

            if (activePeriod != null) {
                val payoutDayActual = if (activePeriod.payoutDay <= 0) daysInMonth
                                      else activePeriod.payoutDay.coerceIn(1, daysInMonth)
                val payoutMillis = toMillis(cy, cm, payoutDayActual)

                if (payoutMillis > todayMillis) {
                    cm++; if (cm > 12) { cm = 1; cy++ }
                    continue
                }

                val prevY: Int
                val prevM: Int
                if (cm == 1) { prevY = cy - 1; prevM = 12 } else { prevY = cy; prevM = cm - 1 }
                val prevMonthDays = Jalali.daysInMonth(prevY, prevM)
                val startDayInPrevMonth = payoutDayActual.coerceAtMost(prevMonthDays)
                val calcStartMillis = toMillis(prevY, prevM, startDayInPrevMonth)
                val calcEndMillis = payoutMillis - 86400000L

                val pStartMillis = toMillis(activePeriod.startYear, activePeriod.startMonth, activePeriod.startDay)
                val pEndMillis = if (activePeriod.endYear != null && activePeriod.endMonth != null && activePeriod.endDay != null) {
                    toMillis(activePeriod.endYear, activePeriod.endMonth, activePeriod.endDay)
                } else {
                    Long.MAX_VALUE
                }

                val effectiveStart = maxOf(calcStartMillis, pStartMillis, overallStartMillis)
                val effectiveEnd = minOf(calcEndMillis, pEndMillis)

                if (effectiveStart > effectiveEnd) {
                    cm++; if (cm > 12) { cm = 1; cy++ }
                    continue
                }

                var totalProfit = 0.0
                var currentMillis = effectiveStart

                while (currentMillis <= effectiveEnd) {
                    val dayStartMillis = currentMillis
                    val dayEndMillis = currentMillis + 86399000L

                    val minBalance = calculateMinBalanceInDay(
                        base = base,
                        dayStartMillis = dayStartMillis,
                        dayEndMillis = dayEndMillis
                    )

                    if (minBalance > 0.0) {
                        val dailyRate = if (activePeriod.type == "ANNUAL") {
                            activePeriod.rate / 100.0 / 365.0
                        } else {
                            val dayJalali = Jalali.toJalaliPublic(currentMillis)
                            val daysInThisMonth = Jalali.daysInMonth(dayJalali[0], dayJalali[1])
                            activePeriod.rate / 100.0 / daysInThisMonth
                        }
                        totalProfit += minBalance * dailyRate
                    }

                    currentMillis += 86400000L
                }

                val roundedProfit = totalProfit
                if (roundedProfit > 0.0) {
                    val dest = activePeriod.destinationAccountId ?: accountId
                    val typeLabel = if (activePeriod.type == "ANNUAL") "سالانه" else "ماهانه"
                    val rateDisplay = if (activePeriod.rate % 1.0 == 0.0) {
                        activePeriod.rate.toLong().toString()
                    } else {
                        activePeriod.rate.toString()
                    }
                    val text = "واریز سود با نرخ $rateDisplay درصد $typeLabel"

                    val profitTx = Transaction(
                        id = 0,
                        accountId = dest,
                        dateMillis = payoutMillis,
                        type = "بستانکار",
                        amount = roundedProfit,
                        note = text,
                        isAutoProfit = true,
                        profitKey = "$accountId:$cy:$cm"
                    )

                    out.add(profitTx)

                    if (dest == accountId) {
                        base.add(profitTx)
                        base.sortBy { it.dateMillis }
                    }
                }
            }

            cm++; if (cm > 12) { cm = 1; cy++ }
        }

        db.tx().insertAll(out)
    }

    private fun calculateMinBalanceInDay(
        base: MutableList<Transaction>,
        dayStartMillis: Long,
        dayEndMillis: Long
    ): Double {
        val balanceAtStart = base
            .filter { it.dateMillis < dayStartMillis }
            .sumOf { if (it.type == "بستانکار") it.amount else -it.amount }

        val dayTransactions = base
            .filter { it.dateMillis in dayStartMillis..dayEndMillis }
            .sortedBy { it.dateMillis }

        if (dayTransactions.isEmpty()) return balanceAtStart

        var minBal = balanceAtStart
        var runningBal = balanceAtStart
        for (t in dayTransactions) {
            runningBal += if (t.type == "بستانکار") t.amount else -t.amount
            if (runningBal < minBal) minBal = runningBal
        }

        return minBal
    }

    private fun toMillis(y: Int, m: Int, d: Int): Long =
        Jalali.parse("%04d/%02d/%02d".format(Locale.US, y, m, d)) ?: 0L
}
