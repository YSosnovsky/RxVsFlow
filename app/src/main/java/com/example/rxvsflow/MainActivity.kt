package com.example.rxvsflow

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.rxvsflow.UserNamePermutations.Failure
import com.example.rxvsflow.UserNamePermutations.NotInitialized
import com.example.rxvsflow.UserNamePermutations.Success
import com.example.rxvsflow.viewModels.FlowViewModel
import com.example.rxvsflow.viewModels.LiveDataViewModel
import com.example.rxvsflow.viewModels.RxViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainActivity : AppCompatActivity() {

    private val flowViewModel: FlowViewModel by viewModels()
    private val rxViewModel: RxViewModel by viewModels()
    private val liveDataViewModel: LiveDataViewModel by viewModels()

    private fun userNameInput(): String = findViewById<EditText>(R.id.user_name).text.toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fromFlow()
//        fromLiveData()

    }

    private fun fromFlow() {
        findViewById<View>(R.id.crash_sign_in).setOnClickListener {
            flowViewModel.crashSignIn(userNameInput())
        }

        findViewById<View>(R.id.sign_in).setOnClickListener {
            flowViewModel.signIn(userNameInput())
        }

        findViewById<View>(R.id.sign_out).setOnClickListener {
            flowViewModel.signOut()
        }

        //consumption
        flowViewModel
            .errors
            .onEach { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
            .flowWithLifecycle(
                lifecycle,
                Lifecycle.State.CREATED
            ) //extension by jetpack - cancels the underlying producer when the Lifecycle moves in and out of the target state
            .launchIn(lifecycleScope)

        flowViewModel
            .usernamePermutations
            .onEach { permutations ->
                findViewById<TextView>(R.id.permutations).text = when (permutations) {
                    is Failure -> "failed - ${permutations.reason}"
                    NotInitialized -> "uninitialized"
                    is Success -> permutations.permutations.joinToString()
                }
            }
            .flowWithLifecycle(lifecycle)
            .launchIn(lifecycleScope) //use 'viewLifecycleOwner.lifecycleScope' if in Fragment
    }

    private fun fromLiveData() {
        findViewById<View>(R.id.crash_sign_in).setOnClickListener {
            liveDataViewModel.crashSignIn(userNameInput())
        }

        findViewById<View>(R.id.sign_in).setOnClickListener {
            liveDataViewModel.signIn(userNameInput())
        }

        findViewById<View>(R.id.sign_out).setOnClickListener {
            liveDataViewModel.signOut()
        }

        //consumption
        liveDataViewModel
            .errors
            .observe(this) {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }

        liveDataViewModel
            .usernamePermutations
            .observe(this) { permutations ->
                findViewById<TextView>(R.id.permutations).text = when (permutations) {
                    is Failure -> "failed - ${permutations.reason}"
                    NotInitialized -> "uninitialized"
                    is Success -> permutations.permutations.joinToString()
                }
            }
    }


}