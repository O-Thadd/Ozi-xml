package com.othadd.ozi

const val NOT_INITIALIZED = "not initialized"
const val WINNER_ANNOUNCEMENT = "winner announcement"
const val WORD_TO_TYPE = "word to type"

class MessagePackage {
    var gameModeratorId: String = NOT_INITIALIZED
    val delayPosting = false
    var roundSummary: String = NOT_INITIALIZED
    var contentDesc: String = NOT_INITIALIZED
}