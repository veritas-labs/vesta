package uk.veritaslabs.vesta

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.veritaslabs.kits.store.DatabaseDriverFactory
import uk.veritaslabs.kits.store.DatabaseOpenResult
import uk.veritaslabs.kits.store.openKitsDatabase
import uk.veritaslabs.vesta.automerge.TaskSources
import uk.veritaslabs.vesta.automerge.morningDigestItems
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.localMoment
import uk.veritaslabs.vesta.tasks.MorningDigestItem
import uk.veritaslabs.vesta.tasks.isoText
import uk.veritaslabs.vesta.tasks.morningDigestDue
import uk.veritaslabs.vesta.tasks.nextMorningDigestMillis
import uk.veritaslabs.vesta.tasks.parseIsoDate

private const val ChannelId = "morning_digest"
private const val NotificationId = 1
private const val PreferencesName = "morning_digest"
private const val LastShownKey = "last_shown"
private const val MaxLines = 6
/** The Intent extra naming the Home whose Tasks a tap on the digest opens. */
const val ExtraTasksHomeId = "uk.veritaslabs.vesta.TASKS_HOME_ID"
private const val ActionDigest = "uk.veritaslabs.vesta.MORNING_DIGEST"

/**
 * The morning digest (docs/adr/ADR-040-morning-task-digest.md): a notification at the first wake of the day after 07:00 listing today's
 * open tasks that have no time set. It is a local, non-waking alarm, never a push service: `RTC` (not `RTC_WAKEUP`) alarms are held
 * until the device is next awake, so the notification appears when the user picks the phone up and never wakes it.
 */
object MorningDigest {
    /** Arms the alarm for the next 07:00; replaces any earlier one. Call whenever the app starts and after anything that moves the clock. */
    fun arm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.set(AlarmManager.RTC, nextMorningDigestMillis(Clock.System.now().toEpochMilliseconds()), alarmIntent(context))
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0, Intent(context, MorningDigestReceiver::class.java).setAction(ActionDigest),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Posts today's digest if it is due and not yet shown today, then records the day, so a day with nothing to say still counts as done. */
    internal suspend fun showIfDue(context: Context) {
        val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        val now = localMoment(Clock.System.now().toEpochMilliseconds())
        val lastShown = preferences.getString(LastShownKey, null)?.let(::parseIsoDate)
        if (!morningDigestDue(now, lastShown)) return
        // Recorded first: a crash while reading should not become a notification loop.
        preferences.edit().putString(LastShownKey, now.date.isoText()).apply()
        val items = readItems(context, now.date)
        if (items.isNotEmpty()) post(context, items)
    }

    private suspend fun readItems(context: Context, today: CalendarDate): List<MorningDigestItem> {
        VestaAndroid.install(context.applicationContext)
        val opened = openKitsDatabase(DatabaseDriverFactory(VestaDatabaseLocation), VestaKitConfig.appName) as? DatabaseOpenResult.Opened
        return opened?.let { morningDigestItems(it.database, TaskSources(it.database), today) }.orEmpty()
    }

    private fun post(context: Context, items: List<MorningDigestItem>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(ChannelId, context.getString(R.string.morning_digest_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.morning_digest_channel_description) },
        )
        val title = context.resources.getQuantityString(R.plurals.morning_digest_title, items.size, items.size)
        val multipleHomes = items.map { it.homeName }.distinct().size > 1
        val lines = items.map { if (multipleHomes) context.getString(R.string.morning_digest_with_home, it.title, it.homeName) else it.title }
        val style = Notification.InboxStyle().also { inbox -> lines.take(MaxLines).forEach(inbox::addLine) }
            .also { inbox -> if (lines.size > MaxLines) inbox.setSummaryText("+${lines.size - MaxLines}") }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // The first task's Home: the one Home it can open when the digest spans several.
                .putExtra(ExtraTasksHomeId, items.first().homeId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, ChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(lines.joinToString(", "))
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
        // A private task's title stays off a locked screen (ADR-031): the lock screen gets only the count.
        if (items.any { it.isPrivate }) {
            builder.setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(
                Notification.Builder(context, ChannelId).setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).build(),
            )
        }
        // Without the notification permission (API 33+) this is silently dropped by the system.
        manager.notify(NotificationId, builder.build())
    }
}

/**
 * Receives the 07:00 alarm, and the boot and clock changes that lose it. After a boot the alarm is gone and the morning may already be
 * past, so a device switched on after 07:00 gets today's digest then.
 */
class MorningDigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ActionDigest, Intent.ACTION_BOOT_COMPLETED -> MorningDigest.showIfDue(app)
                }
            } finally {
                MorningDigest.arm(app)
                pending.finish()
            }
        }
    }
}
