package com.abdownloadmanager.shared.util.downloaderror.definederrors

import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.util.downloaderror.DownloadErrorMapper
import com.abdownloadmanager.shared.util.downloaderror.DownloadErrorMapper.Companion.createErrorReason
import com.abdownloadmanager.shared.util.downloaderror.DownloadErrorReason
import ir.amirab.downloader.exception.CloudflareChallengeException
import ir.amirab.util.compose.asStringSource

object CloudflareChallengeDownloadErrorMapper : DownloadErrorMapper {
    override fun accept(throwable: Throwable): Boolean {
        return throwable is CloudflareChallengeException
    }

    override fun getReason(throwable: Throwable): DownloadErrorReason {
        return createErrorReason(
            title = Res.string.download_error_reason_cloudflare_challenge_title.asStringSource().getString(),
            description = Res.string.download_error_reason_cloudflare_challenge_description.asStringSource().getString(),
            suggestion = Res.string.download_error_reason_cloudflare_challenge_suggestion.asStringSource().getString(),
            throwable = throwable,
        )
    }
}
