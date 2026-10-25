plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(plugin(libs.plugins.resource.factory))
    implementation(plugin(libs.plugins.run.paper))
}

// Lets a precompiled script plugin apply a plugin from the version catalog.
fun plugin(plugin: Provider<PluginDependency>) =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
