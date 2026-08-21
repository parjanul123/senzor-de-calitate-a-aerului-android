package com.example.senzordeaer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings

data class FoundBleDevice(val address: String, val name: String?, val rssi: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainContent()
            }
        }
    }
}

@Composable
fun MainContent() {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    
    var isUserLoggedIn by remember { mutableStateOf(sessionManager.accessToken != null) }
    var loggedInProfile by remember { mutableStateOf<UserProfile?>(null) }
    var profileError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isUserLoggedIn) {
        if (isUserLoggedIn) {
            val token = sessionManager.accessToken
            val refresh = sessionManager.refreshToken
            val uid = sessionManager.userId
            
            if (token != null && uid != null) {
                try {
                    SupabaseClient.client.auth.importSession(
                        UserSession(
                            accessToken = token,
                            refreshToken = refresh ?: "",
                            expiresIn = 3600L,
                            tokenType = "bearer",
                            user = null
                        )
                    )
                } catch (e: Exception) { e.printStackTrace() }

                if (loggedInProfile == null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val dbService = SupabaseService()
                            val p = dbService.getUserProfile(token, uid)
                            withContext(Dispatchers.Main) {
                                if (p != null) {
                                    loggedInProfile = p
                                    profileError = null
                                } else {
                                    profileError = "Eroare profil."
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { profileError = "Eroare: ${e.message}" }
                        }
                    }
                }
            } else {
                isUserLoggedIn = false
            }
        }
    }

    if (isUserLoggedIn) {
        MainAppScreen(
            profile = loggedInProfile, 
            profileError = profileError,
            sessionManager = sessionManager,
            onLogout = {
                sessionManager.clear()
                loggedInProfile = null
                profileError = null
                isUserLoggedIn = false
            }
        )
    } else {
        AuthScreen(
            onLoginSuccess = { profile, error ->
                loggedInProfile = profile
                profileError = error
                isUserLoggedIn = true
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(profile: UserProfile?, profileError: String?, sessionManager: SessionManager, onLogout: () -> Unit) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedItem by remember { mutableStateOf("Acasă") }
    
    var selectedDbDevice by remember { mutableStateOf<Device?>(null) }
    var showWifiProvisioningForDevice by remember { mutableStateOf<Device?>(null) }
    var showMeasurementsForDevice by remember { mutableStateOf<Device?>(null) }
    
    val dbService = remember { SupabaseService() }
    val fastApiService = remember { FastApiService() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text("Senzor de Aer", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                
                val menuItems = listOf(
                    Triple("Acasă", Icons.Default.Home, "Acasă"),
                    Triple("Lista Dispozitive", Icons.AutoMirrored.Filled.List, "Dispozitivele Tale"),
                    Triple("Adaugă", Icons.Default.AddCircle, "Adaugă Dispozitiv"),
                    Triple("Chat AI", Icons.Default.Face, "Chat AI Aer"),
                    Triple("Dashboard AI", Icons.Default.Star, "Dashboard AI"),
                    Triple("Prognoză AI", Icons.Default.Info, "Prognoză AI"),
                    Triple("Antrenare AI", Icons.Default.Build, "Antrenare Model"),
                    Triple("Status AI", Icons.Default.Settings, "Status Servicii AI"),
                    Triple("QR Login", Icons.Default.Share, "Conectare Website")
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(menuItems) { (id, icon, label) ->
                        NavigationDrawerItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = selectedItem == id,
                            onClick = {
                                selectedDbDevice = null; showWifiProvisioningForDevice = null; showMeasurementsForDevice = null
                                selectedItem = id
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Deconectare") },
                    label = { Text("Deconectare") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onLogout() },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        if (showWifiProvisioningForDevice != null) Text("Setare Wi-Fi")
                        else if (showMeasurementsForDevice != null) Text("Măsurători Live")
                        else if (selectedDbDevice != null) Text(selectedDbDevice?.name ?: "Setări Dispozitiv")
                        else Text(selectedItem) 
                    },
                    navigationIcon = {
                        if (showWifiProvisioningForDevice != null) {
                            IconButton(onClick = { showWifiProvisioningForDevice = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Înapoi")
                            }
                        } else if (showMeasurementsForDevice != null) {
                            IconButton(onClick = { showMeasurementsForDevice = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Înapoi")
                            }
                        } else if (selectedDbDevice != null) {
                            IconButton(onClick = { selectedDbDevice = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Înapoi")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Meniu")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (showWifiProvisioningForDevice != null) {
                    DeviceWifiSetupScreen(targetDevice = showWifiProvisioningForDevice!!)
                } 
                else if (showMeasurementsForDevice != null) {
                    DeviceMeasurementsScreen(
                        device = showMeasurementsForDevice!!,
                        sessionManager = sessionManager,
                        dbService = dbService
                    )
                }
                else if (selectedDbDevice != null) {
                    DeviceSettingsDashboard(
                        device = selectedDbDevice!!, 
                        sessionManager = sessionManager, 
                        dbService = dbService,
                        onOpenWifiSetup = { showWifiProvisioningForDevice = selectedDbDevice },
                        onOpenMeasurements = { showMeasurementsForDevice = selectedDbDevice },
                        onDeviceUpdated = { updatedDevice -> selectedDbDevice = updatedDevice }
                    )
                } 
                else {
                    when (selectedItem) {
                        "Acasă" -> HomeScreenContent(profile, profileError, onLogout)
                        "Lista Dispozitive" -> DevicesListScreen(profile, sessionManager, dbService) { clickedDevice ->
                            selectedDbDevice = clickedDevice
                        }
                        "Adaugă" -> AddNewDeviceScreen(sessionManager, dbService) {
                            selectedItem = "Lista Dispozitive"
                        }
                        "Chat AI" -> AiChatScreen(fastApiService)
                        "Dashboard AI" -> AiDashboardScreen(profile, sessionManager, dbService, fastApiService)
                        "Prognoză AI" -> AiForecastScreen(profile, sessionManager, dbService, fastApiService)
                        "Antrenare AI" -> AiTrainingScreen(profile, sessionManager, dbService, fastApiService)
                        "Status AI" -> AiSettingsScreen(fastApiService)
                        "QR Login" -> QRScannerScreen(
                            userId = sessionManager.userId ?: "",
                            onNavigateBack = { selectedItem = "Acasă" }
                        )
                    }
                }
            }
        }
    }
}

private fun formatAiVal(value: Any?): String {
    if (value == null) return "-"
    if (value is Number) return String.format(Locale.US, "%.1f", value.toDouble())
    if (value is Boolean) return if (value) "DA" else "NU"
    
    val map = value as? Map<*, *>
    if (map != null) {
        if (map.containsKey("error")) return "❌ Eroare: ${map["error"]}"
        
        // Extracție valori numerice estimate
        val temp = map["temperature"] ?: map["temp"] ?: map["temp_c"]
        val hum = map["humidity"] ?: map["hum"] ?: map["hum_rel"]
        val pm1 = map["pm1"] ?: map["pm1_0"] ?: map["PM1"]
        val pm25 = map["pm25"] ?: map["pm2_5"] ?: map["PM2.5"]
        val pm10 = map["pm10"] ?: map["pm10_0"] ?: map["PM10"]
        val co2 = map["co2"] ?: map["eco2"] ?: map["CO2"]
        val tvoc = map["tvoc"] ?: map["voc"] ?: map["TVOC"]
        val aqi = map["aqi"] ?: map["AQI"]

        val lines = mutableListOf<String>()
        
        // Linia 1: Particule (PM1, PM2.5, PM10)
        val pmParts = mutableListOf<String>()
        if (pm1 != null) pmParts.add("PM1: ${formatNum(pm1)} µg/m³")
        if (pm25 != null) pmParts.add("PM2.5: ${formatNum(pm25)} µg/m³")
        if (pm10 != null) pmParts.add("PM10: ${formatNum(pm10)} µg/m³")
        if (pmParts.isNotEmpty()) lines.add(pmParts.joinToString(" | "))

        // Linia 2: Temp & Umiditate
        val tStr = if (temp != null) String.format(Locale.US, "%.1f°C", (temp as Number).toDouble()) else null
        val hStr = if (hum != null) String.format(Locale.US, "%.1f%% hum.", (hum as Number).toDouble()) else null
        if (tStr != null || hStr != null) {
            lines.add(listOfNotNull(tStr, hStr).joinToString(" | "))
        }

        // Linia 3: Gaze (eCO2, TVOC)
        val gasParts = mutableListOf<String>()
        if (co2 != null) gasParts.add("eCO2: ${formatNum(co2)} ppm")
        if (tvoc != null) gasParts.add("TVOC: ${formatNum(tvoc)} ppb")
        if (gasParts.isNotEmpty()) lines.add(gasParts.joinToString(" | "))

        // Linia 4: AQI și Rezumat Categorie
        val statusRaw = (map["prediction"] ?: map["status"] ?: map["label"] ?: "").toString()
        val statusText = when {
            statusRaw.contains("Excelent", true) || statusRaw.contains("Excellent", true) || statusRaw.contains("Exc", true) -> "EXCELENT"
            statusRaw.contains("Foarte Bun", true) || statusRaw.contains("Good", true) -> "BUN"
            statusRaw.contains("Moderat", true) || statusRaw.contains("Moderate", true) || statusRaw.contains("Mod", true) -> "MODERAT"
            statusRaw.contains("Poluat", true) || statusRaw.contains("Poor", true) || statusRaw.contains("Pol", true) -> "POLUAT"
            statusRaw.contains("Periculos", true) || statusRaw.contains("Hazardous", true) || statusRaw.contains("Perc", true) -> "PERICULOS"
            else -> statusRaw
        }
        
        val footerParts = mutableListOf<String>()
        if (aqi != null) footerParts.add("AQI estimat: ${formatNum(aqi)}")
        if (statusText.isNotBlank() && statusText != "null") footerParts.add("Status: $statusText")
        if (footerParts.isNotEmpty()) lines.add(footerParts.joinToString(" | "))

        return if (lines.isNotEmpty()) lines.joinToString("\n") else "-"
    }
    
    val s = value.toString()
    return when {
        s.equals("pol", true) || s.contains("Poluat", true) || s.contains("Poor", true) -> "POLUAT"
        s.equals("mod", true) || s.contains("Moderat", true) || s.contains("Moderate", true) -> "MODERAT"
        s.equals("exc", true) || s.contains("Excelent", true) || s.contains("Excellent", true) -> "EXCELENT"
        s.equals("good", true) || s.contains("Bun", true) || s.contains("Good", true) -> "BUN"
        else -> s
    }
}

private fun formatNum(v: Any?): String {
    if (v is Number) return String.format(Locale.US, "%.1f", v.toDouble())
    return v?.toString() ?: "-"
}

private fun selectedDeviceId(device: Device?): String? = device?.device_id?.takeIf { it.isNotBlank() }

private fun deviceDisplayName(device: Device): String {
    val name = device.name?.takeIf { it.isNotBlank() } ?: "Dispozitiv"
    val id = device.device_id?.takeIf { it.isNotBlank() }
    return id?.let { "$name ($it)" } ?: name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiDeviceSelector(
    profile: UserProfile?,
    sessionManager: SessionManager,
    dbService: SupabaseService,
    selectedDevice: Device?,
    onDeviceSelected: (Device?) -> Unit
) {
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(profile, sessionManager.accessToken, sessionManager.userId) {
        val token = sessionManager.accessToken
        val uid = sessionManager.userId
        if (profile == null || token == null || uid == null) return@LaunchedEffect

        isLoading = true
        error = null
        try {
            val fetchedDevices = withContext(Dispatchers.IO) {
                dbService.getMyDevices(token, uid)?.toList() ?: emptyList()
            }
            devices = fetchedDevices
            val currentDeviceId = selectedDeviceId(selectedDevice)
            if (currentDeviceId == null || fetchedDevices.none { it.device_id == currentDeviceId }) {
                onDeviceSelected(fetchedDevices.firstOrNull())
            }
        } catch (e: Exception) {
            error = "Nu am putut încărca dispozitivele: ${e.message}"
            devices = emptyList()
            onDeviceSelected(null)
        } finally {
            isLoading = false
        }
    }

    Text("Selectează dispozitivul:", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(6.dp))
    when {
        isLoading -> CircularProgressIndicator(Modifier.size(20.dp))
        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        devices.isEmpty() -> Text("Nu există dispozitive asociate contului.", style = MaterialTheme.typography.bodySmall)
        else -> ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedDevice?.let(::deviceDisplayName) ?: "Alege un dispozitiv",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(deviceDisplayName(device), fontSize = 13.sp) },
                        onClick = {
                            onDeviceSelected(device)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AiTable(headers: Pair<String, String>, rows: List<Pair<String, Any?>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(headers.first, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            Text(headers.second, modifier = Modifier.weight(3.5f), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.first, modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(formatAiVal(row.second), modifier = Modifier.weight(3.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, lineHeight = 14.sp)
            }
        }
    }
}

private fun jsonRows(value: Any?, prefix: String = ""): List<Pair<String, Any?>> = when (value) {
    is Map<*, *> -> value.entries.flatMap { (key, nestedValue) ->
        val label = listOf(prefix, key?.toString().orEmpty()).filter { it.isNotBlank() }.joinToString(".")
        jsonRows(nestedValue, label)
    }
    is List<*> -> value.flatMapIndexed { index, nestedValue ->
        jsonRows(nestedValue, "$prefix[$index]")
    }
    else -> listOf((if (prefix.isBlank()) "valoare" else prefix) to value)
}

@Composable
fun AiResponseCard(title: String, response: Map<String, Any?>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (response.containsKey("error")) {
                Text(response["error"].toString(), color = MaterialTheme.colorScheme.error)
            } else {
                AiTable("Câmp" to "Valoare", jsonRows(response))
            }
        }
    }
}

@Composable
private fun AiPredictionCard(title: String, response: Map<String, Any?>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (response.containsKey("error")) {
                Text(response["error"].toString(), color = MaterialTheme.colorScheme.error)
                return@Column
            }

            AiTable(
                "Rezultat" to "Valoare",
                listOf(
                    "Predicție" to response["prediction"],
                    "Încredere" to response["confidence"],
                    "Algoritm" to response["model_type"]
                )
            )

            val inputValues = response["input_values"] as? Map<*, *>
            if (!inputValues.isNullOrEmpty()) {
                Text("Valori senzori", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                AiTable(
                    "Senzor" to "Valoare",
                    inputValues.entries.map { (name, value) -> name.toString() to value }
                )
            }

            val featureAssessment = response["feature_assessment"] as? Map<*, *>
            if (!featureAssessment.isNullOrEmpty()) {
                Text("Evaluarea fiecărei valori", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                AiTable(
                    "Senzor" to "Evaluare",
                    featureAssessment.entries.mapNotNull { (name, details) ->
                        val values = details as? Map<*, *> ?: return@mapNotNull null
                        val value = values["value"]
                        val unit = values["unit"]?.toString().orEmpty()
                        val status = values["status"]?.toString().orEmpty()
                        name.toString() to "${formatAiVal(value)} $unit - $status"
                    }
                )
            }
        }
    }
}

@Composable
private fun AiAnomalyCard(title: String, response: Map<String, Any?>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (response.containsKey("error")) {
                Text(response["error"].toString(), color = MaterialTheme.colorScheme.error)
                return@Column
            }

            val result = response["result"] as? Map<*, *>
            if (result == null) {
                AiTable("Câmp" to "Valoare", jsonRows(response))
                return@Column
            }

            val anomalousFeatures = result["anomalous_features"] as? List<*>
            val featureAnalysis = result["feature_analysis"] as? List<*>
            val sensorWarnings = result["sensor_health_warnings"] as? List<*>
            val problemRows = mutableListOf<Pair<String, Any?>>()

            anomalousFeatures.orEmpty().forEachIndexed { index, feature ->
                problemRows += "Anomalie ${index + 1}" to feature
            }
            featureAnalysis.orEmpty().forEach { item ->
                val feature = item as? Map<*, *> ?: return@forEach
                val isOutlier = feature["is_outlier"] == true
                val warning = feature["sensor_warning"]?.toString().orEmpty()
                if (isOutlier || warning.isNotBlank()) {
                    val name = feature["feature"]?.toString() ?: "Valoare senzor"
                    val value = formatAiVal(feature["value"])
                    val reason = if (warning.isNotBlank()) warning else "Schimbare neobișnuită detectată"
                    problemRows += name to "$value - $reason"
                }
            }
            sensorWarnings.orEmpty().forEachIndexed { index, warning ->
                if (warning.toString().isNotBlank() && problemRows.none { it.second == warning }) {
                    problemRows += "Avertizare ${index + 1}" to warning
                }
            }

            val isAnomaly = result["is_anomaly"] == true
            if (isAnomaly) {
                val score = result["score"]
                Text(
                    "Anomalie detectată${score?.let { " (scor: ${formatAiVal(it)})" } ?: ""}",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            if (problemRows.isEmpty()) {
                Text("Nu au fost detectate erori sau schimbări bruște în valorile senzorilor.")
            } else {
                AiTable("Valoare" to "Problemă detectată", problemRows)
            }
        }
    }
}

@Composable
fun AiForecastResults(response: Map<String, Any?>) {
    val forecasts = response["forecast"] as? List<*>

    if (response.containsKey("error") || forecasts == null) {
        AiResponseCard("Rezultate prognoză", response)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rezultate prognoză", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        forecasts.forEach { forecast ->
            val forecastValues = forecast as? Map<*, *> ?: return@forEach
            val horizon = (forecastValues["horizon_hours"] as? Number)?.toInt()
            val inputValues = forecastValues["input_values"] as? Map<*, *>

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = horizon?.let { "Peste $it ore" } ?: "Moment estimat",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    forecastValues["prediction"]?.let { prediction ->
                        Text("Calitatea aerului: $prediction", style = MaterialTheme.typography.bodyMedium)
                    }
                    inputValues?.let { values ->
                        Spacer(Modifier.height(6.dp))
                        AiTable(
                            "Valoare estimată" to "Rezultat",
                            values.entries.map { (name, value) -> name.toString() to value }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiDashboardScreen(profile: UserProfile?, sessionManager: SessionManager, dbService: SupabaseService, apiService: FastApiService) {
    val coroutineScope = rememberCoroutineScope()
    var predictionData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var anomalyData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var selectedPredictionAlgorithm by remember { mutableStateOf("random_forest") }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var isPredicting by remember { mutableStateOf(false) }
    var isCheckingAnomaly by remember { mutableStateOf(false) }
    val isLoading = isPredicting || isCheckingAnomaly
    val scrollState = rememberScrollState()
    val predictionAlgorithms = listOf(
        TrainingModel("random_forest", "Random Forest"),
        TrainingModel("xgboost", "XGBoost"),
        TrainingModel("svm", "SVM")
    )
    val selectedPredictionAlgorithmLabel = predictionAlgorithms
        .first { it.id == selectedPredictionAlgorithm }
        .label

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        Text("Starea aerului", style = MaterialTheme.typography.headlineMedium)
        Text("Ultima măsurătoare din Supabase, evaluată de model", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Acțiuni rapide", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp), fontSize = 14.sp)

                AiDeviceSelector(
                    profile = profile,
                    sessionManager = sessionManager,
                    dbService = dbService,
                    selectedDevice = selectedDevice,
                    onDeviceSelected = { device ->
                        selectedDevice = device
                        predictionData = null
                        anomalyData = null
                    }
                )

                Spacer(Modifier.height(12.dp))

                Text("Algoritm pentru predicție", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    predictionAlgorithms.forEach { algorithm ->
                        FilterChip(
                            selected = selectedPredictionAlgorithm == algorithm.id,
                            onClick = {
                                selectedPredictionAlgorithm = algorithm.id
                                predictionData = null
                            },
                            label = { Text(algorithm.label, fontSize = 11.sp) }
                        )
                    }
                }
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                    }
                }

                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        isPredicting = true
                        anomalyData = null
                        val deviceId = selectedDeviceId(selectedDevice)
                        coroutineScope.launch {
                            predictionData = apiService.getPrediction(selectedPredictionAlgorithm, deviceId)
                            isPredicting = false
                        }
                    }, enabled = !isLoading && selectedDeviceId(selectedDevice) != null, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { 
                        Text("Update Status", fontSize = 10.sp) 
                    }
                    
                    Button(onClick = {
                        isCheckingAnomaly = true
                        predictionData = null
                        anomalyData = null
                        val deviceId = selectedDeviceId(selectedDevice)
                        coroutineScope.launch {
                            anomalyData = apiService.getAnomaly(deviceId)
                            isCheckingAnomaly = false
                        }
                    }, enabled = !isLoading && selectedDeviceId(selectedDevice) != null, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { 
                        Text("Check Anomalii", fontSize = 10.sp) 
                    }
                }
            }
        }
        
        predictionData?.let {
            Spacer(Modifier.height(16.dp))
            AiPredictionCard("Predicție curentă - $selectedPredictionAlgorithmLabel", it)
        }
        anomalyData?.let {
            Spacer(Modifier.height(12.dp))
            AiAnomalyCard("Verificare anomalii", it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiForecastScreen(profile: UserProfile?, sessionManager: SessionManager, dbService: SupabaseService, apiService: FastApiService) {
    val coroutineScope = rememberCoroutineScope()
    var forecastData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val possibleHorizons = listOf(1, 3, 6, 12, 24, 48)
    val selectedHorizons = remember { mutableStateListOf(1, 3, 6, 12, 24) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Prognoză AI", style = MaterialTheme.typography.headlineMedium)
        Text("Valori numerice estimate pentru viitor", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        AiDeviceSelector(
            profile = profile,
            sessionManager = sessionManager,
            dbService = dbService,
            selectedDevice = selectedDevice,
            onDeviceSelected = { device ->
                selectedDevice = device
                forecastData = null
            }
        )

        Spacer(Modifier.height(16.dp))
        
        Text("Selectează Orizonturi (ore):", style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            possibleHorizons.forEach { h ->
                FilterChip(
                    selected = selectedHorizons.contains(h),
                    onClick = { if (selectedHorizons.contains(h)) selectedHorizons.remove(h) else selectedHorizons.add(h) },
                    label = { Text("${h}h", fontSize = 10.sp) }
                )
            }
        }

        Button(onClick = {
            isLoading = true
            val deviceId = selectedDeviceId(selectedDevice)
            coroutineScope.launch {
                forecastData = apiService.getForecast(selectedHorizons.sorted(), deviceId)
                isLoading = false
            }
        }, enabled = !isLoading && selectedDeviceId(selectedDevice) != null && selectedHorizons.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            if (isLoading) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Obține Valori Viitoare", fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))

        if (forecastData != null) {
            AiForecastResults(forecastData!!)
        }
    }
}

@Composable
fun AiManualPredictionScreen(apiService: FastApiService) {
    val coroutineScope = rememberCoroutineScope()
    var temperature by remember { mutableStateOf("") }
    var humidity by remember { mutableStateOf("") }
    var pm25 by remember { mutableStateOf("") }
    var pm10 by remember { mutableStateOf("") }
    var co2 by remember { mutableStateOf("") }
    var response by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val inputs = listOf(
        "Temperatură (°C)" to temperature,
        "Umiditate (%)" to humidity,
        "PM2.5 (µg/m³)" to pm25,
        "PM10 (µg/m³)" to pm10,
        "CO₂ (ppm)" to co2
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Predicție manuală", style = MaterialTheme.typography.headlineMedium)
        Text("Introdu valorile senzorului pentru o evaluare fără a folosi ultima citire.", style = MaterialTheme.typography.bodyMedium)
        inputs.forEachIndexed { index, (label, value) ->
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    when (index) {
                        0 -> temperature = newValue
                        1 -> humidity = newValue
                        2 -> pm25 = newValue
                        3 -> pm10 = newValue
                        else -> co2 = newValue
                    }
                },
                label = { Text(label) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        val validValues = listOf(temperature, humidity, pm25, pm10, co2).all { it.toFloatOrNull() != null }
        Button(
            onClick = {
                isLoading = true
                coroutineScope.launch {
                    response = apiService.predictCustom(
                        temperature.toFloat(), humidity.toFloat(), pm25.toFloat(), pm10.toFloat(), co2.toFloat().toInt()
                    )
                    isLoading = false
                }
            },
            enabled = validValues && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Calculează predicția")
        }
        response?.let { AiResponseCard("Rezultat predicție manuală", it) }
    }
}

private fun nestedMap(source: Map<String, Any>, key: String): Map<*, *>? = source[key] as? Map<*, *>

private data class TrainingModel(val id: String, val label: String)

private fun humanReadableTrainingError(rawError: String): String {
    val normalized = rawError.lowercase(Locale.ROOT)
    return when {
        normalized.contains("quality_label") || normalized.contains("quality_label_source") ->
            "Date insuficiente pentru modele supravegheate (Random Forest, SVM, XGBoost). " +
                "În tabela measurements trebuie adăugate coloanele quality_label și quality_label_source, apoi populate etichete valide. " +
                "Până atunci poți folosi modelul Isolation Forest."
        else -> rawError
    }
}

@Composable
private fun TrainingModelDetails(modelId: String, modelLabel: String, report: Map<String, Any>) {
    val trainingReport = nestedMap(report, "training_report") ?: return
    val modelInfo = trainingReport["model_info"] as? Map<*, *>
    val technicalDetails = trainingReport["technical_details"] as? Map<*, *>
    val recommendedMetric = trainingReport["recommended_metric"] as? Map<*, *>
    val evolution = technicalDetails?.get("evolution") as? List<*>
    val trees = modelInfo?.get("n_estimators")
    val trainingMetrics = when (modelId) {
        "random_forest" -> listOf(
            "OOB score" to listOf("oob_score"),
            "Mean score" to listOf("mean_score", "mean_cv_score", "mean_test_score")
        )
        "isolation_forest" -> listOf("Scor decizie mediu" to listOf("mean_decision_score", "anomaly_score", "decision_function", "score"))
        "xgboost" -> listOf("F1-score (antrenare)" to listOf("f1_score", "f1-score", "f1"))
        "svm" -> listOf(
            "Accuracy (antrenare)" to listOf("accuracy"),
            "F1-score (antrenare)" to listOf("f1_score", "f1-score", "f1")
        )
        else -> listOf("Scor antrenare" to listOf("score"))
    }
    val reportSources = listOf(recommendedMetric, technicalDetails, modelInfo, trainingReport)
    fun findMetric(metricNames: List<String>, source: Map<*, *>? = null): Float? =
        (source?.let { sources -> listOf(sources) } ?: reportSources)
            .filterNotNull()
            .firstNotNullOfOrNull { details ->
                metricNames.firstNotNullOfOrNull { metricName ->
                    (details[metricName] as? Number)?.toFloat()
                }
            }

    val meanScoreEvolution = evolution?.mapIndexed { index, entry ->
        val scores = evolution.take(index + 1).mapNotNull { evolutionEntry ->
            val details = evolutionEntry as? Map<*, *>
            findMetric(listOf("oob_score"), details)
        }
        scores.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(modelLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (modelId == "random_forest") {
                trees?.let { Text("Arbori configurați: $it") }
            }
            if (evolution.isNullOrEmpty()) {
                trainingMetrics.forEach { (metricLabel, metricNames) ->
                    findMetric(metricNames)?.let { metric ->
                        Text("$metricLabel: ${String.format(Locale.US, "%.4f", metric)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            evolution?.forEachIndexed { index, entry ->
                val entryDetails = entry as? Map<*, *>
                val iteration = entryDetails?.get("iteration")
                    ?: entryDetails?.get("round")
                    ?: entryDetails?.get("step")
                    ?: index + 1
                Text("Iterația $iteration", style = MaterialTheme.typography.bodySmall)
                trainingMetrics.forEachIndexed { metricIndex, (metricLabel, metricNames) ->
                    val metric = findMetric(metricNames, entryDetails)
                        ?: if (modelId == "random_forest" && metricIndex == 1) {
                            meanScoreEvolution?.getOrNull(index)
                        } else if (metricIndex == 0) {
                            (entry as? Number)?.toFloat()
                        } else {
                            null
                        }

                    metric?.let { score ->
                        Text(
                            "$metricLabel: ${String.format(Locale.US, "%.4f", score)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        score.takeIf { it in 0f..1f }?.let {
                            LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiTrainingScreen(profile: UserProfile?, sessionManager: SessionManager, dbService: SupabaseService, apiService: FastApiService) {
    val coroutineScope = rememberCoroutineScope()
    var selectedModel by remember { mutableStateOf("random_forest") }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val models = listOf(
        TrainingModel("random_forest", "Random Forest"),
        TrainingModel("isolation_forest", "Isolation Forest"),
        TrainingModel("xgboost", "XGBoost"),
        TrainingModel("svm", "SVM")
    )
    val selectedModelDefinition = models.first { it.id == selectedModel }
    val parsedHours = hours.toIntOrNull()
    val parsedMinutes = minutes.toIntOrNull()
    val hasDuration = parsedHours != null || parsedMinutes != null
    val hasValidDuration = hasDuration &&
        (hours.isBlank() || parsedHours in 1..168) &&
        (minutes.isBlank() || parsedMinutes in 1..10080)

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Antrenare Model", style = MaterialTheme.typography.headlineMedium)
            Text("Ajustează algoritmul AI pentru mai multă precizie", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            AiDeviceSelector(
                profile = profile,
                sessionManager = sessionManager,
                dbService = dbService,
                selectedDevice = selectedDevice,
                onDeviceSelected = { device ->
                    selectedDevice = device
                    report = null
                }
            )

            Spacer(Modifier.height(16.dp))

            Text("Selectează Algoritmul:", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                models.forEach { model ->
                    FilterChip(
                        selected = selectedModel == model.id,
                        onClick = { selectedModel = model.id },
                        label = { Text(model.label) }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it.filter(Char::isDigit) },
                    label = { Text("Ore (opțional)") },
                    isError = hours.isNotBlank() && parsedHours !in 1..168,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text("Minute (opțional)") },
                    isError = minutes.isNotBlank() && parsedMinutes !in 1..10080,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            if (!hasDuration) Text("Completează ore sau minute.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            else if (!hasValidDuration) Text("Ore: 1-168. Minute: 1-10080.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(24.dp))

            Button(onClick = {
                isLoading = true
                val deviceId = selectedDeviceId(selectedDevice)
                coroutineScope.launch {
                    report = apiService.trainModel(
                        model = selectedModel,
                        hours = parsedHours,
                        minutes = parsedMinutes,
                        deviceId = deviceId,
                        allowDerivedLabelFallback = true
                    )
                    isLoading = false
                }
            }, enabled = !isLoading && hasValidDuration && selectedDeviceId(selectedDevice) != null, modifier = Modifier.fillMaxWidth()) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("Lansează Antrenarea")
            }
        }

        if (report != null) {
            item {
                Spacer(Modifier.height(16.dp))
                if (report!!.containsKey("error")) {
                    val readableError = humanReadableTrainingError(report!!["error"].toString())
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = readableError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    AiResponseCard("Raport antrenare", report!!)
                    Spacer(Modifier.height(12.dp))
                    TrainingModelDetails(selectedModelDefinition.id, selectedModelDefinition.label, report!!)
                }
            }
        }
    }
}

@Composable
fun AiSettingsScreen(apiService: FastApiService) {
    val coroutineScope = rememberCoroutineScope()
    var apiHealth by remember { mutableStateOf("Neverificat") }
    var dataHealth by remember { mutableStateOf("Neverificat") }
    var isLoading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Status Servicii AI", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("API Server (Railway): ", fontWeight = FontWeight.Bold)
                    Text(apiHealth, color = if(apiHealth == "Online") Color(0xFF4CAF50) else Color.Red)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sursă Date (Supabase): ", fontWeight = FontWeight.Bold)
                    Text(dataHealth, color = if(dataHealth == "Conectat la Supabase") Color(0xFF4CAF50) else Color.Red)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(onClick = {
            isLoading = true
            coroutineScope.launch {
                apiHealth = apiService.getHealthStatus()
                dataHealth = apiService.getDataStatus()
                isLoading = false
            }
        }, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("Verifică Status Acum")
        }
    }
}

@Composable
fun AiChatScreen(apiService: FastApiService) {
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Pair<String, Boolean>>() }
    var isLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f), state = listState) {
            items(messages) { msg ->
                ChatBubble(text = msg.first, isUser = msg.second)
            }
            if (isLoading) {
                item { CircularProgressIndicator(modifier = Modifier.padding(8.dp).size(24.dp)) }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Întreabă AI-ul...") },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val userMsg = inputText
                        messages.add(userMsg to true)
                        inputText = ""
                        isLoading = true
                        coroutineScope.launch {
                            val response = apiService.chatWithAi(userMsg)
                            messages.add(response to false)
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Trimite", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ChatBubble(text: String, isUser: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun HomeScreenContent(profile: UserProfile?, profileError: String?, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (profileError != null) {
            Icon(Icons.Default.Warning, contentDescription = "Eroare", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Eroare Profil", style = MaterialTheme.typography.titleMedium)
            Text(profileError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            Button(onClick = onLogout) { Text("Logout") }
        } else if (profile != null) {
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Salut, ${profile.username}!", style = MaterialTheme.typography.headlineSmall)
                    Text("Email: ${profile.email}", style = MaterialTheme.typography.bodyMedium)
                    Text("Dispozitive asociate: ${profile.owned_devices}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Acasă", style = MaterialTheme.typography.titleLarge)
            Text("Folosește meniul lateral pentru a vedea datele AI.", style = MaterialTheme.typography.bodyMedium)
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun DevicesListScreen(profile: UserProfile?, sessionManager: SessionManager, dbService: SupabaseService, onDeviceSelected: (Device) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var isDevicesLoading by remember { mutableStateOf(false) }
    var devicePendingRemoval by remember { mutableStateOf<Device?>(null) }
    var isRemovingDevice by remember { mutableStateOf(false) }
    var removalError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile) {
        if (profile != null) {
            isDevicesLoading = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val token = sessionManager.accessToken
                    val uid = sessionManager.userId
                    if (token != null && uid != null) {
                        val fetchedDevices = dbService.getMyDevices(token, uid)
                        withContext(Dispatchers.Main) { devices = fetchedDevices?.toList() ?: emptyList() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) { isDevicesLoading = false }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Dispozitivele Tale", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        removalError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        if (isDevicesLoading) {
            CircularProgressIndicator()
        } else if (devices.isEmpty()) {
            Text("Niciun dispozitiv adăugat.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(devices) { device ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onDeviceSelected(device) }) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = device.name ?: "Senzor", fontWeight = FontWeight.Bold)
                                Text(text = device.location ?: "Locație nesetată", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { devicePendingRemoval = device }) {
                                Icon(Icons.Default.Delete, contentDescription = "Elimină dispozitivul", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    devicePendingRemoval?.let { device ->
        AlertDialog(
            onDismissRequest = { if (!isRemovingDevice) devicePendingRemoval = null },
            title = { Text("Elimină dispozitivul?") },
            text = { Text("${device.name ?: "Acest dispozitiv"} va fi eliminat din lista ta.") },
            confirmButton = {
                Button(
                    enabled = !isRemovingDevice,
                    onClick = {
                        isRemovingDevice = true
                        removalError = null
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val token = sessionManager.accessToken ?: error("Sesiunea utilizatorului lipsește.")
                                val userId = sessionManager.userId ?: error("Sesiunea utilizatorului lipsește.")
                                dbService.deleteDevice(token, userId, device.device_id)
                                withContext(Dispatchers.Main) {
                                    devices = devices.filterNot { it.device_id == device.device_id }
                                    devicePendingRemoval = null
                                }
                            } catch (exception: Exception) {
                                withContext(Dispatchers.Main) {
                                    removalError = "Dispozitivul nu a putut fi eliminat: ${exception.message}"
                                }
                            } finally {
                                withContext(Dispatchers.Main) { isRemovingDevice = false }
                            }
                        }
                    }
                ) {
                    if (isRemovingDevice) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Elimină")
                }
            },
            dismissButton = {
                TextButton(onClick = { devicePendingRemoval = null }, enabled = !isRemovingDevice) { Text("Anulare") }
            }
        )
    }
}

@Composable
fun DeviceSettingsDashboard(device: Device, sessionManager: SessionManager, dbService: SupabaseService, onOpenWifiSetup: () -> Unit, onOpenMeasurements: () -> Unit, onDeviceUpdated: (Device) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showLocationDialog by remember { mutableStateOf(false) }
    var newLocation by remember { mutableStateOf(device.location ?: "") }
    var isUpdating by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Setări Dispozitiv", style = MaterialTheme.typography.headlineMedium)
        Text("Nume: ${device.name}", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onOpenMeasurements, modifier = Modifier.fillMaxWidth()) { Text("Vezi Măsurători Live") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { showLocationDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Modifică Locația") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onOpenWifiSetup, modifier = Modifier.fillMaxWidth()) { Text("Configurare Wi-Fi") }
    }

    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Setează Locația") },
            text = { OutlinedTextField(value = newLocation, onValueChange = { newLocation = it }, label = { Text("Ex: Sufragerie") }) },
            confirmButton = {
                Button(onClick = {
                    isUpdating = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val token = sessionManager.accessToken
                            if (token != null) {
                                dbService.updateDeviceLocation(token, device.device_id, newLocation)
                                device.location = newLocation
                                withContext(Dispatchers.Main) {
                                    onDeviceUpdated(device)
                                    showLocationDialog = false
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show() }
                        } finally {
                            isUpdating = false
                        }
                    }
                }) { if (isUpdating) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Salvează") }
            },
            dismissButton = { TextButton(onClick = { showLocationDialog = false }) { Text("Anulare") } }
        )
    }
}

@Composable
fun DeviceMeasurementsScreen(device: Device, sessionManager: SessionManager, dbService: SupabaseService) {
    var measurements by remember { mutableStateOf<List<Measurement>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(device) {
        val token = sessionManager.accessToken
        if (token != null) {
            while (true) {
                withContext(Dispatchers.IO) {
                    try {
                        val result = dbService.getDeviceMeasurements(token, device.device_id)
                        withContext(Dispatchers.Main) {
                            measurements = result?.filter(::isPlausibleMeasurement) ?: emptyList()
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                delay(10000)
            }
        }
    }

    if (isLoading && measurements.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        val current = measurements.firstOrNull()
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            item {
                if (current != null) {
                    Text("Ultima citire: ${current.created_at}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    AirQualityGauge("CO₂", current.co2.toFloat(), "ppm", 1000f, 2000f, 3000f, "Optim", "Aerisește", "Periculos")
                    AirQualityGauge("PM1", current.pm1.toFloat(), "µg/m³", 10f, 25f, 100f, "Curat", "Moderat", "Poluat")
                    AirQualityGauge("PM2.5", current.pm25.toFloat(), "µg/m³", 12f, 35f, 100f, "Curat", "Moderat", "Poluat")
                    AirQualityGauge("PM10", current.pm10.toFloat(), "µg/m³", 45f, 100f, 200f, "Curat", "Moderat", "Poluat")
                    CustomGauge("Temperatură", current.temperatura, "°C", 40f, { value -> if (value < 18f) Color.Blue else if (value <= 25f) Color(0xFF2E7D32) else Color.Red }, { value -> if (value < 18f) "Rece" else if (value <= 25f) "Optim" else "Cald" })
                    CustomGauge("Umiditate", current.umiditate, "%", 100f, { value -> if (value in 40f..60f) Color(0xFF2E7D32) else Color(0xFFFFA000) }, { value -> if (value in 40f..60f) "Optimă" else "În afara intervalului" })
                    CustomGauge("Presiune", current.presiune, "hPa", 1100f, { value -> if (isNormalAtmosphericPressure(value)) Color(0xFF2E7D32) else Color(0xFFFFA000) }, { value -> if (isNormalAtmosphericPressure(value)) "Normală" else "În afara intervalului" })
                    CustomGauge("VOC", current.voc, "KOhm", 500f, { value -> if (value >= 100f) Color(0xFF2E7D32) else Color(0xFFFFA000) }, { value -> if (value >= 100f) "Nivel bun" else "Verifică aerisirea" })
                    CustomGauge("Lumină", current.lux, "lux", 1000f, { value -> if (value >= 100f) Color(0xFF2E7D32) else Color(0xFFFFA000) }, { value -> if (value >= 100f) "Iluminare bună" else "Lumină redusă" })
                    MeasurementCard("Măsurătoare curentă", current)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Istoric Recent", style = MaterialTheme.typography.titleMedium)
            }
            items(measurements.drop(1)) { m ->
                MeasurementCard("Citire: ${m.created_at}", m)
            }
        }
    }
}

private fun isPlausibleMeasurement(measurement: Measurement): Boolean =
    measurement.temperatura in -40f..85f &&
        measurement.umiditate in 0f..100f &&
        measurement.presiune in 300f..1100f &&
        measurement.voc >= 0f &&
        measurement.lux in 0f..200000f &&
        measurement.co2 in 400..10000 &&
        measurement.pm1 in 0..1000 &&
        measurement.pm25 in 0..1000 &&
        measurement.pm10 in 0..1000

    private fun isNormalAtmosphericPressure(value: Float): Boolean = value in 870f..1085f

private fun measurementRows(measurement: Measurement): List<Pair<String, Any?>> = listOf(
    "device_id" to measurement.device_id,
    "created_at" to measurement.created_at,
    "temperatura (°C)" to measurement.temperatura,
    "umiditate (%)" to measurement.umiditate,
    "presiune" to measurement.presiune,
    "voc (KOhm)" to measurement.voc,
    "lux" to measurement.lux,
    "co2 (ppm)" to measurement.co2,
    "pm1 (µg/m³)" to measurement.pm1,
    "pm25 (µg/m³)" to measurement.pm25,
    "pm10 (µg/m³)" to measurement.pm10
)

@Composable
fun MeasurementCard(title: String, measurement: Measurement) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            AiTable("Măsurătoare" to "Valoare", measurementRows(measurement))
        }
    }
}

private fun measurementInsights(measurement: Measurement): List<Pair<String, String>> = listOf(
    "CO₂" to when {
        measurement.co2 <= 1000 -> "Bun"
        measurement.co2 <= 2000 -> "Ridicat - aerisește"
        else -> "Foarte ridicat"
    },
    "PM1" to when {
        measurement.pm1 <= 10 -> "Curat"
        measurement.pm1 <= 25 -> "Moderat"
        else -> "Ridicat"
    },
    "PM2.5" to when {
        measurement.pm25 <= 12 -> "Curat"
        measurement.pm25 <= 35 -> "Moderat"
        else -> "Ridicat"
    },
    "PM10" to when {
        measurement.pm10 <= 45 -> "Curat"
        measurement.pm10 <= 100 -> "Moderat"
        else -> "Ridicat"
    },
    "Temperatură" to when {
        measurement.temperatura < 18 -> "Rece"
        measurement.temperatura <= 25 -> "Confortabil"
        else -> "Cald"
    },
    "Umiditate" to when {
        measurement.umiditate < 40 -> "Aer uscat"
        measurement.umiditate <= 60 -> "Optimă"
        else -> "Ridicată"
    },
    "Presiune" to if (isNormalAtmosphericPressure(measurement.presiune)) "Normală" else "În afara intervalului",
    "VOC" to if (measurement.voc >= 100f) "Bun" else "Aerisește",
    "Lumină" to if (measurement.lux >= 100f) "Adecvată" else "Redusă"
)

private fun overallAnalysisStatus(analysis: Map<String, Any>): String =
    analysis["prediction"]?.toString()
        ?: analysis["status"]?.toString()
        ?: analysis["label"]?.toString()
        ?: analysis["error"]?.toString()
        ?: "Status indisponibil"

@Composable
fun AiAnalysisCard(analysis: Map<String, Any>, measurement: Measurement) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("Rezultate AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(overallAnalysisStatus(analysis), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("Interpretarea indicatorilor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            AiTable(headers = "Indicator" to "Interpretare", rows = measurementInsights(measurement))
        }
    }
}

@Composable
fun AirQualityGauge(title: String, value: Float, unit: String, goodMax: Float, warningMax: Float, maxValue: Float, goodLabel: String, warningLabel: String, dangerLabel: String, reverseColors: Boolean = false) {
    val isGood = if (reverseColors) value > warningMax else value <= goodMax
    val isWarning = value > min(goodMax, warningMax) && value <= max(goodMax, warningMax)
    val currentColor = if (isGood) Color(0xFF4CAF50) else if (isWarning) Color(0xFFFFC107) else Color(0xFFF44336) 
    val progress = (value / maxValue).coerceIn(0f, 1f)
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (isGood) goodLabel else if (isWarning) warningLabel else dangerLabel, color = currentColor, fontWeight = FontWeight.Bold)
                Text("$value $unit")
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = currentColor)
        }
    }
}

@Composable
fun CustomGauge(title: String, value: Float, unit: String, maxValue: Float, colorLogic: (Float) -> Color, textLogic: (Float) -> String) {
    val progress = (value / maxValue).coerceIn(0f, 1f)
    val currentColor = colorLogic(value)
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(textLogic(value), color = currentColor, fontWeight = FontWeight.Bold)
                Text("$value $unit")
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = currentColor)
        }
    }
}

@Composable
fun DeviceWifiSetupScreen(targetDevice: Device) {
    val context = LocalContext.current
    val bluetoothAdapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    val wifiNetworks = remember { mutableStateListOf<String>() }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var discoveredTarget by remember { mutableStateOf<BluetoothDevice?>(null) }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Se caută dispozitivul selectat pentru configurare Wi-Fi.") }
    var wifiStatus by remember { mutableStateOf("Stare Wi-Fi neverificată") }
    var isChannelReady by remember { mutableStateOf(false) }
    var isWifiScanInProgress by remember { mutableStateOf(false) }
    var wifiStatusRefreshKey by remember { mutableIntStateOf(0) }
    var shouldStartDeviceDiscovery by remember { mutableStateOf(false) }
    val requiredPermissions = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            status = "Permisiuni acordate. Se caută dispozitivul selectat..."
            shouldStartDeviceDiscovery = true
        } else {
            status = "Sunt necesare permisiuni Bluetooth pentru configurare."
        }
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (bluetoothAdapter?.isEnabled == true) {
            status = "Bluetooth este activ. Se caută dispozitivul selectat..."
            shouldStartDeviceDiscovery = true
        } else {
            status = "Bluetooth trebuie activat pentru configurarea Wi-Fi."
        }
    }
    val manager = remember {
        BleProvisioningManager(context, object : ProvisioningCallback {
            override fun onDeviceFound(device: BluetoothDevice) {
                if (device.name == targetDevice.device_id || device.address == targetDevice.device_id) discoveredTarget = device
            }

            override fun onConnected() { status = "Conectat la ${selectedDevice?.name ?: selectedDevice?.address}." }
            override fun onProvisioningSuccess() {
                status = "Canalul de configurare este pregătit. Se verifică rețeaua Wi-Fi."
                isChannelReady = true
            }
            override fun onProvisioningResponse(message: String) {
                status = message
                when {
                    message.startsWith("CONNECTED|") -> {
                        val parts = message.split("|")
                        val network = parts.getOrNull(1).orEmpty()
                        val ipAddress = parts.getOrNull(2).orEmpty()
                        wifiStatus = "Conectat la $network${if (ipAddress.isNotBlank()) " ($ipAddress)" else ""}"
                    }
                    message == "DISCONNECTED" -> wifiStatus = "Dispozitivul nu este conectat la Wi-Fi"
                    message == "WIFI_OK" -> {
                        wifiStatus = "Conectare Wi-Fi reușită. Se actualizează statusul..."
                        wifiStatusRefreshKey++
                    }
                    message == "WIFI_FAIL" -> wifiStatus = "Conectarea la rețeaua selectată a eșuat"
                }
                if (isWifiScanInProgress && message !in setOf("WIFI_OK", "WIFI_FAIL", "DISCONNECTED") && !message.startsWith("CONNECTED|")) {
                    wifiNetworks.clear()
                    message.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach { network ->
                        if (network !in wifiNetworks) wifiNetworks.add(network)
                    }
                    isWifiScanInProgress = false
                    status = if (wifiNetworks.isEmpty()) "ESP nu a găsit rețele Wi-Fi." else "ESP a găsit ${wifiNetworks.size} rețele Wi-Fi."
                }
            }
            override fun onDisconnected() { status = "Conexiunea cu placa a fost închisă." }
        })
    }

    DisposableEffect(manager) { onDispose { manager.disconnect() } }
    LaunchedEffect(discoveredTarget) {
        discoveredTarget?.let { device ->
            selectedDevice = device
            manager.stopScan()
            status = "Se conectează la dispozitivul selectat..."
            manager.connect(context, device)
        }
    }
    LaunchedEffect(shouldStartDeviceDiscovery) {
        if (shouldStartDeviceDiscovery) {
            manager.startScan()
        }
    }
    LaunchedEffect(Unit) {
        when {
            bluetoothAdapter == null -> status = "Bluetooth LE nu este disponibil pe acest telefon."
            !bluetoothAdapter.isEnabled -> {
                status = "Activează Bluetooth pentru configurarea Wi-Fi."
                bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            requiredPermissions.all { context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED } -> {
                shouldStartDeviceDiscovery = true
            }
            else -> {
                permissionLauncher.launch(requiredPermissions)
            }
        }
    }
    LaunchedEffect(isChannelReady, wifiStatusRefreshKey) {
        if (isChannelReady) manager.requestWifiStatus()
    }

    fun hasPermissions() = requiredPermissions.all {
        context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Configurare Wi-Fi", style = MaterialTheme.typography.headlineMedium)
            Text(targetDevice.name ?: "Dispozitiv selectat", style = MaterialTheme.typography.bodyMedium)
            Text(wifiStatus, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        item {
            HorizontalDivider()
            Text("Rețele Wi-Fi detectate de placă", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = {
                    wifiNetworks.clear()
                    ssid = ""
                    password = ""
                    showPasswordDialog = false
                    isWifiScanInProgress = manager.requestWifiNetworks()
                    status = if (isWifiScanInProgress) "ESP scanează rețelele Wi-Fi din jur..." else "Scanarea Wi-Fi nu a putut fi pornită."
                },
                enabled = isChannelReady && !isWifiScanInProgress,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Scanează rețele Wi-Fi din jur") }
            wifiNetworks.forEach { network ->
                FilterChip(
                    selected = ssid == network,
                    onClick = {
                        ssid = network
                        password = ""
                        isPasswordVisible = false
                        showPasswordDialog = true
                    },
                    label = { Text(network) }
                )
            }
            if (wifiNetworks.isEmpty() && !isWifiScanInProgress) {
                Text("Apasă scanare pentru a vedea rețelele disponibile în zona plăcii.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Conectare la $ssid") },
            text = {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Parolă Wi-Fi") },
                    visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "Ascunde parola" else "Arată parola"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    status = if (manager.sendWifiCredentials(ssid, password)) {
                        "Credențialele au fost trimise. ESP încearcă să se conecteze..."
                    } else {
                        "Credențialele nu au putut fi trimise."
                    }
                    showPasswordDialog = false
                }) { Text("Conectează") }
            },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }) { Text("Anulare") } }
        )
    }
}

@Composable
fun AddNewDeviceScreen(sessionManager: SessionManager, dbService: SupabaseService, onSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val devices = remember { mutableStateListOf<BluetoothDevice>() }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var isChannelReady by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var pairingState by remember { mutableStateOf("IDLE") }
    var pairingPin by remember { mutableStateOf("") }
    var enteredPin by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Pornește Bluetooth și caută dispozitivul ESP.") }
    val bluetoothAdapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    val requiredPermissions = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        status = if (permissions.values.all { it }) "Permisiuni acordate. Apasă Caută dispozitive." else "Sunt necesare permisiuni Bluetooth pentru scanare."
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        status = if (bluetoothAdapter?.isEnabled == true) "Bluetooth este activ. Apasă Caută dispozitive." else "Bluetooth trebuie activat pentru a continua."
    }
    val manager = remember {
        BleProvisioningManager(context, object : ProvisioningCallback {
            override fun onDeviceFound(device: BluetoothDevice) {
                if (devices.none { it.address == device.address }) devices.add(device)
            }

            override fun onConnected() { status = "Conectat la ${selectedDevice?.name ?: selectedDevice?.address}. Se verifică serviciile GATT..." }
            override fun onProvisioningSuccess() {
                isChannelReady = true
                pairingState = "READY"
                status = "Dispozitiv conectat. Apasă Pair pentru a trimite codul PIN."
            }
            override fun onProvisioningResponse(message: String) {
                when {
                    message.startsWith("PAIR_CONFIRMED") -> {
                        pairingState = "CONFIRMED"
                        status = "ESP a confirmat asocierea. Se salvează dispozitivul..."
                    }
                    message.startsWith("PAIR_REJECTED") -> {
                        pairingState = "READY"
                        pairingPin = ""
                        enteredPin = ""
                        status = "ESP a respins cererea de asociere."
                    }
                    else -> status = message
                }
            }
            override fun onDisconnected() {
                isChannelReady = false
                pairingState = "IDLE"
                status = "Conexiunea Bluetooth a fost închisă."
            }
        })
    }
    DisposableEffect(manager) { onDispose { manager.disconnect() } }
    LaunchedEffect(pairingState) {
        val device = selectedDevice
        if (pairingState == "CONFIRMED" && device != null && !isSaving) {
            isSaving = true
            try {
                val token = sessionManager.accessToken ?: error("Sesiunea utilizatorului lipsește.")
                val userId = sessionManager.userId ?: error("Sesiunea utilizatorului lipsește.")
                withContext(Dispatchers.IO) {
                    dbService.claimDevice(token, userId, device.name ?: device.address, device.name ?: "ESP32 ${device.address}")
                }
                status = "Dispozitiv asociat și adăugat la dispozitivele tale."
                onSuccess()
            } catch (exception: Exception) {
                pairingState = "READY"
                status = "ESP este asociat, dar salvarea în Supabase a eșuat: ${exception.message}"
                isSaving = false
            }
        }
    }

    fun hasPermissions() = requiredPermissions.all {
        context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Adaugă dispozitiv", style = MaterialTheme.typography.headlineMedium)
            Text("Caută dispozitive ESP32 NimBLE din apropiere.", style = MaterialTheme.typography.bodyMedium)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Button(onClick = {
                val adapter = bluetoothAdapter
                when {
                    adapter == null -> status = "Bluetooth LE nu este disponibil pe acest telefon."
                    !adapter.isEnabled -> bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    !hasPermissions() -> permissionLauncher.launch(requiredPermissions)
                    else -> {
                        devices.clear()
                        selectedDevice = null
                        isChannelReady = false
                        pairingState = "IDLE"
                        pairingPin = ""
                        enteredPin = ""
                        status = "Se caută dispozitive ESP32 NimBLE timp de 10 secunde..."
                        manager.startScan()
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Caută dispozitive Bluetooth") }
        }
        if (devices.isEmpty()) {
            item { Text("Nu a fost găsit încă niciun dispozitiv ESP compatibil.", style = MaterialTheme.typography.bodySmall) }
        }
        items(devices, key = { it.address }) { device ->
            ListItem(
                headlineContent = { Text(device.name ?: "ESP32 NimBLE") },
                supportingContent = { Text(device.address) },
                colors = ListItemDefaults.colors(
                    containerColor = if (selectedDevice?.address == device.address) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                ),
                modifier = Modifier.clickable {
                    selectedDevice = device
                    isChannelReady = false
                    pairingState = "IDLE"
                    pairingPin = ""
                    enteredPin = ""
                    status = "Se conectează la ${device.name ?: device.address}..."
                    manager.connect(context, device)
                }
            )
        }
        item {
            if (pairingState == "READY") {
                Button(
                    onClick = {
                        pairingPin = kotlin.random.Random.nextInt(100000, 1_000_000).toString()
                        status = if (manager.sendPairingCode(pairingPin)) {
                            pairingState = "CODE_SENT"
                            "Codul PIN a fost trimis. Verifică ecranul dispozitivului ESP."
                        } else {
                            pairingPin = ""
                            "Codul PIN nu a putut fi trimis către ESP."
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Pair dispozitiv") }
            }
            if (pairingState == "CODE_SENT") {
                Text("Introdu codul de 6 cifre afișat pe ecranul dispozitivului.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = { enteredPin = it.filter(Char::isDigit).take(6) },
                    label = { Text("PIN de pe dispozitiv") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (enteredPin != pairingPin) {
                            status = "PIN-ul introdus nu corespunde codului trimis către ESP."
                            return@Button
                        }
                        pairingState = "CONFIRMING"
                        if (!manager.confirmPairing(pairingPin)) {
                            pairingState = "CODE_SENT"
                            status = "Confirmarea PIN-ului nu a putut fi trimisă către ESP."
                        } else {
                            pairingState = "CONFIRMED"
                            status = "PIN confirmat. Se salvează dispozitivul..."
                        }
                    },
                    enabled = enteredPin.length == 6,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Confirmă PIN-ul") }
            } else if (pairingState == "CONFIRMING" || isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun AuthScreen(onLoginSuccess: (UserProfile?, String?) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authClient = remember { SupabaseAuthClient() }
    val dbService = remember { SupabaseService() }
    val sessionManager = remember { SessionManager(context) }
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text = if (isLoginMode) "Logare" else "Cont Nou", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Parolă") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))
        Button(enabled = !isLoading, onClick = {
            isLoading = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    if (isLoginMode) {
                        val response = authClient.login(email, password)
                        val token = response.get("access_token").asString
                        val uid = response.getAsJsonObject("user").get("id").asString
                        sessionManager.saveSession(token, "", uid)
                        val profile = dbService.getUserProfile(token, uid)
                        withContext(Dispatchers.Main) { onLoginSuccess(profile, null) }
                    } else {
                        authClient.signUp(email, password)
                        withContext(Dispatchers.Main) { Toast.makeText(context, "Verifică emailul!", Toast.LENGTH_LONG).show() }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Eroare: ${e.message}", Toast.LENGTH_LONG).show() }
                } finally { withContext(Dispatchers.Main) { isLoading = false } }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("Continuă")
        }
        TextButton(onClick = { isLoginMode = !isLoginMode }) { Text(if (isLoginMode) "Creează cont" else "Am deja cont") }
    }
}
