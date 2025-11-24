package com.example.panicbuttonrtdb.prensentation.components

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.panicbuttonrtdb.R
import com.example.panicbuttonrtdb.notification.sendNotification
import com.example.panicbuttonrtdb.viewmodel.ViewModel
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.panicbuttonrtdb.utils.getCurrentLocation

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToggleSwitch(
    viewModel: ViewModel,
    context: Context
) {
    var showDialog by remember { mutableStateOf(false) }
    var pendingToggleState by remember { mutableStateOf(false) }
    var selectedPriority by remember { mutableStateOf("") }
    // FIX: selectedLevel HARUS lowercase untuk ESP8266
    var selectedLevel by remember { mutableStateOf("") }
    val buzzerState by viewModel.buzzerState.observeAsState(initial = "off")
    var message by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // FIX: Launcher untuk meminta izin lokasi
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false)) {
                isLoading = true
                getCurrentLocation(context) { lat, lon ->
                    // FIX: Simpan monitor data dengan user dari session aktif
                    viewModel.saveMonitorData(
                        message = message,
                        priority = selectedPriority,
                        status = "Proses",
                        latitude = lat,
                        longitude = lon
                    )

                    val perumahanId = viewModel.getPerumahanId()

                    // FIX: Gunakan HANYA updateBuzzerState dengan level lowercase
                    viewModel.updateBuzzerState(
                        isOn = true,
                        priority = selectedPriority,
                        level = selectedLevel.lowercase()
                    )

                    sendNotification(
                        context,
                        "Panic Button",
                        "Buzzer telah diaktifkan dengan skala prioritas $selectedPriority"
                    )
                    showDialog = false
                    isLoading = false

                    // Reset nilai untuk penggunaan berikutnya
                    message = ""
                    selectedPriority = ""
                    selectedLevel = ""
                }
            } else {
                Toast.makeText(context, "Izin lokasi dibutuhkan untuk fitur ini", Toast.LENGTH_SHORT).show()
                isLoading = false
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Switch(
            checked = buzzerState == "on",
            onCheckedChange = { isChecked ->
                if (isChecked) {
                    // FIX: ON - tampilkan dialog konfirmasi
                    pendingToggleState = true
                    showDialog = true
                } else {
                    // FIX: OFF - langsung update Firebase dengan state=off dan level=off
                    viewModel.updateBuzzerState(isOn = false)
                    Log.d("ToggleSwitch", "Manual OFF: state=off, level=off")
                }
            },
            thumbContent = {
                if (buzzerState == "on") {
                    Icon(
                        painter = painterResource(id = R.drawable.onmode),
                        contentDescription = "on mode",
                        modifier = Modifier.padding(5.dp),
                        tint = Color.White
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.offmode),
                        contentDescription = "off mode",
                        modifier = Modifier
                            .padding(5.dp)
                            .size(24.dp),
                        tint = Color.White
                    )
                }
            },
            colors = SwitchDefaults.colors(
                checkedTrackColor = colorResource(id = R.color.pudar),
                uncheckedTrackColor = colorResource(id = R.color.merah_pudar),
                uncheckedBorderColor = colorResource(id = R.color.primary),
                checkedThumbColor = colorResource(id = R.color.biru),
                uncheckedThumbColor = colorResource(id = R.color.primary),
                checkedBorderColor = colorResource(id = R.color.biru)
            ),
            modifier = Modifier
                .scale(1.8f)
                .padding(20.dp)
        )
    }

    // FIX: Auto-OFF setelah 30 detik menggunakan fungsi OFF yang sama
    if (buzzerState == "on") {
        LaunchedEffect(key1 = buzzerState) {
            delay(30000)
            // FIX: Gunakan updateBuzzerState untuk konsistensi
            viewModel.updateBuzzerState(isOn = false)
            Log.d("ToggleSwitch", "Auto-OFF 30s: state=off, level=off")
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                // Reset saat dialog ditutup
                selectedPriority = ""
                selectedLevel = ""
                message = ""
                showError = false
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_warning),
                        contentDescription = "ic warning",
                        tint = colorResource(id = R.color.darurat)
                    )
                    Text(
                        text = "Konfirmasi",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorResource(id = R.color.primary)
                    )
                }
            },
            text = {
                val quickMessages = listOf(
                    "Kebakaran",
                    "Bantuan Medis",
                    "Kerumunan Tidak Wajar",
                    "Hewan Berbahaya",
                    "Tolong Segera Datang",
                    "Kemalingan"
                )
                Column {
                    Text(
                        "Tambahkan Pesan dan Prioritas",
                        color = colorResource(id = R.color.font2)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Quick Massage:",
                        fontSize = 12.sp,
                        color = colorResource(id = R.color.font2)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        quickMessages.forEach { quickMsg ->
                            Button(
                                onClick = { message = quickMsg },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorResource(id = R.color.background_button)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = quickMsg,
                                    fontSize = 11.sp,
                                    color = colorResource(id = R.color.font2)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = {
                            Text(
                                text = "Tambahkan Pesan",
                                color = colorResource(id = R.color.font2)
                            )
                        },
                        placeholder = {
                            Text(
                                "Opsional",
                                color = colorResource(id = R.color.font3)
                            )
                        },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = colorResource(id = R.color.font),
                            focusedLabelColor = colorResource(id = R.color.font),
                            cursorColor = colorResource(id = R.color.font)
                        )
                    )

                    // FIX: PriorityButton harus mengembalikan level lowercase
                    PriorityButton(
                        onPrioritySelected = { priority, level ->
                            selectedPriority = priority
                            // FIX: Pastikan level lowercase untuk ESP8266
                            selectedLevel = level.lowercase()
                            showError = false
                        }
                    )

                    if (showError) {
                        Text(
                            text = "Silahkan pilih tombol skala prioritas",
                            color = colorResource(id = R.color.darurat),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            containerColor = Color.White,
            confirmButton = {
                Button(
                    modifier = Modifier.width(130.dp),
                    onClick = {
                        if (selectedPriority.isEmpty() || selectedLevel.isEmpty()) {
                            showError = true
                        } else {
                            showError = false
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            vibrator.vibrate(
                                VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                            )

                            // FIX: Request permission, proses di callback
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(id = R.color.primary)
                    ),
                    enabled = !isLoading
                ) {
                    Text(
                        if (isLoading) "Mengirim..." else "Kirim",
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                Button(
                    modifier = Modifier.width(130.dp),
                    onClick = {
                        showDialog = false
                        selectedPriority = ""
                        selectedLevel = ""
                        message = ""
                        showError = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(id = R.color.background_button)
                    ),
                    enabled = !isLoading
                ) {
                    Text(
                        "Batal",
                        color = colorResource(id = R.color.font3)
                    )
                }
            }
        )
    }
}