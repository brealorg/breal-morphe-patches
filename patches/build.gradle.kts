group = "app.morphe"

patches {
    about {
        name = "Breal Morphe Patches"
        description = "Morphe patch bundle for Boost for Reddit, Imgur, and other supported apps"
        source = "https://github.com/brealorg/breal-morphe-patches.git"
        author = "wchill + brealorg"
        contact = "w@chill.dev"
        website = "https://github.com/brealorg/breal-morphe-patches"
        license = "Additional conditions under GPL section 7 apply: attribution and project name restrictions. See LICENSE file."
    }
}

// Runtime classpath for local patches-list generator.
val patchListGeneratorClasspath: Configuration by configurations.creating

dependencies {
    // Used by JsonGenerator.
    implementation(libs.gson)
    patchListGeneratorClasspath(libs.gson)

    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    implementation(libs.morphe.patches.library)

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))
}

tasks {
    register<JavaExec>("checkStringResources") {
        description = "Checks resource strings for invalid formatting"

        dependsOn(compileKotlin)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.resource.CheckStringResourcesKt")
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }
    // Keep published Maven artifacts aligned with the generated patch catalog.
    publish {
        dependsOn("generatePatchesList")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-parameters")
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/wchill/patcheddit")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// MORPHE_DETERMINISTIC_MPP_MANIFEST_TIMESTAMP_V1
// The upstream patch Gradle plugin injects System.currentTimeMillis(), which
// makes identical source trees produce different MPP digests. Morphe Manager
// does not consume this attribute, so retain it as a deterministic sentinel.
tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
    manifest.attributes["Timestamp"] = "0"
}
