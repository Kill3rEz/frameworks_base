/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalTextApi::class)

package com.android.compose.theme.typography

import android.content.Context
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal class TypefaceTokens(private val typefaceNames: TypefaceNames) {
    companion object {
        val WeightMedium = FontWeight.Medium
        val WeightRegular = FontWeight.Normal
        private const val DEFAULT_TYPEFACE_NAME = "sans-serif"
    }

    private val brandFont = DeviceFontFamilyName(typefaceNames.brand)
    private val plainFont = DeviceFontFamilyName(typefaceNames.plain)
    private val brandEmphasizedWeight =
        if (typefaceNames.brand == DEFAULT_TYPEFACE_NAME) FontWeight.Normal else FontWeight.Bold
    private val plainEmphasizedWeight =
        if (typefaceNames.plain == DEFAULT_TYPEFACE_NAME) FontWeight.Normal else FontWeight.Bold

    // Variable emphasized families only exist for the platform's default Google Sans Flex.
    // When an overlay supplies a family, use the configured family for the same type role.
    private val displayLargeEmphasizedFont =
        emphasizedBrandFont("variable-display-large-emphasized")
    private val displayMediumEmphasizedFont =
        emphasizedBrandFont("variable-display-medium-emphasized")
    private val displaySmallEmphasizedFont =
        emphasizedBrandFont("variable-display-small-emphasized")
    private val headlineLargeEmphasizedFont =
        emphasizedBrandFont("variable-headline-large-emphasized")
    private val headlineMediumEmphasizedFont =
        emphasizedBrandFont("variable-headline-medium-emphasized")
    private val headlineSmallEmphasizedFont =
        emphasizedBrandFont("variable-headline-small-emphasized")
    private val titleLargeEmphasizedFont =
        emphasizedPlainFont("variable-title-large-emphasized")
    private val titleMediumEmphasizedFont =
        emphasizedPlainFont("variable-title-medium-emphasized")
    private val titleSmallEmphasizedFont =
        emphasizedPlainFont("variable-title-small-emphasized")
    private val bodyLargeEmphasizedFont = emphasizedPlainFont("variable-body-large-emphasized")
    private val bodyMediumEmphasizedFont = emphasizedPlainFont("variable-body-medium-emphasized")
    private val bodySmallEmphasizedFont = emphasizedPlainFont("variable-body-small-emphasized")
    private val labelLargeEmphasizedFont = emphasizedPlainFont("variable-label-large-emphasized")
    private val labelMediumEmphasizedFont = emphasizedPlainFont("variable-label-medium-emphasized")
    private val labelSmallEmphasizedFont = emphasizedPlainFont("variable-label-small-emphasized")

    val brand =
        FontFamily(
            Font(brandFont, weight = WeightMedium),
            Font(brandFont, weight = WeightRegular),
        )
    val plain =
        FontFamily(
            Font(plainFont, weight = WeightMedium),
            Font(plainFont, weight = WeightRegular),
        )

    val displayLargeEmphasized = emphasizedBrand(displayLargeEmphasizedFont)
    val displayMediumEmphasized = emphasizedBrand(displayMediumEmphasizedFont)
    val displaySmallEmphasized = emphasizedBrand(displaySmallEmphasizedFont)
    val headlineLargeEmphasized = emphasizedBrand(headlineLargeEmphasizedFont)
    val headlineMediumEmphasized = emphasizedBrand(headlineMediumEmphasizedFont)
    val headlineSmallEmphasized = emphasizedBrand(headlineSmallEmphasizedFont)
    val titleLargeEmphasized = emphasizedPlain(titleLargeEmphasizedFont)
    val titleMediumEmphasized = emphasizedPlain(titleMediumEmphasizedFont)
    val titleSmallEmphasized = emphasizedPlain(titleSmallEmphasizedFont)
    val bodyLargeEmphasized = emphasizedPlain(bodyLargeEmphasizedFont)
    val bodyMediumEmphasized = emphasizedPlain(bodyMediumEmphasizedFont)
    val bodySmallEmphasized = emphasizedPlain(bodySmallEmphasizedFont)
    val labelLargeEmphasized = emphasizedPlain(labelLargeEmphasizedFont)
    val labelMediumEmphasized = emphasizedPlain(labelMediumEmphasizedFont)
    val labelSmallEmphasized = emphasizedPlain(labelSmallEmphasizedFont)

    private fun emphasizedBrandFont(variableFont: String): DeviceFontFamilyName =
        if (typefaceNames.brand == DEFAULT_TYPEFACE_NAME) DeviceFontFamilyName(variableFont)
        else brandFont

    private fun emphasizedPlainFont(variableFont: String): DeviceFontFamilyName =
        if (typefaceNames.plain == DEFAULT_TYPEFACE_NAME) DeviceFontFamilyName(variableFont)
        else plainFont

    private fun emphasizedBrand(font: DeviceFontFamilyName): FontFamily =
        FontFamily(Font(font, weight = brandEmphasizedWeight))

    private fun emphasizedPlain(font: DeviceFontFamilyName): FontFamily =
        FontFamily(Font(font, weight = plainEmphasizedWeight))
}

internal data class TypefaceNames
private constructor(
    val brand: String,
    val plain: String,
) {
    private enum class Config(val configName: String, val default: String) {
        Brand("config_headlineFontFamily", "sans-serif"),
        Plain("config_bodyFontFamily", "sans-serif"),
    }

    companion object {
        fun get(context: Context): TypefaceNames {
            return TypefaceNames(
                brand = getTypefaceName(context, Config.Brand),
                plain = getTypefaceName(context, Config.Plain),
            )
        }

        private fun getTypefaceName(context: Context, config: Config): String {
            return context
                .getString(context.resources.getIdentifier(config.configName, "string", "android"))
                .takeIf { it.isNotEmpty() }
                ?: config.default
        }
    }
}
