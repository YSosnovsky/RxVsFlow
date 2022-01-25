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
import io.reactivex.rxjava3.core.BackpressureStrategy.LATEST
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RxViewModel : ViewModel() {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO


    //there is no initial value with PublisherSubjects as far as I'm aware
    private val userEmitter: PublishSubject<User> = PublishSubject.create()

    //
    val user: Observable<User> =
        userEmitter
            .distinctUntilChanged()

    val usernamePermutations: Flowable<UserNamePermutations> =
        userEmitter
            .map { user ->
                when (user) {
                    null -> Failure("user is null")
                    is SignedIn -> Success(user.name.permute())
                    SignedOut -> Failure("no user found")
                }
            }
            .distinctUntilChanged()
            //start with makes it similar to initialValue, but now the return value is 'Observable'
            .startWith(Single.just(NotInitialized))
            //convert to flowable to access values
            .toFlowable(LATEST)


    init {

        logDebug("how do I get the last value without blocking?")

        //We can use blockingLast, but it's dangerous. Might lead to ANRs if run on main thread
        //also, there is no guarantee that a value is present, so this might block forever
        val user: UserNamePermutations = usernamePermutations.blockingLast()
        logDebug("after blocking, user is[$user]")
    }

    fun signIn(name: String) {
        viewModelScope.launch {
            kotlin.runCatching {
                mockSignIn(name)
            }
                .onSuccess { value: SignedIn -> userEmitter.onNext(value) }
                .onFailure { logError("failed to sign in", it) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            kotlin.runCatching {
                mockSignOut()
            }
                .onSuccess { value: SignedOut -> userEmitter.onNext(value) }
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

    private fun logError(msg: String, tr: Throwable?) {
        Log.e("RxViewModel", msg, tr)
    }

    private fun logDebug(msg: String) {
        Log.d("RxViewModel", msg)
    }
}