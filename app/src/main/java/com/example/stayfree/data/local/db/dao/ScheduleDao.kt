package com.example.stayfree.data.local.db.dao

import androidx.room.*
import com.example.stayfree.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedules WHERE blockRuleId = :ruleId")
    fun getSchedulesForRule(ruleId: Long): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE blockRuleId = :ruleId")
    suspend fun getSchedulesForRuleOnce(ruleId: Long): List<ScheduleEntity>

    // Joined so the Flow also emits when a schedule is written after its rule.
    @Query(
        """SELECT s.endTimeMinutes FROM schedules s JOIN block_rules r ON s.blockRuleId = r.id
        WHERE r.packageName = :pkg AND r.isActive = 1 ORDER BY r.createdAt DESC LIMIT 1"""
    )
    fun observeActiveScheduleEnd(pkg: String): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE blockRuleId = :ruleId")
    suspend fun deleteForRule(ruleId: Long)
}
