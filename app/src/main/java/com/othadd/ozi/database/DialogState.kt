package com.othadd.ozi.database

const val NOTIFY_DIALOG_TYPE = "notify dialog type"
const val PROMPT_DIALOG_TYPE = "prompt dialog type"
const val NO_DIALOG_DIALOG_TYPE = "no dialog dialog type"

data class DialogState(
    var message: String,
    var positiveButtonText: String,
    var negativeButtonText: String,
    var showOkayButton: Boolean,
    val dialogType: String
)

fun getNotifyDialogType(message: String, showButton: Boolean): DialogState{
    return DialogState(message, "", "", showButton, NOTIFY_DIALOG_TYPE)
}

fun getPromptDialogType(message: String, positiveButtonText: String, negativeButtonText: String): DialogState{
    return DialogState(message, positiveButtonText, negativeButtonText, false, PROMPT_DIALOG_TYPE)
}

fun getNoDialogDialogType(): DialogState{
    return DialogState( "", "","", false,NO_DIALOG_DIALOG_TYPE)
}
