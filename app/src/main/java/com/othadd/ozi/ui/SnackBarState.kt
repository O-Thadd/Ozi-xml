package com.othadd.ozi.ui

class SnackBarState(val message: String, val showActionButton: Boolean, val actionButtonText: String, val showCloseButton: Boolean) {
}

fun getPromptSnackBar(message: String, actionButtonText: String): SnackBarState{
    return SnackBarState(message, true, actionButtonText, true)
}

fun getNotifySnackBar(message: String): SnackBarState{
    return SnackBarState(message, false, "", false)
}

fun getNoSnackBarSnackBar(): SnackBarState{
    return SnackBarState("", false, "", false)
}