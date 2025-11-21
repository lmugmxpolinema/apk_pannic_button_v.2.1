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
    private val databaseRef = FirebaseDatabase.getInstance().getReference("/buzzer")
    private val databaseRef2 = FirebaseDatabase.getInstance().getReference("/buzzer_priority")
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

    fun isUserLoggedIn(): Boolean {
        return userPreferences.isUserLoggedIn()
    }

    fun isAdminLoggedIn(): Boolean {
        return userPreferences.isAdminLoggedIn()
    }

    // LiveData untuk memantau status buzzer
    private val _buzzerState = MutableLiveData<String>()
    val buzzerState: LiveData<String> = _buzzerState

    init {
        // Ambil data pengguna yang tersimpan saat aplikasi dibuka kembali
        currentUserName = userPreferences.getUserName() ?: ""
        currentUserHouseNumber = userPreferences.getHouseNumber() ?: ""
    }

    init {
        fetchLatestRecord()
    }

    init {
        // Inisialisasi untuk mendapatkan status awal buzzer dari Firebase
        getBuzzerState()
    }

    // Fungsi untuk menyimpan user baru ke Firebase
    fun saveUserToFirebase(
        name: String,
        houseNumber: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,  // Tambahkan callback untuk error
        perumahanId: String? = null

    ) {

        // Periksa apakah sudah ada pengguna dengan nomor rumah atau nama yang sama
        // Use the appropriate users reference depending on perumahanId
        val targetRef = if (!perumahanId.isNullOrEmpty()) {
            database.getReference("perumahan").child(perumahanId).child("users")
        } else {
            usersRef
        }

        targetRef.orderByChild("houseNumber").equalTo(houseNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Jika nomor rumah sudah ada
                        onFailure("Nomor rumah sudah digunakan.")
                        Toast.makeText(context, "Nomor rumah sudah digunakan", Toast.LENGTH_SHORT).show()
                    } else {
                        // Lakukan pengecekan untuk nama
                        targetRef.orderByChild("name").equalTo(name)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.exists()) {
                                        // Jika nama sudah ada
                                        onFailure("Nama sudah digunakan.")
                                        Toast.makeText(context, "Nama sudah digunakan", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // Jika tidak ada duplikasi, simpan data pengguna
                                        val userId = targetRef.push().key // Buat key unik untuk user baru
                                        val user = User(name, houseNumber, password)

                                        userId?.let {
                                            targetRef.child(it).setValue(user)
                                                .addOnCompleteListener { task ->
                                                    if (task.isSuccessful) {
                                                        onSuccess() // Data berhasil disimpan
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

    // Fungsi untuk validasi login
    // Kotlin
// File: `app/src/main/java/com/example/panicbuttonrtdb/viewmodel/ViewModel.kt`
// Replace the existing validateLogin function with this implementation.

    fun validateLogin(houseNumber: String, password: String, onResult: (Boolean, Boolean) -> Unit) {
        // Admin credentials (unchanged)
        val adminHouseNumber = "admin"
        val adminPassword = "admin"

        if (houseNumber == adminHouseNumber && password == adminPassword) {
            userPreferences.saveAdminLoggedIn(true)
            onResult(true, true)
            return
        }

        // Get selected perumahan id from preferences
        val perumahanId = userPreferences.getPerumahanId() ?: ""
        if (perumahanId.isEmpty()) {
            onResult(false, false)
            return
        }

        // Path sesuai struktur kamu:
        // perumahan/{perumahanId}/users
        val usersRef = database.getReference("perumahan")
            .child(perumahanId)
            .child("users")

        // Cari user berdasarkan houseNumber
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

    // Fungsi untuk logout
    fun logout() {
        // Menghapus status login dari SharedPreferences
        userPreferences.saveUserLoggedIn(false)
        currentUserName = "" // Reset nama pengguna saat logout
        currentUserHouseNumber = "" // Reset nomor rumah saat logout
    }

    fun adminLogout() {
        userPreferences.saveAdminLoggedIn(false)
        userPreferences.clearUserInfo()
    }

    // Fungsi untuk menyimpan data ke path /monitor di Firebase
    fun saveMonitorData(
        message: String,
        priority: String,
        status: String,
        latitude: Double,   // <-- Tambahkan parameter ini
        longitude: Double   // <-- Tambahkan parameter ini
    ) {

        val data = mapOf(
            "name" to currentUserName,
            "houseNumber" to currentUserHouseNumber,
            "message" to message,
            "priority" to priority,
            "status" to status,
            "time" to getCurrentTimestampFormatted(), // Waktu saat toggle diaktifkan
            "latitude" to latitude,      // Tambahkan ini
            "longitude" to longitude     // Tambahkan ini
        )

        // Simpan data ke Firebase
        monitorRef.push().setValue(data)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Firebase", "Data berhasil disimpan ke /monitor")
                } else {
                    Log.e("Firebase", "Gagal menyimpan data", task.exception)
                }
            }
    }

    // Fungsi untuk mendapatkan status buzzer dari Firebase
    private fun getBuzzerState() {
        // Prefer perumahan-based buzzer state if available
        val perumahanId = userPreferences.getPerumahanId() ?: ""
        if (perumahanId.isNotEmpty()) {
            val perumahanBuzzerRef = database.getReference("perumahan")
                .child(perumahanId)
                .child("buzzers")
                .child("main")

            perumahanBuzzerRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Expect snapshot to be a map with keys "state" and "level" or a single value
                    val state = snapshot.child("state").getValue(String::class.java)
                    _buzzerState.value = state ?: "Off"
                }

                override fun onCancelled(error: DatabaseError) {
                    // Fallback to global buzzer
                    databaseRef.addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            _buzzerState.value = snapshot.getValue(String::class.java) ?: "Off"
                        }

                        override fun onCancelled(error: DatabaseError) {
                            // Handle error
                        }
                    })
                }
            })
        } else {
            // fallback to global /buzzer
            databaseRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _buzzerState.value = snapshot.getValue(String::class.java) ?: "Off"
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error, bisa diisi log jika dibutuhkan
                }
            })
        }
    }

    // Fungsi untuk mengatur status buzzer di Firebase
    fun setBuzzerState(state: String) {
        databaseRef.setValue(state)
    }

    //update buzzer
    fun updateBuzzerState(isOn: Boolean, priority: String? = null, level: String? = null) {
        if (isOn && priority != null && level != null) {

            val data = mapOf(
                "priority" to priority,
                "level" to level
            )

            databaseRef2.setValue(data)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("UpdateBuzzerState", "Buzzer updated: $data")
                    } else {
                        Log.e("UpdateBuzzerState", "Failed to update buzzer", task.exception)
                    }
                }

        } else {
            // Toggle off
            databaseRef2.setValue(
                mapOf(
                    "priority" to "off",
                    "level" to "off"
                )
            ).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("UpdateBuzzerState", "Buzzer reset (priority & level = off)")
                } else {
                    Log.e("UpdateBuzzerState", "Failed to reset buzzer", task.exception)
                }
            }
        }
    }

    fun updateAlarmForPerumahan(perumahanId: String, state: String, level: String) {

        val baseRef = FirebaseDatabase.getInstance()
            .getReference("perumahan")
            .child(perumahanId)
            .child("buzzers")
            .child("main")

        val data = mapOf(
            "state" to state,   // on/off
            "level" to level    // biasa/penting/darurat
        )

        baseRef.setValue(data)
            .addOnSuccessListener {
                Log.d("Firebase", "Alarm updated: $data")
            }
            .addOnFailureListener {
                Log.e("Firebase", "ERROR update alarm: ${it.message}")
            }
    }

    fun fetchMonitorData() {
        monitorRef.addValueEventListener(object : ValueEventListener {
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
        monitorRef.orderByKey().limitToLast(1)
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
                    // Handle error
                }
            })
    }


    // fun menampilkan 3 data
    fun latestMonitorItem() {
        monitorRef.orderByKey().limitToLast(3) // take 3 data terbaru
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val records = mutableListOf<MonitorRecord>()

                    for (recordSnapshot in snapshot.children.reversed()) {
                        val record = recordSnapshot.getValue(MonitorRecord::class.java)
                        record?.let { records.add(it)

                        }

                        _monitorData.value = records
                    }

                    _monitorData.value = records // take 3 data
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Failed to fetch monitor data", error.toException())
                }
            })
    }


    // Fungsi untuk menampilkan detail rekap berdasarkan nomor rumah
    fun detailRekap(houseNumber: String) {
        monitorRef.orderByChild("houseNumber").equalTo(houseNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val records = snapshot.children.reversed().mapNotNull { recordSnapshot -> //take data
                        recordSnapshot.getValue(MonitorRecord::class.java)?.copy(id = recordSnapshot.key ?: "") //menetapkan id = key
                    }
                    (records.isNotEmpty()) //check apakah records kosong
                    _monitorData.value = records //jika records tdk kosong update _monitorData
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("detailRekap", "gagal mengambil data", error.toException())
                }
            })
    }

    //fun utk menampilkan riwayat user berdasarkan houseNumber
    fun userHistory() {
        monitorRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val records = mutableListOf<MonitorRecord>()

                for (recordSnapshot in snapshot.children.reversed()){ //reversed utk mengurutkan data dari yang terbaru
                    val record = recordSnapshot.getValue(MonitorRecord::class.java)
                    if (record?.houseNumber == currentUserHouseNumber) {
                        records.add(record)
                    }
                }
                _monitorData.value = records //hanya utk data yang sesuai dengan houseNumber
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase","Gagal mengambil data", error.toException())
            }
        })
    }

    //fun upload foto ke storage
    fun uploadImage(imageUri: Uri, houseNumber: String, imageType: String, context: Context) {
        val imageRef = storage.child("${imageType}/$houseNumber.jpg")

        imageRef.putFile(imageUri)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    saveImagePathToDatabase(uri.toString(), houseNumber, imageType, context)
                }
            }
    }

    //funtion utk simpan path foto ke database
    private fun saveImagePathToDatabase(imageUri: String, houseNumber: String, imageType: String, context: Context) {

        usersRef.orderByChild("houseNumber").equalTo(houseNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (userSnapshot in snapshot.children) {
                            userSnapshot.ref.child(imageType).setValue(imageUri)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "$imageType berhasil di diperbaharui. Tunggu beberapa saat", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    //fun update status pesan
    fun updateStatus(recordId: String) {
        monitorRef.child(recordId).child("status").setValue("Selesai") //update data di status

    }

    // fun utk save no hp & note user
    fun savePhoneNumberAndNote(houseNumber: String, phoneNumber: String, note: String) {
        usersRef.orderByChild("houseNumber").equalTo(houseNumber)
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

    //fun utk fetch no hp dan note user
    fun fetchUserData(houseNumber: String) {
        usersRef.orderByChild("houseNumber").equalTo(houseNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val userSnapshot = snapshot.children.first()
                        val phoneNumber = userSnapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                        val note = userSnapshot.child("note").getValue(String::class.java) ?: ""
                        _userData.postValue(
                            mapOf(
                                "phoneNumber" to phoneNumber,
                                "note" to note)
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

    fun getPerumahanId(): String {
        return userPreferences.getPerumahanId() ?: ""
    }
}