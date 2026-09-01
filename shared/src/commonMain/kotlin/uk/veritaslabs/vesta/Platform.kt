package uk.veritaslabs.vesta

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform