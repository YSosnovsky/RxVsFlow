package com.example.rxvsflow.viewModels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rxvsflow.User
import com.example.rxvsflow.User.SignedIn
import com.example.rxvsflow.User.SignedOut
import com.example.rxvsflow.UserNamePermutations
import com.example.rxvsflow.UserNamePermutations.Failure
import com.example.rxvsflow.UserNamePermutations.NotInitialized
import com.example.rxvsflow.UserNamePermutations.Success
import com.example.rxvsflow.permute
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlowViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val userEmitter = MutableStateFlow<User>(SignedOut)
    val user: StateFlow<User> = userEmitter

    //channel works as SingleLiveEvent
    private val errorChannel = Channel<String>(Channel.BUFFERED)
    val errors: Flow<String> = errorChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            errorChannel.send("crash")
        }
    }

    val usernamePermutations: StateFlow<UserNamePermutations> =
        userEmitter
            .map { user ->
                when (user) {
                    is SignedIn -> Success(user.name.permute())
                    SignedOut -> Failure("no user found")
                }
            } //state flows support 'distinctUntilChanged' out of the box
            .flowOn(Dispatchers.Default)
            .stateIn( //convert the flow to a stateFlow, where Loading is the initial state
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = NotInitialized
            )

    init {
        logDebug("user StateFlow value is never null, it's thread safe and is currently = [${user.value}] ")

        //easy access to value from any thread, value will always return the last value
        logDebug("${usernamePermutations.value}")
    }

    fun signIn(name: String) {
        viewModelScope.launch {
            kotlin.runCatching {
                mockSignIn(name)
            }
                .onSuccess { value: SignedIn -> userEmitter.value = value }
                .onFailure { logError("failed to sign int", it) }
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

    fun crashSignIn(name: String) {
        viewModelScope.launch {
            kotlin.runCatching {
                mockSignInError(name)
            }
                .onSuccess { value: SignedIn -> userEmitter.value = value }
                .onFailure {
                    logError("failed to sign in", it)
                    //sharedFlow forces to only emit 'String'. 'String?' does not compile
                    //better readability, less code complexity with compile enforcement
                    errorChannel.send(it.message ?: "unknown")
                }
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
        Log.e("FlowViewModel", msg, tr)
    }

    private fun logDebug(msg: String) {
        Log.d("FlowViewModel", msg)
    }
}