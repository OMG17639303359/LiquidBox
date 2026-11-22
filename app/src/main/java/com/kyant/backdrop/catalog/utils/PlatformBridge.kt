package com.kyant.backdrop.catalog.utils

import kotlinx.coroutines.android.awaitFrame as awaitAndroidFrame

/**
 * 平台桥接（本工程新增文件，不属于开源源码）。
 *
 * 官方 catalog 是 Kotlin Multiplatform 工程：commonMain 声明
 * `expect fun awaitFrame()`，androidMain 提供 `actual fun awaitFrame()`。
 * 本工程是纯 Android 单平台模块，不支持 expect/actual，因此不搬入那两个
 * 平台声明文件，改为在此提供同名实现，供原样搬入的
 * `DampedDragAnimation.kt` 调用。所有官方组件/工具类源码保持字节不变。
 */
suspend fun awaitFrame() {
    awaitAndroidFrame()
}
