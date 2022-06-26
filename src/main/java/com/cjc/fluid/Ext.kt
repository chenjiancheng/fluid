package com.cjc.fluid

import android.graphics.PointF
import kotlin.math.sqrt




/**
 * 距离：向量长度
 */
fun PointF.norm(): Double {
    return sqrt((x * x + y * y).toDouble())
}

/**
 * 都是一维数组，向量内积
 */
fun PointF.dot(r: PointF): Double {
    return x * r.x + y * r.y.toDouble()
}


operator fun PointF.div(d: Double): PointF {
    return PointF(this.x / d.toFloat(), this.y / d.toFloat())
}

operator fun PointF.times(d: Double): PointF {
    return PointF(this.x * d.toFloat(), this.y * d.toFloat())
}

operator fun Double.times(cubicKernelDerivative: PointF): PointF {
    return cubicKernelDerivative * this
}