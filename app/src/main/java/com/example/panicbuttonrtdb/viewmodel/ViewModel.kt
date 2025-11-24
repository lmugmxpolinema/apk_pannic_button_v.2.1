package com.example.panicbuttonrtdb.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.panicbuttonrtdb.data.MonitorRecord
import com.example.panicbuttonrtdb.data.User
import com.example.panicbuttonrtdb.data.UserPreferences
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

open class ViewModel(private val context: Context) : ViewModel() {

    private val database = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance().reference
    private val userPreferences = UserPreferences(context)
    private val monitorRef = database.getReference("monitor")
    private val usersRef = database.getReference("users")

    var currentUserName by mutableStateOf("")
    var currentUserHouseNumber by mutableStateOf("")

    private val _monitorData = MutableLiveData<List<MonitorRecord>>()
    val monitorData: LiveData<List<MonitorRecord>> get() = _monitorData

    private val _userData = MutableLiveData<Map<String, String>>()
    val userData: LiveData<Map<String, String>> get() = _userData

    private val _latestRecord = MutableStateFlow(MonitorRecord())
    val latestRecord: StateFlow<MonitorRecord> = _latestRecord

    // FIX: LiveData untuk memantau status buzzer dari path ESP8266 yang benar
    private val _buzzerState = MutableLiveData<String>()
    val buzzerState: LiveData<String> = _buzzerState

    init {
        // Ambil data pengguna yang tersimpan saat aplikasi dibuka kembali
        currentUserName = userPreferences.getUserName() ?: ""
        currentUserHouseNumber = userPreferences.getHouseNumber() ?: ""

        fetchLatestRecord()

        // FIX: Inisialisasi listener buzzer state dari path ESP8266
        getBuzzerState()
    }

    fun isUserLoggedIn(): Boolean {
        return userPreferences.isUserLoggedIn()
    }

    fun isAdminLoggedIn(): Boolean {
        return userPreferences.isAdminLoggedIn()
    }

    // FIX: Simpan user ke struktur Firebase yang benar: /users/{uid}/...
    fun saveUserToFirebase(
        name: String,
        houseNumber: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        perumahanId: String? = null
    ) {
        // FIX: Gunakan path perumahan yang benar
        val targetRef = if (!perumahanId.isNullOrEmpty()) {
            database.getReference("perumahan").child(perumahanId).child("users")
        } else {
            usersRef
        }

        targetRef.orderByChild("houseNumber").equalTo(houseNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        onFailure("Nomor rumah sudah digunakan.")
                        Toast.makeText(context, "Nomor rumah sudah digunakan", Toast.LENGTH_SHORT).show()
                    } else {
                        targetRef.orderByChild("name").equalTo(name)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.exists()) {
                                        onFailure("Nama sudah digunakan.")
                                        Toast.makeText(context, "Nama sudah digunakan", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // FIX: Simpan dengan UID sebagai key
                                        val userId = targetRef.push().key
                                        val user = User(name, houseNumber, password)

                                        userId?.let {
                                            targetRef.child(it).setValue(user)
                                                .addOnCompleteListener { task ->
                                                    if (task.isSuccessful) {
                                                        onSuccess()
                                                        Toast.makeText(context, "Sign Up Berhasil", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        onFailure("Gagal menyimpan data.")
                                                    }
                                                }
                                        }
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    onFailure("Terjadi kesalahan: ${error.message}")
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    onFailure("Terjadi kesalahan: ${error.message}")
                }
            })
    }

