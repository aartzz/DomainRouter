plugins {
    java
    `maven-publish`
}

group = "io.github.sisyphus"
version = "1.0.0"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://storehouse.okaeri.eu/repository/maven-public/")
    maven("https://maven.pkg.github.com/Navio1430/NavAuth") {
        credentials {
            username = project.findProperty("gpr.user") as String? ?: ""
            password = project.findProperty("gpr.key") as String? ?: ""
        }
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")

    compileOnly("pl.spcode.navauth:navauth-api:0.1.6-SNAPSHOT")
    annotationProcessor("pl.spcode.navauth:navauth-api:0.1.6-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
