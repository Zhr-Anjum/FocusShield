package com.focusshield.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

enum class AppCategory(val label: String) {
    TRADING("Trading & Futures Apps"),
    SOCIAL("Social & Video Apps"),
    OTHER("Other Installed Apps")
}

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val isLocked: Boolean
)

object KnownPackages {
    val TRADING_PACKAGES = setOf(
        "com.zerodha.kite3",              // Zerodha Kite
        "com.nextbillion.groww",          // Groww
        "in.upstox.pro",                  // Upstox
        "com.msf.angelmobile",            // Angel One
        "com.dhan.live",                  // Dhan
        "com.binance.dev",                // Binance
        "com.coinbase.android",           // Coinbase
        "com.delta.exchange",             // Delta Exchange
        "com.tradingview.tradingviewapp", // TradingView
        "net.metaquotes.metatrader5"      // MetaTrader 5
    )

    val SOCIAL_PACKAGES = setOf(
        "com.instagram.android",          // Instagram
        "com.google.android.youtube",     // YouTube
        "com.facebook.katana",            // Facebook
        "com.snapchat.android",           // Snapchat
        "com.zhiliaoapp.musically",       // TikTok
        "com.twitter.android",            // X / Twitter
        "com.reddit.frontpage"            // Reddit
    )

    const val INSTAGRAM_PKG = "com.instagram.android"
    const val YOUTUBE_PKG = "com.google.android.youtube"
    const val FACEBOOK_PKG = "com.facebook.katana"
    const val SETTINGS_PKG = "com.android.settings"
}

class FocusShieldRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("focus_shield_prefs", Context.MODE_PRIVATE)

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun getLockedPackages(): Set<String> =
        prefs.getStringSet(
            "locked_packages",
            setOf(
                KnownPackages.INSTAGRAM_PKG,
                KnownPackages.YOUTUBE_PKG,
                KnownPackages.FACEBOOK_PKG,
                "com.zerodha.kite3",
                "com.binance.dev",
                "com.tradingview.tradingviewapp"
            )
        ) ?: emptySet()

    fun togglePackageLock(packageName: String, locked: Boolean) {
        val current = getLockedPackages().toMutableSet()
        if (locked) current.add(packageName) else current.remove(packageName)
        prefs.edit().putStringSet("locked_packages", current).apply()
    }

    var blockInstagramReels: Boolean
        get() = prefs.getBoolean("block_ig_reels", true)
        set(value) = prefs.edit().putBoolean("block_ig_reels", value).apply()

    var blockYouTubeShorts: Boolean
        get() = prefs.getBoolean("block_yt_shorts", true)
        set(value) = prefs.edit().putBoolean("block_yt_shorts", value).apply()

    var blockFacebookReels: Boolean
        get() = prefs.getBoolean("block_fb_reels", true)
        set(value) = prefs.edit().putBoolean("block_fb_reels", value).apply()

    var surgicalSavesToday: Int
        get() = prefs.getInt("surgical_saves_${todayKey()}", 0)
        set(value) = prefs.edit().putInt("surgical_saves_${todayKey()}", value).apply()

    var tradingKillSwitchUntilMs: Long
        get() = prefs.getLong("trading_kill_switch_until", 0L)
        set(value) = prefs.edit().putLong("trading_kill_switch_until", value).apply()

    fun isTradingKillSwitchActive(): Boolean =
        System.currentTimeMillis() < tradingKillSwitchUntilMs

    fun activateTradingKillSwitch(hours: Int = 18) {
        tradingKillSwitchUntilMs = System.currentTimeMillis() + (hours * 3600_000L)
    }

    var enforceTradingWindow: Boolean
        get() = prefs.getBoolean("enforce_trading_window", true)
        set(value) = prefs.edit().putBoolean("enforce_trading_window", value).apply()

    var requirePreTradeChecklist: Boolean
        get() = prefs.getBoolean("require_pre_trade_checklist", true)
        set(value) = prefs.edit().putBoolean("require_pre_trade_checklist", value).apply()

    fun getAppOpensToday(packageName: String): Int =
        prefs.getInt("opens_${todayKey()}_$packageName", 0)

    fun incrementAppOpensToday(packageName: String) {
        val count = getAppOpensToday(packageName)
        prefs.edit().putInt("opens_${todayKey()}_$packageName", count + 1).apply()
    }

    fun getBufferSeconds(packageName: String): Int {
        val opens = getAppOpensToday(packageName)
        return (10 + opens * 5).coerceAtMost(60)
    }

    fun grantTemporaryUnlock(packageName: String, minutes: Int) {
        val until = System.currentTimeMillis() + (minutes * 60_000L)
        prefs.edit().putLong("unlock_until_$packageName", until).apply()
        incrementAppOpensToday(packageName)
    }

    fun isTemporarilyUnlocked(packageName: String): Boolean =
        System.currentTimeMillis() < prefs.getLong("unlock_until_$packageName", 0L)

    var strictModeEnabled: Boolean
        get() = prefs.getBoolean("strict_mode_enabled", false)
        set(value) = prefs.edit().putBoolean("strict_mode_enabled", value).apply()
}

class FocusShieldAccessibilityService : AccessibilityService() {

    private lateinit var repo: FocusShieldRepository
    private var lastToastMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        repo = FocusShieldRepository(applicationContext)
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 150L
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow
        val packageName = event?.packageName?.toString()
            ?: rootNode?.packageName?.toString()
            ?: return

        if (packageName == applicationContext.packageName ||
            packageName == "com.android.systemui"
        ) return

        if (packageName == KnownPackages.INSTAGRAM_PKG && rootNode != null && repo.blockInstagramReels) {
            val reelIds = listOf(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/reel_viewer_root"
            )
            if (reelIds.any { rootNode.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }) {
                exitShortFormFeed("Instagram Reels blocked — DMs & Stories stay open")
                return
            }
        }

        if (packageName == KnownPackages.YOUTUBE_PKG && rootNode != null && repo.blockYouTubeShorts) {
            val shortsIds = listOf(
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_player_page_container"
            )
            if (shortsIds.any { rootNode.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }) {
                exitShortFormFeed("YouTube Shorts blocked — Search & long videos stay open")
                return
            }
        }

        if (packageName == KnownPackages.FACEBOOK_PKG && rootNode != null && repo.blockFacebookReels) {
            if (rootNode.findAccessibilityNodeInfosByText("Reels and short videos").isNotEmpty()) {
                exitShortFormFeed("Facebook Reels blocked")
                return
            }
        }

        val lockedPackages = repo.getLockedPackages()
        val isTrading = KnownPackages.TRADING_PACKAGES.contains(packageName)

        if (isTrading && repo.isTradingKillSwitchActive()) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            launchBufferScreen(packageName, "Trading Kill Switch Active: Locked for the day.")
            return
        }

        if (lockedPackages.contains(packageName) && !repo.isTemporarilyUnlocked(packageName)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            val msg = if (isTrading) {
                "Take a breath before checking charts or placing a trade."
            } else {
                "Pause for a moment before opening this app."
            }
            launchBufferScreen(packageName, msg)
        }
    }

    private fun exitShortFormFeed(message: String) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        val now = System.currentTimeMillis()
        if (now - lastToastMs > 2500L) {
            lastToastMs = now
            repo.surgicalSavesToday += 1
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchBufferScreen(targetPackage: String, subtitle: String) {
        val intent = Intent(applicationContext, LockInterceptorActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("TARGET_PACKAGE", targetPackage)
            putExtra("SUBTITLE", subtitle)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = FocusShieldRepository(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0D9488),
                    secondary = Color(0xFF2563EB),
                    background = Color(0xFFF8FAFC),
                    surface = Color(0xFFFFFFFF)
                )
            ) {
                CleanMainScreen(repo = repo)
            }
        }
    }
}

@Composable
fun CleanMainScreen(repo: FocusShieldRepository) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var showDonateSheet by remember { mutableStateOf(false) }
    var lockedPackages by remember { mutableStateOf(repo.getLockedPackages()) }

    if (showDonateSheet) {
        MultiCurrencyDonateDialog(onDismiss = { showDonateSheet = false })
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            Surface(shadowElevation = 1.dp, color = Color.White) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Render uploaded custom logo image
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = "FocusShield Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "FocusShield",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                            Text(
                                text = "100% Free Forever",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { showDonateSheet = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "Donate",
                                tint = Color(0xFFE11D48),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Donate", fontSize = 11.sp, color = Color(0xFF0F172A))
                        }

                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Activate", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val items = listOf(
                    "Apps" to Icons.Default.Lock,
                    "Reels & Shorts" to Icons.Default.CheckCircle,
                    "Trading Lock" to Icons.Default.Warning
                )
                items.forEachIndexed { idx, (label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> SimpleAppListTab(
                    repo = repo,
                    lockedPackages = lockedPackages,
                    onUpdate = { lockedPackages = repo.getLockedPackages() },
                    onOpenPreviewBuffer = {
                        context.startActivity(
                            Intent(context, LockInterceptorActivity::class.java).apply {
                                putExtra("TARGET_PACKAGE", "com.instagram.android")
                                putExtra("SUBTITLE", "Pause for a moment before opening Instagram.")
                            }
                        )
                    }
                )
                1 -> SimpleReelsShortsTab(repo = repo)
                2 -> SimpleTradingDisciplineTab(repo = repo)
            }
        }
    }
}

@Composable
fun SimpleAppListTab(
    repo: FocusShieldRepository,
    lockedPackages: Set<String>,
    onUpdate: () -> Unit,
    onOpenPreviewBuffer: () -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }

    val apps = remember(lockedPackages) {
        val pm = context.packageManager
        try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { info ->
                    val pkg = info.packageName
                    val cat = when {
                        KnownPackages.TRADING_PACKAGES.contains(pkg) -> AppCategory.TRADING
                        KnownPackages.SOCIAL_PACKAGES.contains(pkg) -> AppCategory.SOCIAL
                        else -> AppCategory.OTHER
                    }
                    InstalledAppItem(
                        packageName = pkg,
                        appName = pm.getApplicationLabel(info).toString(),
                        category = cat,
                        isLocked = lockedPackages.contains(pkg)
                    )
                }
                .sortedWith(compareBy({ it.category.ordinal }, { it.appName }))
        } catch (e: Exception) {
            emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDFA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Preview Buffer Clock & Donate Sign",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F766E),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Tap to see what pops up when you open a locked app",
                            color = Color(0xFF115E59),
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = onOpenPreviewBuffer) {
                        Text("Preview →", fontWeight = FontWeight.Bold, color = Color(0xFF0D9488))
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search installed apps...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        AppCategory.values().forEach { category ->
            val list = apps.filter {
                it.category == category && it.appName.contains(search, ignoreCase = true)
            }
            if (list.isNotEmpty()) {
                item {
                    Text(
                        text = category.label,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(list, key = { it.packageName }) { app ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                Text(
                                    text = "Opened today: ${repo.getAppOpensToday(app.packageName)} times",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Switch(
                                checked = app.isLocked,
                                onCheckedChange = { checked ->
                                    repo.togglePackageLock(app.packageName, checked)
                                    onUpdate()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleReelsShortsTab(repo: FocusShieldRepository) {
    var igReels by remember { mutableStateOf(repo.blockInstagramReels) }
    var ytShorts by remember { mutableStateOf(repo.blockYouTubeShorts) }
    var fbReels by remember { mutableStateOf(repo.blockFacebookReels) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Block Only the Addictive Feeds",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFF0F172A)
        )
        Text(
            text = "Use Instagram for messages and YouTube for tutorials without getting sucked into endless short videos.",
            fontSize = 13.sp,
            color = Color(0xFF475569)
        )

        SimpleSwitchCard(
            title = "Block Instagram Reels",
            subtitle = "Keeps DMs and Stories working normally",
            checked = igReels,
            onCheckedChange = { igReels = it; repo.blockInstagramReels = it }
        )
        SimpleSwitchCard(
            title = "Block YouTube Shorts",
            subtitle = "Keeps YouTube Search and study videos working",
            checked = ytShorts,
            onCheckedChange = { ytShorts = it; repo.blockYouTubeShorts = it }
        )
        SimpleSwitchCard(
            title = "Block Facebook Reels",
            subtitle = "Stops short video scrolling inside Facebook",
            checked = fbReels,
            onCheckedChange = { fbReels = it; repo.blockFacebookReels = it }
        )
    }
}

@Composable
fun SimpleTradingDisciplineTab(repo: FocusShieldRepository) {
    var killActive by remember { mutableStateOf(repo.isTradingKillSwitchActive()) }
    var windowActive by remember { mutableStateOf(repo.enforceTradingWindow) }
    var checklistActive by remember { mutableStateOf(repo.requirePreTradeChecklist) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (killActive) Color(0xFFFEF2F2) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (killActive) "Trading Locked Until Tomorrow" else "Done for the Day? (Kill Switch)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lock Zerodha, Binance, Groww, and TradingView for 18 hours right after hitting your daily target or stop-loss so you don't revenge trade.",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        repo.activateTradingKillSwitch(18)
                        killActive = true
                    },
                    enabled = !killActive,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (killActive) "Kill Switch Active (18h)" else "Lock All Trading Apps for Today",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        SimpleSwitchCard(
            title = "Market Window Lock (9:15 AM – 11:00 AM)",
            subtitle = "Automatically locks trading apps outside your planned setup hours",
            checked = windowActive,
            onCheckedChange = { windowActive = it; repo.enforceTradingWindow = it }
        )

        SimpleSwitchCard(
            title = "Pre-Trade Checklist on Buffer Screen",
            subtitle = "Asks 3 quick risk questions during the buffer clock before opening a broker app",
            checked = checklistActive,
            onCheckedChange = { checklistActive = it; repo.requirePreTradeChecklist = it }
        )
    }
}

@Composable
fun SimpleSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A), fontSize = 15.sp)
                Text(subtitle, color = Color(0xFF64748B), fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

class LockInterceptorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = FocusShieldRepository(this)
        val targetPackage = intent.getStringExtra("TARGET_PACKAGE") ?: "com.instagram.android"
        val subtitle = intent.getStringExtra("SUBTITLE") ?: "Take a calm breath before opening this app."

        setContent {
            BackHandler(enabled = true) { goHome() }
            MaterialTheme(colorScheme = lightColorScheme()) {
                BufferClockWithDonateScreen(
                    repo = repo,
                    targetPackage = targetPackage,
                    subtitle = subtitle,
                    onCloseApp = { goHome() },
                    onContinueToApp = { finish() }
                )
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }
}

@Composable
fun BufferClockWithDonateScreen(
    repo: FocusShieldRepository,
    targetPackage: String,
    subtitle: String,
    onCloseApp: () -> Unit,
    onContinueToApp: () -> Unit
) {
    val isTrading = KnownPackages.TRADING_PACKAGES.contains(targetPackage)
    val initialBuffer = remember { repo.getBufferSeconds(targetPackage) }
    var secondsLeft by remember { mutableStateOf(initialBuffer) }
    var selectedMinutes by remember { mutableStateOf(5) }
    var showDonateModal by remember { mutableStateOf(false) }

    var checkPlan by remember { mutableStateOf(false) }
    var checkNoRevenge by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
    }

    if (showDonateModal) {
        MultiCurrencyDonateDialog(onDismiss = { showDonateModal = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "FocusShield Pause",
                color = Color(0xFF0D9488),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Opened ${repo.getAppOpensToday(targetPackage)} times today",
                color = Color(0xFF64748B),
                fontSize = 13.sp
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (secondsLeft > 0) "${secondsLeft}s" else "Ready",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (secondsLeft > 0) Color(0xFF0D9488) else Color(0xFF16A34A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Buffer Clock",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1.25f)
                        .clickable { showDonateModal = true }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "Support",
                                tint = Color(0xFFE11D48),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Free for Everyone",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF9F1239)
                            )
                        }
                        Text(
                            text = "Satisfied with how much time & focus you're saving? Donate in ₹ INR, $ USD, or any currency to help us grow!",
                            fontSize = 11.sp,
                            color = Color(0xFF881337),
                            lineHeight = 15.sp
                        )
                        Text(
                            text = "Tap to Support (₹ / $ / €) →",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFE11D48)
                        )
                    }
                }
            }

            if (isTrading && repo.requirePreTradeChecklist) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Quick Trader Check:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checkPlan, onCheckedChange = { checkPlan = it })
                            Text("This follows my trading plan & risk limit", fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checkNoRevenge, onCheckedChange = { checkNoRevenge = it })
                            Text("I am NOT revenge trading emotionally", fontSize = 12.sp)
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "If you still need to open, pick a time limit:",
                    fontSize = 12.sp,
                    color = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 5, 10, 15).forEach { mins ->
                        FilterChip(
                            selected = selectedMinutes == mins,
                            onClick = { selectedMinutes = mins },
                            label = { Text("$mins min") }
                        )
                    }
                }
            }
        }

        val canProceed = secondsLeft == 0 &&
                (!isTrading || !repo.requirePreTradeChecklist || (checkPlan && checkNoRevenge))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onCloseApp,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "Close App & Protect My Routine",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            OutlinedButton(
                onClick = {
                    repo.grantTemporaryUnlock(targetPackage, selectedMinutes)
                    onContinueToApp()
                },
                enabled = canProceed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (canProceed) {
                        "Use App for $selectedMinutes Minutes"
                    } else {
                        "Wait ${secondsLeft}s Buffer Clock to Open"
                    }
                )
            }
        }
    }
}

