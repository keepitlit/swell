package ca.keepitlit.swell.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.keepitlit.swell.SwellModel
import ca.keepitlit.swell.data.AppEntry
import ca.keepitlit.swell.seats.LEAST_USED_FIRST
import ca.keepitlit.swell.seats.Use
import ca.keepitlit.swell.seats.use

/** One removable app, with every seat its package owns. */
class Candidate(val entry: AppEntry, val use: Use)

/**
 * Every app that can be uninstalled, least used first.
 *
 * Left out: shortcuts (not apps), other profiles (the uninstaller only acts for this user), and
 * apps built into the phone, which the system can only disable or roll back, never remove.
 */
fun leastUsed(model: SwellModel): List<Candidate> {
    val me = Process.myUserHandle()
    return model.apps
        .filter { !it.isShortcut && it.user == me }
        .filter { e -> model.repo.activityInfo(e)?.let { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 } == true }
        .groupBy { it.packageName }
        .map { (_, entries) -> Candidate(entries.first(), model.book.use(entries.map { it.key })) }
        .sortedWith(compareBy(LEAST_USED_FIRST) { it.use })
}

/**
 * Least used apps, to look over and uninstall. Tap lines to select them; the button under the list
 * uninstalls the selection one system confirmation at a time. Declining one stops the rest, so a
 * mistaken batch never means cancelling twenty dialogs.
 */
@Composable
fun LeastUsedPage(open: Boolean, model: SwellModel, onClose: () -> Unit) {
    val look = LocalLook.current
    BackHandler(enabled = open, onBack = onClose)
    AnimatedVisibility(
        open,
        enter = fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 8 },
        exit = fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 8 },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(look.shell)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Wall()
            LeastUsedColumn(model, onClose)
        }
    }
}

@Composable
private fun LeastUsedColumn(model: SwellModel, onClose: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val version = model.version
    val candidates = remember(model.apps, version) { leastUsed(model) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    // An uninstalled app leaves the list; it must leave the selection with it.
    val chosen = candidates.filter { it.entry.packageName in selected }

    var queue by remember { mutableStateOf<List<Candidate>>(emptyList()) }
    var asked by remember { mutableIntStateOf(0) }
    var removed by remember { mutableIntStateOf(0) }
    val uninstaller = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val head = queue.firstOrNull() ?: return@rememberLauncherForActivityResult
        // Not every uninstaller returns a result; whether the package is still there is the answer.
        val gone = r.resultCode == Activity.RESULT_OK || !installed(context, head.entry)
        if (gone) {
            removed += 1
            selected = selected - head.entry.packageName
            queue = queue.drop(1)
        } else {
            queue = emptyList()
        }
    }
    LaunchedEffect(queue) {
        val next = queue.firstOrNull()
        if (next != null) {
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", next.entry.packageName, null))
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
            try {
                uninstaller.launch(intent)
            } catch (_: Exception) {
                model.showToast("Can't uninstall ${next.entry.label}")
                queue = emptyList()
            }
        } else if (asked > 0) {
            model.showToast(
                when {
                    removed == asked && asked == 1 -> "Uninstalled 1 app"
                    removed == asked -> "Uninstalled $asked apps"
                    else -> "Uninstalled $removed of $asked"
                },
            )
            asked = 0
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text(
            "Least used",
            fontFamily = look.clockFont, fontSize = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp,
            color = look.paper, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 4.dp),
        )
        // An app opened only from its notifications reads as unused here; uninstalling deletes its data.
        Text(
            "Counts only apps opened from Swell.",
            color = look.paper3, fontSize = 13.sp, fontFamily = look.bodyFont, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().edgeBlurFade(EDGE_BAND),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = EDGE_BAND, bottom = EDGE_BAND),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(candidates, key = { it.entry.packageName }) { c ->
                val on = c.entry.packageName in selected
                CandidateLine(c, on, model) {
                    selected = if (on) selected - c.entry.packageName else selected + c.entry.packageName
                }
            }
            item {
                Text(
                    "Apps built into the phone can't be uninstalled and aren't listed.",
                    color = look.paper3, fontSize = 13.sp, fontFamily = look.bodyFont, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
        if (chosen.isNotEmpty()) {
            Text(
                if (chosen.size == 1) "Uninstall ${chosen.first().entry.label}" else "Uninstall ${chosen.size} apps",
                fontFamily = look.bodyFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = look.ember2,
                textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(CircleShape)
                    .border(1.dp, look.ember2, CircleShape)
                    .clickable {
                        asked = chosen.size
                        removed = 0
                        queue = chosen
                    }
                    .padding(vertical = 14.dp),
            )
        }
        Text(
            "Done",
            fontFamily = look.bodyFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = look.accent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .padding(vertical = 14.dp),
        )
    }
}

/** One app, split at the centre like home: a select mark and the icon left, its name and use right. */
@Composable
private fun CandidateLine(c: Candidate, on: Boolean, model: SwellModel, onToggle: () -> Unit) {
    val look = LocalLook.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(look.cellRadius))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .then(if (on) Modifier.background(look.ember2) else Modifier.border(1.5.dp, look.rule2, CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                if (on) Text("✓", color = look.shell, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            AppIcon(c.entry, model.repo, 44.dp, look.iconsMono, look.paper)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(c.entry.label, color = look.paper, fontSize = 18.sp, fontFamily = look.bodyFont, maxLines = 1)
            Text(useLine(c.use), color = look.paper3, fontSize = 13.sp, fontFamily = look.bodyFont, maxLines = 1)
        }
    }
}

private fun useLine(u: Use): String {
    fun days(n: Int) = if (n == 1) "1 day" else "$n days"
    if (u.opens == 0) return if (u.watched < 1) "new" else "not opened in ${days(u.watched)}"
    val opens = if (u.opens == 1) "1 open" else "${u.opens} opens"
    return when (val d = u.daysSince) {
        null -> opens
        0 -> "$opens · last today"
        1 -> "$opens · last yesterday"
        else -> "$opens · last $d days ago"
    }
}

private fun installed(context: Context, entry: AppEntry): Boolean = try {
    (context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).isPackageEnabled(entry.packageName, entry.user)
} catch (_: Exception) {
    false
}
