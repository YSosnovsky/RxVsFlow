package com.example.rxvsflow

import com.example.rxvsflow.LocationProvider.LocationListener
import io.reactivex.rxjava3.core.BackpressureStrategy.LATEST
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit.MILLISECONDS

data class Optional<T>(val data: T?)

@Suppress("unused")
class Creation {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val disposables = CompositeDisposable()

    fun creation() {
        //simple
        val flow1 = flow { emit("hello world") }

        //'Not enough information to infer type variable String'
        val rx = Observable.create<String> {
            it.onNext("hello world")

            // what about completion?
            it.onComplete()
        }

        // this might be a better fit
        val rx2 = Observable.just("hello world")
        /*
        Which one should you use?
            Single.just("hello world")
            Flowable.just("hello world")
         */
    }

    //flow creation
    fun varargs() {
        val flow = flowOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17) //infinite - varargs
//        val notCompiling = Observable.just(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17) //finite - doesn't compile :(

        //creating an additional list
        val compiling = Observable.fromIterable(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17))
    }

    // listeners can be converted to flows/observables very easily
    fun convertingListenersToFlows() {
        val locationProvider = LocationProvider()

        locationProvider
            .locationFlow()
            .onEach { println("location: $it") }
            .launchIn(scope)

        val disposable = locationProvider
            .locationObservable()
            .subscribe { println("location: $it") }

        disposables.addAll(disposable)
    }
}

class Consuming {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val disposables = CompositeDisposable()

    fun consuming() {
        // with flows, consumption on a scope is mandatory, in Rx it's not.

        val flow = flowOf(1, 2, 3)
        val observable = Observable.just(1, 2, 3)

        //oops -what happens if I forget about the disposable return value? - memory leaks
        observable.subscribe { println(it) }

        //kotlin enforces consuming on scope
//        flow.collect { println(it) } //does not compile

        scope.launch {
            flow.collect { println(it) } //collection will be cancelled when scope is cancelled
        }

        //helper methods remove 'messy' lambdas - very easy to read
        flow
            .onEach { println(it) }
            .launchIn(scope)
    }

    //what about null values?

    // Flow is better suited for null values
    fun nullNotSupported() {
        val flow: Flow<Int?> = flowOf(1, null, 2) //this is fine!
        val observable = Observable.just(1, null, 2) //this will crash :(

        //no problem - wrap in 'Optional'
        val observable2: Observable<Optional<Int>> =
            Observable.just(Optional(1), Optional(null), Optional(2)) //too long. gets messy and it only gets worse

        //consume flows and ignore nulls - very easy. compiler helps.
        flow
            .filterNotNull() //no 'null' values will be received
            .onEach { if (it * 1 == 42) println("nice!") } //compiler knows it's never null (compiler 'contracts' supported)
            .launchIn(scope)

        //how do we consume 'Optional' types?
        observable2
            .filter { it.data != null } // looks reasonable
            .map { it.data } //warning - the compiler can't ensure non null - warnings everywhere. very messy.
            // what about simple arithmetic operators?
//            .subscribe { if (it * 1 == 42) println("nice!") } //doesn't compile, can be null

            //no problem - let's null check -
//            .subscribe { it?.let { number -> if (number + 0 == 42) println("nice") } //awful. you now have to support null values, that will never be received
            /*.subscribe {
                //how about this?

                //imagine 'observable2' is an API from another developer. what does null mean?
                // is it an error? is null a valid input? how do you deal with it?
                val num = it!!
                if (num + 0 == 42) println("nice!")
            }*/

            .subscribe {
                //how about this?
                val num =
                    requireNotNull(it) { "it cannot be null" } // works, but still very messy. how do I know it can never be null in advance?
                if (num + 0 == 42) println("nice!")
            }

        //maybe my RxJava skill isn't good enough :(
        //try to compare your favorite Rx subscribe method with the flow function.
    }

    sealed class User {
        object SignedOut : User()
        data class SignedIn(
            val name: String,
            val id: String,
            val email: String //etc
        ) : User()
    }

