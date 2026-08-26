// eta.mirror=true 时改用阿里云镜像仓库（本地网络无法直连 Google Maven 时使用），
// 默认使用官方源（CI 保持官方源，不受影响）。本地构建脚本已自动传入该开关。
pluginManagement {
    repositories {
        if (providers.gradleProperty("eta.mirror").orNull == "true") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        } else {
            google {
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                    includeGroupByRegex("androidx.*")
                }
            }
            mavenCentral()
            gradlePluginPortal()
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("eta.mirror").orNull == "true") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        } else {
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "Eta"
include(":app")
