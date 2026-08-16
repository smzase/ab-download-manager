package ir.amirab.downloader.connection.clearance

/**
 * Solves an interactive Cloudflare challenge for a link, typically by showing the
 * user a real browser and letting them click through the challenge, then storing
 * the captured cookies where the paired [ClearanceProvider] will find them.
 *
 * An interactive challenge cannot be satisfied by a plain HTTP client, which is why
 * this has to escape the downloader and reach the UI layer. Implementations live on
 * the platform side ([NoOp] is used where no browser is available).
 */
interface ChallengeSolver {
    /**
     * Attempts to obtain a clearance for [url], suspending until the user either
     * completes or abandons the challenge.
     *
     * @return true when a usable clearance was stored and the download is worth retrying.
     */
    suspend fun solve(url: String): Boolean

    class NoOp : ChallengeSolver {
        override suspend fun solve(url: String): Boolean = false
    }
}