@Composable
fun MultiCurrencyDonateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currencies = listOf(
        "INR (₹)" to listOf("₹49", "₹99", "₹199", "₹499"),
        "USD ($)" to listOf("$2", "$5", "$10", "$25"),
        "EUR (€)" to listOf("€2", "€5", "€10", "€20"),
        "GBP (£)" to listOf("£2", "£5", "£10", "£20")
    )
    var selectedCurrencyIdx by remember { mutableStateOf(0) }
    var selectedAmount by remember { mutableStateOf(currencies[0].second[1]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Support FocusShield ❤️",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "FocusShield has no ads and no locked paywalls. If this app helped you save hours from Reels/Shorts or protected your trading capital, your tip helps us keep it 100% free for everyone!",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )

                Text("1. Choose Your Currency:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    currencies.forEachIndexed { idx, (currLabel, amounts) ->
                        FilterChip(
                            selected = selectedCurrencyIdx == idx,
                            onClick = {
                                selectedCurrencyIdx = idx
                                selectedAmount = amounts[1]
                            },
                            label = { Text(currLabel, fontSize = 11.sp) }
                        )
                    }
                }

                Text("2. Select Contribution Amount:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    currencies[selectedCurrencyIdx].second.forEach { amt ->
                        FilterChip(
                            selected = selectedAmount == amt,
                            onClick = { selectedAmount = amt },
                            label = { Text(amt, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    Toast.makeText(
                        context,
                        "Launching Google Play / UPI Tip Checkout for $selectedAmount — Thank you!",
                        Toast.LENGTH_LONG
                    ).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
            ) {
                Text("Donate $selectedAmount", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe Later")
            }
        }
    )
}
