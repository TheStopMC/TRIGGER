plugins {
    id("java")
}

group = "cat"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.minestom:minestom:2025.07.10b-1.21.7")
    implementation("com.github.quickhull3d:quickhull3d:1.0.0")
    implementation("org.spongepowered:configurate-hocon:3.7.2")
    implementation("com.jamieswhiteshirt:rtree-3i-lite:0.3.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
