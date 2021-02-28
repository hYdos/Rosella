package me.hydos.rosella.resource

import me.hydos.rosella.Rosella

/**
 * Don't use this once [Rosella] get its own ResourceLoader field
 */
object GlobalResourceLoader : ResourceLoader by ClassLoaderResourceLoader(ClassLoader.getSystemClassLoader())