    // FIX: Validasi login dengan path yang benar
    fun validateLogin(houseNumber: String, password: String, onResult: (Boolean, Boolean) -> Unit) {
        val adminHouseNumber = "admin"
        val adminPassword = "admin"

        if (houseNumber == adminHouseNumber && password == adminPassword) {
            userPreferences.saveAdminLoggedIn(true)
            onResult(true, true)
            return
        }

        val perumahanId = userPreferences.getPerumahanId() ?: ""
        if (perumahanId.isEmpty()) {
            onResult(false, false)
            return
        }

        // FIX: Path sesuai struktur: perumahan/{perumahanId}/users
        val usersRef = database.getReference("perumahan")
            .child(perumahanId)
            .child("users")

        usersRef.orderByChild("houseNumber").equalTo(houseNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        onResult(false, false)
                        return
                    }

                    for (child in snapshot.children) {
                        val user = child.getValue(User::class.java)

                        if (user != null && user.password == password) {
                            // FIX: Simpan user ID untuk tracking monitor data
                            val userId = child.key ?: ""

                            userPreferences.saveUserLoggedIn(true)
                            userPreferences.saveUserInfo(user.name, user.houseNumber)

                            currentUserName = user.name
                            currentUserHouseNumber = user.houseNumber

                            onResult(true, false)
                            return
                        }
                    }

                    onResult(false, false)
                }

