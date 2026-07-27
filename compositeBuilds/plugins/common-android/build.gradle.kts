plugins {
    `kotlin-dsl`
}
repositories {
    mavenCentral()
    maven("https://maven.aliyun.com/repository/google")
    google()
}
version = 1
group = "ir.amirab.plugin"
dependencies {
    implementation(libs.pluginAndroidGradle)
    implementation(libs.handlebarsJava)
    implementation(libs.okio.okio)
}
