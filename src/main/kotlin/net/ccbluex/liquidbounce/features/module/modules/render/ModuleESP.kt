/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2016 - 2021 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.Choice
import net.ccbluex.liquidbounce.event.EngineRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.render.engine.*
import net.ccbluex.liquidbounce.render.engine.memory.PositionColorVertexFormat
import net.ccbluex.liquidbounce.render.engine.memory.putVertex
import net.ccbluex.liquidbounce.render.utils.*
import net.ccbluex.liquidbounce.utils.combat.shouldBeShown
import net.ccbluex.liquidbounce.utils.render.espBoxInstancedOutlineRenderTask
import net.ccbluex.liquidbounce.utils.render.espBoxInstancedRenderTask
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Box
import java.awt.Color

object ModuleESP : Module("ESP", Category.RENDER) {

    override val translationBaseKey: String
        get() = "liquidbounce.module.esp"

    private val modes = choices("Mode", "Box") {
        BoxMode
    }

    private val colorMods = choices("ColorMode", "Static") {
        ColorMode
        RainbowMode
    }

    private object ColorMode : Choice("Static", modes) {
        val color by color("Color", Color4b.WHITE)
    }

    private object RainbowMode : Choice("Rainbow", modes)

    val teamColor by boolean("TeamColor", true)

    private object BoxMode : Choice("Box", modes) {

        val renderHandler = handler<EngineRenderEvent> { event ->
            val base = if (RainbowMode.isActive) rainbow() else ColorMode.color

            val filteredEntities = world.entities.filter { it.shouldBeShown() }

            val grouped = filteredEntities.groupBy {
                val dimensions = it.getDimensions(it.pose)

                val d = dimensions.width.toDouble() / 2.0

                Box(
                    -d,
                    0.0,
                    -d,
                    d,
                    dimensions.height.toDouble(),
                    d
                )
            }

            grouped.forEach {
                val box = drawBoxNew(it.key.expand(0.05), Color4b.WHITE)
                val boxOutline = drawBoxOutlineNew(it.key.expand(0.05), Color4b.WHITE)

                val instanceBuffer = PositionColorVertexFormat().apply { initBuffer(it.value.size) }
                val outlineInstanceBuffer = PositionColorVertexFormat().apply { initBuffer(it.value.size) }

                for (entity in it.value) {
                    val pos = Vec3(
                        entity.x + (entity.x - entity.lastRenderX) * event.tickDelta,
                        entity.y + (entity.y - entity.lastRenderY) * event.tickDelta,
                        entity.z + (entity.z - entity.lastRenderZ) * event.tickDelta
                    )

                    val color = getColor(entity) ?: base

                    val baseColor = Color4b(color.r, color.g, color.b, 50)
                    val outlineColor = Color4b(color.r, color.g, color.b, 100)

                    instanceBuffer.putVertex { this.position = pos; this.color = baseColor }
                    outlineInstanceBuffer.putVertex { this.position = pos; this.color = outlineColor }
                }

                RenderEngine.enqueueForRendering(RenderEngine.CAMERA_VIEW_LAYER_WITHOUT_BOBBING, espBoxInstancedRenderTask(instanceBuffer, box.first, box.second))
                RenderEngine.enqueueForRendering(RenderEngine.CAMERA_VIEW_LAYER_WITHOUT_BOBBING, espBoxInstancedOutlineRenderTask(outlineInstanceBuffer, boxOutline.first, boxOutline.second))
            }
        }

    }

    fun getColor(entity: Entity): Color4b? {
        run {
            if (entity is LivingEntity) {
                if (entity.hurtTime > 0) {
                    return Color4b(255, 0, 0)
                }
                if (entity is PlayerEntity && FriendManager.isFriend(entity)) {
                    return Color4b(0, 0, 255)
                }

                if (teamColor) {
                    val chars: CharArray = (entity.displayName ?: return@run).string.toCharArray()
                    var color = Int.MAX_VALUE

                    val colors = "0123456789abcdef"

                    for (i in chars.indices) {
                        if (chars[i] != '§' || i + 1 >= chars.size) {
                            continue
                        }

                        val index = colors.indexOf(chars[i + 1])

                        if (index < 0 || index > 15) {
                            continue
                        }

                        color = ColorUtils.hexColors[index]
                        break
                    }

                    return Color4b(Color(color))
                }
            }
        }

        return null
    }

}