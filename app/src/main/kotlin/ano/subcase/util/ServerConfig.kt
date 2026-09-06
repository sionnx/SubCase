package ano.subcase.util

data class ServerEndpoint(val host: String, val port: Int) {
    override fun toString() = "$host:$port"
}

data class ServerListenConfig(val frontend: ServerEndpoint, val backend: ServerEndpoint)

fun currentServerConfig(): ServerListenConfig {
    val host = if (ConfigStore.isAllowLan) "0.0.0.0" else "127.0.0.1"
    return ServerListenConfig(ServerEndpoint(host, 8080), ServerEndpoint(host, 8081))
}