    fun consumingSealedClass() {

        //assume I have a flow of 'User' class.
        val userFlow = flow {
            emit(User.SignedOut)
            delay(30_000L) //simulate network call
            emit(
                User.SignedIn(
                    id = "user_id",
                    name = "user_name",
                    email = "user_mail"
                )
            )
        }
        //honestly I'm not sure what's the best way to write the same code with Observables :(

        //print 'hello' user whenever a user signs in
        val helloUserMessageFromFlow =
            userFlow
                .filterIsInstance<User.SignedIn>()
                .distinctUntilChanged() //prevent same message emitting - optional
                .onEach { user -> println("hello user ${user.name}") }
                .launchIn(scope)


        //try to achieve with Rx (unfortunately for me, adding delay was painful)
        val userObservableNoDelay = Observable.just(
            User.SignedOut,
            User.SignedIn(
                id = "user_id",
                name = "user_name",
                email = "user_mail"
            )
        )

        val helloUserMessageFromObservable =
            userObservableNoDelay
                .filter { it is User.SignedIn } //no help from compiler :(
                .distinctUntilChanged()
                .subscribe {
                    //what's the type? - Type is 'User'. More boilerplate code to come
                    val user = it as User.SignedIn // this is really not needed, since we already filtered.

                    // but what if someone removes the filter? - crash.

                    //alternative?
                    val maybeSignedIn = it as? User.SignedIn //will not crash, but still need to null check

                    //more boilerplate is needed
                    if (maybeSignedIn != null) {
                        println("hello user ${maybeSignedIn.name}")
                    } else {
                        //what about the 'else' part? do nothing? print warning? print error? how would you handle this?
                        println("should never get here")
                    }

                }

        disposables.add(helloUserMessageFromObservable)
    }
}

@Suppress("unused")
class Mapping {

    //viewModel scopes are managed by androidx libraries 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.0'
    private val scope = CoroutineScope(Dispatchers.Main) + CoroutineName("my custom scope")

    // need to implement every time. possible solution to add BaseViewModel/RxViewModel
    private val disposables = CompositeDisposable()

    //writing async method to run on different thread with delay using Coroutines is very easy
    private suspend fun heyThereSuspend(): String = withContext(Dispatchers.Default) {
        delay(5_000L)
        "hey there"
    }

    //rx - writing async method to run on different thread with delay requires more coding.
    private fun heyThereSingle(): Single<String> {
        return Completable
            .timer(5_000L, MILLISECONDS)
            .andThen(Single.just("hey there"))
            .subscribeOn(Schedulers.computation())
    }

    fun mappingAsyncFlow() {
        flowOf("hey")
            .map { heyThereSuspend() } //operators support 'suspend'. transform: suspend (value: T) -> R
            .flowOn(Dispatchers.IO)
            .onEach { println(it) }
            .launchIn(scope)

        //cancel scope sometime in future - self cancelling in ViewModels
        scope.cancel()
    }

    fun mappingAsyncObservable() {
        val disposable = Observable.just("hey")
//            .flatMap { heyThereSingle() }  //flat map will not compile because type is Single
            .flatMapSingle { heyThereSingle() }
            .subscribeOn(Schedulers.io())
            .subscribe { println(it) }

        disposables.add(disposable)

        //dispose sometime in the future - does not self cancel in ViewModel
        disposables.dispose()
    }
}

class Backpressure {

    private val myFlow = flowOf(1, 2, 3)
    private val myObservable = Observable.just(1, 2, 3)
    private val scope = CoroutineScope(Dispatchers.Main)
    private val disposable = CompositeDisposable()

    fun inFlow() {
        //Flow supports backpressure out of the box
        myFlow
            .debounce(2000L) //easy debounce
            .buffer(
                capacity = 10,
                onBufferOverflow = DROP_OLDEST
            ) //add buffer
            .conflate()
            .onEach { println(it) }
            .launchIn(scope)
    }

    fun inRx() {
        myObservable
            .toFlowable(LATEST)
            .buffer(1)
            .debounce(2000L, MILLISECONDS)
            .buffer(1000, 1) //Too much options for me
    }

}

data class Location(val lat: Double, val long: Double)

class LocationProvider {
    fun interface LocationListener {
        fun onLocationUpdated(location: Location)
    }

    fun addLocationListener(listener: LocationListener) {
        //todo implement
    }

    fun removeLocationListener(listener: LocationListener) {
        //todo implement
    }
}

fun LocationProvider.locationFlow(): Flow<Location> = callbackFlow {
    val listener = LocationListener { location -> trySend(location) }
    addLocationListener(listener)
    invokeOnClose { removeLocationListener(listener) }
}

fun LocationProvider.locationObservable(): Observable<Location> = Observable.create { emitter ->
    val listener = LocationListener { location -> emitter.onNext(location) }
    addLocationListener(listener)
    emitter.setCancellable { removeLocationListener(listener) }

    //looks similar, but when do you call onComplete?
    emitter.onComplete()
}