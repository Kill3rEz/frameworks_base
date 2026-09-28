/*
 * Copyright (C) 2026 The PenguinOS Project
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

package com.android.systemui.qs.panels.ui.compose

import com.android.systemui.qs.pipeline.shared.TileSpec
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object PenguinGrid {
    const val COLUMNS = 4

    const val MAX_ROWS = 6

    const val SETTING = "qs_penguin_grid"

    val BRIGHTNESS_SPEC = TileSpec.create("penguin_brightness")
    val VOLUME_SPEC = TileSpec.create("penguin_volume")

    val MODULE_SPECS = listOf(FOLDER_SPEC, MEDIA_SPEC, BRIGHTNESS_SPEC, VOLUME_SPEC)

    fun isModule(spec: TileSpec) = spec in MODULE_SPECS

    data class Item(val spec: TileSpec, val x: Int, val y: Int, val w: Int, val h: Int) {
        val right: Int
            get() = x + w

        val bottom: Int
            get() = y + h

        fun overlaps(other: Item) =
            x < other.right && other.x < right && y < other.bottom && other.y < bottom

        fun contains(cellX: Int, cellY: Int) = cellX in x until right && cellY in y until bottom
    }

    fun sizes(spec: TileSpec): List<Pair<Int, Int>> =
        when (spec) {
            MEDIA_SPEC -> listOf(2 to 2, 4 to 2, 4 to 4)
            FOLDER_SPEC -> listOf(2 to 2, 4 to 4)
            BRIGHTNESS_SPEC,
            VOLUME_SPEC -> listOf(1 to 2, 2 to 1, 4 to 1)
            else -> listOf(1 to 1, 2 to 1)
        }

    fun defaultSize(spec: TileSpec, largeTile: Boolean): Pair<Int, Int> =
        if (isModule(spec)) sizes(spec).first() else if (largeTile) 2 to 1 else 1 to 1

    fun rows(page: List<Item>) = page.maxOfOrNull { it.bottom } ?: 0

    /**
     * first.
     */
    fun resolve(
        pages: List<List<Item>>,
        modules: Set<TileSpec>,
        tiles: List<TileSpec>,
        largeTiles: Set<TileSpec>,
        preferredPage: Map<TileSpec, Int> = emptyMap(),
    ): List<List<Item>> {
        val present = tiles.toSet()
        val seen = mutableSetOf<TileSpec>()
        val out =
            pages
                .map { page ->
                    val placed = mutableListOf<Item>()
                    for (item in page) {
                        val module = isModule(item.spec)
                        if (module && (item.spec !in modules || placed.any { it.spec == item.spec })) {
                            continue
                        }
                        if (!module && (item.spec !in present || item.spec in seen)) continue
                        val (w, h) =
                            if (module) {
                                if (item.w to item.h in sizes(item.spec)) item.w to item.h
                                else sizes(item.spec).first()
                            } else {
                                defaultSize(item.spec, item.spec in largeTiles)
                            }
                        val fitted =
                            item.copy(
                                w = w,
                                h = h,
                                x = item.x.coerceIn(0, COLUMNS - w),
                                y = item.y.coerceAtLeast(0),
                            )
                        placed +=
                            if (placed.none { it.overlaps(fitted) }) fitted
                            else firstFree(placed, item.spec, w, h)
                        if (!module) seen += item.spec
                    }
                    placed
                }
                .toMutableList()
        if (out.isEmpty()) out.add(mutableListOf())
        for (spec in tiles) {
            if (spec in seen) continue
            val index = preferredPage[spec]?.takeIf { it in out.indices } ?: 0
            val (w, h) = defaultSize(spec, spec in largeTiles)
            out[index].add(firstFree(out[index], spec, w, h))
        }
        return paginate(
            out.filterIndexed { index, page -> index == 0 || page.isNotEmpty() }.map(::compact)
        )
    }

    fun compact(page: List<Item>): List<Item> {
        val out = mutableListOf<Item>()
        for (item in page.sortedWith(compareBy({ it.y }, { it.x }))) {
            var risen = item
            while (risen.y > 0) {
                val up = risen.copy(y = risen.y - 1)
                if (out.any { it.overlaps(up) }) break
                risen = up
            }
            out.add(risen)
        }
        return out
    }

    fun paginate(pages: List<List<Item>>): List<List<Item>> {
        val out = mutableListOf<List<Item>>()
        var carried = emptyList<Item>()
        for (page in pages) {
            carried = spill(page, out)
            while (carried.isNotEmpty()) {
                val next = mutableListOf<Item>()
                for (item in carried) next.add(firstFree(next, item.spec, item.w, item.h))
                carried = spill(next, out)
            }
        }
        return out
    }

    private fun spill(page: List<Item>, out: MutableList<List<Item>>): List<Item> {
        val (fits, over) = page.partition { it.bottom <= MAX_ROWS }
        out.add(fits)
        return over.sortedWith(compareBy({ it.y }, { it.x }))
    }

    fun defaults(
        modules: Set<TileSpec>,
        tiles: List<TileSpec>,
        largeTiles: Set<TileSpec>,
    ): List<List<Item>> {
        val first =
            listOf(
                    Item(FOLDER_SPEC, 0, 0, 2, 2),
                    Item(MEDIA_SPEC, 2, 0, 2, 2),
                    Item(BRIGHTNESS_SPEC, 2, 2, 1, 2),
                    Item(VOLUME_SPEC, 3, 2, 1, 2),
                )
                .filter { it.spec in modules }
        val pages =
            listOfNotNull(
                first,
                listOf(Item(MEDIA_SPEC, 0, 0, 4, 4)).takeIf { MEDIA_SPEC in modules },
                listOf(Item(FOLDER_SPEC, 0, 0, 4, 4)).takeIf { FOLDER_SPEC in modules },
            )
        return resolve(pages, modules, tiles, largeTiles)
    }

    fun firstFree(
        placed: List<Item>,
        spec: TileSpec,
        w: Int,
        h: Int,
        fromY: Int = 0,
        fromX: Int = 0,
    ): Item {
        var y = fromY
        while (true) {
            val startX = if (y == fromY) fromX.coerceAtMost(COLUMNS - w) else 0
            for (x in startX..COLUMNS - w) {
                val candidate = Item(spec, x, y, w, h)
                if (placed.none { it.overlaps(candidate) }) return candidate
            }
            y++
        }
    }

    fun place(page: List<Item>, item: Item, x: Int, y: Int, w: Int = item.w, h: Int = item.h):
        List<Item> {
        val moved = Item(item.spec, x.coerceIn(0, COLUMNS - w), y.coerceAtLeast(0), w, h)
        val result = mutableListOf(moved)
        for (other in page.filter { it.spec != item.spec }.sortedWith(compareBy({ it.y }, { it.x }))) {
            result +=
                if (result.none { it.overlaps(other) }) other
                else firstFree(result, other.spec, other.w, other.h, other.y, other.x)
        }
        return result
    }

    fun parse(json: String?): List<List<Item>>? {
        if (json.isNullOrBlank()) return null
        return try {
            val trimmed = json.trim()
            val pages =
                if (trimmed.startsWith("[")) JSONArray().put(JSONArray(trimmed))
                else JSONObject(trimmed).getJSONArray("pages")
            (0 until pages.length()).map { index ->
                val page = pages.getJSONArray(index)
                (0 until page.length()).map { itemIndex ->
                    val item = page.getJSONObject(itemIndex)
                    Item(
                        spec = TileSpec.create(item.getString("s")),
                        x = item.getInt("x"),
                        y = item.getInt("y"),
                        w = item.getInt("w"),
                        h = item.getInt("h"),
                    )
                }
            }
        } catch (e: JSONException) {
            null
        }
    }

    fun serialize(pages: List<List<Item>>): String =
        JSONObject()
            .put(
                "pages",
                JSONArray(
                    pages.map { page ->
                        JSONArray(
                            page.map { item ->
                                JSONObject()
                                    .put("s", item.spec.spec)
                                    .put("x", item.x)
                                    .put("y", item.y)
                                    .put("w", item.w)
                                    .put("h", item.h)
                            }
                        )
                    }
                ),
            )
            .toString()
}
