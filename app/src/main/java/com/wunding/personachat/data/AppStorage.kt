package com.wunding.personachat.data

import android.content.Context
import java.io.File

/**
 * 统一存储位置：Android/data/com.wunding.personachat/files/{models,personas}
 * 外置应用私有目录，卸载即清理，不需要存储权限；老版本内置数据会自动搬一次。
 */
object AppStorage {
    private val migrated = mutableSetOf<String>()

    fun root(ctx: Context): File =
        (ctx.getExternalFilesDir(null) ?: ctx.filesDir).apply { mkdirs() }

    fun models(ctx: Context): File = sub(ctx, "models")
    fun personas(ctx: Context): File = sub(ctx, "personas")

    @Synchronized
    private fun sub(ctx: Context, name: String): File {
        val dir = File(root(ctx), name).apply { mkdirs() }
        if (name !in migrated) {
            migrated += name
            runCatching {
                val old = File(ctx.filesDir, name)
                if (old.isDirectory) {
                    old.listFiles()?.forEach { f ->
                        val dest = File(dir, f.name)
                        if (!dest.exists()) {
                            if (!f.renameTo(dest)) {
                                runCatching { f.copyTo(dest, overwrite = true); f.delete() }
                            }
                        }
                    }
                    old.delete()
                }
            }
        }
        return dir
    }

    /** 展示给用户看的路径。 */
    fun display(ctx: Context): String = root(ctx).absolutePath
}
