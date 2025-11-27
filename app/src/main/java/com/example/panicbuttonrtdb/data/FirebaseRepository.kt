package com.example.panicbuttonrtdb.data

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class FirebaseRepository {

    private val dbRef = FirebaseDatabase.getInstance().getReference("perumahan")

    fun fetchPerumahanList(
        onResult: (List<Perumahan>) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ) {
        val daftarRef = FirebaseDatabase.getInstance().getReference("daftar_perumahan")

        daftarRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Perumahan>()

                for (child in snapshot.children) {
                    val id = child.key ?: ""
                    val nama = child.getValue(String::class.java) ?: "Tanpa Nama"

                    list.add(Perumahan(id, nama))
                }

                onResult(list)
            }

            override fun onCancelled(error: DatabaseError) {
                onError?.invoke(error.toException())
            }
        })
    }
}
