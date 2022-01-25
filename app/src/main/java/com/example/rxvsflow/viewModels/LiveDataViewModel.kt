package com.example.rxvsflow.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rxvsflow.SingleLiveEvent
import com.example.rxvsflow.User
import com.example.rxvsflow.User.SignedIn
import com.example.rxvsflow.User.SignedOut
import com.example.rxvsflow.UserNamePermutations
import com.example.rxvsflow.UserNamePermutations.Failure
import com.example.rxvsflow.UserNamePermutations.Success
import com.example.rxvsflow.permute
import com.snakydesign.livedataextensions.distinctUntilChanged
import com.snakydesign.livedataextensions.map
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.RuntimeException

class LiveDataViewModel : ViewModel() {
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    //initial value. looks promising
    private val userEmitter = MutableLiveData<User>(SignedOut)
    val user: LiveData<User> = userEmitter

    private val errorsEmitter = SingleLiveEvent<String>() //yet another library!
    val errors: LiveData<String> = errorsEmitter

    val usernamePermutations: LiveData<UserNamePermutations> =
        userEmitter
            .map { user ->
                when (user) {
                    is SignedIn -> Success(user.name.permute())
                    SignedOut -> Failure("no user found")
                }
            }
//            .distinctUntilChanged()


    init {
        val currentUser: User? = user.value
        logDebug(
            "user LiveData value can be null, " +
                "even if I know it's not possible for this case. $currentUser"
        )

        //easy access to value from any thread, value will always return the last value, but it can be null
        logDebug("${usernamePermutations.value}")
    }

    fun signIn(name: String) {
        viewModelScope.launch {
            kotlin.runCatching {
                mockSignIn(name)
            }
                .onSuccess { value: SignedIn -> userEmitter.value = value }
                .onFailure {
                    logError("failed to sign in", it)
                }
        }
    }

    fun crashSignIn(name: String) {
        viewModelScope.launch {
            kotlin.runCatching {
                mockSignInError(name)
            }
                .onSuccess { value: SignedIn -> userEmitter.value = value }
                .onFailure {
                    logError("failed to sign in", it)

                    // value to post can be null even though the type is 'String' and not 'String?'
                    // this is problematic because there is no compiler enforcement
                    errorsEmitter.postValue(it.message)
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            kotlin.runCatching {
                mockSignOut()
            }
                .onSuccess { value: SignedOut -> userEmitter.value = value }
                .onFailure { logError("failed to sign out", it) }
        }
    }

    private suspend fun mockSignIn(name: String): SignedIn = withContext(ioDispatcher) {
        delay(1000L) //simulate async method
        SignedIn(name)
    }

    private suspend fun mockSignOut(): SignedOut = withContext(ioDispatcher) {
        delay(1000L) //simulate async method
        SignedOut
    }

    private suspend fun mockSignInError(name: String): SignedIn = withContext(ioDispatcher) {
        delay(1000L) //simulate async method
        throw RuntimeException("crash! $name")
    }

    private fun logError(msg: String, tr: Throwable?) {
        Log.e("LiveDataViewModel", msg, tr)
    }

    private fun logDebug(msg: String) {
        Log.d("LiveDataViewModel", msg)
    }
}
