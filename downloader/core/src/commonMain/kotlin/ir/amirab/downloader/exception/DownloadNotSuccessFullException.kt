package ir.amirab.downloader.exception

import java.io.IOException

open class UnSuccessfulResponseException(val code: Int, val msg: String) : IOException(
    "$code | $msg"
)

class CloudflareChallengeException(
    code: Int,
    msg: String,
) : UnSuccessfulResponseException(code, msg)