                override fun onCancelled(error: DatabaseError) {
                    onResult(false, false)
                }
            })
    }

    fun logout() {
        userPreferences.saveUserLoggedIn(false)
        currentUserName = ""
        currentUserHouseNumber = ""
    }

    fun adminLogout() {
        userPreferences.saveAdminLoggedIn(false)
        userPreferences.clearUserInfo()
    }

    // FIX: Simpan monitor data dengan user yang BENAR dari session aktif
    // FIX: Path: /perumahan/{perumahanKey}/monitor/{pushId}
    fun saveMonitorData(
        message: String,
        priority: String,
        status: String,
        latitude: Double,
        longitude: Double
    ) {
        // FIX: SELALU ambil dari UserPreferences untuk menghindari stale data
        val latestName = userPreferences.getUserName() ?: ""
        val latestHouseNumber = userPreferences.getHouseNumber() ?: ""
        val perumahanId = getPerumahanId()

        // FIX: Update variabel instance juga
        currentUserName = latestName
        currentUserHouseNumber = latestHouseNumber

        if (perumahanId.isEmpty()) {
            Log.e("Firebase", "saveMonitorData: perumahanId kosong, tidak dapat menyimpan")
            return
        }

        val data = mapOf(
            "name" to latestName,
            "houseNumber" to latestHouseNumber,
            "message" to message,
            "priority" to priority,
            "status" to status,
            "time" to getCurrentTimestampFormatted(),
            "latitude" to latitude,
            "longitude" to longitude
        )

        // FIX: Simpan ke path perumahan yang benar
        val monitorRefPerumahan = database.getReference("perumahan")
            .child(perumahanId)
            .child("monitor")

        monitorRefPerumahan.push().setValue(data)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Firebase", "Data berhasil disimpan ke /perumahan/$perumahanId/monitor")
                } else {
                    Log.e("Firebase", "Gagal menyimpan data", task.exception)
                }
            }
    }

    // FIX: Baca buzzer state dari path ESP8266 yang BENAR
    // ESP membaca: /perumahan/{perumahanKey}/buzzers/main/state
    private fun getBuzzerState() {
        val perumahanId = getPerumahanId()
        if (perumahanId.isEmpty()) {
            _buzzerState.value = "off"
            Log.w("Firebase", "getBuzzerState: perumahanId kosong, set state = off")
            return
        }

        // FIX: Path EKSAK sesuai ESP8266
        val stateRef = database.getReference("perumahan")
            .child(perumahanId)
            .child("buzzers")
            .child("main")
            .child("state")

        stateRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.getValue(String::class.java) ?: "off"
                _buzzerState.value = state
                Log.d("Firebase", "buzzerState updated: $state")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "getBuzzerState cancelled: ${error.message}")
                _buzzerState.value = "off"
            }
        })
    }

    // FIX: SATU-SATUNYA fungsi untuk menulis buzzer data
    // ESP membaca dari: /perumahan/{perumahanKey}/buzzers/main/state dan /level
    // Format level HARUS: "biasa", "penting", "darurat", atau "off"
    fun updateAlarmForPerumahan(perumahanId: String, state: String, level: String) {
        if (perumahanId.isEmpty()) {
            Log.e("Firebase", "updateAlarmForPerumahan: perumahanId kosong")
            return
        }

        // FIX: Path EKSAK sesuai ESP8266
        val baseRef = database.getReference("perumahan")
            .child(perumahanId)
            .child("buzzers")
            .child("main")

        // FIX: Tulis state dan level sesuai format ESP
        val data = mapOf(
            "state" to state,
            "level" to level
        )

        baseRef.setValue(data)
            .addOnSuccessListener {
                Log.d("Firebase", "✓ Alarm updated to /perumahan/$perumahanId/buzzers/main: state=$state, level=$level")
                // FIX: Update LiveData langsung untuk sinkronisasi UI
                _buzzerState.value = state
            }
            .addOnFailureListener {
                Log.e("Firebase", "✗ ERROR update alarm: ${it.message}")
            }
    }

    // FIX: HAPUS fungsi setBuzzerState - tidak digunakan lagi
    // Semua panggilan harus ke updateAlarmForPerumahan

    // FIX: Refactor updateBuzzerState untuk HANYA memanggil updateAlarmForPerumahan
    fun updateBuzzerState(isOn: Boolean, priority: String? = null, level: String? = null) {
        val perumahanId = getPerumahanId()

        if (perumahanId.isEmpty()) {
            Log.e("Firebase", "updateBuzzerState: perumahanId kosong")
            return
        }

        if (!isOn) {
            // FIX: OFF harus menulis state="off" dan level="off"
            updateAlarmForPerumahan(
                perumahanId = perumahanId,
                state = "off",
                level = "off"
            )
            return
        }

        // FIX: ON - gunakan level yang diberikan, default "biasa"
        // Level HARUS lowercase: "biasa", "penting", "darurat"
        val finalLevel = (level ?: "biasa").lowercase()

        updateAlarmForPerumahan(
            perumahanId = perumahanId,
            state = "on",
            level = finalLevel
        )
    }

    fun fetchMonitorData() {
        // FIX: Baca dari path perumahan
        val perumahanId = getPerumahanId()
        if (perumahanId.isEmpty()) {
            _monitorData.value = emptyList()
            return
        }

        val monitorRefPerumahan = database.getReference("perumahan")
            .child(perumahanId)
            .child("monitor")

        monitorRefPerumahan.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val records = mutableListOf<MonitorRecord>()
                for (recordSnapshot in snapshot.children.reversed()) {
                    val record = recordSnapshot.getValue(MonitorRecord::class.java)
                    record?.let { records.add(it) }
                }
                _monitorData.value = records
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to fetch monitor data", error.toException())
            }
        })
    }

    fun fetchLatestRecord() {
        val perumahanId = getPerumahanId()
        if (perumahanId.isEmpty()) return

        val monitorRefPerumahan = database.getReference("perumahan")
            .child(perumahanId)
            .child("monitor")

        monitorRefPerumahan.orderByKey().limitToLast(1)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val data = snapshot.children.first().getValue(MonitorRecord::class.java)
                        data?.let {
                            viewModelScope.launch {
                                _latestRecord.emit(it)
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Failed to fetch latest record", error.toException())
                }
            })
    }

    fun latestMonitorItem() {
        val perumahanId = getPerumahanId()
        if (perumahanId.isEmpty()) {
            _monitorData.value = emptyList()
            return
        }

        val monitorRefPerumahan = database.getReference("perumahan")
            .child(perumahanId)
            .child("monitor")

        monitorRefPerumahan.orderByKey().limitToLast(3)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val records = mutableListOf<MonitorRecord>()
                    for (recordSnapshot in snapshot.children.reversed()) {
                        val record = recordSnapshot.getValue(MonitorRecord::class.java)
                        record?.let { records.add(it) }
                    }
                    _monitorData.value = records
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Failed to fetch monitor data", error.toException())
                }
            })
    }

    fun detailRekap(houseNumber: String) {
        val perumahanId = getPerumahanId()
        if (perumahanId.isEmpty()) {
            _monitorData.value = emptyList()
            return
        }

        val monitorRefPerumahan = database.getReference("perumahan")
            .child(perumahanId)
            .child("monitor")

        monitorRefPerumahan.orderByChild("houseNumber").equalTo(houseNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val records = snapshot.children.reversed().mapNotNull { recordSnapshot ->
                        recordSnapshot.getValue(MonitorRecord::class.java)?.copy(id = recordSnapshot.key ?: "")
                    }
                    _monitorData.value = records
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("detailRekap", "gagal mengambil data", error.toException())
                }
            })
    }

    fun userHistory() {
        val perumahanId = getPerumahanId()
        if (perumahanId.isEmpty()) {
            _monitorData.value = emptyList()
            return
        }

        val monitorRefPerumahan = database.getReference("perumahan")
            .child(perumahanId)
            .child("monitor")

        monitorRefPerumahan.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val records = mutableListOf<MonitorRecord>()
                // FIX: Gunakan nilai terbaru dari preferences
                val currentHouse = userPreferences.getHouseNumber() ?: ""

                for (recordSnapshot in snapshot.children.reversed()) {
                    val record = recordSnapshot.getValue(MonitorRecord::class.java)
                    if (record?.houseNumber == currentHouse) {
                        records.add(record)
                    }
                }
                _monitorData.value = records
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Gagal mengambil data", error.toException())
            }
        })
    }

    fun uploadImage(imageUri: Uri, houseNumber: String, imageType: String, context: Context) {
        val imageRef = storage.child("${imageType}/$houseNumber.jpg")

        imageRef.putFile(imageUri)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    saveImagePathToDatabase(uri.toString(), houseNumber, imageType, context)
                }
            }
    }

    private fun saveImagePathToDatabase(imageUri: String, houseNumber: String, imageType: String, context: Context) {
        val perumahanId = getPerumahanId()
        if (perumahanId.isEmpty()) return

        val usersRefPerumahan = database.getReference("perumahan")
            .child(perumahanId)
            .child("users")

        usersRefPerumahan.orderByChild("houseNumber").equalTo(houseNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (userSnapshot in snapshot.children) {
                            userSnapshot.ref.child(imageType).setValue(imageUri)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "$imageType berhasil diperbaharui. Tunggu beberapa saat", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    fun updateStatus(recordId: String) {
        val perumahanId = getPerumahanId()
        if (perumahanId.isEmpty()) return

        val monitorRefPerumahan = database.getReference("perumahan")
            .child(perumahanId)
            .child("monitor")

        monitorRefPerumahan.child(recordId).child("status").setValue("Selesai")
    }

    fun savePhoneNumberAndNote(houseNumber: String, phoneNumber: String, note: String) {
        val perumahanId = getPerumahanId()
        if (perumahanId.isEmpty()) return

        val usersRefPerumahan = database.getReference("perumahan")
            .child(perumahanId)
            .child("users")

        usersRefPerumahan.orderByChild("houseNumber").equalTo(houseNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (child in snapshot.children) {
                            child.ref.child("phoneNumber").setValue(phoneNumber)
                            child.ref.child("note").setValue(note)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Log.d("Firebase", "Data berhasil diperbarui untuk $houseNumber")
                                        Toast.makeText(context, "Keterangan berhasil simpan", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Log.e("Firebase", "Gagal memperbarui data: ${task.exception?.message}")
                                    }
                                }
                        }
                    } else {
                        Log.e("Firebase", "Data dengan houseNumber $houseNumber tidak ditemukan")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Error: ${error.message}")
                }
            })
    }

    fun fetchUserData(houseNumber: String) {
        val perumahanId = getPerumahanId()
        if (perumahanId.isEmpty()) return

        val usersRefPerumahan = database.getReference("perumahan")
            .child(perumahanId)
            .child("users")

        usersRefPerumahan.orderByChild("houseNumber").equalTo(houseNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val userSnapshot = snapshot.children.first()
                        val phoneNumber = userSnapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                        val note = userSnapshot.child("note").getValue(String::class.java) ?: ""
                        _userData.postValue(
                            mapOf(
                                "phoneNumber" to phoneNumber,
                                "note" to note
                            )
                        )
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("fetchUserData", "Gagal mengambil data: ${error.message}")
                }
            })
    }

    private fun getCurrentTimestampFormatted(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd 'waktu' HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    // FIX: Helper untuk mengambil perumahanId dari SharedPreferences
    fun getPerumahanId(): String {
        return userPreferences.getPerumahanId() ?: ""
    }
}