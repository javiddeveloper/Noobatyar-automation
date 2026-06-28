package xyz.sattar.javid.proqueue.core.network

import io.ktor.client.HttpClient

expect object HttpClientFactory {
    fun create(): HttpClient
}
