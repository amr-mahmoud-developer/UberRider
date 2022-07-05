package com.example.uberrider

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.uberrider.Model.RiderInfoModel
import com.example.uberrider.databinding.ActivitySplashScreenBinding
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import java.util.*
import kotlin.concurrent.schedule


class SplashScreenActivity : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    private lateinit var riderRef: DatabaseReference
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener

    // register for sign-in activity
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { res ->
        onSignInResult(res)
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            // Successfully signed in
            Toast.makeText(this, "login successful", Toast.LENGTH_LONG).show()
        } else {
            // Sign in failed. If response is null the user canceled the
            // sign-in flow using the back button. Otherwise check
            // response.getError().getErrorCode() and handle the error.
            return
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        init()
        //delay for 3 seconds
        Timer().schedule(3000) {
            firebaseAuth.addAuthStateListener(listener)
        }
    }

    private fun init() {
        database = FirebaseDatabase.getInstance()
        riderRef = database.getReference(Common.riderRef)
        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener {
            if (it.currentUser != null) {
                Common.riderKey = it.currentUser!!.uid
                // get token and store it in realtime database
                FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        return@OnCompleteListener
                    }

                    // store token key in realtime database
                    FirebaseDatabase.getInstance().reference.child(Common.TokenRef)
                        .child(Common.riderKey + "/Token")
                        .setValue(task.result)

                })
                checkUserFromFirebase()

            } else {
                createSignInIntent()
            }
        }
    }

    private fun checkUserFromFirebase() {


        riderRef.child(Common.riderKey)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val model = snapshot.getValue(RiderInfoModel::class.java)
                        goToHomeActivity(model!!)
                    } else {
                        showRegisterLayout()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SplashScreenActivity, error.message, Toast.LENGTH_LONG)
                        .show()
                }

            })
    }

    private fun showRegisterLayout() {

        val builder = AlertDialog.Builder(this, R.style.dialogTheme)

        val itemView = LayoutInflater.from(this).inflate(R.layout.layout_register, null)

        val edt_first_name = itemView.findViewById<TextInputEditText>(R.id.edt_first_name)
        val edt_last_name = itemView.findViewById<TextInputEditText>(R.id.edt_last_name)
        val edt_phone_number = itemView.findViewById<TextInputEditText>(R.id.edit_phone_number)
        val btn_continue = itemView.findViewById<Button>(R.id.btn_register)

        edt_phone_number.setText(firebaseAuth.currentUser!!.phoneNumber)

        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()

        btn_continue.setOnClickListener {
            val model = RiderInfoModel()
            model.firstName = edt_first_name.text.toString()
            model.lastName = edt_last_name.text.toString()
            model.phoneNumber = edt_phone_number.text.toString()


            riderRef.child(Common.riderKey).setValue(model)
                .addOnFailureListener { e ->
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
                .addOnSuccessListener {
                    Toast.makeText(this, "Register done", Toast.LENGTH_LONG).show()
                    goToHomeActivity(model)


                }
            dialog.dismiss()
        }


    }

    private fun goToHomeActivity(model: RiderInfoModel) {
        Common.currentUser = model
        intent = Intent(this, RiderHomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    fun createSignInIntent() {

        // define our first sign-in layout
        val customLayout = AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
            .setPhoneButtonId(R.id.btn_phone_sign_in)
            .setGoogleButtonId(R.id.btn_google_sign_in)
            .build()

        // Choose authentication providers
        val providers = arrayListOf(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        // Create and launch sign-in intent
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setTheme(R.style.loginTheme)
            .setAuthMethodPickerLayout(customLayout)
            .setIsSmartLockEnabled(false)
            .build()
        signInLauncher.launch(signInIntent)
    }

    override fun onDestroy() {
        firebaseAuth.removeAuthStateListener(listener)
        super.onDestroy()
    }
}