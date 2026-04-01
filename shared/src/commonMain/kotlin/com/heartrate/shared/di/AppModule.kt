package com.heartrate.shared.di

import org.koin.core.module.Module

/**
 * Koin Dependency Injection for Shared Module
 *
 * Platform-specific implementations are provided via expect/actual.
 * See AppModule.android.kt and AppModule.desktop.kt for actual implementations.
 */

/**
 * Get all Koin modules for the app
 * This is an expect declaration - actual implementations provided per platform
 */
expect fun getAppModules(): List<Module>
