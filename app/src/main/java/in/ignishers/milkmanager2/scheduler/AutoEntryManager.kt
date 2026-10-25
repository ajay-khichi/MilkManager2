package `in`.ignishers.milkmanager2.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import `in`.ignishers.milkmanager2.MainActivity
import `in`.ignishers.milkmanager2.R
import `in`.ignishers.milkmanager2.database.AppDatabase
import `in`.ignishers.milkmanager2.models.DailyTransaction
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AutoEntryManager {

    const val CHANNEL_ID = "auto_entry_notifications"
    const val NOTIFICATION_ID_MORNING = 1001
    const val NOTIFICATION_ID_EVENING = 1002

    suspend fun executeAutoEntry(
        context: Context,
        session: String,
        date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
        showNotification: Boolean = true
    ): Int {
        val db = AppDatabase.getDatabase(context)
        val isMasterEnabled = db.appSettingsDao().getSettingValue("auto_entry_enabled") == "true"
        if (!isMasterEnabled) return 0

        val milkRate = db.milkRateDao().getLatestRate()?.price ?: BigDecimal("60.0")
        val allCustomers = db.customerDao().getAllCustomersList()
        var processedCount = 0
        var totalMilkRecorded = BigDecimal.ZERO
        var totalAmountRecorded = BigDecimal.ZERO

        for (cust in allCustomers) {
            if (!cust.autoEntryEnabled) continue

            val qty = if (session.equals("Morning", ignoreCase = true)) {
                cust.defaultQtyMorning
            } else {
                cust.defaultQtyEvening
            }

            if (qty != null && qty > BigDecimal.ZERO) {
                val customerId = cust.id ?: continue
                val hasEntry = db.dailyTransactionDao().hasTodayEntry(customerId, date, session)
                if (!hasEntry) {
                    val amount = qty.multiply(milkRate)
                    val transaction = DailyTransaction(
                        customerId = customerId,
                        date = date,
                        session = session,
                        quantity = qty,
                        amount = amount,
                        milkType = "Regular",
                        timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    )
                    db.dailyTransactionDao().insert(transaction)

                    cust.currentDue = (cust.currentDue ?: BigDecimal.ZERO).add(amount)
                    cust.syncUpdatedAt = System.currentTimeMillis()
                    cust.syncIsSynced = 0
                    db.customerDao().update(cust)

                    processedCount++
                    totalMilkRecorded = totalMilkRecorded.add(qty)
                    totalAmountRecorded = totalAmountRecorded.add(amount)
                }
            }
        }

        if (showNotification) {
            sendAutoEntryNotification(context, session, processedCount, totalMilkRecorded, totalAmountRecorded)
        }

        return processedCount
    }

    suspend fun checkAndRunPendingAutoEntries(context: Context): Int {
        val db = AppDatabase.getDatabase(context)
        val isMasterEnabled = db.appSettingsDao().getSettingValue("auto_entry_enabled") == "true"
        if (!isMasterEnabled) return 0

        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        var count = 0

        // Morning auto entry is scheduled for 5:00 AM
        if (hour >= 5) {
            count += executeAutoEntry(context, "Morning", today, showNotification = true)
        }
        // Evening auto entry is scheduled for 4:00 PM (16:00)
        if (hour >= 16) {
            count += executeAutoEntry(context, "Evening", today, showNotification = true)
        }
        return count
    }

    fun sendAutoEntryNotification(
        context: Context,
        session: String,
        processedCount: Int,
        totalMilk: BigDecimal,
        totalAmount: BigDecimal
    ) {
        try {
            // Check permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // Create Notification Channel for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_auto_entry),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notification_channel_auto_entry_desc)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val isMorning = session.equals("Morning", ignoreCase = true)
            val title = if (isMorning) {
                context.getString(R.string.morning_auto_entry_done)
            } else {
                context.getString(R.string.evening_auto_entry_done)
            }

            val contentText = if (processedCount > 0) {
                context.getString(
                    R.string.auto_entry_notification_body,
                    processedCount,
                    totalMilk.stripTrailingZeros().toPlainString(),
                    totalAmount.stripTrailingZeros().toPlainString()
                )
            } else {
                context.getString(R.string.auto_entry_notification_up_to_date, session)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                if (isMorning) NOTIFICATION_ID_MORNING else NOTIFICATION_ID_EVENING,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sale_record)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notificationId = if (isMorning) NOTIFICATION_ID_MORNING else NOTIFICATION_ID_EVENING
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: Throwable) {
            // Safe catch to avoid crash
            e.printStackTrace()
        }
    }
}
