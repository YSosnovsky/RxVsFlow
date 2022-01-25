package com.example.rxvsflow

sealed class User {
    object SignedOut : User()
    data class SignedIn(
        val name: String
    ) : User()
}

sealed class UserNamePermutations {
    data class Failure(val reason: String) : UserNamePermutations()
    data class Success(val permutations: List<String>) : UserNamePermutations()
    object NotInitialized: UserNamePermutations()
}

// helper method to get all possible permutations of a string
fun String.permute(result: String = ""): List<String> =
    if (isEmpty()) listOf(result) else flatMapIndexed { i, c -> removeRange(i, i + 1).permute(result + c) }
