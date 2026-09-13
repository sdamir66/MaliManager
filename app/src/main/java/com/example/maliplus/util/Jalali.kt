package com.example.maliplus.util

import java.util.Calendar
import java.util.Locale

object Jalali {
    fun format(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val r = gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        return "%04d/%02d/%02d".format(Locale.US, r[0], r[1], r[2])
    }
    fun nowJalali(): IntArray { return fromMillis(System.currentTimeMillis()) }
    fun fromMillis(millis: Long): IntArray {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
    fun startOfJalaliMonth(year: Int, month: Int): Long = jalaliToMillis(year, month, 1)
    fun endOfJalaliMonth(year: Int, month: Int): Long = jalaliToMillis(year, month, daysInMonth(year, month), true)
    fun daysInMonth(year: Int, month: Int): Int = if (month <= 6) 31 else if (month <= 11) 30 else if (isLeap(year)) 30 else 29
    fun isLeap(jy: Int): Boolean { val r = ((jy % 33) + 33) % 33; return r in setOf(1, 5, 9, 13, 17, 22, 26, 30) }
    fun parse(s: String): Long? {
        val p = s.trim().replace('-', '/').split('/')
        if (p.size != 3) return null
        val y = p[0].toIntOrNull() ?: return null; val m = p[1].toIntOrNull() ?: return null; val d = p[2].toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..daysInMonth(y, m)) return null
        return jalaliToMillis(y, m, d)
    }
    fun monthName(m: Int) = listOf("", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")[m]

    private fun jalaliToMillis(jy:Int,jm:Int,jd:Int,end:Boolean=false):Long {
        val g = jalaliToGregorian(jy,jm,jd) ?: error("invalid date")
        return Calendar.getInstance().apply { set(g[0], g[1]-1, g[2], if(end)23 else 0, if(end)59 else 0, if(end)59 else 0); set(Calendar.MILLISECOND, if(end)999 else 0) }.timeInMillis
    }
    private fun jalaliToGregorian(jy:Int,jm:Int,jd:Int):IntArray? {
        val gy=jy+621; val days = (jy-979)*365 + ((jy-979)/33)*8 + (((jy-979)%33)+3)/4 + if(jm<=6)(jm-1)*31 else (jm-1)*30+6 + jd-1
        var gDays=days+79; var gy2=1600 + 400*(gDays/146097); var rem=gDays%146097
        if(rem>=36525){rem--;gy2 += 100*(rem/36524); rem%=36524; if(rem>=365) rem++}
        gy2 += 4*(rem/1461); rem%=1461
        if(rem>=366){rem--;gy2 += rem/365; rem%=365}
        val gd=rem+1; val leap=((gy2%4==0 && gy2%100!=0)||gy2%400==0)
        val mdays=intArrayOf(0,31,if(leap)29 else 28,31,30,31,30,31,31,30,31,30,31); var gm=1; var x=gd
        while(gm<=12 && x>mdays[gm]){x-=mdays[gm];gm++}
        return if(gm<=12) intArrayOf(gy2,gm,x) else null
    }
    private fun gregorianToJalali(gy:Int,gm:Int,gd:Int):IntArray {
        val gdm=intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334); var jy=gy-621
        var days=365*(gy-1600)+(gy-1600+3)/4-(gy-1600+99)/100+(gy-1600+399)/400; days += gd+gdm[gm-1]; if(gm>2 && ((gy%4==0&&gy%100!=0)||gy%400==0)) days++; days-=79
        val jy2=jy-979; val cycle=jy2/33; val rem=jy2%33; var jday=days-33*cycle*12053-(rem/4)*1461; var jyear=979+33*cycle+4*(rem/4)
        if(rem%4==0 && jday<=0){jyear--;jday+=365}; if(jday>186){jyear+=(jday-1)/365;jday=(jday-1)%365}else{jyear+=(jday-1)/366;jday=(jday-1)%366}
        val jm=if(jday<=186)(jday-1)/31+1 else (jday-187)/30+7; val jd=if(jday<=186)(jday-1)%31+1 else (jday-187)%30+1; return intArrayOf(jyear,jm,jd)
    }
}
