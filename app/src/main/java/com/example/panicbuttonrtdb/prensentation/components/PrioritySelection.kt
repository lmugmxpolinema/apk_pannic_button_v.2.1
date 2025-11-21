package com.example.panicbuttonrtdb.prensentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.panicbuttonrtdb.R

@Composable
fun PriorityButton(
    modifier: Modifier = Modifier,
    onPrioritySelected: (String, String) -> Unit   // priority + level
) {
    var selectedPriority by remember { mutableStateOf("Darurat") }

    fun getLevel(priority: String): String {
        return priority.lowercase()   // contoh: Darurat -> darurat
    }

    Column(
        modifier.fillMaxWidth()
    ) {
        Text(
            "Pilih Prioritas",
            color = colorResource(id = R.color.font2),
            modifier = Modifier.padding(bottom = 4.dp, top = 4.dp)
        )

        Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // --- DARURAT ---
            if (selectedPriority == "Darurat") {
                Button(
                    onClick = {
                        selectedPriority = "Darurat"
                        onPrioritySelected("Darurat", getLevel("Darurat"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.darurat)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Darurat", color = Color.White, fontSize = 13.sp)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        selectedPriority = "Darurat"
                        onPrioritySelected("Darurat", getLevel("Darurat"))
                    },
                    border = BorderStroke(1.dp, colorResource(id = R.color.darurat)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Darurat", color = colorResource(id = R.color.font3), fontSize = 13.sp)
                }
            }

            // --- PENTING ---
            if (selectedPriority == "Penting") {
                Button(
                    onClick = {
                        selectedPriority = "Penting"
                        onPrioritySelected("Penting", getLevel("Penting"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.penting)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Penting", color = Color.White, fontSize = 13.sp)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        selectedPriority = "Penting"
                        onPrioritySelected("Penting", getLevel("Penting"))
                    },
                    border = BorderStroke(1.dp, colorResource(id = R.color.penting)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Penting", color = colorResource(id = R.color.font3), fontSize = 13.sp)
                }
            }

            // --- BIASA ---
            if (selectedPriority == "Biasa") {
                Button(
                    onClick = {
                        selectedPriority = "Biasa"
                        onPrioritySelected("Biasa", getLevel("Biasa"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.biasa)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Biasa", color = Color.White, fontSize = 13.sp)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        selectedPriority = "Biasa"
                        onPrioritySelected("Biasa", getLevel("Biasa"))
                    },
                    border = BorderStroke(1.dp, colorResource(id = R.color.biasa)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Biasa", color = colorResource(id = R.color.font3), fontSize = 13.sp)
                }
            }
        }
    }
}
