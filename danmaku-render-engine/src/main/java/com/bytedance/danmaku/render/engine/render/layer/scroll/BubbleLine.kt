package com.bytedance.danmaku.render.engine.render.layer.scroll

import com.bytedance.danmaku.render.engine.control.DanmakuController
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.render.IRenderLayer
import com.bytedance.danmaku.render.engine.render.draw.DrawItem
import com.bytedance.danmaku.render.engine.render.layer.line.BaseRenderLine
import com.bytedance.danmaku.render.engine.utils.HIGH_REFRESH_MAX_TIME
import com.bytedance.danmaku.render.engine.utils.STEPPER_TIME
import kotlin.random.Random

class BubbleLine(controller: DanmakuController,
                 private val mLayer: IRenderLayer
) : BaseRenderLine(controller, mLayer) {

    private var mLastTypeSettingTime = -1L
    private var mStepperTime = STEPPER_TIME

    private val spaces = mutableListOf<Pair<Float, Float>>()

    override fun onLayoutChanged(width: Float, height: Float, x: Float, y: Float) {
        super.onLayoutChanged(width, height, x, y)
        measureAndLayout()
    }

    override fun addItem(playTime: Long, item: DrawItem<DanmakuData>): Boolean {
        val maxSpace = spaces.maxByOrNull { it.second - it.first } ?: return false
        if (maxSpace.second - maxSpace.first < item.width) {
            return false
        }
        spaces.remove(maxSpace)
        val newSpace: Pair<Float, Float>
        val insert2Left = Random.nextBoolean()
        if (insert2Left) {
            item.x = maxSpace.first
            newSpace = maxSpace.copy(first = item.x + item.width)
        } else {
            item.x = maxSpace.second - item.width
            newSpace = maxSpace.copy(second = item.x)
        }
        if (newSpace.second > newSpace.first) {
            spaces.add(newSpace)
        }
        item.y = this.y
        item.showTime = playTime
        mDrawingItems.add(item)
        return true
    }

    /**
     * Do the typesetting work
     * @param isPlaying move item forward if is playing
     * @param configChanged need to re-measure and re-layout items if config changed
     */
    override fun typesetting(playTime: Long, isPlaying: Boolean, configChanged: Boolean): Int {
        if (mLastTypeSettingTime < 0) {
            mLastTypeSettingTime = System.currentTimeMillis()
        } else {
            val newTypeSettingTime = System.currentTimeMillis()
            mStepperTime = if (newTypeSettingTime - mLastTypeSettingTime < HIGH_REFRESH_MAX_TIME) {
                newTypeSettingTime - mLastTypeSettingTime
            } else {
                STEPPER_TIME
            }
            mLastTypeSettingTime = newTypeSettingTime
        }

        if (isPlaying) {
            // move drawing items if is playing
            mDrawingItems.forEach { item ->
                if (!item.isPaused) {
                    //item.x -= getItemSpeed(item) * mStepperTime
                    item.showDuration += mStepperTime
                }
            }
            // remove items that already out of screen
            val iterator = mDrawingItems.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.showDuration >= mConfig.scroll.moveTime) {
                    mLayer.releaseItem(item)
                    iterator.remove()

                    // 重新计算space，避免无法合并
                    calSpace()
                }
            }
        }

        if (configChanged) {
            measureAndLayout()
        }
        return mDrawingItems.size
    }

    /**
     * Re-measure and re-layout current drawing items
     */
    private fun measureAndLayout() {
        calSpace()
    }

    private fun calSpace() {
        spaces.clear()
        if (mDrawingItems.isEmpty()) {
            spaces.add(0f to width)
            return
        }
        val items = mDrawingItems.sortedBy { it.x }
        items.forEachIndexed { index, drawItem ->
            if (index == 0) {
                val space = 0f to drawItem.x
                if (space.second - space.first > 30) {
                    spaces.add(space)
                }
                return@forEachIndexed
            }
            val prev = mDrawingItems[index - 1]
            val space = prev.x + prev.width to drawItem.x
            if (space.second - space.first > 30) {
                spaces.add(space)
            }
        }
        val lastItem = items.last()
        val space = lastItem.x + lastItem.width to width
        if (space.second - space.first > 30) {
            spaces.add(space)
        }
    }
}