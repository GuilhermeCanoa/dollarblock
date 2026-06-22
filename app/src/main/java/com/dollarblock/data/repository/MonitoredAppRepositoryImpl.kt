package com.dollarblock.data.repository

import com.dollarblock.data.local.db.DailyUsageDao
import com.dollarblock.data.local.db.MonitoredAppDao
import com.dollarblock.data.local.db.MonitoredAppEntity
import com.dollarblock.data.usage.UsageStatsProvider
import com.dollarblock.domain.model.MonitoredAppUsage
import com.dollarblock.domain.repository.MonitoredAppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitoredAppRepositoryImpl @Inject constructor(
    private val monitoredAppDao: MonitoredAppDao,
    private val dailyUsageDao: DailyUsageDao,
    private val usageStatsProvider: UsageStatsProvider,
) : MonitoredAppRepository {

    override fun observeMonitoredAppsUsage(): Flow<List<MonitoredAppUsage>> {
        val today = usageStatsProvider.currentEpochDay()
        return combine(
            monitoredAppDao.observeAll(),
            dailyUsageDao.observeForDay(today),
        ) { apps, usageToday ->
            val usageByPackage = usageToday.associateBy { it.packageName }
            apps.map { app ->
                val usedMillis = usageByPackage[app.packageName]?.usedMillis ?: 0L
                MonitoredAppUsage(
                    packageName = app.packageName,
                    appName = app.appName,
                    isMonitored = app.isMonitored,
                    dailyLimitMinutes = app.dailyLimitMinutes,
                    usedMinutesToday = (usedMillis / 60_000L).toInt(),
                )
            }
        }
    }

    override suspend fun setMonitored(packageName: String, appName: String, isMonitored: Boolean) {
        val existing = monitoredAppDao.getByPackage(packageName)
        if (existing != null) {
            monitoredAppDao.setMonitored(packageName, isMonitored)
        } else {
            monitoredAppDao.upsert(
                MonitoredAppEntity(
                    packageName = packageName,
                    appName = appName,
                    isMonitored = isMonitored,
                    dailyLimitMinutes = null,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun setDailyLimit(packageName: String, dailyLimitMinutes: Int?) {
        val existing = monitoredAppDao.getByPackage(packageName)
        if (existing != null) {
            monitoredAppDao.setDailyLimit(packageName, dailyLimitMinutes)
        } else {
            monitoredAppDao.upsert(
                MonitoredAppEntity(
                    packageName = packageName,
                    appName = packageName,
                    isMonitored = false,
                    dailyLimitMinutes = dailyLimitMinutes,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun syncTodayUsage(foregroundPackage: String?, foregroundSinceMillis: Long?) {
        val today = usageStatsProvider.currentEpochDay()
        val monitoredPackages = monitoredAppDao.getMonitoredPackages().toSet()
        if (monitoredPackages.isEmpty()) return

        val usageByPackage = usageStatsProvider.getTodayUsageByPackage(foregroundPackage, foregroundSinceMillis)
        usageByPackage
            .filterKeys { it in monitoredPackages }
            .forEach { (packageName, usedMillis) ->
                dailyUsageDao.upsertUsage(
                    packageName = packageName,
                    epochDay = today,
                    usedMillis = usedMillis,
                    updatedAt = System.currentTimeMillis(),
                )
            }
    }
}
