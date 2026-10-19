package uk.veritaslabs.vesta

import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.io.File
import java.util.Timer
import java.util.TimerTask
import javax.imageio.ImageIO
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.automerge.TaskSources
import uk.veritaslabs.vesta.automerge.morningDigestItems
import uk.veritaslabs.vesta.home.TasksDeepLink
import uk.veritaslabs.vesta.tasks.MorningDigestItem
import uk.veritaslabs.vesta.tasks.MorningDigestRunner
import uk.veritaslabs.vesta.tasks.isoText
import uk.veritaslabs.vesta.tasks.parseIsoDate

private const val MaxLines = 6
private const val TrayIconLifetimeMillis = 5 * 60_000L

/**
 * The morning digest on the desktop (docs/adr/ADR-040-morning-task-digest.md): while Vesta is running, the first check after 07:00 shows
 * today's open tasks with no time as a system tray notice, once a day. Nothing runs while the app is closed. Clicking the notice brings
 * the window forward on that Home's Tasks. Where there is no system tray (GNOME), it falls back to `notify-send`.
 */
suspend fun runMorningDigest(database: KitsDatabase, sources: TaskSources, bringToFront: () -> Unit) {
    val marker = File(System.getProperty("user.home"), ".vesta/morning-digest")
    MorningDigestRunner(
        read = { today -> morningDigestItems(database, sources, today) },
        lastShown = { runCatching { marker.readText().trim() }.getOrNull()?.let(::parseIsoDate) },
        record = { day -> runCatching { marker.parentFile?.mkdirs(); marker.writeText(day.isoText()) } },
        show = { items -> showNotice(items, bringToFront) },
    ).run()
}

/** Shows the digest by the best route this desktop has: the system tray, else `notify-send` on Linux (GNOME has no tray). */
private fun showNotice(items: List<MorningDigestItem>, bringToFront: () -> Unit) {
    val multipleHomes = items.map { it.homeName }.distinct().size > 1
    val lines = items.map { if (multipleHomes) "${it.title} (${it.homeName})" else it.title }
    val more = (lines.size - MaxLines).takeIf { it > 0 }?.let { "\n+$it more" }.orEmpty()
    val title = if (items.size == 1) "1 task today" else "${items.size} tasks today"
    val body = lines.take(MaxLines).joinToString("\n") + more
    val open = { TasksDeepLink.request(items.first().homeId); bringToFront() }
    when {
        SystemTray.isSupported() -> showTrayNotice(title, body, open)
        sendWithNotifySend(title, body, open) -> Unit
        else -> System.err.println("Vesta: morning digest not shown: no system tray and no notify-send")
    }
}

/** `notify-send` waits for the click on the notice and prints its action; a click (or the notice's default action) runs [onClick]. False if it cannot run. */
private fun sendWithNotifySend(title: String, body: String, onClick: () -> Unit): Boolean = runCatching {
    val process = ProcessBuilder("notify-send", "--app-name=Vesta", "--action=default=Open Tasks", title, body)
        .redirectErrorStream(true).start()
    Thread {
        val output = process.inputStream.bufferedReader().readText().trim()
        if (output == "default") onClick()
    }.apply { isDaemon = true; start() }
}.isSuccess

private fun showTrayNotice(title: String, body: String, onClick: () -> Unit) {
    val tray = SystemTray.getSystemTray()
    val image = runCatching { ImageIO.read(MorningDigestRunner::class.java.getResource("/vesta-icon.png") ?: return@runCatching null) }.getOrNull()
        ?: return
    val icon = TrayIcon(image.getScaledInstance(tray.trayIconSize.width, -1, Image.SCALE_SMOOTH), "Vesta")
    icon.addActionListener { onClick() }
    tray.add(icon)
    icon.displayMessage(title, body, TrayIcon.MessageType.INFO)
    // The icon only needs to outlive the notice, so a click on it still lands.
    Timer(true).schedule(object : TimerTask() { override fun run() = tray.remove(icon) }, TrayIconLifetimeMillis)
}
