package com.example.panicbuttonrtdb.prensentation.screens

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.panicbuttonrtdb.R
import com.example.panicbuttonrtdb.prensentation.components.OutlinedTextFieldPassword
import com.example.panicbuttonrtdb.viewmodel.ViewModel
import com.example.panicbuttonrtdb.viewmodel.ViewModelFactory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import com.example.panicbuttonrtdb.data.FirebaseRepository
import com.example.panicbuttonrtdb.data.Perumahan
import com.example.panicbuttonrtdb.data.UserPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown

@Composable
fun LoginScreen(
    context: Context,
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: ViewModel = viewModel(factory = ViewModelFactory(LocalContext.current))
) {

    BackHandler {
        (context as? Activity)?.finish()
    }
    var houseNumber by remember { mutableStateOf("") }
    val (password, setPassword) = remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }  // Indikator loading

    Column(
        modifier
            .background(color = colorResource(id = R.color.primary))
            .fillMaxSize()
    ){
        Box(
            modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(top = 60.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "ic_logo",
                modifier = Modifier.size(160.dp)
            )
        }
        Box(
            modifier
                .background(color = colorResource(id = R.color.primary))
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier
                    .height(600.dp)
                    .background(
                        color = Color.White, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp)
            ){
                Column(
                    modifier
                        .padding(top = 40.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Login",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(id = R.color.font),

                        )

                    Spacer(modifier = Modifier.height(44.dp))

                    // --- DROPDOWN PERUMAHAN ---
                    val repo = remember { FirebaseRepository() }

                    var perumahanList by remember { mutableStateOf<List<Perumahan>>(emptyList()) }
                    var expanded by remember { mutableStateOf(false) }
                    var selectedName by remember { mutableStateOf("Pilih Perumahan") }
                    var selectedPerumahan by remember { mutableStateOf<Perumahan?>(null) }

                    // Load data perumahan
                    LaunchedEffect(Unit) {
                        repo.fetchPerumahanList(
                            onResult = { list ->
                                perumahanList = list
                            },
                            onError = {
                                // optional handle error
                            }
                        )
                    }

// ----------------- OUTLINED TEXT FIELD DROPDOWN -----------------
                    Box(modifier = Modifier.fillMaxWidth()) {

                        OutlinedTextField(
                            value = selectedName,
                            onValueChange = { },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = true },
                            enabled = false,
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_home),
                                    contentDescription = "ic home"
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "dropdown"
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = Color.Black,
                                disabledLeadingIconColor = colorResource(id = R.color.defauld),
                                disabledTrailingIconColor = colorResource(id = R.color.defauld),
                                disabledBorderColor = colorResource(id = R.color.defauld),
                            )
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                        ) {
                            perumahanList.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.nama) },
                                    onClick = {
                                        selectedPerumahan = item
                                        selectedName = item.nama
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = houseNumber,
                        onValueChange = {houseNumber = it},
                        label = { Text(text = "Nomor Rumah") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next
                        ),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_home),
                                contentDescription = "ic home",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorResource(id = R.color.font),
                            focusedLabelColor = colorResource(id = R.color.font),
                            focusedLeadingIconColor = colorResource(id = R.color.font),
                            unfocusedLeadingIconColor = colorResource(id = R.color.defauld),
                            cursorColor = colorResource(id = R.color.font)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextFieldPassword(password, setPassword)

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (selectedPerumahan == null) {
                                Toast.makeText(context, "Pilih perumahan terlebih dahulu", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (houseNumber.isNotEmpty() && password.isNotEmpty()) {

                                // Simpan perumahan
                                UserPreferences(context).savePerumahan(
                                    selectedPerumahan!!.id,
                                    selectedPerumahan!!.nama
                                )

                                isLoading = true

                                viewModel.validateLogin(houseNumber, password) { success, isAdmin ->
                                    isLoading = false
                                    if (success) {
                                        if (isAdmin) {
                                            navController.navigate("dashboard_admin")
                                        } else {
                                            navController.navigate("dashboard")
                                        }
                                    } else {
                                        Toast.makeText(context, "Nomor rumah atau Sandi salah", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context,"Mohon isi semua kolom", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                        ,
                        enabled = !isLoading, // Matikan tombol saat loading
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(id = R.color.font),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = "Login")
                    }

                    Spacer(modifier = Modifier.height(36.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Belum memiliki akun?"
                        )
                        Text(
                            modifier = Modifier
                                .clickable { navController.navigate("signup") },
                            text = "Daftar",
                            fontWeight = FontWeight.Bold,
                            color = colorResource(id = R.color.font)
                        )
                    }
                }
            }
        }
    }
}
