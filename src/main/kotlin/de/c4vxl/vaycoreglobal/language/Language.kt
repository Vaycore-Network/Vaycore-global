package de.c4vxl.vaycoreglobal.language

import de.c4vxl.vaycoreglobal.Main
import de.c4vxl.vaycoreglobal.utils.ResourceUtils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.nio.file.Path

/**
 * Lookup table for translations based on keys
 * @param translations A map from key to translation
 * @param name The name of the language
 */
class Language(
    val translations: Map<String, String>,
    val name: String
) {
    companion object {
        /**
         * Loads a language from file
         * @param path The path to the language file
         */
        fun fromFile(path: String): Language? {
            val file = File(path)
            if (!file.exists()) return null
            
            // Load translations
            val config = YamlConfiguration.loadConfiguration(file)
            val translations = buildMap { 
                config.getKeys(true).forEach {
                    put(it, config.getString(it) ?: it)
                }
            }

            return Language(translations, file.nameWithoutExtension)
        }

        /**
         * Returns the directory to load language file in to
         */
        val translationsDirectory: Path
            get() = Path.of(
                Main.config.getString("language.translations-dir")
                    ?: "./translations/"
            )

        /**
         * Returns a list of supported languages
         */
        val availableLanguages: List<String>
            get() = translationsDirectory.toFile()
                .listFiles { file -> file.isFile && file.extension.contains("yml") }
                ?.map { it.nameWithoutExtension }
                ?: listOf()

        /**
         * Loads a language by name
         * @param name The name of the language to load
         */
        fun get(name: String): Language? =
            fromFile(
                translationsDirectory.resolve("$name.yml").toString()
            )

        /**
         * Loads languages from jar to disk
         */
        fun load() {
            ResourceUtils.readResource("langs").split("\n")
                .forEach { lang ->
                    ResourceUtils.saveResource(
                        "lang/$lang.yml",
                        translationsDirectory.resolve("$lang.yml").toString()
                    )
                }
        }

        /**
         * Returns the file of the language database
         */
        val langDB
            get() = File(Main.instance.config.getString("language.db") ?: "languages.yml")

        /**
         * Default fallback-language
         */
        val default: Language
            get() = get(
                Main.instance.config.getString("language.default") ?: "english"
            )!!

        /**
         * Holds a players language preference
         */
        var CommandSender.language: Language
            get() {
                val player = this as? Player ?: return default
                val lang = YamlConfiguration.loadConfiguration(langDB)
                    .getString(player.uniqueId.toString()) ?: return default

                return get(lang) ?: default
            }
            set(value) {
                val player = this as? Player ?: return
                YamlConfiguration.loadConfiguration(langDB).apply {
                    this.set(player.uniqueId.toString(), value.name)
                    this.save(langDB)
                }
            }

        /**
         * Provide a language extension for sub-plugins using Language#child
         * @param namespace The namespace used to load this extension
         * @param language The language this extension is made for
         * @param languageFileContent A yml-formatted string of the translations
         * @param overwrite If set to 'true' old extension will be overwritten
         */
        fun provideLanguageExtension(namespace: String, language: String, languageFileContent: String, overwrite: Boolean = false) {
            val file = translationsDirectory.resolve("extensions/${namespace}/${language}.yml")

            // Create parent folder
            file.parent.toFile().mkdirs()

            // File exists
            if (!overwrite && file.toFile().exists())
                return

            // Save language
            file.toFile().writeText(languageFileContent)
        }
    }

    /**
     * Returns the translation of a key
     * @param key The translation key
     * @param args Arguments to the translation
     */
    fun get(key: String, vararg args: String): String {
        var value = resolveKey(key)

        // Handle arguments
        args.forEachIndexed { i, arg ->
            value = value.replace("$$i", arg)
        }

        return value
    }

    /**
     * Looks up the translation of a key
     * @param key The key to lookup
     * @param visited A list of previously visited keys to prevent circular key-references
     */
    private fun resolveKey(key: String, visited: MutableSet<String> = mutableSetOf()): String {
        // Key already visited
        // This prevents circular references leading to stack overflows
        if (!visited.add(key)) return key

        var value = translations.getOrDefault(key, key)

        // Resolve references
        value = Regex("""\$\{([^}]+)}""").replace(value) {
            resolveKey(
                it.groupValues[1],
                visited
            )
        }

        visited.remove(key)
        return value
    }

    /**
     * Returns a styled-component with the translation of a key
     * @param key The translation key
     * @param args Arguments to the translation
     */
    fun getCmp(key: String, vararg args: String): Component =
        MiniMessage.miniMessage().deserialize(get(key, *args))

    /**
     * Returns a language extension
     * This should be used by other plugins to load their own translations
     *
     * @param namespace The name of the other plugin
     */
    fun child(namespace: String): Language =
        fromFile(
            translationsDirectory.resolve(
                "extensions/${namespace}/${this.name}.yml"
            ).toString()
        )!!
}